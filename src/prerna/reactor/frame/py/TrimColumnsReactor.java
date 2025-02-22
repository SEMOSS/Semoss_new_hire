package prerna.reactor.frame.py;

import java.util.List;
import java.util.Vector;

import prerna.ds.py.PandasFrame;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.usertracking.AnalyticsTrackerHelper;
import prerna.util.usertracking.UserTrackerFactory;

public class TrimColumnsReactor extends AbstractPyFrameReactor {

	/**
	 * This reactor trims column values
	 * The inputs to the reactor are: 
	 * 1) the columns to update
	 */
	
	public TrimColumnsReactor() {
		this.keysToGet = new String[] { ReactorKeysEnum.COLUMNS.getKey() };
	}

	@Override
	public NounMetadata execute() {
		// get frame
		PandasFrame frame = (PandasFrame) getFrame();
		String wrapperFrameName = frame.getWrapperName();

		// get inputs
		List<String> columns = getColumns();
		for (int i = 0; i < columns.size(); i++) {
			String col = columns.get(i);
			if (col.contains("__")) {
				String[] split = col.split("__");
				col = split[1];
				wrapperFrameName = split[0];
			}
			String script = wrapperFrameName + ".trim_col('" + col + "')";
			frame.runScript(script);
			this.addExecutedCode(script);
		}

		// NEW TRACKING
		UserTrackerFactory.getInstance().trackAnalyticsWidget(
				this.insight, 
				frame, 
				"Trim", 
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

		GenRowStruct colGrs = this.store.getNoun(this.keysToGet[0]);
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
