package prerna.reactor.frame.rdbms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import prerna.ds.OwlTemporalEngineMeta;
import prerna.ds.rdbms.AbstractRdbmsFrame;
import prerna.reactor.frame.AbstractFrameReactor;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Constants;

public class ToUpperCaseReactor extends AbstractFrameReactor {
	
	private static final Logger classLogger = LogManager.getLogger(ToUpperCaseReactor.class);

	public ToUpperCaseReactor() {
		this.keysToGet = new String[] { ReactorKeysEnum.COLUMNS.getKey() };
	}
	
	@Override
	public NounMetadata execute() {
		// get frame
		AbstractRdbmsFrame frame = (AbstractRdbmsFrame) getFrame();
		OwlTemporalEngineMeta metaData = frame.getMetaData();

		GenRowStruct inputsGRS = this.getCurRow();
		String update = "";
		if (inputsGRS != null && !inputsGRS.isEmpty()) {
			for (int selectIndex = 0; selectIndex < inputsGRS.size(); selectIndex++) {
				NounMetadata input = inputsGRS.getNoun(selectIndex);
				String columnName = input.getValue() + "";
				
				String table = frame.getName();
				String column = columnName;
				if (columnName.contains("__")) {
					String[] split = columnName.split("__");
					table = split[0];
					column = split[1];
				}
				
				String dataType = metaData.getHeaderTypeAsString(table + "__" + column);
				if (dataType.equals("STRING")) {
					// execute update table set column = UPPER(column);
					update += "UPDATE " + table + " SET " + column + " = UPPER(" + column + ") ; ";			
				}
			}
			if (update.length() > 0) {
				try {
					frame.getBuilder().runQuery(update);
				} catch (Exception e) {
					classLogger.error(Constants.STACKTRACE, e);
				}
			}
		}
		return new NounMetadata(frame, PixelDataType.FRAME, PixelOperationType.FRAME_DATA_CHANGE);
	}

}
