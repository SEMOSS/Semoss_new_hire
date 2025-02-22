package prerna.reactor.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import prerna.auth.AccessToken;
import prerna.auth.AuthProvider;
import prerna.auth.User;
import prerna.auth.utils.SecurityAdminUtils;
import prerna.auth.utils.SecurityProjectUtils;
import prerna.reactor.AbstractReactor;
import prerna.rpa.config.JobConfigKeys;
import prerna.rpa.quartz.jobs.insight.RunPixelJobFromDB;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Constants;
import prerna.util.Utility;

public class ScheduleJobReactor extends AbstractReactor {

	private static final Logger classLogger = LogManager.getLogger(ScheduleJobReactor.class);

	// Inputs
	public static final String TRIGGER_NOW = "triggerNow";
	public static final String TRIGGER_ON_LOAD = "triggerOnLoad";
	public static final String UI_STATE = "uiState";

	// Outputs
	public static final String JSON_CONFIG = "jsonConfig";

	protected Scheduler scheduler = null;

	public ScheduleJobReactor() {
		this.keysToGet = new String[] { ReactorKeysEnum.JOB_NAME.getKey(), ReactorKeysEnum.JOB_GROUP.getKey(),
				ReactorKeysEnum.CRON_EXPRESSION.getKey(), ReactorKeysEnum.CRON_TZ.getKey(), 
				ReactorKeysEnum.RECIPE.getKey(), ReactorKeysEnum.RECIPE_PARAMETERS.getKey(),
				TRIGGER_ON_LOAD, TRIGGER_NOW, UI_STATE, ReactorKeysEnum.JOB_TAGS.getKey() };
	}

	@Override
	public NounMetadata execute() {
		if(Utility.schedulerForceDisable()) {
			throw new IllegalArgumentException("Scheduler is not enabled");
		}
		organizeKeys();

		String userId = null;
		// Get inputs
        String jobId = UUID.randomUUID().toString();
		String jobName = this.keyValue.get(ReactorKeysEnum.JOB_NAME.getKey());
		String jobGroup = this.keyValue.get(ReactorKeysEnum.JOB_GROUP.getKey());
		String cronExpression = this.keyValue.get(ReactorKeysEnum.CRON_EXPRESSION.getKey());
		TimeZone cronTimeZone = null;
		String cronTz = this.keyValue.get(ReactorKeysEnum.CRON_TZ.getKey());
		if(cronTz == null || (cronTz=cronTz.trim()).isEmpty()) {
			cronTz = Utility.getApplicationTimeZoneId();
		}
		try {
			cronTimeZone = TimeZone.getTimeZone(cronTz);
		} catch(Exception e) {
			classLogger.error(Constants.STACKTRACE, e);
			throw new IllegalArgumentException("Invalid Time Zone = " + cronTz);
		}
		List<String> jobTags = getJobTags();

		SchedulerDatabaseUtility.validateInput(jobName, jobGroup, cronExpression);

		// the job group is the app the user is in
		// user must be an admin or editor of the app
		// to add a scheduled job
		User user = this.insight.getUser();
		if(!SecurityAdminUtils.userIsAdmin(user) && !SecurityProjectUtils.userCanEditProject(user, jobGroup)) {
			throw new IllegalArgumentException("User does not have proper permissions to schedule jobs");
		}
		
		String recipe = this.keyValue.get(ReactorKeysEnum.RECIPE.getKey());
		recipe = SchedulerDatabaseUtility.validateAndDecodeRecipe(recipe);
		recipe = recipe.trim();
		
		String recipeParameters = this.keyValue.get(ReactorKeysEnum.RECIPE_PARAMETERS.getKey());
		recipeParameters = SchedulerDatabaseUtility.validateAndDecodeRecipeParameters(recipeParameters);
		if(recipeParameters == null) {
			recipeParameters = "";
		} else {
			recipeParameters = recipeParameters.trim();
		}
		
		// get triggers
		boolean triggerOnLoad = getTriggerOnLoad();
		boolean triggerNow = getTriggerNow();

		String uiState = this.keyValue.get(UI_STATE);
		if(uiState == null) {
			throw new NullPointerException("UI State is null and needs to be passed");
		}
		
		try {
			scheduler = SchedulerFactorySingleton.getInstance().getScheduler();

			// start up scheduler
			SchedulerDatabaseUtility.startScheduler(scheduler);

			// get user access information
			List<AuthProvider> authProviders = user.getLogins();
			StringBuilder providerInfo = new StringBuilder(); 
			for (int i = 0; i < authProviders.size(); i++) {
				AuthProvider authProvider = authProviders.get(i); 
				AccessToken token = user.getAccessToken(authProvider);
				// save user id for later insertion
				userId = token.getId();
				providerInfo.append(authProvider.name()).append(":").append(token.getId());
				if (i != authProviders.size() - 1) {
					providerInfo.append(",");
				}
			}

			JobKey jobKey = JobKey.jobKey(jobId, jobGroup);
			// if job exists throw error, job already exists
			if (scheduler.checkExists(jobKey)) {
				classLogger.error("job " + Utility.cleanLogString(jobKey.toString()) + " already exists");
				throw new IllegalArgumentException("job " + Utility.cleanLogString(jobKey.toString()) + " already exists");
			}

			try {
				scheduleJob(jobKey, jobId, jobName, jobGroup, 
						cronExpression, cronTimeZone, 
						recipe, recipeParameters,
						triggerOnLoad, uiState, providerInfo.toString());
			} catch (SchedulerException e) {
				throw new RuntimeException("Failed to schedule the job", e);
			}
			
			if (triggerNow) {
				triggerJobNow(jobKey);
			}

			// insert into SMOSS_JOB_RECIPES table
			classLogger.info("Saving JobId to database: "+jobId);
			SchedulerDatabaseUtility.insertIntoJobRecipesTable(userId, jobId, 
					jobName, jobGroup, 
					cronExpression, cronTimeZone,
					recipe, recipeParameters, 
					"Default", triggerOnLoad, uiState, jobTags);

			Map<String, Object> retMap = createRetMap(jobId, jobName, jobGroup, 
					cronExpression, cronTimeZone, 
					recipe, recipeParameters,
					triggerOnLoad, uiState, providerInfo.toString());
						
			return new NounMetadata(retMap, PixelDataType.MAP, PixelOperationType.SCHEDULE_JOB);
		} catch (SchedulerException se) {
			classLogger.error(Constants.STACKTRACE, se);
			throw new IllegalArgumentException("Unable to schedule the job. Error message = " + se.getMessage());
		}
	}

	////////////////// Helper Methods ////////////////////////

	protected void triggerJobNow(JobKey jobKey) {
		try {
			if (scheduler.checkExists(jobKey)) {
				scheduler.triggerJob(jobKey);
			}
		} catch (SchedulerException se) {
			classLogger.error(Constants.STACKTRACE, se);
		}
	}

	/**
	 * 
	 * @param jobId
	 * @param jobName
	 * @param jobGroup
	 * @param cronExpression
	 * @param cronTimeZone
	 * @param recipe
	 * @param recipeParameters
	 * @param triggerOnLoad
	 * @param uiState
	 * @param providerInfo
	 * @return
	 * @throws ParseConfigException
	 * @throws IllegalConfigException
	 * @throws SchedulerException
	 */
	protected JobKey scheduleJob(JobKey jobKey, String jobId, String jobName, String jobGroup, 
			String cronExpression, TimeZone cronTimeZone, String recipe, String recipeParameters, 
			boolean triggerOnLoad, String uiState, String providerInfo) throws SchedulerException {
		JobDataMap jobDataMap = getJobDataMap(jobId, jobName, jobGroup, 
				cronExpression, cronTimeZone, recipe, recipeParameters, 
				triggerOnLoad, uiState, providerInfo);
				
		Class<? extends Job> jobClass = RunPixelJobFromDB.class;

		// Schedule the job
		JobDetail job = JobBuilder.newJob(jobClass).withIdentity(jobKey).usingJobData(jobDataMap).storeDurably().build();
		Trigger trigger = TriggerBuilder.newTrigger().withIdentity(jobId+ "Trigger", jobGroup + "TriggerGroup")
				.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
				.inTimeZone(cronTimeZone)).build();

		scheduler.scheduleJob(job, trigger);

		classLogger.info("Scheduled " + Utility.cleanLogString(jobId) + " to run on the following schedule: " + Utility.cleanLogString(cronExpression) + ".");
		
		// Return the job key
		return job.getKey();
	}
	
	/**
	 * 
	 * @param jobId
	 * @param jobName
	 * @param jobGroup
	 * @param cronExpression
	 * @param cronTimeZone
	 * @param recipe
	 * @param recipeParameters
	 * @param triggerOnLoad
	 * @param uiState
	 * @param providerInfo
	 * @return
	 */
	public static JobDataMap getJobDataMap(String jobId, String jobName, String jobGroup, 
			String cronExpression, TimeZone cronTimeZone, String recipe, String recipeParameters, 
			boolean triggerOnLoad, String uiState, String providerInfo) {
		JobDataMap jobDataMap = new JobDataMap();
		jobDataMap.put(providerInfo, triggerOnLoad);
		jobDataMap.put(JobConfigKeys.JOB_ID, jobId);
		jobDataMap.put(JobConfigKeys.JOB_NAME, jobName);
		jobDataMap.put(JobConfigKeys.JOB_GROUP, jobGroup);
		jobDataMap.put(JobConfigKeys.JOB_CRON_EXPRESSION, cronExpression);
		jobDataMap.put(JobConfigKeys.JOB_CRON_TIMEZONE, cronTimeZone.getID());
		jobDataMap.put(JobConfigKeys.TRIGGER_ON_LOAD, triggerOnLoad);
		jobDataMap.put(JobConfigKeys.UI_STATE, uiState);
		jobDataMap.put(JobConfigKeys.JOB_CLASS_NAME, "RunPixelJob");
		jobDataMap.put(JobConfigKeys.PIXEL, recipe);
		jobDataMap.put(JobConfigKeys.PIXEL_PARAMETERS, recipeParameters);
		jobDataMap.put(JobConfigKeys.USER_ACCESS, providerInfo);
		return jobDataMap;
	}
	
	/**
	 * TODO: DO I NEED TO RETURN THIS MAP ? 
	 * 
	 * @param jobId
	 * @param jobName
	 * @param jobGroup
	 * @param cronExpression
	 * @param cronTimeZone
	 * @param recipe
	 * @param recipeParameters
	 * @param triggerOnLoad
	 * @param uiState
	 * @param providerInfo
	 * @return
	 */
	public static Map<String, Object> createRetMap(String jobId, String jobName, String jobGroup, 
			String cronExpression, TimeZone cronTimeZone, String recipe, String recipeParameters, 
			boolean triggerOnLoad, String uiState, String providerInfo) {
		Map<String, Object> retMap = new HashMap<>();
		retMap.put(JobConfigKeys.JOB_ID, jobId);
		retMap.put(JobConfigKeys.JOB_NAME, jobName);
		retMap.put(JobConfigKeys.JOB_GROUP, jobGroup);
		retMap.put(JobConfigKeys.JOB_CRON_EXPRESSION, cronExpression);
		retMap.put(JobConfigKeys.JOB_CRON_TIMEZONE, cronTimeZone.getID());
		retMap.put(JobConfigKeys.TRIGGER_ON_LOAD, triggerOnLoad);
		retMap.put(JobConfigKeys.UI_STATE, uiState);
		retMap.put(JobConfigKeys.JOB_CLASS_NAME, "RunPixelJob");
		retMap.put(JobConfigKeys.PIXEL, recipe);
		retMap.put(JobConfigKeys.PIXEL_PARAMETERS, recipeParameters);
		return retMap;
	}

	protected boolean getTriggerOnLoad() {
		GenRowStruct boolGrs = this.store.getNoun(TRIGGER_ON_LOAD);
		if (boolGrs != null && !boolGrs.isEmpty()) {
			List<Object> val = boolGrs.getValuesOfType(PixelDataType.BOOLEAN);
			if(val != null && !val.isEmpty()) {
				return (boolean) val.get(0);
			}
		}

		return false;
	}

	protected boolean getTriggerNow() {
		GenRowStruct boolGrs = this.store.getNoun(TRIGGER_NOW);
		if (boolGrs != null && !boolGrs.isEmpty()) {
			List<Object> val = boolGrs.getValuesOfType(PixelDataType.BOOLEAN);
			if(val != null && !val.isEmpty()) {
				return (boolean) val.get(0);
			}
		}

		return false;
	}
	
	protected List<String> getJobTags() {
		List<String> jobTags = null;
		GenRowStruct grs= this.store.getNoun(ReactorKeysEnum.JOB_TAGS.getKey());
		if(grs != null && !grs.isEmpty()) {
			jobTags = new ArrayList<>();
			int size = grs.size();
			for(int i = 0; i < size; i++) {
				jobTags.add( grs.get(i)+"" );
			}
		}
		return jobTags;
	}
}
