package prerna.reactor.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import prerna.algorithm.api.SemossDataType;
import prerna.auth.User;
import prerna.engine.api.IHeadersDataRow;
import prerna.reactor.task.TaskBuilderReactor;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Constants;
import prerna.util.Utility;

public abstract class AbstractExportTxtReactor extends TaskBuilderReactor {

	protected static final String APPEND_TIMESTAMP = "appendTimestamp";

	protected String fileLocation = null;
	protected Logger logger;
	protected String delimiter;
	protected boolean appendTimestamp = true;
	
	/**
	 * Set the delimiter for the export
	 * 
	 * @param delimiter
	 */
	protected void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}

	/**
	 * Get the file name
	 * @param customName
	 * @param extension
	 * @param appendTimestamp
	 * @return
	 */
	public static String getExportFileName(User user, String customName, String extension, boolean appendTimestamp) {
		if(appendTimestamp) {
			return getExportFileName(user, customName,  extension);
		} else if(customName != null && !customName.trim().isEmpty()) {
			return Utility.normalizePath(customName.trim()) + "." + extension;
		}
		return Utility.normalizePath("SEMOSS_Export") + "." + extension;
	}
	
	protected boolean appendTimeStamp() {
		GenRowStruct boolGrs = this.store.getNoun(APPEND_TIMESTAMP);
		if(boolGrs != null) {
			if(boolGrs.size() > 0) {
				List<Object> val = boolGrs.getValuesOfType(PixelDataType.BOOLEAN);
				return (boolean) val.get(0);
			}
		}
		
		List<NounMetadata> booleanInput = this.curRow.getNounsOfType(PixelDataType.BOOLEAN);
		if(booleanInput != null && !booleanInput.isEmpty()) {
			return (boolean) booleanInput.get(0).getValue();
		}
		
		return true;
	}
	
	/**
	 * Get the file name and always append the timestamp
	 * @param customName
	 * @param extension
	 * @return
	 */
	public static String getExportFileName(User user, String customName, String extension) {
		// get a random file name
		ZonedDateTime currentDateTime = Utility.getCurrentZonedDateTimeForUser(user);
		 // Define a custom DateTimeFormatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-Z");
        String dateFormatted = currentDateTime.format(formatter);
		if(customName != null && !customName.trim().isEmpty()) {
			return Utility.normalizePath(customName.trim() + "_" + dateFormatted) + "." + extension;
		}
		return Utility.normalizePath("SEMOSS_Export_" + dateFormatted) + "." + extension;
	}
	
	@Override
	protected void buildTask() {
		// TODO: consolidate with Utility writeResultToFile?
		// TODO: consolidate with Utility writeResultToFile?
		// TODO: consolidate with Utility writeResultToFile?
		// TODO: consolidate with Utility writeResultToFile?
		// TODO: consolidate with Utility writeResultToFile?
		// TODO: consolidate with Utility writeResultToFile?
		// TODO: consolidate with Utility writeResultToFile?
		
		if (delimiter == null) {
			throw new IllegalArgumentException("Delimiter has not been defined for output");
		}
		File f = new File(this.fileLocation);
		try {
			// optimize the query so that it matches the general results on FE
			try {
				this.task.optimizeQuery(-1);
			} catch (Exception e) {
				logger.error(Constants.STACKTRACE, e);
				throw new IllegalArgumentException("Error occurred generating the query to write to the file");
			}
			
			long start = System.currentTimeMillis();

			try {
				f.createNewFile();
			} catch (IOException e) {
				logger.error(Constants.STACKTRACE, e);
			}

			FileWriter writer = null;
			BufferedWriter bufferedWriter = null;

			try {
				writer = new FileWriter(f);
				bufferedWriter = new BufferedWriter(writer);

				// store some variables and just reset
				// should be faster than creating new ones each time
				int i = 0;
				int size = 0;
				StringBuilder builder = null;
				// create typesArr as an array for faster searching
				String[] headers = null;
				SemossDataType[] typesArr = null;

				// we need to iterate and write the headers during the first time
				if (this.task.hasNext()) {
					IHeadersDataRow row = this.task.next();
					List<Map<String, Object>> headerInfo = this.task.getHeaderInfo();

					// generate the header row
					// and define constants used throughout like size, and types
					i = 0;
					headers = row.getHeaders();
					size = headers.length;
					typesArr = new SemossDataType[size];
					builder = new StringBuilder();
					for (; i < size; i++) {
						builder.append("\"").append(headers[i]).append("\"");
						if ((i + 1) != size) {
							builder.append(this.delimiter);
						}

						if(headerInfo.get(i).containsKey("dataType")) {
							typesArr[i] = SemossDataType.convertStringToDataType(headerInfo.get(i).get("dataType").toString());
						} else if(headerInfo.get(i).containsKey("type")) {
							typesArr[i] = SemossDataType.convertStringToDataType(headerInfo.get(i).get("type").toString());
						} else {
							typesArr[i] = SemossDataType.STRING;
						}
					}
					// write the header to the file
					bufferedWriter.write(builder.append("\n").toString());

					// generate the data row
					Object[] dataRow = row.getValues();
					builder = new StringBuilder();
					i = 0;
					for (; i < size; i++) {
						if(Utility.isNullValue(dataRow[i])) {
							builder.append("null");
						} else {
							if (typesArr[i] == SemossDataType.STRING) {
								builder.append("\"").append(dataRow[i].toString().replace("\"", "\"\"")).append("\"");
							} else {
								builder.append(dataRow[i]);
							}
						}
						if ((i + 1) != size) {
							builder.append(this.delimiter);
						}
					}
					// write row to file
					bufferedWriter.write(builder.append("\n").toString());
				}

				int counter = 1;
				// now loop through all the data
				while (this.task.hasNext()) {
					IHeadersDataRow row = this.task.next();
					// generate the data row
					Object[] dataRow = row.getValues();
					builder = new StringBuilder();
					i = 0;
					for (; i < size; i++) {
						if(Utility.isNullValue(dataRow[i])) {
							builder.append("null");
						} else {
							if (typesArr[i] == SemossDataType.STRING) {
								builder.append("\"").append(dataRow[i].toString().replace("\"", "\"\"")).append("\"");
							} else {
								builder.append(dataRow[i]);
							}
						}
						if ((i + 1) != size) {
							builder.append(this.delimiter);
						}
					}
					// write row to file
					bufferedWriter.write(builder.append("\n").toString());

					if (counter % 10_000 == 0) {
						logger.info("Finished writing line " + counter);
					}
					counter++;
				}

			} catch (IOException e) {
				logger.error(Constants.STACKTRACE, e);
			} finally {
				try {
					if (bufferedWriter != null) {
						bufferedWriter.close();
					}
					if (writer != null) {
						writer.close();
					}
				} catch (IOException e) {
					logger.error(Constants.STACKTRACE, e);
				}
			}

			long end = System.currentTimeMillis();
			logger.info("Time to output file = " + (end - start) + " ms");
		} catch (Exception e) {
			if (f.exists()) {
				f.delete();
			}
			throw new IllegalArgumentException(e.getMessage(), e);
		} finally {
			if(this.task != null) {
				try {
					this.task.close();
				} catch (IOException e) {
					logger.error(Constants.STACKTRACE, e);
				}
			}
		}
	}

}
