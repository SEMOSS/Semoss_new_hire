package prerna.reactor.frame.r;

import java.util.List;
import java.util.Vector;

import prerna.ds.OwlTemporalEngineMeta;
import prerna.ds.r.RDataTable;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.usertracking.AnalyticsTrackerHelper;
import prerna.util.usertracking.UserTrackerFactory;

public class ToProperCaseReactor extends AbstractRFrameReactor {

	/**
	 * This reactor changes columns to all upper case 
	 * The inputs to the reactor are: 
	 * 1) the columns to update
	 */
	
	public ToProperCaseReactor() {
		this.keysToGet = new String[] { ReactorKeysEnum.FRAME.getKey(), ReactorKeysEnum.COLUMNS.getKey() };
	}
	
	@Override
	public NounMetadata execute() {
		// initialize rJavaTranslator
		init();
		
		this.rJavaTranslator.checkPackages(new String[] {"snakecase"});
		
		StringBuilder builder = new StringBuilder();
		builder.append("library(snakecase);");
		
		// get frame
		RDataTable frame = (RDataTable) getFrame();
		OwlTemporalEngineMeta metaData = frame.getMetaData();

		// get table name
		String table = frame.getName();

		// get inputs
		List<String> columns = getColumns();
		for (int i = 0; i < columns.size(); i++) {
			String col = columns.get(i);
			if (col.contains("__")) {
				String[] split = col.split("__");
				col = split[1];
				table = split[0];
			}
			String dataType = metaData.getHeaderTypeAsString(table + "__" + col);
			if(dataType == null)
				return getWarning("Frame is out of sync / No Such Column. Cannot perform this operation");

			if (dataType.equalsIgnoreCase("STRING")) {
				// define the script to be executed
				builder.append(table + "$" + col + " <- to_upper_camel_case(" + table + "$" + col + ", sep_out=\" \");");
			}
		}
		
		// execute the r script
		// script will be of the form:
		// FRAME$column <- stri_trans_totitle(FRAME$column)
		this.rJavaTranslator.runR(builder.toString());
		this.addExecutedCode(builder.toString());

		// NEW TRACKING
		UserTrackerFactory.getInstance().trackAnalyticsWidget(
				this.insight, 
				frame, 
				"ToProper", 
				AnalyticsTrackerHelper.getHashInputs(this.store, this.keysToGet));
		
		return new NounMetadata(frame, PixelDataType.FRAME, PixelOperationType.FRAME_DATA_CHANGE);
	}
	
	//////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////
	///////////////////////// GET PIXEL INPUT ////////////////////////////
	//////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////

	private List<String> getColumns() {
		List<String> columns = new Vector<String>();

		GenRowStruct colGrs = this.store.getNoun(this.keysToGet[1]);
		if (colGrs != null && !colGrs.isEmpty()) {
			for (int selectIndex = 0; selectIndex < colGrs.size(); selectIndex++) {
				String column = colGrs.get(selectIndex) + "";
				columns.add(column);
			}
		} else {
			GenRowStruct inputsGRS = this.getCurRow();
			// keep track of selectors to change to upper case
			if (inputsGRS != null && !inputsGRS.isEmpty()) {
				for (int selectIndex = 0; selectIndex < inputsGRS.size(); selectIndex++) {
					String column = inputsGRS.get(selectIndex) + "";
					columns.add(column);
				}
			}
		}

		return columns;
	}
}
