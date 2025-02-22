package prerna.tcp.client;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.StreamingOutput;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;

import prerna.auth.User;
import prerna.om.Insight;
import prerna.reactor.job.JobReactor;
import prerna.sablecc2.PixelRunner;
import prerna.sablecc2.PixelStreamUtility;
import prerna.sablecc2.comm.JobManager;
import prerna.sablecc2.om.execptions.SemossPixelException;
import prerna.tcp.PayloadStruct;
import prerna.tcp.TCPLogMessage;
import prerna.tcp.client.workers.NativePyEngineWorker;
import prerna.util.Constants;
import prerna.util.DIHelper;
import prerna.util.Utility;

public class NativePySocketClient extends SocketClient implements Runnable, Closeable  {
	
	private static final Logger classLogger = LogManager.getLogger(NativePySocketClient.class);
	
    private String HOST = null;
    private int PORT = -1;
    private boolean SSL = false;
    
    //Map requestMap = new HashMap();
    //Map responseMap = new HashMap();
    
    private boolean ready = false;
    private boolean connected = false;
    private AtomicInteger count = new AtomicInteger(0);
    private long averageMillis = 200;
    private boolean killall = false; // use this if the server is dead or it has crashed
    private User user;
	
    private Socket clientSocket = null;
	//InputStream is = null;
	//OutputStream os = null;
	//SocketClientHandler sch = new SocketClientHandler();
	private Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

	/**
	 * 
	 */
    public void connect(final String HOST, final int PORT, final boolean SSL) {
    	this.HOST = HOST;
    	this.PORT = PORT;
    	this.SSL = SSL;
    }
    
    @Override
    public void close() {
    	if(this.requestMap != null) {
    		this.requestMap.clear();
    	}
    	closeStream(this.os);
    	closeStream(this.is);
    	closeStream(this.clientSocket);
    	this.connected = false;
    	this.killall = true;
    }
    
    /**
     * 
     */
    public void run()	
    {
    	// there is 2 portions to the run
    	// one is before connect
    	// one is after. The reason this is done is to avoid an extra handler for information
    	
        // Configure SSL.git
    	if(!connected && !killall)
    	{
	    	int attempt = 1;
	    	int SLEEP_TIME = 800;
	    	if(DIHelper.getInstance().getProperty("SLEEP_TIME") != null) {
	    		SLEEP_TIME = Integer.parseInt(DIHelper.getInstance().getProperty("SLEEP_TIME"));
	    	}
	    	
	    	classLogger.info("Trying with the sleep time of " + SLEEP_TIME);
	    	while(!connected && attempt < 6) // I do an attempt here too hmm.. 
	    	{
		    	try
		    	{
		    		clientSocket =  new Socket(this.HOST, this.PORT);
		    		
		    		// pick input and output stream and start the threads
		    		this.is = clientSocket.getInputStream();
		    		this.os = clientSocket.getOutputStream();
		            classLogger.info("CLIENT Connection complete !!!!!!!");

		            Thread.sleep(100); // sleep some before executing command
		            // prime it 
		            //classLogger.info("First command.. Prime" + executeCommand("2+2"));
		            connected = true;
		            ready = true;
		            killall = false;
		            synchronized(this)
		            {
		            	this.notifyAll();
		            }
		    	} catch(Exception ex) {
		    		attempt++;
		    		classLogger.info("Attempting Number " + attempt);
		    		// see if sleeping helps ?
		    		try {
		    			// sleeping only for 1 second here
		    			// but the py executor sleeps in 2 second increments
		    			Thread.sleep(attempt*SLEEP_TIME);
		    		} catch(Exception ex2) {
		    			// ignored
		    		}
		    	}
	    	}
	    	
	    	if(attempt > 6) {
	            classLogger.error("CLIENT Connection Failed !!!!!!!");
	            killall = true;
	            connected = false;
	            ready = false;
	            synchronized(this)
	            {
	            	this.notifyAll();
	            }
	            throw new IllegalArgumentException("Failed to connect to your isolated analytics engine");
	    	}
    	}
    	
    	// this is the read portion
    	if(connected)
    	{
    		StringBuilder partialAssimilator = new StringBuilder("");
    		StringBuilder outputAssimilator = new StringBuilder("");
    		while (!killall) 
    		{
    			try {
    				byte[] length = new byte[4];
    				is.read(length);

    				int size = ByteBuffer.wrap(length).getInt();
    				//System.err.println("Incoming data is of size " + size);
    				
    				if(size > 0)
    				{
	    				byte[] msg = new byte[size];
	    				int size_read = 0;
	    				while(size_read < size)
	    				{
	    					int to_read = size - size_read;
	    					byte [] newMsg = new byte[to_read];
	    					int cur_size = is.read(newMsg);
	    					System.arraycopy(newMsg, 0, msg, size_read, cur_size);
	    					size_read = size_read + cur_size;
	    					//System.out.println("incoming size " + size + "  read size.. " + size_read);
	    				}
	
	    				String message = new String(msg);
	    				//System.err.print(message);
	    				PayloadStruct ps = gson.fromJson(message, PayloadStruct.class);
	    				PayloadStruct lock = (PayloadStruct) requestMap.get(ps.epoc);
	    				classLogger.debug("incoming payload " + ps);

	    				// std out no questions
	    				if(ps.operation == ps.operation.STDOUT && ps.payload != null && !ps.response)
	    				{
	    					//classLogger.info(ps.payload[0]);
	    					//classLogger.info("Standard output");
	    					String logMessage = (String) ps.payload[0];
	    					outputAssimilator.append(ps.payload[0]);

	    					if (logMessage.startsWith("SMSS_PYTHON_LOGGER<==<>==>")) {
	    						
	    						String logMessagePayload = logMessage.split("<==<>==>")[1];
	    						TCPLogMessage tcpLogMessage = gson.fromJson(logMessagePayload, TCPLogMessage.class);
	    						
	    						String py_log = tcpLogMessage.name + ":" + tcpLogMessage.lineNumber + " " + tcpLogMessage.message;
	    						
	    						switch(tcpLogMessage.stack) {
		    						case "FRONTEND":
		    							exposeLog(logMessage, lock.insightId);
		    					        break;
		    					    case "BACKEND":
			    					    switch(tcpLogMessage.levelName) {
			    					    	case "DEBUG":
			    					    		// if python enabled debug level, then just add the debug flag to info
			    					    		classLogger.info("DEBUG " + py_log);
			    					    		break;
			    					    	case "INFO":
				    					    	classLogger.info(py_log);
				    					        break;
				    					    case "WARNING":
				    					    	classLogger.warn(py_log);
				    					        break;
				    					    case "ERROR":
				    					    case "CRITICAL":
				    					    	classLogger.error(py_log);
				    					        break;
			    						}
		    					        break;
	    						}	    						
	    					} else if (lock != null) {
	    						exposeLog(logMessage, lock.insightId);
	    					} 
	    				}
	    				
	       				// need some way to say this is the output from the actual python vs. something that is a classLogger
	    				// this is done through interim and operations
	    				// partial stdout
	    				// i.e. response is true and it is being sent as a stdout
	    				else if(ps.response && ps.operation == ps.operation.STDOUT)
	    				{
	    					//classLogger.info("Partial Response from the py");
	    					// need to return output here
	    					if(ps.payload != null && !((String)ps.payload[0]).equalsIgnoreCase("NONE"))
	    					{
	    						partialAssimilator.append(ps.payload[0] + "");
	    						if(lock != null && lock.insightId != null)
	    							JobManager.getManager().addPartialOut(lock.insightId, ps.payload[0]+"");
	    					}
	    					if(!ps.interim)
	    					{
	    						//System.err.println("Final message.. ");
		    					classLogger.info("FINAL PARTIALs OUTPUT <<<<<<<" + partialAssimilator + ">>>>>>>>>>>>");
		    					// re-initialize it

	    					}
	    				}
	    				// this is the response.. i.e. the full response
	    				else if(ps.response)
	    				{
	    					//classLogger.info("Response from the py");
	    					//System.err.println("This is working as designed");
	    					if(ps != null 
	    							&& ps.payload !=null 
	    							&& ps.payload.length > 0 
	    							&& ps.payload[0] != null 
	    							&& ps.payload[0].toString().equalsIgnoreCase("None")
	    						) {
	    						if(partialAssimilator.length() > 0)
	    							ps.payload = new String[] {partialAssimilator.toString()};
	    						else 
	    							ps.payload = new String[] {outputAssimilator.toString()};
	    					}
	    					ps.epoc = ps.epoc.trim();
		    				lock = (PayloadStruct)requestMap.remove(ps.epoc);

	    					// try to convert it into a full object
	    					// need to check if it is primitive before converting
	    					// try to convert it into a full object
	    					try {
	    						if(ps.payload[0] != null) { 
			    					if(ps.payload[0] instanceof String) {
		    							Object obj = gson.fromJson((String)ps.payload[0], Object.class);
				    					ps.payload[0] = obj;
			    					}
	    						}
	    					} catch(Exception ignored) {
	    						classLogger.warn("Ignoring unable to gson.fromJson() of " + ps.payload[0]);
	    					}
	    					// put it in response
	    					responseMap.put(ps.epoc, ps);
	    					if(lock != null)
	    					{
	    						synchronized(lock)
	    						{
	    							lock.notifyAll();
	    						}
	    					}
	    					outputAssimilator = new StringBuilder("");
	    					partialAssimilator = new StringBuilder("");

	    				}
	    				// this is a request
	    				else if(ps.operation == ps.operation.ENGINE)
	    				{
	    					//classLogger.info("reverse request for data");
	    					// this is a request we need to process
	    					// need a way here to also push the payload classes
	    					// will come to it in a bit
	    					// clean up the payload struct a little
	    					ps = convertPayloadClasses(ps);
	    					processEngineRequest(ps);
	    				}
	    				// this is a request for a reactor
	    				else if(ps.operation == ps.operation.REACTOR)
	    				{
	    					ByteArrayOutputStream output = new ByteArrayOutputStream();
	    					try {
		    					String insightId = ps.insightId;
								Insight insight = insightMap.get(insightId);
		    					String pixelOp = (String) ps.payload[0];
		    					PixelRunner pixelRunner = insight.runPixel(pixelOp);
		    					StreamingOutput streamedOutput = PixelStreamUtility.collectPixelData(pixelRunner);
		    					streamedOutput.write(output);
		    					JsonElement json = JsonParser.parseString(new String(output.toByteArray(),"UTF-8"));
		    					ps.payload = new Object[] {json};
		    					ps.response = true;
		    					executeCommand(ps);
	    					} catch(Exception e) {
    							ps.response = true;
    							ps.ex = "An error occurred running the pixel";
    							// return the error
    							executeCommand(ps);
	    					} finally {
	    						output.close();
	    					}
	    				}
	    				// unhandled pieces.. nothing we can do here.. just give the response back
	    				// so we dont choke the thread
	    				else
	    				{
	    					classLogger.info("message not handled by py server");
		    				lock = (PayloadStruct)requestMap.remove(ps.epoc);
	    					responseMap.put(ps.epoc, ps);
	    					if(lock != null)
	    					{
	    						synchronized(lock)
	    						{
	    							lock.notifyAll();
	    						}
	    					}

	    				}
    				}
    				else
    				{
    					killall = true;
    					break;
    				}
    			}catch (SocketException ex1)
    			{
    				classLogger.error(Constants.STACKTRACE, ex1);
    				crash();
    				break;
    				
    			}catch (Exception ex) {
    				classLogger.error(Constants.STACKTRACE, ex);
//    				killall = true;
//    				connected=false;
//    				break;
    			}
    		}
    		connected = false;
    		System.err.println("NativePySocketClient is disconnected");
    		classLogger.warn("NativePySocketClient is disconnected");
    	}
    }
    
    private void processEngineRequest(PayloadStruct ps)
    {
		String insightId = ps.insightId;
		if(insightId == null || (insightId=insightId.trim()).isEmpty() || insightId.equals("${i}")) {
			ps.response = true;
			ps.ex = "Insight Id is undefined or null";
			// return the error
			executeCommand(ps);
			return;
		}
		Insight insight = insightMap.get(insightId);
				
		user = insight.getUser();
		if(user == null)
		{
			ps.response = true;
			ps.ex = "There is no user associated with this insight id";
			// return the error
			executeCommand(ps);
			return;
		}
		else
		{
			NativePyEngineWorker worker = new NativePyEngineWorker(this.getUser(), ps, insight);
			worker.run();
			executeCommand(worker.getOutput());
		}
    }
    
    private PayloadStruct convertPayloadClasses(PayloadStruct input)
    {
    	if(input.payloadClassNames != null)
    	{
    		input.payloadClasses = new Class[input.payloadClassNames.length];
    		for(int classIndex = 0;classIndex < input.payloadClassNames.length;classIndex++)
    		{
    			try {
					String className = input.payloadClassNames[classIndex];
					input.payloadClasses[classIndex] = Class.forName(className);
					if(input.payloadClasses[classIndex] == Insight.class)
					{
						String insightId = input.insightId;
						Insight insight = insightMap.get(insightId);
						input.payload[classIndex] = insight;
					}
				} catch (ClassNotFoundException e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
    		}
    	}
    	return input;
    }
    
    // this is the method that pushes to the front end
    // when output happens
    private void exposeLog(String data, String insightId) {
    	if(insightId != null && data != null) {
    		Insight insight = insightMap.get(insightId);
    		String jobId =  insight.getVarStore().get(JobReactor.JOB_KEY).getValue().toString();
    		JobManager.getManager().addStdOut(jobId, data);
    	}
    }
    
    public Object executeCommand(PayloadStruct ps)
    {
    	if(killall) {
        	throw new SemossPixelException("Analytic engine is no longer available. This happened because you exceeded the memory limits provided or performed an illegal operation. Please relook at your recipe");
    	}
    	
    	if(!connected) {
        	throw new SemossPixelException("Your micro-process is not available. Please logout and try again. !");
    	}
    	
    	String id = ps.epoc;
    	if(!ps.response || id == null)
    	{
	    	id = "ps"+ count.getAndIncrement();
	    	ps.epoc = id;
    	}
    	ps.longRunning = true;
    	    	
    	synchronized(ps) // going back to single threaded .. earlier it was ps
    	{	
    		//if(ps.hasReturn)
    		// put it into request map
    		if(!ps.response) {
    			requestMap.put(id, ps);
    		}
    		writePayload(ps);
			classLogger.debug("outgoing payload " + ps.epoc);

	    	// send the message
    		// time to wait = average time * 10
    		// if this is a request wait for it
    		if(!ps.response) // this is a response to something the socket has asked
    		{
				int pollNum = 1; // 1 second
				while(!responseMap.containsKey(ps.epoc) && (pollNum <  10 || ps.longRunning) && !killall)
				{
					//classLogger.info("Checking to see if there was a response");
					try {
						if(pollNum < 10) {
							ps.wait(averageMillis);
						} else { //if(ps.longRunning) // this is to make sure the kill all is being checked
							ps.wait(); // wait eternally - we dont know how long some of the load operations would take besides, I am not sure if the null gets us anything
						}
						pollNum++;
					} catch (InterruptedException e) {
						classLogger.error(Constants.STACKTRACE, e);
					}
				}
				if(!responseMap.containsKey(ps.epoc) && ps.hasReturn)
				{
					classLogger.info("Timed out for epoc " + ps.epoc + " " + ps.methodName);
					
				}
    		}

			// after 10 seconds give up
			//printUnprocessed();
			return responseMap.remove(ps.epoc);
    	}
    }
    
    private void writePayload(PayloadStruct ps)
    {
    	// nulling the classes so they dont screw up json
    	ps.payloadClasses = null;
    	try
    	{
    		String jsonPS = gson.toJson(ps);
    		byte [] psBytes = pack(jsonPS, ps.epoc);
    		try {
    			os.write(psBytes);
    		} catch(IOException ex) {
    		classLogger.error(Constants.STACKTRACE, ex);
    		//crash();
    		}
    	}catch(Exception ex)
    	{
    		classLogger.error(Constants.STACKTRACE, ex);
    	}
    }
    
	public byte[] pack(String message, String epoc) {
		byte[] psBytes = message.getBytes(StandardCharsets.UTF_8);

		// get the length
		int length = psBytes.length;

		//System.err.println("Packing with length " + length);

		// make this into array
		byte[] lenBytes = ByteBuffer.allocate(4).putInt(length).array();
		
		byte[] epocBytes = ByteBuffer.allocate(20).put(epoc.getBytes(StandardCharsets.UTF_8)).array();

		
		// pack both of these
		byte[] finalByte = new byte[psBytes.length + lenBytes.length + epocBytes.length];

		for (int lenIndex = 0; lenIndex < lenBytes.length; lenIndex++)
			finalByte[lenIndex] = lenBytes[lenIndex];

		// write the epoc bytes
		for (int lenIndex = 0; lenIndex < epocBytes.length; lenIndex++)
			finalByte[lenIndex + lenBytes.length] = epocBytes[lenIndex];
		
		for (int lenIndex = 0; lenIndex < psBytes.length; lenIndex++)
			finalByte[lenIndex + lenBytes.length + epocBytes.length] = psBytes[lenIndex];

		return finalByte;

	}

    
    private void writeEmptyPayload()
    {
    	PayloadStruct ps = new PayloadStruct();
    	ps.epoc=Utility.getRandomString(8);
    	ps.methodName = "EMPTYEMPTYEMPTY";
    	writePayload(ps);
    }
    

    public void writeReleaseAllPayload()
    {
    	PayloadStruct ps = new PayloadStruct();
    	ps.epoc=Utility.getRandomString(8);
    	ps.methodName = "RELEASE_ALL";
    	writePayload(ps);
    }

    
    /**
     * Logout of py server
     */
    @Override
    public boolean stopPyServe() {
    	try {
    		if(isConnected()) {
    			ExecutorService executor = Executors.newSingleThreadExecutor();

    			Callable<Boolean> callableTask = () -> {
    				PayloadStruct ps = new PayloadStruct();
    				ps.epoc = "stop_all";
    				ps.hasReturn = false;
    				ps.methodName = "CLOSE_ALL_LOGOUT<o>";
    				ps.payload = new String[] { "CLOSE_ALL_LOGOUT<o>"};
    				ps.operation = PayloadStruct.OPERATION.CMD;
    				writePayload(ps);
    				return true;
    			};

    			Future<Boolean> future = executor.submit(callableTask);
    			try {
    				// wait 1 minute at most
    				boolean result = future.get(60, TimeUnit.SECONDS);
    				classLogger.info("Stop PyServe result = " + result);
    				return result;
    			} catch (TimeoutException e) {
    				classLogger.warn("Not able to release the payload structs within a timely fashion");
    				future.cancel(true);
    				return false;
    			} catch (InterruptedException | ExecutionException e) {
    				classLogger.error(Constants.STACKTRACE, e);
    				return false;
    			} finally {
    				executor.shutdown();
    			}
    		} else {
    			return true;
    		}
    	} finally {
    		// always call close on the IO
    		close();
    	}
    }

    /**
     * 
     */
    public void crash() {
    	// this happens when the client has completely crashed
    	// make the connected to be false
    	// take everything that is waiting on it
    	// go through request map and start pushing
    	
    	// run as executor since it is synchronized
    	// and dont want to get stuck if an issue occurs and the notify never happens
    	// we will close and kill process anyway
    	ExecutorService executor = Executors.newSingleThreadExecutor();
    	
        Callable<String> callableTask = () -> {
        	try {
    	    	for(Object k : this.requestMap.keySet()) {
    	    		PayloadStruct ps = (PayloadStruct) this.requestMap.get(k);
    	    		classLogger.debug("Releasing <" + k + "> <" + ps.methodName + ">");
    	    		ps.ex = "Server has crashed. This happened because you exceeded the memory limits provided or performed an illegal operation. Please relook at your recipe";
    	    		synchronized(ps) {
    	    			ps.notifyAll();
    	    		}
    	    	}
        	} catch(Exception e) {
        		classLogger.error(Constants.STACKTRACE, e);
        	}
            return "Successfully released the payload structs";
        };

        Future<String> future = executor.submit(callableTask);
        try {
        	// wait 1 minute at most
            String result = future.get(60, TimeUnit.SECONDS);
            classLogger.info(result);
        } catch (TimeoutException e) {
        	classLogger.warn("Not able to release the payload structs within a timely fashion");
            future.cancel(true); 
        } catch (InterruptedException | ExecutionException e) {
        	classLogger.error(Constants.STACKTRACE, e);
        } finally {
            executor.shutdown();
        }
    	
    	this.close();
    	throw new SemossPixelException("Analytic engine is no longer available. This happened because you exceeded the memory limits provided or performed an illegal operation. Please relook at your recipe");
    }
    
    /**
     * 
     * @param closeThis
     */
    private void closeStream(Closeable closeThis) {
    	if(closeThis != null) {
	    	try {
				closeThis.close();
			} catch (IOException e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
    	}
    }
    
    /**
     * 
     * @param user
     */
    public void setUser(User user) {
    	this.user = user;
    }

    /**
     * 
     * @return
     */
    public User getUser() {
    	return this.user;
    }
    
    /**
     * 
     * @return
     */
    public boolean isConnected() {
    	return this.connected;
    }
    
    /**
     * 
     * @return
     */
    public boolean isReady() {
    	return this.ready;
    }
}
