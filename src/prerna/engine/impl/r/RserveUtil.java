package prerna.engine.impl.r;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rosuda.REngine.Rserve.RConnection;

import com.google.common.base.Strings;

import prerna.reactor.mgmt.MgmtUtil;
import prerna.util.Constants;
import prerna.util.Utility;

public class RserveUtil {

	protected static final Logger classLogger = LogManager.getLogger(RserveUtil.class);

	private static final String R_FOLDER = (Utility.getBaseFolder() + "/" + "R" + "/" + "Temp" + "/").replace('\\', '/');
	private static final String R_DATA_EXT = ".RData";

	// R binary location
	private static String rBin;
	private static String rLibs;
	static {
		String rHomeEnv = System.getenv(RSingleton.R_HOME);
		if(rHomeEnv != null && !rHomeEnv.isEmpty()) {
			String rHome = rHomeEnv.replace("\\", "/");
			Path rHomePath = Paths.get(rHome);
			if (!Files.isDirectory(rHomePath)) {
				throw new IllegalArgumentException("R_HOME does not exist or is not a directory");
			}
			rBin = rHome + "/bin/R";
			rBin = rBin.replace("\\", "/");
		} else {
			rBin = "R"; // Just hope its in the path
		}

		String rLibEnv = System.getenv(RSingleton.R_LIBS);
		if(rLibEnv == null || rLibEnv.isEmpty()) {
			rLibEnv = Utility.getDIHelperProperty(RSingleton.R_LIBS);
		}
		if(rLibEnv == null) {
			rLibEnv = "";
		}
		rLibs = rLibEnv;
	}
	
	//////////////////////////////////////////////////////////////////////
	// Rserve properties
	//////////////////////////////////////////////////////////////////////
	
	public static final String IS_USER_RSERVE_KEY = "IS_USER_RSERVE";
	public static final boolean IS_USER_RSERVE = Boolean.parseBoolean(Utility.getDIHelperProperty(IS_USER_RSERVE_KEY) + "");
			
	public static final String R_USER_CONNECTION_TYPE_KEY = "R_USER_CONNECTION_TYPE";
	public static final String R_USER_CONNECTION_TYPE = Utility.getDIHelperProperty(R_USER_CONNECTION_TYPE_KEY) != null
			? Utility.getDIHelperProperty(R_USER_CONNECTION_TYPE_KEY)
			: "undefined";
			
	public static final String RSERVE_CONNECTION_POOL_SIZE_KEY = "RSERVE_CONNECTION_POOL_SIZE";
	public static final int RSERVE_CONNECTION_POOL_SIZE = Utility.getDIHelperProperty(RSERVE_CONNECTION_POOL_SIZE_KEY) != null
			? Integer.parseInt(Utility.getDIHelperProperty(RSERVE_CONNECTION_POOL_SIZE_KEY))
			: 12;
			
	public static final String R_USER_RECOVERY_DEFAULT_KEY = "R_USER_RECOVERY_DEFAULT";
	public static final boolean R_USER_RECOVERY_DEFAULT = Boolean.parseBoolean(Utility.getDIHelperProperty(R_USER_RECOVERY_DEFAULT_KEY) + "");
	
	public static final String R_KILL_ON_STARTUP_KEY = "R_KILL_ON_STARTUP";
	public static final boolean R_KILL_ON_STARTUP = Boolean.parseBoolean(Utility.getDIHelperProperty(R_KILL_ON_STARTUP_KEY) + "");
	
	//////////////////////////////////////////////////////////////////////
	// Connections
	//////////////////////////////////////////////////////////////////////
	public static RConnection connect(String host, int port) throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<RConnection> future = executor.submit(new Callable<RConnection>() {
				@Override
				public RConnection call() throws Exception {
					return new RConnection(host, port);
				}
			});
			return future.get(7L, TimeUnit.SECONDS); 
		} finally {
			executor.shutdownNow();
		}
	}
	
	
	//////////////////////////////////////////////////////////////////////
	// Start and stop
	//////////////////////////////////////////////////////////////////////
	public static Process startR(int port) throws Exception {
		Process process = null;
		// Start
		try {
			String baseFolder = Utility.getBaseFolder();
			File output = new File(baseFolder + "/Rserve.output.log");
			File error = new File(baseFolder + "/Rserve.error.log");

			ProcessBuilder pb;
			if (SystemUtils.IS_OS_WINDOWS) {
				pb = new ProcessBuilder(rBin, "CMD", rLibs+RSingleton.RSERVE_LOC, "--vanilla", "--RS-port", port + "");
			} else if(!(Strings.isNullOrEmpty(Utility.getDIHelperProperty("ULIMIT_R_MEM_LIMIT")))){
				String ulimit = Utility.getDIHelperProperty("ULIMIT_R_MEM_LIMIT");
				pb = new ProcessBuilder("/bin/bash", "-c","ulimit -v " + ulimit + " && " + rBin+" CMD Rserve --vanilla --RS-port " + port);
				List<String> coms = pb.command();
				classLogger.info(Arrays.toString(coms.toArray()));
			} else {
				pb = new ProcessBuilder(rBin, "CMD", "Rserve", "--vanilla", "--RS-port", port + "");
			}
			
			pb.redirectOutput(output);
			pb.redirectError(error);
			process = pb.start();
			process.waitFor(7L, TimeUnit.SECONDS);
			classLogger.info("R starting Rserve on process id >> " + MgmtUtil.getProcessID(process));
			MgmtUtil.printChild(MgmtUtil.getProcessID(process));
		} catch (Exception e) {
			classLogger.error(Constants.STACKTRACE, e);
			throw e;
		}
		return process;
	}
	
	public static void stopR(int port) throws Exception {
		// Stop
		File tempFile = new File(R_FOLDER + Utility.getRandomString(12) + ".txt");
		try {
			if (SystemUtils.IS_OS_WINDOWS) {
				// Dump the output of netstat to a file
				ProcessBuilder pbNetstat = new ProcessBuilder("netstat", "-ano");
				pbNetstat.redirectOutput(tempFile);
				Process processNetstat = pbNetstat.start();
				processNetstat.waitFor(7L, TimeUnit.SECONDS);
				
				// Parse netstat output to get the PIDs of processes running on Rserve's port
				List<String> lines = FileUtils.readLines(tempFile, "UTF-8");
				List<String> pids = lines.stream()
						.filter(l -> l.contains("LISTENING")) // Only grab processes in LISTENING state
						.map(l -> l.trim().split("\\s+")) // Trim the empty characters and split into rows
						.filter(r -> r[1].contains(":" + port)) // Only use those that are listening on the right port 
						.map(r -> r[4]) // Grab the pid
						.collect(Collectors.toList());
				for (String pid : pids) {
					try {
						Integer.parseInt(pid.trim());
					} catch (NumberFormatException e) {
						classLogger.error("pid is not a valid pid");
						classLogger.error(Constants.STACKTRACE, e);
						throw e;
					}
					// Go through and kill these processes
					ProcessBuilder pbTaskkill = new ProcessBuilder("taskkill", "/PID", pid, "/F").inheritIO();
					Process processTaskkill = pbTaskkill.start();
					processTaskkill.waitFor(7L, TimeUnit.SECONDS);
					classLogger.info("Stopped Rserve running on port " + port + " with the pid " + Utility.cleanLogString(pid) + ".");
				}

			} else {
				// Dump the output of lsof to a file
				ProcessBuilder pbLsof = new ProcessBuilder("lsof", "-t", "-i:" + port, "-sTCP:LISTEN");
				pbLsof.redirectOutput(tempFile);
				Process processLsof = pbLsof.start();
				processLsof.waitFor(7L, TimeUnit.SECONDS);
				
				// Parse lsof output to get the PIDs of processes (in this case each line is just the pid)
				List<String> lines = FileUtils.readLines(tempFile, "UTF-8");
				for (String pid : lines) {
					try {
						Integer.parseInt(pid.trim());
					} catch (NumberFormatException e) {
						classLogger.error("pid is not a valid pid");
						classLogger.error(Constants.STACKTRACE, e);
						throw e;
					}
					ProcessBuilder pbKill = new ProcessBuilder("kill", "-9", pid).inheritIO();
					Process processKill = pbKill.start();
					processKill.waitFor(7L, TimeUnit.SECONDS);
					classLogger.info("Stopped Rserve running on port " + port + " with the pid " + Utility.cleanLogString(pid) + ".");
				}
			}
		} finally {
			tempFile.delete();
		}
	}
	
	
	//////////////////////////////////////////////////////////////////////
	// Recovery
	//////////////////////////////////////////////////////////////////////
	public static String getRDataFile(String rDataFileName) {
		return R_FOLDER + rDataFileName + R_DATA_EXT;
	}
	
	public static String getRDataFile(String rootDirectory, String rDataFileName) {
		rootDirectory = rootDirectory.replace('\\', '/');
		if (rootDirectory.endsWith("/")) {
			rootDirectory = rootDirectory.substring(0, rootDirectory.length() - 1);
		}
		return rootDirectory.replace('\\', '/') + '/' + rDataFileName + R_DATA_EXT;
	}
	
	
	//////////////////////////////////////////////////////////////////////
	// End All R
	//////////////////////////////////////////////////////////////////////
	/**
	 * Stops all r processes.
	 * @throws Exception
	 */
	public static void endR() throws Exception {
		// Need to allow this process to execute the below commands
//		SecurityManager priorManager = System.getSecurityManager();
//		System.setSecurityManager(null);
		
		// End
		try {
			ProcessBuilder pb;
			if (SystemUtils.IS_OS_WINDOWS) {
				pb = new ProcessBuilder("taskkill", "/f", "/IM", "Rserve.exe");
			} else {
				pb = new ProcessBuilder("pkill", "Rserve");
			}
			Process process = pb.start();
			process.waitFor(7L, TimeUnit.SECONDS);
		} finally {
			
			// Restore the prior security manager
//			System.setSecurityManager(priorManager);
		}
	}
	
}
