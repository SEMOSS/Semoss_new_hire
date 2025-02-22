//package prerna.engine.api.impl.util;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.openrdf.model.vocabulary.RDF;
//import org.openrdf.model.vocabulary.RDFS;
//
//import com.hp.hpl.jena.vocabulary.OWL;
//
//import prerna.engine.api.IDatabaseEngine;
//import prerna.engine.api.IDatabaseEngine.ACTION_TYPE;
//import prerna.engine.api.IHeadersDataRow;
//import prerna.engine.api.IRawSelectWrapper;
//import prerna.engine.impl.rdf.RDFFileSesameEngine;
//import prerna.query.querystruct.AbstractQueryStruct;
//import prerna.query.querystruct.SelectQueryStruct;
//import prerna.query.querystruct.selectors.QueryColumnSelector;
//import prerna.query.querystruct.selectors.QueryFunctionHelper;
//import prerna.query.querystruct.selectors.QueryFunctionSelector;
//import prerna.rdf.engine.wrappers.WrapperManager;
//import prerna.sablecc2.om.PixelDataType;
//import prerna.sablecc2.om.PixelOperationType;
//import prerna.sablecc2.om.nounmeta.NounMetadata;
//import prerna.util.Constants;
//import prerna.util.Utility;
//
//public class Owler extends AbstractOwler {
//
//	private static final Logger classLogger = LogManager.getLogger(Owler.class);
//
//	/**
//	 * Constructor for the class when we are creating a brand new OWL file
//	 * 
//	 * @param fileName The location of the new OWL file
//	 * @param type     The type of the engine the OWL file is being created for
//	 * @throws Exception 
//	 */
//	public Owler(String engineId, String owlPath, IDatabaseEngine.DATABASE_TYPE type) throws Exception {
//		super(engineId, owlPath, type);
//	}
//
//	/**
//	 * Constructor for the class when we are adding to an existing OWL file
//	 * 
//	 * @param existingEngine The engine we are adding to
//	 */
//	public Owler(IDatabaseEngine existingEngine) {
//		super(existingEngine);
//	}
//
//	/*
//	 * Adding into the OWL
//	 */
//
//	/////////////////// ADDING CONCEPTS INTO THE OWL ////////////////// 
//
//	/**
//	 * Add a concept to the OWL If RDF : a concept has a data type (String) If RDBMS
//	 * : this will represent a table and not have a datatype
//	 * 
//	 * @param tableName
//	 * @param dataType
//	 * @param conceptual
//	 * @return
//	 */
//	public String addConcept(String tableName, String dataType, String conceptual) {
//		// since RDF uses this multiple times, don't create it each time and just store
//		// it in a hash to send back
//		if (!conceptHash.containsKey(tableName)) {
//			// here is the logic to create the physical uri for the concept
//			// the base URI for the concept will be the baseNodeURI
//			String subject = BASE_NODE_URI + "/" + tableName;
//
//			// now lets start to add the triples
//			// lets add the triples pertaining to those numbered above
//
//			// 1) adding the physical URI concept as a subClassOf the baseNodeURI
//			this.addToBaseEngine(subject, RDFS.SUBCLASSOF.stringValue(), BASE_NODE_URI);
//
//			// 2) now lets add the dataType of the concept
//			// this will only apply if it is RDF
//			if (dataType != null) {
//				String typeObject = "TYPE:" + dataType;
//				this.addToBaseEngine(subject, RDFS.CLASS.stringValue(), typeObject);
//			}
//			if (MetadataUtility.ignoreConceptData(this.type)) {
//				// add an ignore data tag so we can easily query
//				this.addToBaseEngine(subject, RDFS.DOMAIN.toString(), "noData", false);
//			}
//
//			// 3) now lets add the physical URI to the pixel name URI
//			String pixelName = Utility.cleanVariableString(tableName);
//			pixelNames.add(pixelName);
//			String pixelUri = BASE_NODE_URI + "/" + pixelName;
//			this.addToBaseEngine(subject, PIXEL_RELATION_URI, pixelUri);
//
//			// 4) let us add the original table name as the conceptual name
//			if (conceptual == null) {
//				conceptual = tableName;
//			}
//			this.addToBaseEngine(subject, CONCEPTUAL_RELATION_URI, conceptual, false);
//
//			// store it in the hash for future use
//			// NOTE : The hash contains the physical URI
//			conceptHash.put(tableName, subject);
//		}
//		return conceptHash.get(tableName);
//	}
//
//	public String addConcept(String tableName, String dataType) {
//		return addConcept(tableName, dataType, null);
//	}
//
//	public String addConcept(String concept) {
//		return addConcept(concept, "STRING", null);
//	}
//
//	////////////////////////////////// END ADDING CONCEPTS INTO THE OWL //////////////////////////////////
//
//	////////////////////////////////// ADDING RELATIONSHIP INTO THE OWL //////////////////////////////////
//
//	/**
//	 * Add a relationship between two concepts In RDBMS : the predicate must be
//	 * fromTable.fromColumn.toTable.toColumn
//	 * 
//	 * @param fromTable
//	 * @param toTable
//	 * @param predicate
//	 * @return
//	 */
//	public String addRelation(String fromTable, String toTable, String predicate) {
//		// since RDF uses this multiple times, don't create it each time and just store
//		// it in a hash to send back
//		if (!relationHash.containsKey(fromTable + toTable + predicate)) {
//
//			// need to make sure both the fromConcept and the toConcept are already defined
//			// as concepts
//			// TODO: this works for RDBMS even though it only takes in the concept names
//			// because we usually perform
//			// the addConcept call before... this is really just intended to retrieve the
//			String fromConceptURI = addConcept(fromTable, null, null);
//			String toConceptURI = addConcept(toTable, null, null);
//
//			// create the base relationship uri
//			String baseRelationURI = SEMOSS_URI_PREFIX + DEFAULT_RELATION_CLASS;
//			String predicateSubject = baseRelationURI + "/" + predicate;
//
//			// now lets start to add the triples
//			// lets add the triples pertaining to those numbered above
//
//			// 1) now add the physical relationship URI
//			this.addToBaseEngine(predicateSubject, RDFS.SUBPROPERTYOF.stringValue(), baseRelationURI);
//
//			// 2) now add the relationship between the two nodes
//			this.addToBaseEngine(fromConceptURI, predicateSubject, toConceptURI);
//
//			// lastly, store it in the hash for future use
//			relationHash.put(fromTable + toTable + predicate, predicateSubject);
//		}
//		return relationHash.get(fromTable + toTable + predicate);
//	}
//
//	////////////////////////////////// END ADDING RELATIONSHIP INTO THE OWL //////////////////////////////////
//
//	////////////////////////////////// ADDING PROPERTIES TO CONCEPTS IN THE OWL //////////////////////////////////
//
//	/**
//	 * Add a property to a given concept
//	 * 
//	 * @param tableName
//	 * @param propertyCol
//	 * @param dataType
//	 * @param adtlDataType
//	 * @param conceptual
//	 * @return
//	 */
//	public String addProp(String tableName, String propertyCol, String dataType, String adtlDataType, String conceptual) {
//		if (!propHash.containsKey(tableName + "%" + propertyCol)) {
//			String conceptURI = addConcept(tableName, null, null);
//
//			// create the property URI
//			String property = null;
//			if (type == IDatabaseEngine.DATABASE_TYPE.SESAME) {
//				// THIS IS BECAUSE OF LEGACY QUERIES!!!
//				property = BASE_PROPERTY_URI + "/" + propertyCol;
//			} else {
//				property = BASE_PROPERTY_URI + "/" + propertyCol + "/" + tableName;
//			}
//
//			// now lets start to add the triples
//			// lets add the triples pertaining to those numbered above
//
//			// 1) adding the property as type of base property URI
//			this.addToBaseEngine(property, RDF.TYPE.stringValue(), BASE_PROPERTY_URI);
//
//			// 2) adding the property to the concept
//			this.addToBaseEngine(conceptURI, OWL.DatatypeProperty.toString(), property);
//
//			// 3) adding the property data type
//			String typeObject = "TYPE:" + dataType;
//			this.addToBaseEngine(property, RDFS.CLASS.stringValue(), typeObject);
//
//			// 4) adding the property additional data type, if available
//			if (adtlDataType != null && !adtlDataType.isEmpty()) {
//				String adtlTypeObject = "ADTLTYPE:" + adtlDataType;
//				this.addToBaseEngine(property, ADDITIONAL_DATATYPE_RELATION_URI, adtlTypeObject, false);
//			}
//
//			// 5) now lets add the physical URI to the pixel name URI
//			String pixelName = Utility.cleanVariableString(propertyCol);
//			String pixelFullName = pixelName + "/" + Utility.cleanVariableString(tableName);
//			String pixelUri = BASE_PROPERTY_URI + "/" + pixelFullName;
//			this.addToBaseEngine(property, PIXEL_RELATION_URI, pixelUri);
//
//			// 5) let us add the original table name as the conceptual name
//			if (conceptual == null) {
//				conceptual = propertyCol;
//			}
//			this.addToBaseEngine(property, CONCEPTUAL_RELATION_URI, conceptual, false);
//
//			// lastly, store it in the hash for future use
//			// NOTE : The hash contains the physical URI
//			propHash.put(tableName + "%" + propertyCol, property);
//		}
//
//		return propHash.get(tableName + "%" + propertyCol);
//	}
//
//	/**
//	 * This method will add a property onto a concept in the OWL file There are some
//	 * differences based on how the information is used based on if it is a RDF
//	 * engine or a RDBMS engine
//	 * 
//	 * @param tableName    For RDF: This is the name of the concept For RDBMS: This
//	 *                     is the name of the table where the concept exists. If the
//	 *                     concept doesn't exist, it is assumed the column name of
//	 *                     the concept is the same as the table name
//	 * @param propertyCol  This will be the name of the property
//	 * @param dataType     The dataType for the property
//	 * @param adtlDataType Additional data type for the property
//	 * @return Returns the physical URI for the node
//	 */
//	public String addProp(String tableName, String propertyCol, String dataType, String adtlDataType) {
//		return addProp(tableName, propertyCol, dataType, adtlDataType, null);
//	}
//
//	public String addProp(String tableName, String propertyCol, String dataType) {
//		return addProp(tableName, propertyCol, dataType, null, null);
//	}
//
//	/**
//	 * This method will calculate the unique values in each column/property and add
//	 * it to the owl file.
//	 * 
//	 * @param queryEngine
//	 */
//	public void addUniqueCounts(IDatabaseEngine queryEngine) {
//		String uniqueCountProp = SEMOSS_URI_PREFIX + DEFAULT_PROP_CLASS + "/UNIQUE";
//
//		List<String> pixelConcepts = queryEngine.getPixelConcepts();
//		for (String pixelConcept : pixelConcepts) {
//			List<String> pSelectors = queryEngine.getPixelSelectors(pixelConcept);
//			for (String selectorPixel : pSelectors) {
//				SelectQueryStruct qs = new SelectQueryStruct();
//				QueryFunctionSelector newSelector = new QueryFunctionSelector();
//				newSelector.setFunction(QueryFunctionHelper.UNIQUE_COUNT);
//				newSelector.setDistinct(true);
//				QueryColumnSelector innerSelector = new QueryColumnSelector(selectorPixel);
//				newSelector.addInnerSelector(innerSelector);
//				qs.addSelector(newSelector);
//				qs.setQsType(AbstractQueryStruct.QUERY_STRUCT_TYPE.ENGINE);
//
//				IRawSelectWrapper it = null;
//				try {
//					it = WrapperManager.getInstance().getRawWrapper(queryEngine, qs);
//					if (!it.hasNext()) {
//						continue;
//					}
//					long uniqueRows = ((Number) it.next().getValues()[0]).longValue();
//					String propertyPhysicalUri = queryEngine.getPhysicalUriFromPixelSelector(selectorPixel);
//					this.addToBaseEngine(propertyPhysicalUri, uniqueCountProp, uniqueRows, false);
//				} catch (Exception e) {
//					classLogger.error(Constants.STACKTRACE, e);
//				} finally {
//					if(it != null) {
//						try {
//							it.close();
//						} catch (IOException e) {
//							classLogger.error(Constants.STACKTRACE, e);
//						}
//					}
//				}
//			}
//		}
//
//		this.commit();
//		try {
//			this.export(false);
//		} catch (IOException e) {
//			classLogger.error(Constants.STACKTRACE, e);
//		}
//	}
//
//	////////////////////////////////// END ADDING PROPERTIES TO CONCEPTS INTO THE OWL //////////////////////////////////
//
//
//	public void addLegacyPrimKey(String tableName, String columnName) {
//		String physicalUri = conceptHash.get(tableName);
//		if (physicalUri == null) {
//			physicalUri = addConcept(tableName, null, null);
//		}
//		this.addToBaseEngine(physicalUri, AbstractOwler.LEGACY_PRIM_KEY_URI, columnName, false);
//	}
//
//	////////////////////////////////// ADDITIONAL METHODS TO INSERT INTO THE OWL //////////////////////////////////
//
//	/**
//	 * Have one class a subclass of another class
//	 * This code is really intended for RDF databases... 
//	 * not sure what use it will have to utilize this within an RDBMS
//	 * 
//	 * @param childType  The child concept node
//	 * @param parentType The parent concept node
//	 */
//	public void addSubclass(String childType, String parentType) {
//		String childURI = addConcept(childType);
//		String parentURI = addConcept(parentType);
//		this.addToBaseEngine(childURI, RDFS.SUBCLASSOF.stringValue(), parentURI);
//	}
//
//	////////////////////////////////// END ADDITIONAL METHODS TO INSERT INTO THE OWL //////////////////////////////////
//
//	/*
//	 * REMOVING FROM THE OWL
//	 */
//
//	////////////////////////////////// REMOVING CONCEPTS FROM THE OWL //////////////////////////////////
//	/**
//	 * Remove a concept from the OWL If RDF : a concept has a data type (String) If
//	 * RDBMS : this will represent a table and not have a datatype
//	 * 
//	 * @param appId      id of app
//	 * @param tableName  name of concept/table
//	 * @param dataType   data type of column values
//	 * @param conceptual
//	 * @return
//	 */
//	public NounMetadata removeConcept(String tableName, String dataType, String conceptual) {
//		// since RDF uses this multiple times, don't create it each time and just store
//		// it in a hash to send back
//		if (!conceptHash.containsKey(tableName)) {
//			// create the physical uri for the concept
//			// the base URI for the concept will be the baseNodeURI
//			String subject = BASE_NODE_URI + "/" + tableName;
//
//			String conceptPhysical = engine.getPhysicalUriFromPixelSelector(tableName);
//			List<String> properties = engine.getPropertyUris4PhysicalUri(conceptPhysical);
//			StringBuilder bindings = new StringBuilder();
//			for (String prop : properties) {
//				bindings.append("(<").append(prop).append(">)");
//			}
//
//			// remove relationships to node
//			List<String[]> fkRelationships = getPhysicalRelationships(owlEngine);
//
//			for (String[] relations: fkRelationships) {
//				String instanceName = Utility.getInstanceName(relations[2]);
//				String[] tablesAndPrimaryKeys = instanceName.split("\\.");
//
//				for (int i=0; i < tablesAndPrimaryKeys.length; i+=2) {
//					String key = tablesAndPrimaryKeys[i];
//
//					if (tableName.equalsIgnoreCase(key)) {
//						owlEngine.doAction(ACTION_TYPE.REMOVE_STATEMENT, new Object[] { relations[0], relations[2], relations[1], true });
//						owlEngine.doAction(ACTION_TYPE.REMOVE_STATEMENT, new Object[] { relations[2], RDFS.SUBPROPERTYOF.toString(), "http://semoss.org/ontologies/Relation", true });
//					}
//				}
//			}
//
//			if (bindings.length() > 0) {
//				// get everything downstream of the props
//				{
//					String query = "select ?s ?p ?o where { {?s ?p ?o} } bindings ?s {" + bindings.toString() + "}";
//
//					IRawSelectWrapper it = null;
//					try {
//						it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//						while (it.hasNext()) {
//							IHeadersDataRow headerRows = it.next();
//							executeRemoveQuery(headerRows, owlEngine);
//						}
//					} catch (Exception e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					} finally {
//						if(it != null) {
//							try {
//								it.close();
//							} catch (IOException e) {
//								classLogger.error(Constants.STACKTRACE, e);
//							}
//						}
//					}
//				}
//
//				// repeat for upstream of prop
//				{
//					String query = "select ?s ?p ?o where { {?s ?p ?o} } bindings ?o {"	+ bindings.toString() + "}";
//
//					IRawSelectWrapper it = null;
//					try {
//						it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//						while (it.hasNext()) {
//							IHeadersDataRow headerRows = it.next();
//							executeRemoveQuery(headerRows, owlEngine);
//						}
//					} catch (Exception e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					} finally {
//						if(it != null) {
//							try {
//								it.close();
//							} catch (IOException e) {
//								classLogger.error(Constants.STACKTRACE, e);
//							}
//						}
//					}
//				}
//			}
//
//			boolean hasTriple = false;
//
//			// now repeat for the node itself
//			// remove everything downstream of the node
//			{
//				String query = "select ?s ?p ?o where { bind(<" + conceptPhysical + "> as ?s) {?s ?p ?o} }";
//
//				IRawSelectWrapper it = null;
//				try {
//					it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//					while (it.hasNext()) {
//						hasTriple = true;
//						IHeadersDataRow headerRows = it.next();
//						executeRemoveQuery(headerRows, owlEngine);
//					}
//				} catch (Exception e) {
//					classLogger.error(Constants.STACKTRACE, e);
//				} finally {
//					if(it != null) {
//						try {
//							it.close();
//						} catch (IOException e) {
//							classLogger.error(Constants.STACKTRACE, e);
//						}
//					}
//				}
//			}
//
//			// repeat for upstream of the node
//			{
//				String query = "select ?s ?p ?o where { bind(<" + conceptPhysical + "> as ?o) {?s ?p ?o} }";
//
//				IRawSelectWrapper it = null;
//				try {
//					it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//					while (it.hasNext()) {
//						hasTriple = true;
//						IHeadersDataRow headerRows = it.next();
//						executeRemoveQuery(headerRows, owlEngine);
//					}
//				} catch (Exception e) {
//					classLogger.error(Constants.STACKTRACE, e);
//				} finally {
//					if(it != null) {
//						try {
//							it.close();
//						} catch (IOException e) {
//							classLogger.error(Constants.STACKTRACE, e);
//						}
//					}
//				}
//			}
//
//			if (!hasTriple) {
//				throw new IllegalArgumentException("Cannot find concept in existing metadata to remove");
//			}
//
//			// remove it from the hash
//			conceptHash.remove(tableName, subject);
//		}
//		
//		NounMetadata noun = new NounMetadata(true, PixelDataType.BOOLEAN);
//		noun.addAdditionalReturn(new NounMetadata("Successfully removed concept and all its dependencies",
//				PixelDataType.CONST_STRING, PixelOperationType.SUCCESS));
//		return noun;
//	}
//
//	public void removeConcept(String concept) {
//		removeConcept(concept, "STRING", null);
//	}
//
//	public void removeConcept(String tableName, String dataType) {
//		removeConcept(tableName, dataType, null);
//	}
//	
//	////////////////////////////////// END REMOVING CONCEPTS FROM THE OWL //////////////////////////////////
//
//
//	////////////////////////////////// REMOVING RELATIONSHIPS FROM THE OWL //////////////////////////////////
//
//	/**
//	 * Remove an added predicate joining two tables together
//	 * @param fromTable
//	 * @param toTable
//	 * @param predicate
//	 */
//	public void removeRelation(String fromTable, String toTable, String predicate) {
//		String fromConceptURI = addConcept(fromTable, null, null);
//		String toConceptURI = addConcept(toTable, null, null);
//
//		// create the base relationship uri
//		String baseRelationURI = SEMOSS_URI_PREFIX + DEFAULT_RELATION_CLASS;
//		String predicateSubject = baseRelationURI + "/" + predicate;
//
//		// now lets start to add the triples
//		// lets add the triples pertaining to those numbered above
//
//		// 1) now add the physical relationship URI
//		this.removeFromBaseEngine(predicateSubject, RDFS.SUBPROPERTYOF.stringValue(), baseRelationURI);
//
//		// 2) now add the relationship between the two nodes
//		this.removeFromBaseEngine(fromConceptURI, predicateSubject, toConceptURI);
//
//		// lastly, store it in the hash for future use
//		relationHash.remove(fromTable + toTable + predicate);
//	}
//
//	////////////////////////////////// END REMOVING RELATIONSHIPS FROM THE OWL //////////////////////////////////
//
//
//	////////////////////////////////// REMOVING PROPERTIES FROM THE OWL //////////////////////////////////
//
//	/**
//	 * Remove a property from a given concept
//	 * 
//	 * @param tableName
//	 * @param propertyCol
//	 * @param dataType
//	 * @param adtlDataType
//	 * @param conceptual
//	 * @return
//	 * @return
//	 */
//	public NounMetadata removeProp(String tableName, String propertyCol, String dataType, String adtlDataType, String conceptual) {
//		// create the property URI
//		String property = null;
//		if (type == IDatabaseEngine.DATABASE_TYPE.SESAME) {
//			// THIS IS BECAUSE OF LEGACY QUERIES!!!
//			property = BASE_PROPERTY_URI + "/" + propertyCol;
//		} else {
//			property = BASE_PROPERTY_URI + "/" + propertyCol + "/" + tableName;
//		}
//
//		{
//			// remove everything downstream of the property
//			String downstreamQuery = "select ?s ?p ?o where { bind(<" + property + "> as ?s) " + "{?s ?p ?o} }";
//			IRawSelectWrapper it = null;
//			try {
//				it = WrapperManager.getInstance().getRawWrapper(this.owlEngine, downstreamQuery);
//				while (it.hasNext()) {
//					IHeadersDataRow headerRows = it.next();
//					executeRemoveQuery(headerRows, this.owlEngine);
//				}
//			} catch (Exception e) {
//				classLogger.error(Constants.STACKTRACE, e);
//			} finally {
//				if(it != null) {
//					try {
//						it.close();
//					} catch (IOException e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					}
//				}
//			}
//		}
//
//		{
//			// repeat for upstream of the property
//			String upstreamQuery = "select ?s ?p ?o where { bind(<" + property + "> as ?o) {?s ?p ?o} }";
//			IRawSelectWrapper it = null;
//			try {
//				it = WrapperManager.getInstance().getRawWrapper(this.owlEngine, upstreamQuery);
//				while (it.hasNext()) {
//					IHeadersDataRow headerRows = it.next();
//					executeRemoveQuery(headerRows, this.owlEngine);
//				}
//			} catch (Exception e) {
//				classLogger.error(Constants.STACKTRACE, e);
//			} finally {
//				if(it != null) {
//					try {
//						it.close();
//					} catch (IOException e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					}
//				}
//			}
//		}
//
//		NounMetadata noun = new NounMetadata(true, PixelDataType.BOOLEAN);
//		noun.addAdditionalReturn(new NounMetadata("Successfully removed property", PixelDataType.CONST_STRING, PixelOperationType.SUCCESS));
//		return noun;
//	}
//
//	/**
//	 * This method will remove a property from a concept in the OWL file There are some
//	 * differences based on how the information is used based on if it is a RDF
//	 * engine or a RDBMS engine
//	 * 
//	 * @param tableName    For RDF: This is the name of the concept For RDBMS: This
//	 *                     is the name of the table where the concept exists. If the
//	 *                     concept doesn't exist, it is assumed the column name of
//	 *                     the concept is the same as the table name
//	 * @param propertyCol  This will be the name of the property
//	 * @param dataType     The dataType for the property
//	 * @param adtlDataType Additional data type for the property
//	 * @return Returns the physical URI for the node
//	 */
//	public NounMetadata removeProp(String tableName, String propertyCol, String dataType, String adtlDataType) {
//		return removeProp(tableName, propertyCol, dataType, adtlDataType, null);
//	}
//
//	public NounMetadata removeProp(String tableName, String propertyCol, String dataType) {
//		return removeProp(tableName, propertyCol, dataType, null, null);
//	}
//	
//	
//	////////////////////////////////////////////////////////////////////////////////////////////////
//	
//	// rename things
//	
//	/**
//	 * Rename an old concept name to a new name
//	 * @param appId
//	 * @param oldConceptName
//	 * @param newConceptName
//	 * @return
//	 */
//	public NounMetadata renameConcept(String oldConceptName, String newConceptName, String newConceptualName) {
//		// we need to take the table name and make the URL
//		String newConceptPhysicalUri = BASE_NODE_URI + "/" + newConceptName;
//
//		// then we need to take the properties of the table and store
//		Map<String, String> newProperties = new HashMap<String, String>();
//		Map<String, String> newRelations = new HashMap<String, String>();
//
//		List<String> properties = null;
//		
//		// then everything downstream needs to be edited
//		// then everything upstream needs to be edited
//		List<Object[]> newTriplesToAdd = new ArrayList<>();
//		List<Object[]> oldTriplesToDelete = new ArrayList<>();
//
//		// then we need to change the property name as well to point to the new table name
//		
//		String oldConceptPhysical = engine.getPhysicalUriFromPixelSelector(oldConceptName);
//		properties = engine.getPropertyUris4PhysicalUri(oldConceptPhysical);
//		StringBuilder bindings = new StringBuilder();
//		for (String oldProp : properties) {
//			bindings.append("(<").append(oldProp).append(">)");
//
//			// store new prop with new table name
//			int index = oldProp.lastIndexOf("/");
//			String newProp = oldProp.substring(0, index) + "/" + newConceptName;
//			newProperties.put(oldProp, newProp);
//		}
//
//		// remove relationships to node
//		List<String[]> fkRelationships = getPhysicalRelationships(owlEngine);
//		String baseRelationURI = SEMOSS_URI_PREFIX + DEFAULT_RELATION_CLASS;
//
//		for (String[] relations : fkRelationships) {
//			// track if change needs to be made
//			boolean editRelation = false;
//			String start = relations[0];
//			String end = relations[1];
//			String relURI = relations[2];
//			String relationName = Utility.getInstanceName(relURI); // this is either a.b.c.d or x.a.b.y.c.d
//			String[] tablesAndPrimaryKeys = relationName.split("\\.");
//				
//			String newStart = relations[0];
//			String newEnd = relations[1];
//			if (start.equals(oldConceptPhysical)) {
//				editRelation = true;
//				// need to change the start table
//				newStart = newConceptPhysicalUri;
//
//				// need to change the a in relationName
//				if (tablesAndPrimaryKeys.length == 4) {
//					// a.b.c.d
//					tablesAndPrimaryKeys[0] = newConceptName;
//				} else if (tablesAndPrimaryKeys.length == 6) {
//					// this has the schema
//					// x.a.b.y.c.d
//					tablesAndPrimaryKeys[1] = newConceptName;
//				}
//
//			} else if (end.equals(oldConceptPhysical)) {
//				editRelation = true;
//				// need to change the end table
//				newEnd = newConceptPhysicalUri;
//
//				// need to change the c in relationName
//				if (tablesAndPrimaryKeys.length == 4) {
//					// a.b.c.d
//					tablesAndPrimaryKeys[2] = newConceptName;
//				} else if (tablesAndPrimaryKeys.length == 6) {
//					// this has the schema
//					// x.a.b.y.c.d
//					tablesAndPrimaryKeys[4] = newConceptName;
//				}
//			}
//			if (editRelation) {
//				// create relationship name a.b.c.d or x.a.b.y.c.d
//				String newRelName = String.join(".", tablesAndPrimaryKeys);
//				String newRelationURI = baseRelationURI + "/" + newRelName;
//				newRelations.put(relURI, newRelationURI);
//
//				// store old relationship info
//				oldTriplesToDelete.add(new Object[] { relations[0], relations[2], relations[1], true });
//				oldTriplesToDelete.add(new Object[] { relations[2], RDFS.SUBPROPERTYOF.toString(), AbstractOwler.BASE_RELATION_URI, true });
//
//				// store new relationship info
//				newTriplesToAdd.add(new Object[] { newStart, newRelationURI, newEnd, true });
//				newTriplesToAdd.add(new Object[] { newRelationURI, RDFS.SUBPROPERTYOF.toString(), AbstractOwler.BASE_RELATION_URI, true });
//			}
//
//		}
//
//		if (bindings.length() > 0) {
//			// get everything downstream of the props
//			{
//				String query = "select ?s ?p ?o where { {?s ?p ?o} } bindings ?s {" + bindings.toString() + "}";
//
//				IRawSelectWrapper it = null;
//				try {
//					it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//					while (it.hasNext()) {
//						IHeadersDataRow headerRows = it.next();
//						storeTripleToDelete(headerRows, oldTriplesToDelete);
//
//						// add the new concept downstream props
//						Object[] raw = headerRows.getRawValues();
//						String s = raw[0].toString();
//						String p = raw[1].toString();
//						String o = raw[2].toString();
//						String newS = newProperties.get(s);
//						if (p.equals(AbstractOwler.PIXEL_RELATION_URI)) {
//							String newO = o.substring(0, o.lastIndexOf("/") + 1);
//							o = newO + newConceptName;
//						}
//						boolean isLiteral = objectIsLiteral(p);
//						if (isLiteral) {
//							newTriplesToAdd.add(new Object[] { newS, p, headerRows.getValues()[2], false });
//						} else {
//							newTriplesToAdd.add(new Object[] { newS, p, o, true });
//						}
//					}
//				} catch (Exception e) {
//					classLogger.error(Constants.STACKTRACE, e);
//				} finally {
//					if(it != null) {
//						try {
//							it.close();
//						} catch (IOException e) {
//							classLogger.error(Constants.STACKTRACE, e);
//						}
//					}
//				}
//			}
//
//			// repeat for upstream of prop
//			{
//				String query = "select ?s ?p ?o where { {?s ?p ?o} } bindings ?o {" + bindings.toString() + "}";
//
//				IRawSelectWrapper it = null;
//				try {
//					it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//					while (it.hasNext()) {
//						IHeadersDataRow headerRows = it.next();
//						storeTripleToDelete(headerRows, oldTriplesToDelete);
//
//						// add the new concept upstream props
//						Object[] raw = headerRows.getRawValues();
//						String s = raw[0].toString();
//						String p = raw[1].toString();
//						String o = raw[2].toString();
//						String newO = newProperties.get(o);
//						if (s.equals(oldConceptPhysical)) {
//							newTriplesToAdd.add(new Object[] { newConceptPhysicalUri, p, newO, true });
//						} else {
//							newTriplesToAdd.add(new Object[] { s, p, newO, true });
//						}
//					}
//				} catch (Exception e) {
//					classLogger.error(Constants.STACKTRACE, e);
//				} finally {
//					if(it != null) {
//						try {
//							it.close();
//						} catch (IOException e) {
//							classLogger.error(Constants.STACKTRACE, e);
//						}
//					}
//				}
//			}
//		}
//
//		boolean hasTriple = false;
//
//		// now repeat for the node itself
//		// remove everything downstream of the node
//		{
//			String query = "select ?s ?p ?o where { bind(<" + oldConceptPhysical + "> as ?s) {?s ?p ?o} }";
//
//			IRawSelectWrapper it = null;
//			try {
//				it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//				while (it.hasNext()) {
//					hasTriple = true;
//					IHeadersDataRow headerRows = it.next();
//
//					// add downstream for node props
//					Object[] raw = headerRows.getRawValues();
//
//					String p = raw[1].toString();
//					if (newRelations.containsKey(p)) {
//						// this relation has already been modified
//						continue;
//					}
//
//					String o = raw[2].toString();
//					if (newProperties.containsKey(o)) {
//						o = newProperties.get(o);
//					} else if (o.equals(oldConceptPhysical)) {
//						o = newConceptPhysicalUri;
//					}
//					boolean isLiteral = objectIsLiteral(p);
//					if (isLiteral) {
//						newTriplesToAdd
//								.add(new Object[] { newConceptPhysicalUri, p, headerRows.getValues()[2], false });
//					} else {
//						newTriplesToAdd.add(new Object[] { newConceptPhysicalUri, p, o, true });
//					}
//					storeTripleToDelete(headerRows, oldTriplesToDelete);
//				}
//			} catch (Exception e) {
//				classLogger.error(Constants.STACKTRACE, e);
//			} finally {
//				if(it != null) {
//					try {
//						it.close();
//					} catch (IOException e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					}
//				}
//			}
//		}
//
//		// repeat for upstream of the node
//		{
//			String query = "select ?s ?p ?o where { bind(<" + oldConceptPhysical + "> as ?o) {?s ?p ?o} }";
//
//			IRawSelectWrapper it = null;
//			try {
//				it = WrapperManager.getInstance().getRawWrapper(owlEngine, query);
//				while (it.hasNext()) {
//					hasTriple = true;
//					IHeadersDataRow headerRows = it.next();
//
//					// add for the upstream of the node
//					Object[] raw = headerRows.getRawValues();
//
//					String s = raw[0].toString();
//					if (s.equals(oldConceptPhysical)) {
//						s = newConceptPhysicalUri;
//					} else if (newProperties.containsKey(s)) {
//						s = newProperties.get(s);
//					}
//					String p = raw[1].toString();
//					if (newRelations.containsKey(p)) {
//						// this relation has already been modified
//						continue;
//					}
//					newTriplesToAdd.add(new Object[] { s, p, newConceptPhysicalUri, true });
//					storeTripleToDelete(headerRows, oldTriplesToDelete);
//				}
//			} catch (Exception e) {
//				classLogger.error(Constants.STACKTRACE, e);
//			} finally {
//				if(it != null) {
//					try {
//						it.close();
//					} catch (IOException e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					}
//				}
//			}
//		}
//
//		if (!hasTriple) {
//			throw new IllegalArgumentException("Cannot find concept in existing metadata to remove");
//		}
//			
//		// delete the old triples
//		for (Object[] data : oldTriplesToDelete) {
//			owlEngine.removeStatement(data);
//		}
//
//		for (Object[] data : newTriplesToAdd) {
//			owlEngine.addStatement(data);
//		}
//
//		NounMetadata noun = new NounMetadata(true, PixelDataType.BOOLEAN);
//		noun.addAdditionalReturn(new NounMetadata("Successfully removed concept and all its dependencies", PixelDataType.CONST_STRING, PixelOperationType.SUCCESS));
//		return noun;
//	}
//	
//	
//	
//	/**
//	 * Remove a concept from the OWL If RDF : a concept has a data type (String) If
//	 * RDBMS : this will represent a table and not have a datatype
//	 * 
//	 * @param appId
//	 * @param tableName
//	 * @param oldPropName
//	 * @param newPropName
//	 * @return
//	 */
//	public NounMetadata renameProp(String tableName, String oldPropName, String newPropName) {
//		// need to grab everything downstream of the node and edit it
//		// need to grab everything upstream of the node and edit it
//		
//		String propPhysicalUri = BASE_PROPERTY_URI + "/" + oldPropName + "/" + tableName;
//		String newPropPhysicalUri = BASE_PROPERTY_URI + "/" + newPropName + "/" + tableName;
//
//		List<Object[]> newTriplesToAdd = new ArrayList<>();
//		List<Object[]> oldTriplesToDelete = new ArrayList<>();
//
//		// remove relationships to node
//		List<String[]> fkRelationships = getPhysicalRelationships(owlEngine);
//		String baseRelationURI = SEMOSS_URI_PREFIX + DEFAULT_RELATION_CLASS;
//
//		for (String[] relations: fkRelationships) {
//			// track if change needs to be made
//			boolean editRelation = false;
//			String start = relations[0];
//			String end = relations[1];
//			String relURI = relations[2];
//			String relationName = Utility.getInstanceName(relURI); // this is either a.b.c.d or x.a.b.y.c.d
//			String[] tablesAndPrimaryKeys = relationName.split("\\.");
//			
//			// need to change the b or d in relationName
//			if (tablesAndPrimaryKeys.length == 4) {
//				String startCol = tablesAndPrimaryKeys[1];
//				String endCol = tablesAndPrimaryKeys[3];
//				if(startCol.equals(oldPropName)) {
//					editRelation = true;
//					tablesAndPrimaryKeys[1] = newPropName;
//				} else if (endCol.equals(oldPropName)) {
//					editRelation = true;
//					tablesAndPrimaryKeys[3] = newPropName;
//				}
//			} else if (tablesAndPrimaryKeys.length == 6) {
//				// this has the schema
//				// x.a.b.y.c.d
//				String startCol = tablesAndPrimaryKeys[2];
//				String endCol = tablesAndPrimaryKeys[5];
//				if(startCol.equals(oldPropName)) {
//					editRelation = true;
//					tablesAndPrimaryKeys[2] = newPropName;
//				} else if (endCol.equals(oldPropName)) {
//					editRelation = true;
//					tablesAndPrimaryKeys[5] = newPropName;
//				}
//			}
//
//			if (editRelation) {
//				// create relationship name a.b.c.d or x.a.b.y.c.d
//				String newRelName = String.join(".", tablesAndPrimaryKeys);
//				String newRelationURI = baseRelationURI + "/" + newRelName;
//
//				// store old relationship info
//				oldTriplesToDelete.add(new Object[] { relations[0], relations[2], relations[1], true });
//				oldTriplesToDelete.add(new Object[] { relations[2], RDFS.SUBPROPERTYOF.toString(), AbstractOwler.BASE_RELATION_URI, true });
//
//				// store new relationship info
//				newTriplesToAdd.add(new Object[] { start, newRelationURI, end, true });
//				newTriplesToAdd.add(new Object[] { newRelationURI, RDFS.SUBPROPERTYOF.toString(), AbstractOwler.BASE_RELATION_URI, true });
//			}
//
//		}
//		
//		
//		{
//			// remove everything downstream of the property
//			String downstreamQuery = "select ?s ?p ?o where { bind(<" + propPhysicalUri + "> as ?s) " + "{?s ?p ?o} }";
//			IRawSelectWrapper it = null;
//			try {
//				it = WrapperManager.getInstance().getRawWrapper(owlEngine, downstreamQuery);
//				while (it.hasNext()) {
//					IHeadersDataRow headerRows = it.next();
//					storeTripleToDelete(headerRows, oldTriplesToDelete);
//
//					Object[] raw = headerRows.getRawValues();
//					String p = raw[1].toString();
//					String o = raw[2].toString();
//					boolean isLiteral = objectIsLiteral(p);
//					if (isLiteral) {
//						newTriplesToAdd.add(new Object[] { newPropPhysicalUri, p, headerRows.getValues()[2], false });
//					} else {
//						newTriplesToAdd.add(new Object[] { newPropPhysicalUri, p, o, true });
//					}
//				}
//			} catch (Exception e) {
//				classLogger.error(Constants.STACKTRACE, e);
//			} finally {
//				if(it != null) {
//					try {
//						it.close();
//					} catch (IOException e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					}
//				}
//			}
//		}
//
//		{
//			// repeat for upstream of the property
//			String upstreamQuery = "select ?s ?p ?o where { bind(<" + propPhysicalUri + "> as ?o) {?s ?p ?o} }";
//			IRawSelectWrapper it = null;
//			try {
//				it = WrapperManager.getInstance().getRawWrapper(owlEngine, upstreamQuery);
//				while (it.hasNext()) {
//					IHeadersDataRow headerRows = it.next();
//					storeTripleToDelete(headerRows, oldTriplesToDelete);
//					
//					Object[] raw = headerRows.getRawValues();
//					String s = raw[0].toString();
//					String p = raw[1].toString();
//					newTriplesToAdd.add(new Object[] { s, p, newPropPhysicalUri, true });
//				}
//			} catch (Exception e) {
//				classLogger.error(Constants.STACKTRACE, e);
//			} finally {
//				if(it != null) {
//					try {
//						it.close();
//					} catch (IOException e) {
//						classLogger.error(Constants.STACKTRACE, e);
//					}
//				}
//			}
//		}
//		// delete the old triples
//		for (Object[] data : oldTriplesToDelete) {
//			owlEngine.removeStatement(data);
//		}
//		
//		// add new triples
//		for(Object[] data : newTriplesToAdd) {
//			owlEngine.addStatement(data);
//		}
//		
//		NounMetadata noun = new NounMetadata(true, PixelDataType.BOOLEAN);
//		noun.addAdditionalReturn(new NounMetadata("Successfully removed property", PixelDataType.CONST_STRING, PixelOperationType.SUCCESS));
//		return noun;
//	}
//	
//
//	////////////////////////////////// END REMOVING PROPERTIES TO CONCEPTS INTO THE OWL //////////////////////////////////
//
//	////////////////////////////////// UTILITY METHODS TO REMOVE FROM OWL //////////////////////////////////
//
//	private List<String[]> getPhysicalRelationships(IDatabaseEngine engine) {
//		String query = "SELECT DISTINCT ?start ?end ?rel WHERE { "
//				+ "{?start <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://semoss.org/ontologies/Concept> }"
//				+ "{?end <http://www.w3.org/2000/01/rdf-schema#subClassOf> <http://semoss.org/ontologies/Concept> }"
//				+ "{?rel <" + RDFS.SUBPROPERTYOF + "> <http://semoss.org/ontologies/Relation>} " + "{?start ?rel ?end}"
//				+ "Filter(?rel != <" + RDFS.SUBPROPERTYOF + ">)"
//				+ "Filter(?rel != <http://semoss.org/ontologies/Relation>)" + "}";
//		return Utility.getVectorArrayOfReturn(query, engine, true);
//	}
//
//	private void executeRemoveQuery(IHeadersDataRow headerRows, RDFFileSesameEngine owlEngine) {
//		Object[] raw = headerRows.getRawValues();
//		String s = raw[0].toString();
//		String p = raw[1].toString();
//		String o = raw[2].toString();
//		boolean isLiteral = objectIsLiteral(p);
//		if (isLiteral) {
//			owlEngine.removeStatement(new Object[] { s, p, headerRows.getValues()[2], false });
//		} else {
//			owlEngine.removeStatement(new Object[] { s, p, o, true });
//		}
//	}
//	
//	private void storeTripleToDelete(IHeadersDataRow headerRows, List<Object[]> dataToDelete ) {
//		Object[] raw = headerRows.getRawValues();
//		String s = raw[0].toString();
//		String p = raw[1].toString();
//		String o = raw[2].toString();
//		boolean isLiteral = objectIsLiteral(p);
//		if (isLiteral) {
//			dataToDelete.add(new Object[] { s, p, headerRows.getValues()[2], false });
//		} else {
//			dataToDelete.add(new Object[] { s, p, o, true });
//		}
//	}
//
//	/**
//	 * Determine if the predicate points to a literal
//	 * 
//	 * @param predicate
//	 * @return
//	 */
//	protected boolean objectIsLiteral(String predicate) {
//		Set<String> literalPreds = new HashSet<String>();
//
//		literalPreds.add(RDFS.LABEL.toString());
//		literalPreds.add(OWL.sameAs.toString());
//		literalPreds.add(RDFS.COMMENT.toString());
//		literalPreds.add(Owler.SEMOSS_URI_PREFIX + Owler.DEFAULT_PROP_CLASS + "/UNIQUE");
//		literalPreds.add(Owler.CONCEPTUAL_RELATION_URI);
//		literalPreds.add(RDFS.DOMAIN.toString());
//
//		if (literalPreds.contains(predicate)) {
//			return true;
//		}
//		return false;
//	}
//
//	
//	////////////////////////////////// TESTING //////////////////////////////////
//	////////////////////////////////// TESTING //////////////////////////////////
//	////////////////////////////////// TESTING //////////////////////////////////
//	////////////////////////////////// TESTING //////////////////////////////////
//
//	/*
//	public static void main(String[] args) {
//		TestUtilityMethods.loadDIHelper("C:\\workspace\\Semoss_Dev\\RDF_Map.prop");
//		Owler owler = new Owler("C:\\workspace\\Semoss_Dev\\themes_OWL.OWL", IDatabase.ENGINE_TYPE.RDBMS);
//
//		owler.addConcept("ADMIN_THEME ", "id", "VARCHAR(255)");
//		owler.addProp("ADMIN_THEME ", "id", "theme_name", "VARCHAR(255)", null);
//		owler.addProp("ADMIN_THEME ", "id", "theme_map", "CLOB", null);
//		owler.addProp("ADMIN_THEME ", "id", "is_active", "BOOLEAN", null);
//
//		// load the owl into a rfse
//		RDFFileSesameEngine rfse = new RDFFileSesameEngine();
//		try {
//			owler.export();
//		} catch (IOException e) {
//			classLogger.error(Constants.STACKTRACE, e);
//		}
//		owler.getOwlAsString();
//		rfse.openFile(owler.getOwlPath(), "RDF/XML", SEMOSS_URI_PREFIX);
//	}
//
//	public String getOwlAsString() {
//		// this will both write the owl to a file and print it onto the console
//		String owl = null;
//		try {
//			owl = engine.exportBaseEngAsString(true);
//			System.out.println("OWL.. " + owl);
//		} catch (IOException e) {
//			classLogger.error(Constants.STACKTRACE, e);
//		}
//
//		return owl;
//	}
//	*/
//
//	////////////////////////////////// END TESTING //////////////////////////////////
//}
