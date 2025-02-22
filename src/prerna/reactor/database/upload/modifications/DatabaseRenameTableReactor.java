package prerna.reactor.database.upload.modifications;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import prerna.auth.utils.SecurityEngineUtils;
import prerna.auth.utils.SecurityQueryUtils;
import prerna.cluster.util.ClusterUtil;
import prerna.engine.api.IDatabaseEngine;
import prerna.engine.api.IEngineModifier;
import prerna.engine.impl.modifications.EngineModificationFactory;
import prerna.engine.impl.owl.WriteOWLEngine;
import prerna.reactor.AbstractReactor;
import prerna.reactor.masterdatabase.SyncDatabaseWithLocalMasterReactor;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Constants;
import prerna.util.EngineSyncUtility;
import prerna.util.Utility;

public class DatabaseRenameTableReactor extends AbstractReactor {
	
	private static final Logger classLogger = LogManager.getLogger(DatabaseRenameTableReactor.class);

	public DatabaseRenameTableReactor() {
		this.keysToGet = new String[] { 
				ReactorKeysEnum.DATABASE.getKey(), 
				ReactorKeysEnum.CONCEPT.getKey(),
				ReactorKeysEnum.NEW_VALUE.getKey()
				};
		this.keyRequired = new int[] { 1, 1, 1};
	}

	@Override
	public NounMetadata execute() {
		organizeKeys();
		
		String databaseId = this.keyValue.get(this.keysToGet[0]);
		databaseId = SecurityQueryUtils.testUserEngineIdForAlias(this.insight.getUser(), databaseId);
		if(!SecurityEngineUtils.userCanEditEngine(this.insight.getUser(), databaseId)) {
			throw new IllegalArgumentException("Database" + databaseId + " does not exist or user does not have access to database");
		}
		
		String table = this.keyValue.get(this.keysToGet[1]);
		String newTable = this.keyValue.get(this.keysToGet[2]);
		
		boolean dbUpdate = false;
		IDatabaseEngine database = Utility.getDatabase(databaseId);
		try(WriteOWLEngine owlEngine = database.getOWLEngineFactory().getWriteOWL()) {
			IEngineModifier modifier = EngineModificationFactory.getEngineModifier(database);
			if(modifier == null) {
				throw new IllegalArgumentException("This type of data modification has not been implemented for this database type");
			}
			try {
				modifier.renameConcept(table, newTable);
			} catch (Exception e) {
				throw new IllegalArgumentException("Error occurred to alter the table. Error returned from driver: " + e.getMessage(), e);
			}
			
			// update owl
			try {
				owlEngine.renameConcept(table, newTable, newTable);
				owlEngine.commit();
				owlEngine.export();
				SyncDatabaseWithLocalMasterReactor syncWithLocal = new SyncDatabaseWithLocalMasterReactor();
				syncWithLocal.setInsight(this.insight);
				syncWithLocal.setNounStore(this.store);
				syncWithLocal.In();
				syncWithLocal.execute();
			} catch (IOException e) {
				NounMetadata noun = new NounMetadata(dbUpdate, PixelDataType.BOOLEAN);
				noun.addAdditionalReturn(getError("Error occurred saving the metadata file with the executed changes"));
				return noun;
			}
			EngineSyncUtility.clearEngineCache(databaseId);
			ClusterUtil.pushOwl(databaseId, owlEngine);
		} catch (IOException | InterruptedException e1) {
			classLogger.error(Constants.STACKTRACE, e1);
		}
		
		NounMetadata noun = new NounMetadata(dbUpdate, PixelDataType.BOOLEAN);
		noun.addAdditionalReturn(NounMetadata.getSuccessNounMessage("Successfully renamed the column"));
		return noun;
	}


}
