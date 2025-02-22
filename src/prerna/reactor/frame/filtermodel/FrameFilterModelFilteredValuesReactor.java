package prerna.reactor.frame.filtermodel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import prerna.algorithm.api.ITableDataFrame;
import prerna.algorithm.api.SemossDataType;
import prerna.engine.api.IRawSelectWrapper;
import prerna.om.InsightPanel;
import prerna.query.querystruct.SelectQueryStruct;
import prerna.query.querystruct.filters.GenRowFilters;
import prerna.query.querystruct.filters.IQueryFilter;
import prerna.query.querystruct.filters.OrQueryFilter;
import prerna.query.querystruct.filters.SimpleQueryFilter;
import prerna.query.querystruct.selectors.QueryColumnOrderBySelector;
import prerna.query.querystruct.selectors.QueryColumnSelector;
import prerna.query.querystruct.selectors.QueryFunctionHelper;
import prerna.query.querystruct.selectors.QueryFunctionSelector;
import prerna.reactor.frame.filter.AbstractFilterReactor;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Constants;

public class FrameFilterModelFilteredValuesReactor extends AbstractFilterReactor {

	private static final Logger classLogger = LogManager.getLogger(FrameFilterModelFilteredValuesReactor.class);
	
	/**
	 * This reactor has many inputs
	 * 
	 * 1) columnName <- required
	 * 2) filterWord <- optional
	 * 3) limit <- optional
	 * 4) offset <- optional
	 * 5) panel <- optional
	 * 
	 * This reactor returns the filter values that are filtered out
	 * i.e. these would be values that are unchecked in a drop down selection
	 */
	
	public FrameFilterModelFilteredValuesReactor() {
		this.keysToGet = new String[] { ReactorKeysEnum.COLUMN.getKey(), ReactorKeysEnum.FILTER_WORD.getKey(),
				ReactorKeysEnum.LIMIT.getKey(), ReactorKeysEnum.OFFSET.getKey(), ReactorKeysEnum.PANEL.getKey() };
	}

	@Override
	public NounMetadata execute() {
		ITableDataFrame dataframe = getFrame();

		GenRowStruct colGrs = this.store.getNoun(keysToGet[0]);
		if (colGrs == null || colGrs.isEmpty()) {
			throw new IllegalArgumentException("Need to set the column for the filter model");
		}
		String tableCol = colGrs.get(0).toString();

		String filterWord = null;
		GenRowStruct filterWordGrs = this.store.getNoun(keysToGet[1]);
		if (filterWordGrs != null && !filterWordGrs.isEmpty()) {
			filterWord = filterWordGrs.get(0).toString();
		}

		int limit = -1;
		GenRowStruct limitGrs = this.store.getNoun(keysToGet[2]);
		if (limitGrs != null && !limitGrs.isEmpty()) {
			limit = ((Number) limitGrs.get(0)).intValue();
		}

		int offset = -1;
		GenRowStruct offsetGrs = this.store.getNoun(keysToGet[3]);
		if (offsetGrs != null && !offsetGrs.isEmpty()) {
			offset = ((Number) offsetGrs.get(0)).intValue();
		}

		InsightPanel panel = null;
		GenRowStruct panelGrs = this.store.getNoun(keysToGet[4]);
		if (panelGrs != null && !panelGrs.isEmpty()) {
			panel = (InsightPanel) panelGrs.get(0);
		}

		return getFilterModel(dataframe, tableCol, filterWord, limit, offset, panel);
	}

	public NounMetadata getFilterModel(ITableDataFrame dataframe, String tableCol, String filterWord, int limit, int offset, InsightPanel panel) {
		// store results in this map
		Map<String, Object> retMap = new HashMap<String, Object>();
		// first just return the info that was passed in
		retMap.put("column", tableCol);
		retMap.put("limit", limit);
		retMap.put("offset", offset);
		retMap.put("filterWord", filterWord);

		// set the base info in the query struct
		SelectQueryStruct qs = new SelectQueryStruct();
		QueryColumnSelector selector = new QueryColumnSelector(tableCol);
		qs.addSelector(selector);
		qs.setLimit(limit);
		qs.setOffSet(offset);
		qs.addOrderBy(new QueryColumnOrderBySelector(tableCol));

		// get the base filters that are being applied that we are concerned about
		GenRowFilters baseFilters = dataframe.getFrameFilters().copy();
		if (panel != null) {
			baseFilters.merge(panel.getPanelFilters().copy());
		}
		
		// if the current filters doesn't use the column
		// there is no values that are unchecked to select
		// i.e. nothing is done that is filtered that the user can undo for this column
		List<Object> filterValues = new ArrayList<Object>();
		if (columnFiltered(baseFilters, tableCol)) {

			// to get the values that the user has filtered out
			// we need create the inverse of the filters
			// if they touch the column we care about
			GenRowFilters inverseFilters = new GenRowFilters();
			List<IQueryFilter> baseFiltersList = baseFilters.getFilters();
			for (IQueryFilter filter : baseFiltersList) {
				// check if it is a simple or complex filter
				if (filter.getQueryFilterType() == IQueryFilter.QUERY_FILTER_TYPE.SIMPLE) {
					if (filter.containsColumn(tableCol)) {
						// reverse the comparator
						SimpleQueryFilter fCopy = (SimpleQueryFilter) filter.copy();
						fCopy.reverseComparator();

						if(IQueryFilter.comparatorIsNotEquals(fCopy.getComparator()) && !SimpleQueryFilter.colValuesContainsNull(fCopy)) {
							// include a show of null
							// so we need to add this fCopy with a null find
							NounMetadata nullLComparison = new NounMetadata(new QueryColumnSelector(tableCol), PixelDataType.COLUMN);
							List<Object> nullList = new Vector<Object>();
							nullList.add(null);
							NounMetadata nullRComparison = new NounMetadata(nullList, PixelDataType.CONST_STRING);
							SimpleQueryFilter nullFilter = new SimpleQueryFilter(nullLComparison, "==", nullRComparison);
							
							OrQueryFilter orFilter = new OrQueryFilter(fCopy, nullFilter);
							inverseFilters.addFilters(orFilter);
						} else {
							inverseFilters.addFilters(fCopy);
						}
					} else {
						// just add it to the filters
						inverseFilters.addFilters(filter.copy());
					}
				}
				// okay, this is hard to figure out if it is not simple
				// so just add it
				else {
					inverseFilters.addFilters(filter.copy());
				}
			}

			// to get the filtered values
			// run with the inverse filters of the current column
			qs.setExplicitFilters(inverseFilters);
			
			// add the filter word as a like filter
			if (filterWord != null && !filterWord.trim().isEmpty()) {
				NounMetadata lComparison = new NounMetadata(new QueryColumnSelector(tableCol), PixelDataType.COLUMN);
				String comparator = "?like";
				NounMetadata rComparison = new NounMetadata(filterWord, PixelDataType.CONST_STRING);
				SimpleQueryFilter wFilter = new SimpleQueryFilter(lComparison, comparator, rComparison);
				qs.addExplicitFilter(wFilter);
			}
			
			
			// flush out the values
			IRawSelectWrapper filterValuesIt = null;
			try {
				filterValuesIt = dataframe.query(qs);
				while (filterValuesIt.hasNext()) {
					filterValues.add(filterValuesIt.next().getValues()[0]);
				}
			} catch (Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			} finally {
				if(filterValuesIt != null) {
					try {
						filterValuesIt.close();
					} catch (IOException e) {
						classLogger.error(Constants.STACKTRACE, e);
					}
				}
			}
		}
		retMap.put("filterValues", filterValues);

		// for numerical, also add the min/max
		String alias = selector.getAlias();
		String metaName = dataframe.getMetaData().getUniqueNameFromAlias(alias);
		if (metaName == null) {
			metaName = alias;
		}
		SemossDataType columnType = dataframe.getMetaData().getHeaderTypeAsEnum(metaName);
		if (SemossDataType.INT == columnType || SemossDataType.DOUBLE == columnType) {
			QueryColumnSelector innerSelector = new QueryColumnSelector(tableCol);

			QueryFunctionSelector mathSelector = new QueryFunctionSelector();
			mathSelector.addInnerSelector(innerSelector);
			mathSelector.setFunction(QueryFunctionHelper.MIN);
			
			SelectQueryStruct mathQS = new SelectQueryStruct();
			mathQS.addSelector(mathSelector);

			// get the absolute min when no filters are present
			Map<String, Object> minMaxMap = new HashMap<String, Object>();
			IRawSelectWrapper it = null;
			try {
				it = dataframe.query(mathQS);
				minMaxMap.put("absMin", it.next().getValues()[0]);
			} catch (Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			} finally {
				if(it != null) {
					try {
						it.close();
					} catch (IOException e) {
						classLogger.error(Constants.STACKTRACE, e);
					}
				}
			}
			// get the abs max when no filters are present
			mathSelector.setFunction(QueryFunctionHelper.MAX);
			try {
				it = dataframe.query(mathQS);
				minMaxMap.put("absMax", it.next().getValues()[0]);
			} catch (Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			} finally {
				if(it != null) {
					try {
						it.close();
					} catch (IOException e) {
						classLogger.error(Constants.STACKTRACE, e);
					}
				}
			}

			// add in the filters now and repeat
			mathQS.setExplicitFilters(baseFilters);
			// run for actual max
			try {
				it = dataframe.query(mathQS);
				minMaxMap.put("max", it.next().getValues()[0]);
			} catch (Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			} finally {
				if(it != null) {
					try {
						it.close();
					} catch (IOException e) {
						classLogger.error(Constants.STACKTRACE, e);
					}
				}
			}
			// run for actual min
			mathSelector.setFunction(QueryFunctionHelper.MIN);
			try {
				it = dataframe.query(mathQS);
				minMaxMap.put("min", it.next().getValues()[0]);
			} catch (Exception e) {
				classLogger.error(Constants.STACKTRACE, e);
			} finally {
				if(it != null) {
					try {
						it.close();
					} catch (IOException e) {
						classLogger.error(Constants.STACKTRACE, e);
					}
				}
			}

			retMap.put("minMax", minMaxMap);
		}

		return new NounMetadata(retMap, PixelDataType.CUSTOM_DATA_STRUCTURE, PixelOperationType.FILTER_MODEL);
	}

	private boolean columnFiltered(GenRowFilters filters, String columnName) {
		Set<String> filteredCols = filters.getAllFilteredColumns();
		if (filteredCols.contains(columnName)) {
			return true;
		}
		if (columnName.contains("__")) {
			if (filteredCols.contains(columnName.split("__")[1])) {
				return true;
			}
		}
		return false;
	}
}
