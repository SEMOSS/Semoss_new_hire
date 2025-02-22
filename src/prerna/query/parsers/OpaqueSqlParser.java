package prerna.query.parsers;

import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import prerna.query.querystruct.SelectQueryStruct;
import prerna.query.querystruct.filters.AndQueryFilter;
import prerna.query.querystruct.filters.IQueryFilter;
import prerna.query.querystruct.filters.OrQueryFilter;
import prerna.query.querystruct.filters.SimpleQueryFilter;
import prerna.query.querystruct.selectors.IQuerySelector;
import prerna.query.querystruct.selectors.QueryConstantSelector;
import prerna.query.querystruct.selectors.QueryOpaqueSelector;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;

public class OpaqueSqlParser {

	// keep table alias
	private Map<String, String> tableAlias = null;
	// keep column alias
	private Map<String, String> columnAlias = null;

	public OpaqueSqlParser() {
		this.tableAlias = new Hashtable <String, String>();
		this.columnAlias = new Hashtable <String, String>();
	}

	public SelectQueryStruct processQuery(String query) throws Exception {
		SelectQueryStruct qs = new SelectQueryStruct();
		// parse the sql
		Statement stmt = CCJSqlParserUtil.parse(query);
		Select select = ((Select)stmt);
		PlainSelect sb = (PlainSelect)select.getSelectBody();

		// get the list of select expression terms
		List<SelectItem> items = sb.getSelectItems();

		// we will go through the joins first
		// because we need to figure out how to do all the aliases
		// for the selects
		Table fromTable = (Table) sb.getFromItem();
		String fromTableName = fromTable.getName();

		// if there is no alias
		// we will determine to set the table as the alias
		Alias fromTableAliasObj = fromTable.getAlias();
		if(fromTableAliasObj != null) {
			tableAlias.put(fromTable.getAlias().getName(), fromTable.getName());
		} else {
			tableAlias.put(fromTableName, fromTableName);
		}

		// process the joins into the QS
		fillJoins(qs, fromTableName, sb.getJoins());

		// now that we have the joins
		// we also have the table aliases we need
		// so we can add the selectors
		fillSelects(qs, items, fromTableName);

		// fill the filters
		fillFilters(qs, null, sb.getWhere());

		// fill the groups
		fillGroups(qs, sb.getGroupBy());

		// fill the order by
		fillOrder(qs, sb.getOrderByElements());

		// fill the limit
		fillLimitOffset(qs, sb.getLimit());

		return qs;
	}


	/**
	 * Add the selectors into the query struct
	 * @param qs
	 * @param selects
	 */
	public void fillSelects(SelectQueryStruct qs, List<SelectItem> selects, String originalFromTable) {
		for(int selectIndex = 0;selectIndex < selects.size();selectIndex++) {
			IQuerySelector thisSelect = null;
			
			SelectItem si = selects.get(selectIndex);
			if(si instanceof SelectExpressionItem) {
				SelectExpressionItem sei = (SelectExpressionItem) si;
				Alias seiAlias = sei.getAlias();
				Expression expr = sei.getExpression();
				// get eth selector
				// method does this recursively to determine operations
				
				if(seiAlias != null) {
					thisSelect = determineSelector(expr, seiAlias.getName());
				} else {
					thisSelect = determineSelector(expr, null);
				}
				qs.addSelector(thisSelect);
			} else if(si instanceof AllColumns) {
				thisSelect = new QueryOpaqueSelector(si.toString());
				((QueryOpaqueSelector) thisSelect).setTable(originalFromTable);
			}
			
			if(thisSelect != null) {
				qs.addSelector(thisSelect);
			}
		}
	}

	/**
	 * Return the selector based on the expression input
	 * @param expr
	 * @param parentQuerySelector
	 * @param expressionType
	 * @param alias
	 * @return
	 */
	private IQuerySelector determineSelector(Expression expr, String alias) {
		// see if it is a basic operation
		IQuerySelector basic = processBasicSelector(expr);
		if(basic != null) {
			return basic;
		};

		// not basic
		// i dont care what it is
		// just shove the expression into the selector location
		QueryOpaqueSelector selector = new QueryOpaqueSelector(expr.toString());
		if(tableAlias.size() == 1) {
			selector.setTable(tableAlias.keySet().iterator().next());
		}
		selector.setAlias(alias);
		return selector;
	}

	/**
	 * Process a basic selector
	 * @param expr
	 * @return
	 */
	private IQuerySelector processBasicSelector(Expression expr) {
		// we will use this to process
		// basic constants
		// and columns

		// init the basic selector
		IQuerySelector constSelector = null;

		if(expr instanceof LongValue) {
			long longValue = ((LongValue) expr).getValue();
			constSelector = new QueryConstantSelector();
			((QueryConstantSelector)constSelector).setConstant(longValue);
		} else if (expr instanceof DoubleValue) {
			double doubleValue = ((DoubleValue) expr).getValue();
			constSelector = new QueryConstantSelector();
			((QueryConstantSelector)constSelector).setConstant(doubleValue);
		}  else if(expr instanceof SignedExpression) {
			Expression innerExpression = ((SignedExpression) expr).getExpression();
			if(innerExpression instanceof LongValue) {
				long longValue = ((LongValue) innerExpression).getValue();
				constSelector = new QueryConstantSelector();
				((QueryConstantSelector)constSelector).setConstant(-1 * longValue);
			} else if (innerExpression instanceof DoubleValue) {
				double doubleValue = ((DoubleValue) innerExpression).getValue();
				constSelector = new QueryConstantSelector();
				((QueryConstantSelector)constSelector).setConstant(-1 * doubleValue);
			}
		} else if(expr instanceof StringValue) {
			String strValue = ((StringValue) expr).getValue();
			constSelector = new QueryConstantSelector();
			((QueryConstantSelector)constSelector).setConstant(strValue);
		} else if(expr instanceof DateValue) {
			// need to see about this
			Date dateValue = ((DateValue) expr).getValue();
			constSelector = new QueryConstantSelector();
			((QueryConstantSelector)constSelector).setConstant(dateValue);
		} else if(expr instanceof NullValue) {
			// need to see about this as well
			String strValue = ((NullValue) expr).toString();
			constSelector = new QueryConstantSelector();
			((QueryConstantSelector)constSelector).setConstant(strValue);
		} 
//		else if(expr instanceof Column) {
//			String colValue = ((Column) expr).getColumnName();
//			// need a way to get the alias
//			String tableValue = ((Column) expr).getTable().getName();
//			constSelector = new QueryColumnSelector();
//			if(tableAlias.containsKey(tableValue)) {
//				tableValue = tableAlias.get(tableValue);
//			}
//			colValue = tableValue + "__" + colValue;
//			constSelector = new QueryColumnSelector(colValue);
//			((QueryColumnSelector) constSelector).setTableAlias(tableValue);
//		}

		// if it is not basic
		// we will end up returning null
		return constSelector;
	}

	/**
	 * Add the joins and store table aliases used
	 * @param qs
	 * @param tableName
	 * @param joins
	 */
	public void fillJoins(SelectQueryStruct qs, String tableName, List <Join> joins) {
		// if there are no joins
		// nothing to do
		if(joins == null || joins.isEmpty()) {
			return;
		}

		// joins are all sitting on
		// select.getJoins()
		// each one of which is telling what type of join it is
		// for the case of engineconcept ec and engine e
		// the join seems to say simple is true
		// the last one it says simple is false and it also puts an equation to it
		// each join has a table associated with it
		// sb.joins.get(index).rightitem - table and alias
		// sb.join.get(index).onExpression - tells you the quals to expression

		for(int joinIndex = 0; joinIndex < joins.size(); joinIndex++) {
			Join thisJoin = joins.get(joinIndex);
			Table rightTable = (Table)thisJoin.getRightItem();
			// add the alias
			String rightTableName = rightTable.getName();
			String rightTableAlias = rightTableName;
			if(rightTable.getAlias() != null) {
				rightTableAlias = rightTable.getAlias().getName();
			}

			// if somebody -- need to see if sql grammar can accomodate for stupidity where alias and table are same kind of
			//tableAlias.put(rightTableName, rightTableAlias);
			tableAlias.put(rightTableAlias, rightTableName);
			boolean simple = thisJoin.isSimple();
			if(!simple) {
				EqualsTo joinExpr = (EqualsTo)thisJoin.getOnExpression();
				String toTable = ((Column)joinExpr.getRightExpression()).getTable().getName();
				String toColumn = ((Column)joinExpr.getRightExpression()).getColumnName();
				String fromTable = tableName;
				String fromColumn = ((Column)joinExpr.getLeftExpression()).getColumnName();
				String joinType = null;

				// need to translate the alias into column name
				String full_from = fromTable  + "__" + fromColumn;
				String full_To = rightTableName + "__" + toColumn;

				if(thisJoin.isInner()) {
					joinType = "inner.join";
				} else if(thisJoin.isLeft()) {
					joinType = "left.outer.join";
				} else if(thisJoin.isRight()) {
					joinType = "right.outer.join";
				} else if(thisJoin.isOuter()) {
					joinType = "outer.join";
				} else if(thisJoin.isFull()) {
					joinType = "full.join";
				}
				qs.addRelation(full_from, full_To, joinType);
			} else {
				// Need to understand how implicit join is being handled
			}
		}
	}

	// things I need to recurse
	// Main Query Struct
	// What was previously executed
	// for instance if the previous piece was and and this is or, I need to close and start it ?
	// Need some way to jump back to the previous level

	// I need some way to go in and go out kind of like the sablecc here
	// so I will do this artifically
	// Start of with a simple query filter or even a null
	// if the first one is an and/or 
	// I create the and/or filter and call this with left expression
	// and right expression
	// With the filter as a curFilter
	// if the expression is simple
	// it will continue to add itself as a simple filter
	// if the expression is complex then it will create another filter and add it into it - FE cant handle it right now
	// Once complete, there is nothing to change, since at this point it is all done
	// everytime I finish up with the expression which is a simple one like equals etc. 

	public void fillFilters(SelectQueryStruct qs, IQueryFilter curFilter, Expression expr) {
		// this is a simple one just go ahead and process it like anything else
		// this should go first.. 
		// if unable to process it is only then we should attempt to create other pieces
		IQueryFilter filter = processFilter(expr);
		if(filter != null) {
			if(curFilter != null) {		
				if(curFilter instanceof AndQueryFilter) {
					((AndQueryFilter)curFilter).addFilter(filter);
				}else {
					((OrQueryFilter)curFilter).addFilter(filter);
				}
			} else {
				curFilter = filter;
				qs.addImplicitFilter(curFilter);				
			}
		} else {	
			if(expr instanceof AndExpression) {
				AndQueryFilter newFilter = null;
				if(curFilter == null) {
					curFilter = new AndQueryFilter();
					qs.addImplicitFilter(curFilter);
				} else if(!(curFilter instanceof AndQueryFilter)) {
					newFilter = new AndQueryFilter();
					// I need something which adds this to the curFilter
					// at this point the cur filter has to be an or
					// it could be a subfilter
					// for now I will process it as a or
					((OrQueryFilter)curFilter).addFilter(newFilter);				
					curFilter = newFilter;
				}		

				// process left
				fillFilters(qs,curFilter, ((AndExpression) expr).getLeftExpression());
				// process right
				fillFilters(qs,curFilter, ((AndExpression) expr).getRightExpression());
			} else if(expr instanceof OrExpression) {
				OrQueryFilter newFilter = null;
				if(curFilter == null) {
					curFilter = new OrQueryFilter();
					qs.addImplicitFilter(curFilter);
				} else if(!(curFilter instanceof OrQueryFilter)) {
					newFilter = new OrQueryFilter();

					// I need something which adds this to the curFilter
					// at this point the cur filter has to be an or
					// it could be a subfilter
					// for now I will process it as a or
					((AndQueryFilter)curFilter).addFilter(newFilter);				
					curFilter = newFilter;
				}		

				// process left
				fillFilters(qs,curFilter, ((OrExpression) expr).getLeftExpression());
				// process right
				fillFilters(qs, curFilter, ((OrExpression) expr).getRightExpression());
			} else if (expr instanceof Parenthesis) {
				System.out.println("This is where it is struck");
				fillFilters(qs, curFilter, ((Parenthesis)expr).getExpression());
			}
		}
	}


	public IQueryFilter processFilter(Expression expr) {
		IQueryFilter retFilter = null;

		// >>>> Logical
		// EqualsTo
		// NotEqualsTo
		// GreaterThan
		// GreaterThanEquals
		// MinorThan - because LessThan would be too easy
		// MinorThanEquals
		// AndExpression
		// OrExpression

		if(expr instanceof EqualsTo) {
			EqualsTo eExpr = (EqualsTo)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			NounMetadata r = getNoun(eExpr.getRightExpression());
			if(l == null) {
				l = new NounMetadata(eExpr.getLeftExpression().toString(), PixelDataType.CONST_STRING);
			}
			if(r == null) {
				r = new NounMetadata(eExpr.getRightExpression().toString(), PixelDataType.CONST_STRING);
			}
			retFilter = new SimpleQueryFilter(l, "==", r);
		} else if(expr instanceof NotEqualsTo) {
			NotEqualsTo eExpr = (NotEqualsTo)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			NounMetadata r = getNoun(eExpr.getRightExpression());
			if(l == null) {
				l = new NounMetadata(eExpr.getLeftExpression().toString(), PixelDataType.CONST_STRING);
			}
			if(r == null) {
				r = new NounMetadata(eExpr.getRightExpression().toString(), PixelDataType.CONST_STRING);
			}
			retFilter = new SimpleQueryFilter(l, "!=", r);
		} else if(expr instanceof GreaterThan) {
			GreaterThan eExpr = (GreaterThan)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			NounMetadata r = getNoun(eExpr.getRightExpression());
			if(l == null) {
				l = new NounMetadata(eExpr.getLeftExpression().toString(), PixelDataType.CONST_STRING);
			}
			if(r == null) {
				r = new NounMetadata(eExpr.getRightExpression().toString(), PixelDataType.CONST_STRING);
			}
			retFilter = new SimpleQueryFilter(l, ">", r);
		} else if(expr instanceof MinorThan) {
			MinorThan eExpr = (MinorThan)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			NounMetadata r = getNoun(eExpr.getRightExpression());
			if(l == null) {
				l = new NounMetadata(eExpr.getLeftExpression().toString(), PixelDataType.CONST_STRING);
			}
			if(r == null) {
				r = new NounMetadata(eExpr.getRightExpression().toString(), PixelDataType.CONST_STRING);
			}
			retFilter = new SimpleQueryFilter(l, "<", r);
		} else if(expr instanceof GreaterThanEquals) {
			GreaterThanEquals eExpr = (GreaterThanEquals)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			NounMetadata r = getNoun(eExpr.getRightExpression());
			if(l == null) {
				l = new NounMetadata(eExpr.getLeftExpression().toString(), PixelDataType.CONST_STRING);
			}
			if(r == null) {
				r = new NounMetadata(eExpr.getRightExpression().toString(), PixelDataType.CONST_STRING);
			}
			retFilter = new SimpleQueryFilter(l, ">=", r);
		} else if(expr instanceof MinorThanEquals) {
			MinorThanEquals eExpr = (MinorThanEquals)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			NounMetadata r = getNoun(eExpr.getRightExpression());
			if(l == null) {
				l = new NounMetadata(eExpr.getLeftExpression().toString(), PixelDataType.CONST_STRING);
			}
			if(r == null) {
				r = new NounMetadata(eExpr.getRightExpression().toString(), PixelDataType.CONST_STRING);
			}
			retFilter = new SimpleQueryFilter(l, "<=", r);
		} else if(expr instanceof LikeExpression) {
			LikeExpression eExpr = (LikeExpression)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			NounMetadata r = getNoun(eExpr.getRightExpression());
			if(l == null) {
				l = new NounMetadata(eExpr.getLeftExpression().toString(), PixelDataType.CONST_STRING);
			}
			if(r == null) {
				r = new NounMetadata(eExpr.getRightExpression().toString(), PixelDataType.CONST_STRING);
			}
			retFilter = new SimpleQueryFilter(l, "?like", r);
		} else if(expr instanceof InExpression) {
			InExpression eExpr = (InExpression)expr;
			// there are only three choices may be four ok
			NounMetadata l = getNoun(eExpr.getLeftExpression());
			ItemsList list = eExpr.getRightItemsList();
			NounMetadata r = null;
			if(list instanceof ExpressionList) {
				List<Expression> el = ((ExpressionList)list).getExpressions();
				List <Object> ol = new Vector<Object>();
				for(int elIndex = 0;elIndex < el.size();elIndex++) {
					NounMetadata thisVal = getNoun(el.get(elIndex));
					ol.add(thisVal.getValue());
				}

				r = new NounMetadata(ol, PixelDataType.VECTOR);
			}
			if(l != null && r != null) {
				retFilter = new SimpleQueryFilter(l, "?like", r);
			}
		}
		return retFilter;
	}


	private NounMetadata getNoun(Expression expr) {
		// Column
		// DoubleValue
		// DateValue
		// LongValue
		// TimestampValue
		// TimeValue
		// StringValue
		// NullValue

		NounMetadata retData = null;

		if(expr instanceof LongValue) {
			long longValue = ((LongValue) expr).getValue();
			retData = new NounMetadata(longValue, PixelDataType.CONST_DECIMAL);
		} else if(expr instanceof DoubleValue) {
			double longValue = ((DoubleValue) expr).getValue();
			retData = new NounMetadata(longValue, PixelDataType.CONST_DECIMAL);
		} else if(expr instanceof StringValue) {
			String strValue = ((StringValue) expr).getValue();
			retData = new NounMetadata(strValue, PixelDataType.CONST_STRING);
		} else if(expr instanceof DateValue) {
			// need to see about this
			Date dateValue = ((DateValue) expr).getValue();
			retData = new NounMetadata(dateValue+"", PixelDataType.CONST_STRING);
		} else if(expr instanceof NullValue) {
			// need to see about this as well
			String strValue = ((NullValue) expr).toString();
			retData = new NounMetadata(strValue, PixelDataType.CONST_STRING);
		} else {
			// assume it is some kind of selector column or expression
			IQuerySelector selector = determineSelector(expr, null);
			retData = new NounMetadata(selector, PixelDataType.COLUMN);
		}
		return retData;
	}

	/**
	 * Fills in the limit and offset for the query
	 * @param qs
	 * @param limit
	 */
	public void fillLimitOffset(SelectQueryStruct qs, Limit limit) {
		if(limit == null) {
			return;
		}
		// add limit
		if(limit.getRowCount() instanceof LongValue) {
			long limitRow =  ((LongValue)limit.getRowCount()).getValue();
			qs.setLimit(limitRow);
		}

		// add offset
		if(limit.getOffset() instanceof LongValue) {
			long offset =  ((LongValue)limit.getOffset()).getValue();
			qs.setOffSet(offset);
		}
	}

	/**
	 * Add in the order by
	 * @param qs
	 * @param orders
	 */
	public void fillOrder(SelectQueryStruct qs, List <OrderByElement> orders) {
		if(orders == null || orders.isEmpty()) {
			return;
		}

		for(int orderIndex = 0; orderIndex < orders.size(); orderIndex++) {
			OrderByElement thisElement = orders.get(orderIndex);
			Expression expr = thisElement.getExpression();
			String sortDir = "ASC";
			if(thisElement.isAscDescPresent() && !thisElement.isAsc()) {
				sortDir = "DESC";
			}

			if(expr instanceof Column) {
				String colName = ((Column)expr).getColumnName();
				if(columnAlias.containsKey(colName)) {
					String fullColumn = columnAlias.get(colName);
					String [] colParts = fullColumn.split("__");
					String concept = colParts[0];
					String property = colParts[1];
					qs.addOrderBy(concept, property, sortDir);
				}
			}			
		}
	}

	/**
	 * Add in the group bys
	 * @param qs
	 * @param groupByElement
	 */
	public void fillGroups(SelectQueryStruct qs, GroupByElement groups) {
		if(groups == null) {
			return;
		}
		List<Expression> groupByElement = groups.getGroupByExpressions();
		if(groupByElement == null || groupByElement.isEmpty()) {
			return;
		}
		
		for(int groupIndex = 0; groupIndex < groupByElement.size(); groupIndex++) {
			Expression expr = groupByElement.get(groupIndex);
			// this has to be a column
			// right now this assumption is wrong
			// it could be an alias of a derived column
			if(expr instanceof Column) {
				String colName = ((Column) expr).getColumnName();
				if(columnAlias.containsKey(colName)) {
					String fullColumn = columnAlias.get(colName);
					String [] colParts = fullColumn.split("__");
					String concept = colParts[0];
					String property = colParts[1];
					qs.addGroupBy(concept, property);
				}
			}
		}
	}

//
//	public static void main(String [] args) throws Exception {
//		OpaqueSqlParser test = new OpaqueSqlParser();
//		String query = "Select * from employee";
//		query =  "select distinct c.logicalname ln, (ec.physicalName + 1) ep from "
//				+ "concept c, engineconcept ec, engine e inner join sometable s on c.logicalname=s.logical where (ec.localconceptid=c.localconceptid and "
//				+ "c.conceptualname in ('val1', 'val2')) or (ec.localconceptid + 5) =1 group by ln order by ln limit 200 offset 50 ";// order by c.logicalname";
//
//		query = "select distinct f.studio, (f.movie_budget - 3) / 2 from f where f.movie_budget * 4 > 10";
//		test.processQuery(query);
//	}
}

