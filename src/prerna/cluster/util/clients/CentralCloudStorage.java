package prerna.cluster.util.clients;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import prerna.auth.utils.SecurityEngineUtils;
import prerna.auth.utils.SecurityProjectUtils;
import prerna.auth.utils.WorkspaceAssetUtils;
import prerna.cluster.util.ClusterSynchronizer;
import prerna.cluster.util.ClusterUtil;
import prerna.engine.api.IDatabaseEngine;
import prerna.engine.api.IEngine;
import prerna.engine.api.IEngine.CATALOG_TYPE;
import prerna.engine.impl.AbstractDatabaseEngine;
import prerna.engine.impl.SmssUtilities;
import prerna.engine.impl.owl.WriteOWLEngine;
import prerna.engine.impl.storage.AbstractRCloneStorageEngine;
import prerna.engine.impl.storage.AzureBlobStorageEngine;
import prerna.engine.impl.storage.GoogleCloudStorageEngine;
import prerna.engine.impl.storage.LocalFileSystemStorageEngine;
import prerna.engine.impl.storage.MinioStorageEngine;
import prerna.engine.impl.storage.S3StorageEngine;
import prerna.project.api.IProject;
import prerna.project.impl.ProjectHelper;
import prerna.util.AssetUtility;
import prerna.util.Constants;
import prerna.util.DIHelper;
import prerna.util.EngineSyncUtility;
import prerna.util.EngineUtility;
import prerna.util.ProjectSyncUtility;
import prerna.util.ProjectWatcher;
import prerna.util.SMSSNoInitEngineWatcher;
import prerna.util.SMSSWebWatcher;
import prerna.util.Utility;
import prerna.util.sql.RdbmsTypeEnum;

public class CentralCloudStorage implements ICloudClient {

	private static final Logger classLogger = LogManager.getLogger(CentralCloudStorage.class);

	public static final String DATABASE_BLOB = "semoss-db";
	public static final String STORAGE_BLOB = "semoss-storage";
	public static final String MODEL_BLOB = "semoss-model";
	public static final String VECTOR_BLOB = "semoss-vector";
	public static final String FUNCTION_BLOB = "semoss-function";
	public static final String VENV_BLOB = "semoss-venv";
	public static final String PROJECT_BLOB = "semoss-project";
	public static final String USER_BLOB = "semoss-user";
	// images
	public static final String DB_IMAGES_BLOB = "semoss-dbimagecontainer";
	public static final String STORAGE_IMAGES_BLOB = "semoss-storageimagecontainer";
	public static final String MODEL_IMAGES_BLOB = "semoss-modelimagecontainer";
	public static final String VECTOR_IMAGES_BLOB = "semoss-vectorimagecontainer";
	public static final String FUNCTION_IMAGES_BLOB = "semoss-functionimagecontainer";
	public static final String VENV_IMAGES_BLOB = "semoss-venvimagecontainer";
	public static final String PROJECT_IMAGES_BLOB = "semoss-projectimagecontainer";

	private static CentralCloudStorage instance = null;
	private static AbstractRCloneStorageEngine centralStorageEngine = null;
	
	private static final String FILE_SEPARATOR = java.nio.file.FileSystems.getDefault().getSeparator();
	public static final String SMSS_POSTFIX = "-smss";

	// these can change based on the cloud client type
	private static String DB_CONTAINER_PREFIX = "/" + DATABASE_BLOB + "/";
	private static String STORAGE_CONTAINER_PREFIX = "/" + STORAGE_BLOB + "/";
	private static String MODEL_CONTAINER_PREFIX = "/" + MODEL_BLOB + "/";
	private static String VECTOR_CONTAINER_PREFIX = "/" + VECTOR_BLOB + "/";
	private static String FUNCTION_CONTAINER_PREFIX = "/" + FUNCTION_BLOB + "/";
	private static String VENV_CONTAINER_PREFIX = "/" + VENV_BLOB + "/";
	private static String PROJECT_CONTAINER_PREFIX = "/" + PROJECT_BLOB + "/";
	private static String USER_CONTAINER_PREFIX = "/" + USER_BLOB + "/";
	
	/**
	 * 
	 * @throws Exception
	 */
	private CentralCloudStorage() throws Exception {
		buildStorageEngine();
	}
	
	public static CentralCloudStorage getInstance() throws Exception {
		if(instance != null) {
			return instance;
		}
		
		if(instance == null) {
			synchronized (CentralCloudStorage.class) {
				if(instance != null) {
					return instance;
				}
				
				String baseFolder = Utility.getBaseFolder().replace("\\", "/");
				if(!baseFolder.endsWith("/")) {
					baseFolder += "/";
				}
				instance = new CentralCloudStorage();
				
				// instantiate the sync
				if(ClusterUtil.IS_CLUSTER_ZK) {
					ClusterSynchronizer.getInstance();		
				}
	
				
			}
		}
		
		return instance;
	}
	
	
	private static synchronized void buildStorageEngine() throws Exception {
		Properties props = new Properties();
		AppCloudClientProperties clientProps = new AppCloudClientProperties();
		if(ClusterUtil.STORAGE_PROVIDER == null || ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("AZURE")){
			
			centralStorageEngine = new AzureBlobStorageEngine();
			propertiesMigratePut(props, AzureBlobStorageEngine.AZ_ACCOUNT_NAME, clientProps, AbstractClientBuilder.AZ_NAME);
			propertiesMigratePut(props, AzureBlobStorageEngine.AZ_PRIMARY_KEY, clientProps, AbstractClientBuilder.AZ_KEY);
			propertiesMigratePut(props, AzureBlobStorageEngine.AZ_CONN_STRING, clientProps, AbstractClientBuilder.AZ_CONN_STRING);
			propertiesMigratePut(props, AzureBlobStorageEngine.AZ_GENERATE_DYNAMIC_SAS, clientProps, AbstractClientBuilder.AZ_GENERATE_DYNAMIC_SAS);

			
			// we have a different structure for AZ storage since it doesn't represent the blobs as folders
			CentralCloudStorage.DB_CONTAINER_PREFIX = "db-";
			CentralCloudStorage.STORAGE_CONTAINER_PREFIX = "semoss-storage";
			CentralCloudStorage.MODEL_CONTAINER_PREFIX = "semoss-model";
			CentralCloudStorage.VECTOR_CONTAINER_PREFIX = "semoss-vector";
			CentralCloudStorage.FUNCTION_CONTAINER_PREFIX = "semoss-function";
			CentralCloudStorage.VENV_CONTAINER_PREFIX = "semoss-venv";
			CentralCloudStorage.PROJECT_CONTAINER_PREFIX = "project-";
			CentralCloudStorage.USER_CONTAINER_PREFIX = "user-";
			
		}
		else if(ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("AWS") ||
				ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("S3")){
			
			centralStorageEngine = new S3StorageEngine();
			propertiesMigratePut(props, S3StorageEngine.S3_REGION_KEY, clientProps, AbstractClientBuilder.S3_REGION_KEY);
			propertiesMigratePut(props, S3StorageEngine.S3_BUCKET_KEY, clientProps, AbstractClientBuilder.S3_BUCKET_KEY);
			propertiesMigratePut(props, S3StorageEngine.S3_ACCESS_KEY, clientProps, AbstractClientBuilder.S3_ACCESS_KEY);
			propertiesMigratePut(props, S3StorageEngine.S3_SECRET_KEY, clientProps, AbstractClientBuilder.S3_SECRET_KEY);

		} 
		else if(ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("MINIO")){
			
			centralStorageEngine = new MinioStorageEngine();
			propertiesMigratePut(props, MinioStorageEngine.MINIO_REGION_KEY, clientProps, AbstractClientBuilder.S3_REGION_KEY);
			propertiesMigratePut(props, MinioStorageEngine.MINIO_BUCKET_KEY, clientProps, AbstractClientBuilder.S3_BUCKET_KEY);
			propertiesMigratePut(props, MinioStorageEngine.MINIO_ACCESS_KEY, clientProps, AbstractClientBuilder.S3_ACCESS_KEY);
			propertiesMigratePut(props, MinioStorageEngine.MINIO_SECRET_KEY, clientProps, AbstractClientBuilder.S3_SECRET_KEY);
			propertiesMigratePut(props, MinioStorageEngine.MINIO_ENDPOINT_KEY, clientProps, AbstractClientBuilder.S3_ENDPOINT_KEY);

			if(!props.containsKey(MinioStorageEngine.MINIO_REGION_KEY)) {
				propertiesMigratePut(props, MinioStorageEngine.MINIO_REGION_KEY, clientProps, MinioStorageEngine.MINIO_REGION_KEY);
			}
			if(!props.containsKey(MinioStorageEngine.MINIO_BUCKET_KEY)) {
				propertiesMigratePut(props, MinioStorageEngine.MINIO_BUCKET_KEY, clientProps, MinioStorageEngine.MINIO_BUCKET_KEY);
			}
			if(!props.containsKey(MinioStorageEngine.MINIO_ACCESS_KEY)) {
				propertiesMigratePut(props, MinioStorageEngine.MINIO_ACCESS_KEY, clientProps, MinioStorageEngine.MINIO_ACCESS_KEY);
			}
			if(!props.containsKey(MinioStorageEngine.MINIO_SECRET_KEY)) {
				propertiesMigratePut(props, MinioStorageEngine.MINIO_SECRET_KEY, clientProps, MinioStorageEngine.MINIO_SECRET_KEY);
			}
			if(!props.containsKey(MinioStorageEngine.MINIO_ENDPOINT_KEY)) {
				propertiesMigratePut(props, MinioStorageEngine.MINIO_ENDPOINT_KEY, clientProps, MinioStorageEngine.MINIO_ENDPOINT_KEY);
			}
		} 
		else if(ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("GCS") ||
				ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("GCP") ||
				ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("GOOGLE")){
			
			centralStorageEngine = new GoogleCloudStorageEngine();
			propertiesMigratePut(props, GoogleCloudStorageEngine.GCS_REGION, clientProps, AbstractClientBuilder.GCP_REGION_KEY);
			propertiesMigratePut(props, GoogleCloudStorageEngine.GCS_SERVICE_ACCOUNT_FILE_KEY, clientProps, AbstractClientBuilder.GCP_SERVICE_ACCOUNT_FILE_KEY);
			propertiesMigratePut(props, GoogleCloudStorageEngine.GCS_BUCKET_KEY, clientProps, AbstractClientBuilder.GCP_BUCKET_KEY);
			
		}
		else if(ClusterUtil.STORAGE_PROVIDER.equalsIgnoreCase("LOCAL_FILE_SYSTEM")){
			
			centralStorageEngine = new LocalFileSystemStorageEngine();
			propertiesMigratePut(props, LocalFileSystemStorageEngine.PATH_PREFIX, clientProps, LocalFileSystemStorageEngine.PATH_PREFIX);
			if(!props.containsKey(LocalFileSystemStorageEngine.PATH_PREFIX)) {
				propertiesMigratePut(props, LocalFileSystemStorageEngine.PATH_PREFIX, clientProps, LocalFileSystemStorageEngine.LOCAL_PATH_PREFIX);
			}
		}
		else {
			throw new IllegalArgumentException("You have specified an incorrect storage provider");
		}
		
		props.put(Constants.ENGINE, "CENTRAL_CLOUD_STORAGE");
		centralStorageEngine.open(props);
	}
	
	/**
	 * 
	 * @param prop
	 * @param propKey
	 * @param clientProps
	 * @param oldKey
	 */
	private static void propertiesMigratePut(Properties prop, String propKey, AppCloudClientProperties clientProps, String oldKey) {
		if(clientProps.get(oldKey) != null) {
			prop.put(propKey, clientProps.get(oldKey));
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////

	/**
	 * 
	 * @param type
	 * @return
	 */
	public String getCloudPrefixForEngine(IEngine.CATALOG_TYPE type) {
		if(IEngine.CATALOG_TYPE.DATABASE == type) {
			return DB_CONTAINER_PREFIX;
		} else if(IEngine.CATALOG_TYPE.STORAGE == type) {
			return STORAGE_CONTAINER_PREFIX;
		} else if(IEngine.CATALOG_TYPE.MODEL == type) {
			return MODEL_CONTAINER_PREFIX;
		} else if(IEngine.CATALOG_TYPE.VECTOR == type) {
			return VECTOR_CONTAINER_PREFIX;
		} else if(IEngine.CATALOG_TYPE.FUNCTION == type) {
			return FUNCTION_CONTAINER_PREFIX;
		} else if(IEngine.CATALOG_TYPE.VENV == type) {
			return VENV_CONTAINER_PREFIX;
		} else if(IEngine.CATALOG_TYPE.PROJECT == type) {
			return PROJECT_CONTAINER_PREFIX;
		}
		
		throw new IllegalArgumentException("Unhandled engine type = " + type);
	}
	
	/**
	 * 
	 * @param type
	 * @return
	 */
	public String getCloudEngineImageBucket(IEngine.CATALOG_TYPE type) {
		if(IEngine.CATALOG_TYPE.DATABASE == type) {
			return DB_IMAGES_BLOB;
		} else if(IEngine.CATALOG_TYPE.STORAGE == type) {
			return STORAGE_IMAGES_BLOB;
		} else if(IEngine.CATALOG_TYPE.MODEL == type) {
			return MODEL_IMAGES_BLOB;
		} else if(IEngine.CATALOG_TYPE.VECTOR == type) {
			return VECTOR_IMAGES_BLOB;
		} else if(IEngine.CATALOG_TYPE.FUNCTION == type) {
			return FUNCTION_IMAGES_BLOB;
		} else if(IEngine.CATALOG_TYPE.VENV == type) {
			return VENV_IMAGES_BLOB;
		} else if(IEngine.CATALOG_TYPE.PROJECT == type) {
			return PROJECT_IMAGES_BLOB;
		}
		
		throw new IllegalArgumentException("Unhandled engine type = " + type);
	}
	
	/**
	 * 
	 * @param aliasAndEngineId
	 * @param localSmssFileName
	 * @param type
	 */
	public void catalogPulledEngine(String aliasAndEngineId, String localSmssFileName, IEngine.CATALOG_TYPE type) {
		if(IEngine.CATALOG_TYPE.DATABASE == type) {
			classLogger.info("Synchronizing the database metadata for " + aliasAndEngineId);
			SMSSWebWatcher.catalogEngine(localSmssFileName, EngineUtility.DATABASE_FOLDER);
			return;
		} else if(IEngine.CATALOG_TYPE.STORAGE == type) {
			classLogger.info("Synchronizing the storage metadata for " + aliasAndEngineId);
			SMSSNoInitEngineWatcher.catalogEngine(localSmssFileName, EngineUtility.STORAGE_FOLDER);
			return;
		} else if(IEngine.CATALOG_TYPE.MODEL == type) {
			classLogger.info("Synchronizing the model metadata for " + aliasAndEngineId);
			SMSSNoInitEngineWatcher.catalogEngine(localSmssFileName, EngineUtility.MODEL_FOLDER);
			return;
		} else if(IEngine.CATALOG_TYPE.VECTOR == type) {
			classLogger.info("Synchronizing the model metadata for " + aliasAndEngineId);
			SMSSNoInitEngineWatcher.catalogEngine(localSmssFileName, EngineUtility.VECTOR_FOLDER);
			return;
		} else if(IEngine.CATALOG_TYPE.FUNCTION == type) {
			classLogger.info("Synchronizing the model metadata for " + aliasAndEngineId);
			SMSSNoInitEngineWatcher.catalogEngine(localSmssFileName, EngineUtility.FUNCTION_FOLDER);
			return;
		} else if(IEngine.CATALOG_TYPE.VENV == type) {
			classLogger.info("Synchronizing the model metadata for " + aliasAndEngineId);
			SMSSNoInitEngineWatcher.catalogEngine(localSmssFileName, EngineUtility.VENV_FOLDER);
			return;
		} else if(IEngine.CATALOG_TYPE.PROJECT == type) {
			classLogger.info("Synchronizing the project for " + aliasAndEngineId);
			ProjectWatcher.catalogProject(localSmssFileName, EngineUtility.PROJECT_FOLDER);
			return;
		}
		
		throw new IllegalArgumentException("Unhandled engine type = " + type);
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	
	/*
	 * Engine methods
	 */
	
	@Override
	public void pushEngine(String engineId) throws IOException, InterruptedException {
		IEngine engine = Utility.getEngine(engineId, false);
		if (engine == null) {
			throw new IllegalArgumentException("Engine not found...");
		}

		IEngine.CATALOG_TYPE engineType = engine.getCatalogType();

		String engineName = SecurityEngineUtils.getEngineAliasForId(engineId);
		String aliasAndEngineId = SmssUtilities.getUniqueName(engineName, engineId);
		
		String localEngineBaseFolder = EngineUtility.getLocalEngineBaseDirectory(engineType);
		String localEngineFolder = Utility.normalizePath(localEngineBaseFolder + FILE_SEPARATOR + aliasAndEngineId);
		{
			// lets make sure this exists
			File localEngineF = new File(localEngineFolder);
			if(!localEngineF.exists() || !localEngineF.isDirectory()) {
				localEngineF.mkdirs();
			}
			ClusterUtil.validateFolder(localEngineFolder);
		}
		String localSmssFileName = aliasAndEngineId + ".smss";
		String localSmssFilePath = localEngineBaseFolder + FILE_SEPARATOR + localSmssFileName;

		String cloudContainerPrefix = getCloudPrefixForEngine(engineType);
		String cloudEngineFolder = cloudContainerPrefix + engineId;
		String cloudSmssFolder = cloudContainerPrefix + engineId + SMSS_POSTFIX;

		String sharedRCloneConfig = null;
		
		// synchronize on the engine id
		classLogger.info("Applying lock for " + aliasAndEngineId + " to push engine " + aliasAndEngineId);
		ReentrantLock lock = EngineSyncUtility.getEngineLock(engineId);
		lock.lock();
		classLogger.info("Engine "+ aliasAndEngineId + " is locked");
		try {
			if(engine.holdsFileLocks()) {
				DIHelper.getInstance().removeEngineProperty(engineId);
				engine.close();
			}
			
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			centralStorageEngine.syncLocalToStorage(localEngineFolder, cloudEngineFolder, sharedRCloneConfig, null);
			centralStorageEngine.copyToStorage(localSmssFilePath, cloudSmssFolder, sharedRCloneConfig, null);
		} finally {
			try {
				// Re-open the engine
				if(engine.holdsFileLocks()) {
					Utility.getEngine(engineId, false);
				}
				if(sharedRCloneConfig != null) {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				}
			} catch(Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Engine "+ aliasAndEngineId + " is unlocked");
		}
	}
	
	@Override
	public void pullEngine(String engineId) throws IOException, InterruptedException {
		Object[] typeAndSubtype = SecurityEngineUtils.getEngineTypeAndSubtype(engineId);
		IEngine.CATALOG_TYPE engineType = (CATALOG_TYPE) typeAndSubtype[0];
		pullEngine(engineId, engineType);
	}
	
	@Override
	public void pullEngine(String engineId, IEngine.CATALOG_TYPE engineType) throws IOException, InterruptedException {
		pullEngine(engineId, engineType, false);
	}
	
	@Override
	public void pullEngine(String engineId, IEngine.CATALOG_TYPE engineType, boolean engineAlreadyLoaded) throws IOException, InterruptedException {
		IEngine engine = null;
		if (engineAlreadyLoaded) {
			engine = Utility.getEngine(engineId, false);
			if (engine == null) {
				throw new IllegalArgumentException("Engine not found...");
			}
			engineType = engine.getCatalogType();
		} else if(engineType == null){
			Object[] typeAndSubtype = SecurityEngineUtils.getEngineTypeAndSubtype(engineId);
			engineType = (CATALOG_TYPE) typeAndSubtype[0];
		}
		
		// We need to pull the folder alias__databaseId and the file alias__databaseId.smss
		String engineName = SecurityEngineUtils.getEngineAliasForId(engineId);
		// this is done for FE where something like ModelInferenceLogsDatabase 
		// doesn't have an alias and its ID is loaded as the alias
		if(engineId.equals(engineName)) {
			engineName = null;
		}
		String aliasAndEngineId = SmssUtilities.getUniqueName(engineName, engineId);
		
		String localEngineBaseFolder = EngineUtility.getLocalEngineBaseDirectory(engineType);
		String localEngineFolder = localEngineBaseFolder + FILE_SEPARATOR + aliasAndEngineId;
		String localSmssFileName = aliasAndEngineId + ".smss";
		String localSmssFilePath = Utility.normalizePath(localEngineBaseFolder + FILE_SEPARATOR + localSmssFileName);

		String cloudContainerPrefix = getCloudPrefixForEngine(engineType);
		String cloudEngineFolder = cloudContainerPrefix + engineId;
		String cloudEngineSmssFolder = cloudContainerPrefix + engineId + SMSS_POSTFIX;

		String sharedRCloneConfig = null;

		// synchronize on the engine id
		classLogger.info("Applying lock for " + aliasAndEngineId + " to pull engine");
		ReentrantLock lock = EngineSyncUtility.getEngineLock(engineId);
		lock.lock();
		classLogger.info("Engine "+ aliasAndEngineId + " is locked");
		try {
			if(engine != null) {
				try {
					DIHelper.getInstance().removeEngineProperty(engineId);
					engine.close();
				} catch(Exception e) {
					classLogger.warn("Error occurred trying to close engine " + aliasAndEngineId + " - will proceed with pulling details in attempt to fix engine when re-opened");
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			
			// Make the database directory (if it doesn't already exist)
			File localEngineF = new File(Utility.normalizePath(localEngineFolder));
			if(!localEngineF.exists() || !localEngineF.isDirectory()) {
				localEngineF.mkdirs(); 
			}

			// Pull the contents of the database folder before the smss
			classLogger.info("Pulling engine from remote=" + Utility.cleanLogString(aliasAndEngineId) + " to target=" + Utility.cleanLogString(localEngineFolder));
			centralStorageEngine.syncStorageToLocal(cloudEngineFolder, localEngineFolder, sharedRCloneConfig);
			classLogger.debug("Done pulling from remote=" + Utility.cleanLogString(aliasAndEngineId) + " to target=" + Utility.cleanLogString(localEngineFolder));

			// Now pull the smss
			classLogger.info("Pulling smss from remote=" + Utility.cleanLogString(cloudEngineSmssFolder) + " to target=" + localEngineBaseFolder);
			// THIS MUST BE COPY AND NOT SYNC TO AVOID DELETING EVERYTHING IN THE DB FOLDER
			centralStorageEngine.copyToLocal(cloudEngineSmssFolder, localEngineBaseFolder, sharedRCloneConfig);
			classLogger.debug("Done pulling from remote=" + Utility.cleanLogString(cloudEngineSmssFolder) + " to target=" + localEngineBaseFolder);

			// Catalog the db if it is new
			if (!engineAlreadyLoaded) {
				catalogPulledEngine(aliasAndEngineId, localSmssFileName, engineType);
			}
		} finally {
			if(engineAlreadyLoaded) {
				try {
					// Re-open the engine
					Utility.getEngine(engineId, engineType, false);
				} catch(Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
			try {
				if(sharedRCloneConfig != null) {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				}
			} catch(Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Engine "+ aliasAndEngineId + " is unlocked");
		}
	}
	
	@Override
	public void pushEngineSmss(String engineId) throws IOException, InterruptedException {
		Object[] typeAndSubtype = SecurityEngineUtils.getEngineTypeAndSubtype(engineId);
		IEngine.CATALOG_TYPE engineType = (CATALOG_TYPE) typeAndSubtype[0];
		pushEngineSmss(engineId, engineType);
	}
	
	@Override
	public void pushEngineSmss(String engineId, IEngine.CATALOG_TYPE engineType) throws IOException, InterruptedException {
		// We need to push the file alias__appId.smss
		String engineName = SecurityEngineUtils.getEngineAliasForId(engineId);
		String aliasAndEngineId = SmssUtilities.getUniqueName(engineName, engineId);
		String localSmssFileName = SmssUtilities.getUniqueName(engineName, engineId) + ".smss";
		String localEngineBaseFolder = EngineUtility.getLocalEngineBaseDirectory(engineType);
		String localSmssFilePath = Utility.normalizePath(localEngineBaseFolder + FILE_SEPARATOR + localSmssFileName);
		
		String cloudContainerPrefix = getCloudPrefixForEngine(engineType);
		String cloudSmssFolder = cloudContainerPrefix + engineId + SMSS_POSTFIX;

		// synchronize on the model id
		classLogger.info("Applying lock for " + aliasAndEngineId + " to push engine");
		ReentrantLock lock = EngineSyncUtility.getEngineLock(engineId);
		lock.lock();
		classLogger.info("Engine " + aliasAndEngineId + " is locked");
		try {
			centralStorageEngine.copyToStorage(localSmssFilePath, cloudSmssFolder, null);
		} finally {
			lock.unlock();
			classLogger.info("Engine " + aliasAndEngineId + " is unlocked");
		}
	}

	@Override
	public void pullEngineSmss(String engineId) throws IOException, InterruptedException {
		Object[] typeAndSubtype = SecurityEngineUtils.getEngineTypeAndSubtype(engineId);
		IEngine.CATALOG_TYPE engineType = (CATALOG_TYPE) typeAndSubtype[0];
		pullEngineSmss(engineId, engineType);
	}
	
	@Override
	public void pullEngineSmss(String engineId, IEngine.CATALOG_TYPE engineType) throws IOException, InterruptedException {
		// We need to push the file alias__modelId.smss
		String engineName = SecurityEngineUtils.getEngineAliasForId(engineId);
		String aliasAndEngineId = SmssUtilities.getUniqueName(engineName, engineId);
		
		String cloudContainerPrefix = getCloudPrefixForEngine(engineType);
		String cloudSmssFolder = cloudContainerPrefix + engineId + SMSS_POSTFIX;

		// synchronize on the engine id
		classLogger.info("Applying lock for " + aliasAndEngineId + " to push engine smss");
		ReentrantLock lock = EngineSyncUtility.getEngineLock(engineId);
		lock.lock();
		classLogger.info("Engine " + aliasAndEngineId + " is locked");
		try {
			centralStorageEngine.copyToLocal(cloudSmssFolder, cloudContainerPrefix);
		} finally {
			lock.unlock();
			classLogger.info("Engine " + aliasAndEngineId + " is unlocked");
		}
	}
	
	@Override
	public void deleteEngine(String engineId) throws IOException, InterruptedException {
		Object[] typeAndSubtype = SecurityEngineUtils.getEngineTypeAndSubtype(engineId);
		IEngine.CATALOG_TYPE engineType = (CATALOG_TYPE) typeAndSubtype[0];
		deleteEngine(engineId, engineType);
	}
	
	@Override
	public void deleteEngine(String engineId, IEngine.CATALOG_TYPE engineType) throws IOException, InterruptedException {
		String sharedRCloneConfig = null;
		if(centralStorageEngine.canReuseRcloneConfig()) {
			sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
		}
		String cloudContainerPrefix = getCloudPrefixForEngine(engineType);
		String cloudEngineFolder = cloudContainerPrefix + engineId;
		String cloudSmssFolder = cloudContainerPrefix + engineId + SMSS_POSTFIX;

		centralStorageEngine.deleteFolderFromStorage(cloudEngineFolder, sharedRCloneConfig);
		centralStorageEngine.deleteFolderFromStorage(cloudSmssFolder, sharedRCloneConfig);
		
		deleteEngineAndProjectImageById(engineType, engineId);
	}
	
	@Override
	public void pullEngineAndProjectImageFolder(IEngine.CATALOG_TYPE engineType) throws IOException, InterruptedException {
		String cloudImageFolder = getCloudEngineImageBucket(engineType);
		String localImagesFolderPath = EngineUtility.getLocalEngineImageDirectory(engineType);
		File localImageF = new File(localImagesFolderPath);
		if(!localImageF.exists() || !localImageF.isDirectory()) {
			localImageF.mkdirs();
		}
		centralStorageEngine.copyToLocal(cloudImageFolder, localImagesFolderPath);
	}

	@Override
	public void pushEngineAndProjectImage(IEngine.CATALOG_TYPE engineType, String fileName) throws IOException, InterruptedException {
		String cloudImageFolder = getCloudEngineImageBucket(engineType);
		String localImagesFolderPath = EngineUtility.getLocalEngineImageDirectory(engineType);
		String fileToPush = localImagesFolderPath + "/" + fileName;
		centralStorageEngine.copyToStorage(fileToPush, cloudImageFolder, null);
	}
	
	@Override
	public void deleteEngineAndProjectImage(IEngine.CATALOG_TYPE engineType, String fileName) throws IOException, InterruptedException {
		String cloudImageFolder = getCloudEngineImageBucket(engineType);
		String fileToDelete = cloudImageFolder + "/" + fileName;
		centralStorageEngine.deleteFromStorage(fileToDelete);
	}
	
	@Override
	public void deleteEngineAndProjectImageById(CATALOG_TYPE engineType, String engineId) throws IOException, InterruptedException {
		String localImageDirectory = EngineUtility.getLocalEngineImageDirectory(engineType);
		File localCloudImageF = new File(localImageDirectory);
		// find all the image file that have this engineId
		File[] oldImageFiles = localCloudImageF.listFiles(new FilenameFilter() {
			
			String engineId = null;
			
			private FilenameFilter init(String engineId) {
				this.engineId = engineId;
				return this;
			}
			
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(engineId);
			}
		}.init(engineId));
		
		for(File f : oldImageFiles) {
			// delete from the cloud
			deleteEngineAndProjectImage(engineType, f.getName());
			// delete locally
			f.delete();
		}
	}
	
	@Override
	public void copyLocalFileToEngineCloudFolder(String engineId, CATALOG_TYPE engineType, String localFilePath) throws IOException, InterruptedException {
		IEngine engine = Utility.getEngine(engineId, false);
		if (engine == null) {
			throw new IllegalArgumentException("Engine not found...");
		}
		
		String engineName = engine.getEngineName();
		String aliasAndEngineId = SmssUtilities.getUniqueName(engineName, engineId);

		// from the local file path
		// determine where it should be in the cloud storage
		String localEngineFolder = EngineUtility.getLocalEngineBaseDirectory(engineType) + FILE_SEPARATOR + aliasAndEngineId;
		Path localEnginePath = Paths.get(localEngineFolder);
		Path storagePath = null;
		
		File absoluteFolder = new File(localFilePath);
		if(absoluteFolder.isDirectory()) {
			// this is adding a hidden file into every sub folder to make sure there is no empty directory
			ClusterUtil.validateFolder(absoluteFolder.getAbsolutePath());
			storagePath = localEnginePath.relativize( absoluteFolder.toPath() );
		} else {
			storagePath = localEnginePath.relativize( absoluteFolder.toPath() );
			// we need to get the parent folder since we are pushing a file
			storagePath = storagePath.getParent();
		}
		
		String cloudStorageFolderPath = getCloudPrefixForEngine(engineType) + engineId + "/" + storagePath;

		classLogger.info("Applying lock for engine " + aliasAndEngineId + " to push local file " + localFilePath + " to cloud path " + cloudStorageFolderPath);
		ReentrantLock lock = EngineSyncUtility.getEngineLock(engineId);
		lock.lock();
		classLogger.info("Engine " + aliasAndEngineId + " is locked");
		try {
			classLogger.info("Pushing folder local=" + localFilePath + " to from remote=" + cloudStorageFolderPath);
			centralStorageEngine.copyToStorage(localFilePath, cloudStorageFolderPath, null);
		} finally {
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Engine " + aliasAndEngineId + " is unlocked");
		}
	}

	@Override
	public void copyEngineCloudFileToLocalFile(String engineId, CATALOG_TYPE engineType, String localFilePath) throws IOException, InterruptedException {
		IEngine engine = Utility.getEngine(engineId, false);
		if (engine == null) {
			throw new IllegalArgumentException("Engine not found...");
		}
		
		String engineName = engine.getEngineName();
		String aliasAndEngineId = SmssUtilities.getUniqueName(engineName, engineId);

		File absoluteFolder = new File(localFilePath);
		if(!absoluteFolder.exists()) {
			if(absoluteFolder.isDirectory()) {
				absoluteFolder.mkdirs();
			} else {
				absoluteFolder.getParentFile().mkdirs();
			}
		}
		
		// from the local file path
		// determine where it should be in the cloud storage
		String localEngineFolder = EngineUtility.getLocalEngineBaseDirectory(engineType) + FILE_SEPARATOR + aliasAndEngineId;
		Path localEnginePath = Paths.get(localEngineFolder);

		Path storagePath = localEnginePath.relativize( absoluteFolder.toPath() );
		String cloudStorageFolderPath = getCloudPrefixForEngine(engineType) + engineId + "/" + storagePath;
		
		classLogger.info("Applying lock for engine " + aliasAndEngineId + " to pull cloud path " + cloudStorageFolderPath + " to local file " + localFilePath);
		ReentrantLock lock = EngineSyncUtility.getEngineLock(engineId);
		lock.lock();
		classLogger.info("Engine " + aliasAndEngineId + " is locked");
		try {
			classLogger.info("Pulling remote=" + cloudStorageFolderPath + " to folder local=" + localFilePath);
			centralStorageEngine.copyToLocal(cloudStorageFolderPath, localFilePath);
		} finally {
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Engine " + aliasAndEngineId + " is unlocked");
		}
	}
	
	@Override
	public void deleteEngineCloudFile(String engineId, CATALOG_TYPE engineType, String localFilePath) throws IOException, InterruptedException {
		IEngine engine = Utility.getEngine(engineId, false);
		if (engine == null) {
			throw new IllegalArgumentException("Engine not found...");
		}
		
		String engineName = engine.getEngineName();
		String aliasAndEngineId = SmssUtilities.getUniqueName(engineName, engineId);

		File absoluteFolder = new File(localFilePath);
		// from the local file path
		// determine where it should be in the cloud storage
		String localEngineFolder = EngineUtility.getLocalEngineBaseDirectory(engineType) + FILE_SEPARATOR + aliasAndEngineId;
		Path localEnginePath = Paths.get(localEngineFolder);

		Path storagePath = localEnginePath.relativize( absoluteFolder.toPath() );
		String cloudStorageFolderPath = getCloudPrefixForEngine(engineType) + engineId + "/" + storagePath;
		
		classLogger.info("Applying lock for engine " + aliasAndEngineId + " to delete cloud path " + cloudStorageFolderPath);
		ReentrantLock lock = EngineSyncUtility.getEngineLock(engineId);
		lock.lock();
		classLogger.info("Engine " + aliasAndEngineId + " is locked");
		try {
			classLogger.info("Deleting remote=" + cloudStorageFolderPath);
			centralStorageEngine.deleteFromStorage(cloudStorageFolderPath);
		} finally {
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Engine " + aliasAndEngineId + " is unlocked");
		}
	}
	
	/*
	 * Database
	 */
	
	@Override
	public void pushLocalDatabaseFile(String databaseId, RdbmsTypeEnum dbType) throws IOException, InterruptedException {
		if (dbType != RdbmsTypeEnum.SQLITE
				&& dbType != RdbmsTypeEnum.H2_DB) {
			throw new IllegalArgumentException("Unallowed database type. Must be either SQLITE or H2");
		}
		
		IDatabaseEngine database = Utility.getDatabase(databaseId, false);
		if (database == null) {
			throw new IllegalArgumentException("Database not found...");
		}
		
		// We need to push the folder alias__databaseId and the file alias__databaseId.smss
		String databaseName = SecurityEngineUtils.getEngineAliasForId(databaseId);
		String aliasAndDatabaseId = SmssUtilities.getUniqueName(databaseName, databaseId);
		String localDatabaseFolder = EngineUtility.DATABASE_FOLDER + FILE_SEPARATOR + aliasAndDatabaseId;
		
		String storageDatabaseFolder = DB_CONTAINER_PREFIX + databaseId;
		
		// synchronize on the engine id
		classLogger.info("Applying lock for " + aliasAndDatabaseId + " to push db file");
		ReentrantLock lock = EngineSyncUtility.getEngineLock(databaseId);
		lock.lock();
		classLogger.info("Database "+ aliasAndDatabaseId + " is locked");
		try {
			DIHelper.getInstance().removeEngineProperty(databaseId);
			database.close();

			classLogger.info("Pushing local database file from " + localDatabaseFolder + " to remote " + storageDatabaseFolder);
			List<String> dbFiles = null;
			if (dbType == RdbmsTypeEnum.SQLITE) {
				dbFiles = getSqlLiteFile(localDatabaseFolder);
			} else if (dbType == RdbmsTypeEnum.H2_DB) {
				dbFiles = getH2File(localDatabaseFolder);
			}
			for (String dbFileName : dbFiles) {
				centralStorageEngine.copyToStorage(localDatabaseFolder+"/"+dbFileName, storageDatabaseFolder, null);
			}
		} finally {
			try {
				// Re-open the database
				Utility.getDatabase(databaseId, false);
			} catch(Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Database "+ aliasAndDatabaseId + " is unlocked");
		}
	}

	@Override
	public void pullLocalDatabaseFile(String databaseId, RdbmsTypeEnum rdbmsType) throws IOException, InterruptedException {
		if (rdbmsType != RdbmsTypeEnum.SQLITE
				&& rdbmsType != RdbmsTypeEnum.H2_DB) {
			throw new IllegalArgumentException("Unallowed database type. Must be either SQLITE or H2");
		}
		
		IDatabaseEngine database = Utility.getDatabase(databaseId, false);
		if (database == null) {
			throw new IllegalArgumentException("Database not found...");
		}
		String databaseName = SecurityEngineUtils.getEngineAliasForId(databaseId);
		String aliasAndDatabaseId = SmssUtilities.getUniqueName(databaseName, databaseId);
		String localDatabaseFolder = EngineUtility.DATABASE_FOLDER + FILE_SEPARATOR + aliasAndDatabaseId;

		String storageDatabaseFolder = DB_CONTAINER_PREFIX + databaseId;

		String sharedRCloneConfig = null;

		// synchronize on the engine id
		classLogger.info("Applying lock for " + aliasAndDatabaseId + " to pull database file");
		ReentrantLock lock = EngineSyncUtility.getEngineLock(databaseId);
		lock.lock();
		classLogger.info("Database "+ databaseId + " is locked");
		try {
			DIHelper.getInstance().removeEngineProperty(databaseId);
			database.close();
			
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			
			classLogger.info("Pulling database files for " + aliasAndDatabaseId + " from remote=" + databaseId);
			List<String> filesToPull = new ArrayList<>();
			List<String> cloudFiles = centralStorageEngine.list(storageDatabaseFolder, sharedRCloneConfig);
			for(String cloudF : cloudFiles) {
				if(rdbmsType == RdbmsTypeEnum.SQLITE && cloudF.endsWith(".sqlite")) {
					filesToPull.add(cloudF);	
				} else if(rdbmsType == RdbmsTypeEnum.H2_DB && cloudF.endsWith(".mv.db")) {
					filesToPull.add(cloudF);	
				}
			}
			
			for(String fileToPull : filesToPull) {
				centralStorageEngine.copyToLocal(storageDatabaseFolder+"/"+fileToPull, localDatabaseFolder, sharedRCloneConfig);
			}
		} finally {
			try {
				// Re-open the database
				Utility.getDatabase(databaseId, false);
				if(sharedRCloneConfig != null) {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				}
			} catch(Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Database "+ aliasAndDatabaseId + " is unlocked");
		}
	}

	@Override
	public void pushOwl(String databaseId) throws IOException, InterruptedException {
		pushOwl(databaseId, null);
	}
	
	@Override
	public void pushOwl(String databaseId, WriteOWLEngine owlEngine) throws IOException, InterruptedException {
		IDatabaseEngine database = Utility.getDatabase(databaseId, false);
		if (database == null) {
			throw new IllegalArgumentException("Database not found...");
		}
		
		String databaseName = SecurityEngineUtils.getEngineAliasForId(databaseId);
		String aliasAndDatabaseId = SmssUtilities.getUniqueName(databaseName, databaseId);
		File localOwlF = SmssUtilities.getOwlFile(database.getSmssProp());
		String localOwlFile = localOwlF.getAbsolutePath();
		String localOwlPositionFile = localOwlF.getParent() + "/" + AbstractDatabaseEngine.OWL_POSITION_FILENAME;
		boolean hasPositionFile = new File(localOwlPositionFile).exists();
		
		String storageDatabaseFolder = DB_CONTAINER_PREFIX + databaseId;

		String sharedRCloneConfig = null;

		// synchronize on the engine id
		classLogger.info("Applying lock for " + aliasAndDatabaseId + " to push database owl and postions.json");
		ReentrantLock lock = EngineSyncUtility.getEngineLock(databaseId);
		lock.lock();
		classLogger.info("Database " + aliasAndDatabaseId + " is locked");
		try {
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			centralStorageEngine.copyToStorage(localOwlFile, storageDatabaseFolder, sharedRCloneConfig, null);
			if(hasPositionFile) {
				centralStorageEngine.copyToStorage(localOwlPositionFile, storageDatabaseFolder, sharedRCloneConfig, null);
			}
		} finally {
			if(sharedRCloneConfig != null) {
				try {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				} catch(Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Database "+ aliasAndDatabaseId + " is unlocked");
		}
	}
	
	@Override
	public void pullOwl(String databaseId) throws IOException, InterruptedException {
		pullOwl(databaseId, null);
	}
	
	@Override
	public void pullOwl(String databaseId, WriteOWLEngine owlEngine) throws IOException, InterruptedException {
		IDatabaseEngine database = Utility.getDatabase(databaseId, false);
		if (database == null) {
			throw new IllegalArgumentException("Database not found...");
		}
		
		String databaseName = SecurityEngineUtils.getEngineAliasForId(databaseId);
		String aliasAndDatabaseId = SmssUtilities.getUniqueName(databaseName, databaseId);
		String localDatabaseFolder = EngineUtility.DATABASE_FOLDER + FILE_SEPARATOR + aliasAndDatabaseId;

		File localOwlF = SmssUtilities.getOwlFile(database.getSmssProp());
		String localOwlFile = localOwlF.getAbsolutePath();
		String owlFileName = localOwlF.getName();
		
		String storageDatabaseFolder = DB_CONTAINER_PREFIX + databaseId;
		String storageDatabaseOwl = storageDatabaseFolder + "/" + owlFileName;
		String storageDatabaseOwlPosition = storageDatabaseFolder + "/" + AbstractDatabaseEngine.OWL_POSITION_FILENAME;
		
		String sharedRCloneConfig = null;

		// synchronize on the engine id
		boolean autoClose = false;
		try {
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			if(owlEngine == null) {
				autoClose = true;
				classLogger.info("Grabbing current owl engine factory");
				owlEngine = database.getOWLEngineFactory().getWriteOWL();
			}
			//close the owl
			owlEngine.closeOwl();
			centralStorageEngine.copyToLocal(storageDatabaseOwl, localDatabaseFolder);
			centralStorageEngine.copyToLocal(storageDatabaseOwlPosition, localDatabaseFolder);
		} finally {
			try {
				owlEngine.reloadOWLFile();
			} catch (Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			if(autoClose) {
				owlEngine.close();
			}
			if(sharedRCloneConfig != null) {
				try {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				} catch(Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////////////
	
	/*
	 * Project
	 */
	
	
	@Override
	public void pushProject(String projectId) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId, false);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		}

		// We need to push the folder alias__projectId and the file alias__projectId.smss
		String alias = project.getProjectName();
		if(alias == null) {
			alias = SecurityProjectUtils.getProjectAliasForId(projectId);
		}

		String aliasAndProjectId = alias + "__" + projectId;
		String localProjectFolder = EngineUtility.PROJECT_FOLDER + FILE_SEPARATOR + aliasAndProjectId;
		String localSmssFileName = aliasAndProjectId + ".smss";
		String localSmssFilePath = EngineUtility.PROJECT_FOLDER + FILE_SEPARATOR + localSmssFileName;

		String sharedRCloneConfig = null;

		String storageProjectFolder = PROJECT_CONTAINER_PREFIX + projectId;
		String storageSmssFolder = PROJECT_CONTAINER_PREFIX + projectId + SMSS_POSTFIX;

		// synchronize on the project id
		classLogger.info("Applying lock for " + aliasAndProjectId + " to push project");
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project "+ aliasAndProjectId + " is locked");
		try {
			DIHelper.getInstance().removeProjectProperty(projectId);
			project.close();
			
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			centralStorageEngine.syncLocalToStorage(localProjectFolder, storageProjectFolder, sharedRCloneConfig, null);
			centralStorageEngine.copyToStorage(localSmssFilePath, storageSmssFolder, sharedRCloneConfig, null);
		} finally {
			try {
				// Re-open the project
				Utility.getProject(projectId, false);
				if(sharedRCloneConfig != null) {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				}
			} catch(Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Project "+ aliasAndProjectId + " is unlocked");
		}
	}
	
	@Override
	public void pullProject(String projectId) throws IOException, InterruptedException {
		pullProject(projectId, false);
	}

	@Override
	public void pullProject(String projectId, boolean projectAlreadyLoaded) throws IOException, InterruptedException {
		IProject project = null;
		if (projectAlreadyLoaded) {
			project = Utility.getProject(projectId, false);
			if (project == null) {
				throw new IllegalArgumentException("Project not found...");
			}
		}

		// We need to pull the folder alias__projectId and the file alias__projectId.smss
		String alias = SecurityProjectUtils.getProjectAliasForId(projectId);
		String aliasAndProjectId = alias + "__" + projectId;
		String localProjectFolder = EngineUtility.PROJECT_FOLDER + FILE_SEPARATOR + aliasAndProjectId;
		String localSmssFileName = aliasAndProjectId + ".smss";
		String localSmssFilePath = EngineUtility.PROJECT_FOLDER + FILE_SEPARATOR + localSmssFileName;

		String sharedRCloneConfig = null;

		String storageProjectFolder = PROJECT_CONTAINER_PREFIX + projectId;
		String storageSmssFolder = PROJECT_CONTAINER_PREFIX + projectId + SMSS_POSTFIX;

		// synchronize on the project id
		classLogger.info("Applying lock for " + aliasAndProjectId + " to push project");
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project "+ aliasAndProjectId + " is locked");
		try {
			if (projectAlreadyLoaded) {
				DIHelper.getInstance().removeProjectProperty(projectId);
				project.close();
			}
			
			// Make the project directory (if it doesn't already exist)
			File localProjectF = new File(Utility.normalizePath(localProjectFolder));
			if(!localProjectF.exists() || !localProjectF.isDirectory()) {
				localProjectF.mkdirs(); 
			}
			
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			centralStorageEngine.syncStorageToLocal(storageProjectFolder, localProjectFolder, sharedRCloneConfig);
			centralStorageEngine.copyToLocal(storageSmssFolder, EngineUtility.PROJECT_FOLDER, sharedRCloneConfig);
			
			// Catalog the project if it is new
			if (!projectAlreadyLoaded) {
				catalogPulledEngine(aliasAndProjectId, localSmssFileName, IEngine.CATALOG_TYPE.PROJECT);
			}
		} finally {
			try {
				// Re-open the project - if already loaded
				if (projectAlreadyLoaded) {
					Utility.getProject(projectId, false);
				}
				if(sharedRCloneConfig != null) {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				}
			} catch(Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Project "+ aliasAndProjectId + " is unlocked");
		}
	}
	
	@Override
	public void pushProjectSmss(String projectId) throws IOException, InterruptedException {
		// We need to push the file alias__projectId.smss
		String projectName = SecurityProjectUtils.getProjectAliasForId(projectId);
		String aliasAndProjectId = SmssUtilities.getUniqueName(projectName, projectId);
		String localSmssFileName = SmssUtilities.getUniqueName(projectName, projectId) + ".smss";
		String localSmssFilePath = Utility.normalizePath(EngineUtility.PROJECT_FOLDER + FILE_SEPARATOR + localSmssFileName);
		
		String storageSmssFolder = PROJECT_CONTAINER_PREFIX + projectId + SMSS_POSTFIX;

		// synchronize on the project id
		classLogger.info("Applying lock for " + aliasAndProjectId + " to push project smss");
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project " + aliasAndProjectId + " is locked");
		try {
			centralStorageEngine.copyToStorage(localSmssFilePath, storageSmssFolder, null);
		} finally {
			lock.unlock();
			classLogger.info("Project " + aliasAndProjectId + " is unlocked");
		}
	}
	
	@Override
	public void pullProjectSmss(String projectId) throws IOException, InterruptedException {
		// We need to push the file alias__projectId.smss
		String projectName = SecurityProjectUtils.getProjectAliasForId(projectId);
		String aliasAndProjectId = SmssUtilities.getUniqueName(projectName, projectId);
		
		String storageSmssFolder = PROJECT_CONTAINER_PREFIX + projectId + SMSS_POSTFIX;

		// synchronize on the project id
		classLogger.info("Applying lock for " + aliasAndProjectId + " to push project smss");
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project " + aliasAndProjectId + " is locked");
		try {
			centralStorageEngine.copyToLocal(storageSmssFolder, EngineUtility.PROJECT_FOLDER);
		} finally {
			lock.unlock();
			classLogger.info("Project " + aliasAndProjectId + " is unlocked");
		}
	}
	
	@Override
	public void deleteProject(String projectId) throws IOException, InterruptedException {
		String sharedRCloneConfig = null;
		if(centralStorageEngine.canReuseRcloneConfig()) {
			sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
		}
		String storageProjectFolder = PROJECT_CONTAINER_PREFIX + projectId;
		String storageSmssFolder = PROJECT_CONTAINER_PREFIX + projectId + SMSS_POSTFIX;

		centralStorageEngine.deleteFolderFromStorage(storageProjectFolder, sharedRCloneConfig);
		centralStorageEngine.deleteFolderFromStorage(storageSmssFolder, sharedRCloneConfig);
		
		deleteEngineAndProjectImageById(CATALOG_TYPE.PROJECT, projectId);
	}

	@Override
	public void pullInsightsDB(String projectId) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId, false);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		}
		
		String projectName = project.getProjectName();
		String aliasAndProjectId = SmssUtilities.getUniqueName(projectName, projectId);
		String localProjectFolder = EngineUtility.PROJECT_FOLDER + FILE_SEPARATOR + aliasAndProjectId;
		String insightDbFileName = getInsightDB(project, localProjectFolder);

		String storageProjectInsightFilePath = PROJECT_CONTAINER_PREFIX + projectId + "/" + insightDbFileName;
		// synchronize on the project id
		classLogger.info("Applying lock for " + aliasAndProjectId + " to pull insights db");
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project " + aliasAndProjectId + " is locked");
		try {
			project.getInsightDatabase().close();
			centralStorageEngine.copyToLocal(storageProjectInsightFilePath, localProjectFolder);
		}  finally {
			try {
				//open the insight db
				String insightDbLoc = SmssUtilities.getInsightsRdbmsFile(project.getSmssProp()).getAbsolutePath();
				if(insightDbLoc != null) {
					try {
						project.setInsightDatabase( ProjectHelper.loadInsightsEngine(project.getSmssProp(), LogManager.getLogger(AbstractDatabaseEngine.class)));
					} catch (Exception e) {
						classLogger.error(Constants.STACKTRACE, e);
						throw new IllegalArgumentException("Error in loading new insights database for project " + aliasAndProjectId);
					}
				} else {
					throw new IllegalArgumentException("Insight database was not able to be found");
				}
			}
			finally {
				// always unlock regardless of errors
				lock.unlock();
				classLogger.info("Project " + aliasAndProjectId + " is unlocked");
			}
		}
	}

	@Override
	public void pushInsightDB(String projectId) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId, false);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		}
		
		String projectName = project.getProjectName();
		String aliasAndProjectId = SmssUtilities.getUniqueName(projectName, projectId);
		String localProjectFolder = EngineUtility.PROJECT_FOLDER + FILE_SEPARATOR + aliasAndProjectId;
		String insightDbFileName = getInsightDB(project, localProjectFolder);
		String localProjectInsightDb = localProjectFolder + "/" + insightDbFileName;
		
		String storageProjectFolder = PROJECT_CONTAINER_PREFIX + projectId;
		
		// synchronize on the project id
		classLogger.info("Applying lock for " + aliasAndProjectId + " to pull insights db");
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project " + aliasAndProjectId + " is locked");
		try {
			project.getInsightDatabase().close();
			centralStorageEngine.copyToStorage(localProjectInsightDb, storageProjectFolder, null);
		}  finally {
			try {
				//open the insight db
				String insightDbLoc = SmssUtilities.getInsightsRdbmsFile(project.getSmssProp()).getAbsolutePath();
				if(insightDbLoc != null) {
					try {
						project.setInsightDatabase( ProjectHelper.loadInsightsEngine(project.getSmssProp(), LogManager.getLogger(AbstractDatabaseEngine.class)));
					} catch (Exception e) {
						classLogger.error(Constants.STACKTRACE, e);
						throw new IllegalArgumentException("Error in loading new insights database for project " + aliasAndProjectId);
					}
				} else {
					throw new IllegalArgumentException("Insight database was not able to be found");
				}
			}
			finally {
				// always unlock regardless of errors
				lock.unlock();
				classLogger.info("Project " + aliasAndProjectId + " is unlocked");
			}
		}
	}

	@Override
	public void pushProjectFolder(String projectId, String localAbsoluteFilePath, String storageRelativePath) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId, false);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		}
		if(storageRelativePath != null) {
			storageRelativePath = storageRelativePath.replace("\\", "/");
		}
		if(storageRelativePath.startsWith("/")) {
			storageRelativePath = storageRelativePath.substring(1);
		}
		
		String projectName = SecurityProjectUtils.getProjectAliasForId(projectId);
		String aliasAndProjectId = SmssUtilities.getUniqueName(projectName, projectId);
		
		File absoluteFolder = new File(Utility.normalizePath(localAbsoluteFilePath));
		if(absoluteFolder.isDirectory()) {
			//this is adding a hidden file into every sub folder to make sure there is no empty directory
			ClusterUtil.validateFolder(absoluteFolder.getAbsolutePath());
		}
		
		String storageProjectFolderPath = PROJECT_CONTAINER_PREFIX + projectId;
		if(storageRelativePath != null) {
			storageProjectFolderPath = storageProjectFolderPath + "/" + storageRelativePath;
		}
		
		// adding a lock for now, but there may be times we don't need one and other times we do
		// reaching h2 db from version folder vs static assets in project...
		
		classLogger.info("Applying lock for project " + aliasAndProjectId + " to push project relative folder " + storageRelativePath);
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project " + aliasAndProjectId + " is locked");
		try {
			classLogger.info("Pushing folder from local=" + localAbsoluteFilePath + " to remote=" + storageProjectFolderPath);
			centralStorageEngine.syncLocalToStorage(localAbsoluteFilePath, storageProjectFolderPath, null);
		} finally {
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Project " + aliasAndProjectId + " is unlocked");
		}
	}

	@Override
	public void pullProjectFolder(String projectId, String localAbsoluteFilePath, String storageRelativePath) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId, false);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		}
		if(storageRelativePath != null) {
			storageRelativePath = storageRelativePath.replace("\\", "/");
		}
		if(storageRelativePath.startsWith("/")) {
			storageRelativePath = storageRelativePath.substring(1);
		}
		
		String projectName = SecurityProjectUtils.getProjectAliasForId(projectId);
		String aliasAndProjectId = SmssUtilities.getUniqueName(projectName, projectId);
		
		String storageProjectFolderPath = PROJECT_CONTAINER_PREFIX + projectId;
		if(storageRelativePath != null) {
			storageProjectFolderPath = storageProjectFolderPath + "/" + storageRelativePath;
		}
		
		// adding a lock for now, but there may be times we don't need one and other times we do
		// reaching h2 db from version folder vs static assets in project...
		
		classLogger.info("Applying lock for project " + aliasAndProjectId + " to pull project relative folder " + storageRelativePath);
		ReentrantLock lock = ProjectSyncUtility.getProjectLock(projectId);
		lock.lock();
		classLogger.info("Project " + aliasAndProjectId + " is locked");
		try {
			classLogger.info("Pulling folder from remote=" + storageProjectFolderPath + " to local=" + localAbsoluteFilePath);
			centralStorageEngine.syncStorageToLocal(storageProjectFolderPath, localAbsoluteFilePath);
		} finally {
			// always unlock regardless of errors
			lock.unlock();
			classLogger.info("Project " + aliasAndProjectId + " is unlocked");
		}
	}

	
	///////////////////////////////////////////////////////////////////////////////////
	
	/*
	 * Insight
	 */
	

	@Override
	public void pushInsight(String projectId, String insightId) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		}

		// only need to pull the insight folder - 99% the project is always already loaded to get to this point
		String localInsightFolderPath = Utility.normalizePath(AssetUtility.getProjectVersionFolder(project.getProjectName(), projectId) + "/" + insightId);
		File localInsightF = new File(localInsightFolderPath);
		if(!localInsightF.exists() || !localInsightF.exists()) {
			localInsightF.mkdirs();
		}
		String storageInsightFolder = PROJECT_CONTAINER_PREFIX+projectId+"/"+Constants.APP_ROOT_FOLDER+"/"+Constants.VERSION_FOLDER+"/"+insightId;
		
		classLogger.info("Pushing insight from local=" + Utility.cleanLogString(localInsightFolderPath) + " to remote=" + Utility.cleanLogString(storageInsightFolder));
		centralStorageEngine.syncLocalToStorage(localInsightFolderPath, storageInsightFolder, null);
		classLogger.debug("Done pushing insight from local=" + Utility.cleanLogString(localInsightFolderPath) + " to remote=" + Utility.cleanLogString(storageInsightFolder));
	}

	@Override
	public void pullInsight(String projectId, String insightId) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		}

		// only need to pull the insight folder - 99% the project is always already loaded to get to this point
		String localInsightFolderPath = Utility.normalizePath(AssetUtility.getProjectVersionFolder(project.getProjectName(), projectId) + "/" + insightId);
		File localInsightF = new File(localInsightFolderPath);
		if(!localInsightF.exists() || !localInsightF.exists()) {
			localInsightF.mkdirs();
		}
		String storageInsightFolder = PROJECT_CONTAINER_PREFIX+projectId+"/"+Constants.APP_ROOT_FOLDER+"/"+Constants.VERSION_FOLDER+"/"+insightId;
		
		classLogger.info("Pulling insight from remote=" + Utility.cleanLogString(storageInsightFolder) + " to target=" + Utility.cleanLogString(localInsightFolderPath));
		centralStorageEngine.syncStorageToLocal(storageInsightFolder, localInsightFolderPath);
		classLogger.debug("Done pulling insight from remote=" + Utility.cleanLogString(storageInsightFolder) + " to target=" + Utility.cleanLogString(localInsightFolderPath));
	}

	@Override
	public void pushInsightImage(String projectId, String insightId, String oldImageFileName, String newImageFileName) throws IOException, InterruptedException {
		IProject project = Utility.getProject(projectId, false);
		if (project == null) {
			throw new IllegalArgumentException("Project not found...");
		} 

		String sharedRCloneConfig = null;
		try {
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			
			String storageInsightFolder = PROJECT_CONTAINER_PREFIX+projectId+"/"+Constants.APP_ROOT_FOLDER+"/"+Constants.VERSION_FOLDER+"/"+insightId;
			// since extensions might be different, need to actually delete the old file by name
			if(oldImageFileName != null) {
				String storageOldFileToDelete = storageInsightFolder+"/"+oldImageFileName;
				classLogger.info("Deleting old insight image from remote=" + Utility.cleanLogString(storageOldFileToDelete));
				centralStorageEngine.deleteFromStorage(storageOldFileToDelete, sharedRCloneConfig);
				classLogger.debug("Done deleting old insight image from remote=" + Utility.cleanLogString(storageOldFileToDelete));
			} else {
				classLogger.info("No old insight image on remote to delete");
			}

			if(newImageFileName != null) {
				String localInsightImageFilePath = Utility.normalizePath(AssetUtility.getProjectVersionFolder(project.getProjectName(), projectId) + "/" + insightId + "/" + newImageFileName);
				classLogger.info("Pushing insight image from local=" + Utility.cleanLogString(localInsightImageFilePath) + " to remote=" + Utility.cleanLogString(storageInsightFolder));
				centralStorageEngine.copyToStorage(localInsightImageFilePath, storageInsightFolder, sharedRCloneConfig, null);
				classLogger.debug("Done pushing insight image from local=" + Utility.cleanLogString(localInsightImageFilePath) + " to remote=" + Utility.cleanLogString(storageInsightFolder));
			} else {
				classLogger.info("No new insight image to add to remote");
			}
		} finally {
			if(sharedRCloneConfig != null) {
				try {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				} catch(Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	
	/*
	 * User
	 */

	@Override
	public void pullUserAssetOrWorkspace(String projectId, boolean isAsset, boolean projectAlreadyLoaded) throws IOException, InterruptedException {
		IProject project = null;
		String alias = null;
		if (projectAlreadyLoaded) {
			project = Utility.getUserAssetWorkspaceProject(projectId, isAsset);
			if (project == null) {
				throw new IllegalArgumentException("User asset/workspace project not found...");
			}
			alias = project.getProjectName();
		} else {
			if(isAsset) {
				alias = WorkspaceAssetUtils.ASSET_APP_NAME;
			} else {
				alias = WorkspaceAssetUtils.WORKSPACE_APP_NAME;
			}
		}

		// We need to pull the folder alias__projectId and the file alias__projectId.smss
		String aliasAndUserAssetWorkspaceId = SmssUtilities.getUniqueName(alias, projectId);
		String localUserAndAssetFolder = EngineUtility.USER_FOLDER + FILE_SEPARATOR + aliasAndUserAssetWorkspaceId;
		String storageUserAssetWorkspaceFolder = USER_CONTAINER_PREFIX + projectId;
		String storageSmssFolder = USER_CONTAINER_PREFIX + projectId + SMSS_POSTFIX;
		
		String sharedRCloneConfig = null;
		try {
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			// Close the user asset/workspace project so that we can pull without file locks
			try {
				if (projectAlreadyLoaded) {
					DIHelper.getInstance().removeProjectProperty(projectId);
					project.close();
				}

				// Make the user asset/workspace directory (if it doesn't already exist)
				File localUserAndAssetF = new File(localUserAndAssetFolder);
				if(!localUserAndAssetF.exists() || !localUserAndAssetF.isDirectory()) {
					localUserAndAssetF.mkdir(); 
				}

				// Pull the contents of the project folder before the smss
				classLogger.info("Pulling user asset/workspace from remote=" + storageUserAssetWorkspaceFolder + " to target=" + localUserAndAssetFolder);
				centralStorageEngine.syncStorageToLocal(storageUserAssetWorkspaceFolder, localUserAndAssetFolder, sharedRCloneConfig);
				classLogger.debug("Done pulling from remote=" + storageUserAssetWorkspaceFolder + " to target=" + localUserAndAssetFolder);

				// Now pull the smss
				classLogger.info("Pulling smss from remote=" + storageSmssFolder + " to target=" + EngineUtility.USER_FOLDER);
				// THIS MUST BE COPY AND NOT SYNC TO AVOID DELETING EVERYTHING IN THE USER FOLDER
				centralStorageEngine.copyToLocal(storageSmssFolder, EngineUtility.USER_FOLDER, sharedRCloneConfig);
				classLogger.debug("Done pulling from remote=" + storageSmssFolder + " to target=" + EngineUtility.USER_FOLDER);
			} finally {
				// Re-open the project
				if (projectAlreadyLoaded) {
					Utility.getUserAssetWorkspaceProject(projectId, isAsset);
				}
			}
		} finally {
			if(sharedRCloneConfig != null) {
				try {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				} catch(Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
		}
	}

	@Override
	public void pushUserAssetOrWorkspace(String projectId, boolean isAsset) throws IOException, InterruptedException {
		IProject project = Utility.getUserAssetWorkspaceProject(projectId, isAsset);
		if (project == null) {
			throw new IllegalArgumentException("User asset/workspace project not found...");
		}

		// We need to push the folder alias__projectId and the file alias__projectId.smss
		String alias = project.getProjectName();
		String aliasAndUserAssetWorkspaceId = alias + "__" + projectId;
		String localUserAssetWorkspaceFolder = EngineUtility.USER_FOLDER + FILE_SEPARATOR + aliasAndUserAssetWorkspaceId;
		String localSmssFileName = aliasAndUserAssetWorkspaceId + ".smss";
		String localSmssFilePath = EngineUtility.USER_FOLDER + FILE_SEPARATOR + localSmssFileName;

		String sharedRCloneConfig = null;

		String storageUserAssetWorkspaceFolder = USER_CONTAINER_PREFIX + projectId;
		String storageSmssFolder = USER_CONTAINER_PREFIX + projectId + SMSS_POSTFIX;

		try {
			DIHelper.getInstance().removeProjectProperty(projectId);
			project.close();
			
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			centralStorageEngine.syncLocalToStorage(localUserAssetWorkspaceFolder, storageUserAssetWorkspaceFolder, sharedRCloneConfig, null);
			centralStorageEngine.copyToStorage(localSmssFilePath, storageSmssFolder, sharedRCloneConfig, null);
		} finally {
			try {
				// Re-open the project
				Utility.getUserAssetWorkspaceProject(projectId, isAsset);
			} catch(Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			}
			if(sharedRCloneConfig != null) {
				try {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				} catch(Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
		}
	}
	
	///////////////////////////////////////////////////////////////////////////////////

	// utility methods
	
	/**
	 * 
	 * @param directory
	 * @return
	 */
	protected List<String> getSqlLiteFile(String directory) {
		File dir = new File(directory);
		List<String> sqlFiles = new ArrayList<>();
		//search dir for .sqlite files 
		for(File file : dir.listFiles()) {
			if (file.getName().endsWith((".sqlite"))) {
				sqlFiles.add(file.getName());
			}
		}
		if(sqlFiles.size() > 1){
			if(sqlFiles.contains("insights_database.sqlite")) {
				sqlFiles.remove("insights_database.sqlite");
			}
		}
		if(sqlFiles.size() > 1) {
			classLogger.warn("Found multiple sqlite files. Only expecting 1 for database");
			classLogger.warn("Found multiple sqlite files. Only expecting 1 for database");
			classLogger.warn("Found multiple sqlite files. Only expecting 1 for database");
			classLogger.warn("Found multiple sqlite files. Only expecting 1 for database");
		}
		return sqlFiles;
	}
	
	/**
	 * 
	 * @param directory
	 * @return
	 */
	protected List<String> getH2File(String directory) {
		File dir = new File(directory);
		List<String> sqlFiles = new ArrayList<>();
		//search dir for .sqlite files 
		for(File file : dir.listFiles()) {
			if (file.getName().endsWith((".mv.db"))) {
				sqlFiles.add(file.getName());
			}
		}
		if(sqlFiles.size() > 1){
			if(sqlFiles.contains("insights_database.mv.db")) {
				sqlFiles.remove("insights_database.mv.db");
			}
		}
		if(sqlFiles.size() > 1) {
			classLogger.warn("Found multiple h2 files. Only expecting 1 for database");
			classLogger.warn("Found multiple h2 files. Only expecting 1 for database");
			classLogger.warn("Found multiple h2 files. Only expecting 1 for database");
			classLogger.warn("Found multiple h2 files. Only expecting 1 for database");
		}
		return sqlFiles;
	}

	/**
	 * 
	 * @param project
	 * @param specificProjectFolder
	 * @return
	 */
	protected String getInsightDB(IProject project, String specificProjectFolder) {
		RdbmsTypeEnum insightDbType = project.getInsightDatabase().getDbType();
		String insightDbName = null;
		if (insightDbType == RdbmsTypeEnum.H2_DB) {
			insightDbName = "insights_database.mv.db";
		} else {
			insightDbName = "insights_database.sqlite";
		}
		File dir = new File(specificProjectFolder);
		for (File file : dir.listFiles()) {
			if (file.getName().equalsIgnoreCase(insightDbName)){
				return file.getName();
			}
		}
		throw new IllegalArgumentException("There is no insight database for project: " + project.getProjectName());
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Map<String, List<String>> listAllContainersByBucket() throws IOException, InterruptedException {
		Map<String, List<String>> allBuckets = new HashMap<String, List<String>>();
		String sharedRCloneConfig = null;
		try {
			if(centralStorageEngine.canReuseRcloneConfig()) {
				sharedRCloneConfig = centralStorageEngine.createRCloneConfig();
			}
			
			List<String> buckets = Arrays.asList(DATABASE_BLOB, STORAGE_BLOB, MODEL_BLOB, VECTOR_BLOB, FUNCTION_BLOB, VENV_BLOB, PROJECT_BLOB);
			
			for(String b : buckets) {
				List<String> cloudFiles = centralStorageEngine.list(b, sharedRCloneConfig);
				allBuckets.put(b, cloudFiles);
			}
		} finally {
			if(sharedRCloneConfig != null) {
				try {
					centralStorageEngine.deleteRcloneConfig(sharedRCloneConfig);
				} catch(Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
		}
		
		return allBuckets;
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	
	/*
	 * Legacy
	 */

	@Override
	public List<String> listAllBlobContainers() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deleteContainer(String containerId) throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		
	}

	////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////

	
//	public static void main(String[] args) throws Exception {
//		TestUtilityMethods.loadAll("C:/workspace/Semoss_Dev/RDF_Map.prop");
//		Properties coreProp = DIHelper.getInstance().getCoreProp();
//		coreProp.put("SEMOSS_STORAGE_PROVIDER", "MINIO");
//		coreProp.put(MinioStorageEngine.MINIO_REGION_KEY, "us-east-1");
//		coreProp.put(MinioStorageEngine.MINIO_ACCESS_KEY, "***REMOVED***");
//		coreProp.put(MinioStorageEngine.MINIO_SECRET_KEY, "***REMOVED***");
//		coreProp.put(MinioStorageEngine.MINIO_ENDPOINT_KEY, "http://localhost:9000");
//		coreProp.put(Constants.ENGINE, "CENTRAL_STORAGE");
//
//		{
//			String baseFolder = DIHelper.getInstance().getProperty(Constants.BASE_FOLDER);
//			String engineProp = baseFolder + "\\db\\diabetes sanjay and sarji__56af9395-64fd-40a2-b68c-bbd6961336a5.smss";
//			IDatabaseEngine sampleDb = new RDBMSNativeEngine();
//			sampleDb.open(engineProp);
//			DIHelper.getInstance().setEngineProperty("56af9395-64fd-40a2-b68c-bbd6961336a5", sampleDb);
//		}
//		
//		ICloudClient centralStorage = CentralCloudStorage.getInstance();
//		centralStorage.pushEngine("56af9395-64fd-40a2-b68c-bbd6961336a5");
//		centralStorage.pullEngine("56af9395-64fd-40a2-b68c-bbd6961336a5", null, true);
//		centralStorage.pullLocalDatabaseFile("56af9395-64fd-40a2-b68c-bbd6961336a5", RdbmsTypeEnum.H2_DB);
//	}

}
