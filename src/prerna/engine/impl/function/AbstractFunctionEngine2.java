package prerna.engine.impl.function;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import prerna.engine.api.IEngine;
import prerna.engine.api.IReactorEngine;
import prerna.engine.impl.SmssUtilities;
import prerna.reactor.AbstractReactor;
import prerna.util.Constants;
import prerna.util.EngineUtility;
import prerna.util.UploadUtilities;
import prerna.util.Utility;

public abstract class AbstractFunctionEngine2 extends AbstractReactor implements IReactorEngine {

	private static final Logger classLogger = LogManager.getLogger(AbstractFunctionEngine2.class);

	private String engineId;
	private String engineName;
	
	private String smssFilePath;
	protected Properties smssProp;
	
	protected String functionName;
	protected String functionDescription;
	protected List<FunctionParameter> parameters;
	protected List<String> requiredParameters;
	protected Map vars = new HashMap();
	
	@Override
	public void open(String smssFilePath) throws Exception {
		setSmssFilePath(smssFilePath);
		open(Utility.loadProperties(smssFilePath));
	}
	
	@Override
	public void open(Properties smssProp) throws Exception {
		setSmssProp(smssProp);

		if(!smssProp.containsKey(IReactorEngine.NAME_KEY)) {
			throw new IllegalArgumentException("Must have key " + IReactorEngine.NAME_KEY + " in SMSS");
		}
		if(!smssProp.containsKey(IReactorEngine.DESCRIPTION_KEY)) {
			throw new IllegalArgumentException("Must have key " + IReactorEngine.DESCRIPTION_KEY + " in SMSS");
		}

		this.functionName = smssProp.getProperty(IReactorEngine.NAME_KEY);
		this.functionDescription = smssProp.getProperty(IReactorEngine.DESCRIPTION_KEY);
		
		if(smssProp.containsKey(IReactorEngine.PARAMETER_KEY)) {
			this.parameters = new Gson().fromJson(smssProp.getProperty(IReactorEngine.PARAMETER_KEY), new TypeToken<List<FunctionParameter>>() {}.getType());
		}
		
		if(smssProp.containsKey(IReactorEngine.REQUIRED_PARAMETER_KEY)) {
			this.requiredParameters = new Gson().fromJson(smssProp.getProperty(IReactorEngine.REQUIRED_PARAMETER_KEY), new TypeToken<List<String>>() {}.getType());
		}
		this.vars = new HashMap<>(this.smssProp);
	}

	@Override
	public void delete() throws IOException {
		classLogger.debug("Delete function engine " + SmssUtilities.getUniqueName(this.engineName, this.engineId));
		try {
			this.close();
		} catch(IOException e) {
			classLogger.warn("Error occurred trying to close service engine");
			classLogger.error(Constants.STACKTRACE, e);
		}
		
		File engineFolder = new File(
				EngineUtility.getSpecificEngineBaseFolder
					(IEngine.CATALOG_TYPE.FUNCTION, this.engineId, this.engineName)
				);
		try {
			FileUtils.deleteDirectory(engineFolder);
		} catch (IOException e) {
			classLogger.error(Constants.STACKTRACE, e);
		}

		classLogger.debug("Deleting smss " + this.smssFilePath);
		File smssFile = new File(this.smssFilePath);
		try {
			FileUtils.forceDelete(smssFile);
		} catch(IOException e) {
			classLogger.error(Constants.STACKTRACE, e);
		}

		// remove from DIHelper
		UploadUtilities.removeEngineFromDIHelper(this.engineId);
	}
	
	@Override
	public JSONObject getFunctionDefintionJson() {
		JSONObject json = new JSONObject();
		json.put("name", this.functionName);
		json.put("description", this.functionDescription);
		
		JSONObject parameterJSON = new JSONObject();
		if(this.parameters != null && !this.parameters.isEmpty()) {
			parameterJSON.put("type", "object");
			JSONObject propertiesJSON = new JSONObject();
			for(FunctionParameter fParam : this.parameters) {
				JSONObject thisPropJSON = new JSONObject();
				thisPropJSON.put("type", fParam.getParameterType());
				thisPropJSON.put("description", fParam.getParameterDescription());
				propertiesJSON.put(fParam.getParameterName(), thisPropJSON);
			}
			parameterJSON.put("properties", propertiesJSON);
		}
		json.put("parameters", parameterJSON);
		
		JSONArray requiredJSON = new JSONArray();
		if(this.requiredParameters != null && !this.requiredParameters.isEmpty()) {
			requiredJSON.put(this.requiredParameters);
		}
		json.put("required", requiredJSON);
		
		return json;
	}

	@Override
	public void setEngineId(String engineId) {
		this.engineId = engineId;
	}

	@Override
	public String getEngineId() {
		return this.engineId;
	}

	@Override
	public void setEngineName(String engineName) {
		this.engineName = engineName;
	}

	@Override
	public String getEngineName() {
		return this.engineName;
	}
	
	@Override
	public String getFunctionName() {
		return functionName;
	}

	@Override
	public String getFunctionDescription() {
		return functionDescription;
	}

	@Override
	public void setFunctionDescription(String functionDescription) {
		this.functionDescription = functionDescription;
	}

	@Override
	public List<FunctionParameter> getParameters() {
		return parameters;
	}


	@Override
	public List<String> getRequiredParameters() {
		return this.requiredParameters;
	}
		
	@Override
	public void setSmssFilePath(String smssFilePath) {
		this.smssFilePath = smssFilePath;
	}

	@Override
	public String getSmssFilePath() {
		return this.smssFilePath;
	}

	@Override
	public void setSmssProp(Properties smssProp) {
		this.smssProp = smssProp;
	}

	@Override
	public Properties getSmssProp() {
		return this.smssProp;
	}

	@Override
	public Properties getOrigSmssProp() {
		return this.smssProp;
	}

	@Override
	public CATALOG_TYPE getCatalogType() {
		return IEngine.CATALOG_TYPE.FUNCTION;
	}

	@Override
	public String getCatalogSubType(Properties smssProp) {
		return "REST";
	}

	@Override
	public boolean holdsFileLocks() {
		return false;
	}

	//////////////////////////////////////////////////////////////
	public String fillVars(String input) {
		StringSubstitutor sub = new StringSubstitutor(vars);
		String resolvedString = sub.replace(input);
		return resolvedString;
	}

	
	
	
	
	
	
}
