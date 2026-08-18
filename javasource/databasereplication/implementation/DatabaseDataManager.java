package databasereplication.implementation;

import com.mendix.core.CoreException;
import com.mendix.logging.ILogNode;
import com.mendix.replication.MendixReplicationException;
import com.mendix.replication.ParseException;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.systemwideinterfaces.core.UserAction;
import com.mendix.systemwideinterfaces.core.meta.IMetaPrimitive.PrimitiveType;
import databasereplication.proxies.ReplicationStatusValues;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DatabaseDataManager extends IDataManager {
	/**
	 * Try to find an existing instance of the Synchronizer. Each different importer must be unique by Name
	 * The language is changed after the existing instance if retrieved or after a new instance is created
	 */
	protected DatabaseDataManager( IMendixObject tableMapping, String replicationName, DBReplicationExecutionSettings settings) {
		super( tableMapping, replicationName, settings);
	}

	/**
	 * Start synchronizing the objects
	 *  First a connection will be made with the database based on the database type and connection information which was declared in the SynchronizerHandler
	 *  Execute the query and create or synchronize a MendixObject for each row in the result
	 * @param applyEntityAcces
	 * @throws CoreException
	 */
	@Override
	public IMendixObject startSynchronizing(UserAction<?> action, Boolean applyEntityAcces) throws CoreException {
		IMendixObject result = null;
		try {
			this.prepareForSynchronization(action, applyEntityAcces);
			
			this.settings.getLogger().info("Start synchronizing");

			String query = this.settings.getQuery();
			this.settings.getLogger().info(String.format("Executing query on external database, query: %s", query));
			
			try (DatabaseConnector connector = new DatabaseConnector(this.settings.getDbSettings())) {
				Connection connection = connector.getConnection();
				connection.setAutoCommit(false);

				if (query.length() > 0 && this.state == RunningState.Running) {
					try (Statement statement = connection.createStatement()) {
						this.info.getTimeMeasurement().startPerformanceTest("Execute query on foreign db");
						statement.setFetchSize(getSettings().getConfiguration().MetaInfoProcessingBatchThreshold);
						ResultSet rs = statement.executeQuery(query);
						this.info.getTimeMeasurement().endPerformanceTest("Execute query on foreign db");
						result = this.processResultsSet(rs, this.valueParser);
					}
				}
			} catch (SQLException e) {
			  	throw new MendixReplicationException(Messages.COULD_NOT_EXECUTE_QUERY
						.getMessage(e.getMessage(), query), e).addVersion();
			}

		} catch (Exception e) {
			result = this.callFinishingMicroflow(ReplicationStatusValues.Failed);
			if (e instanceof MendixReplicationException)
				throw (MendixReplicationException) e;
			else
				throw new MendixReplicationException(e);
		} finally {
			_instances.remove(this.replicationName);

			if (this.info != null && this.info.getTimeMeasurement() != null)
				this.info.getTimeMeasurement().endPerformanceTest("Over all");
		}
		return result;

	}

	protected IMendixObject processResultsSet(ResultSet rs, DBValueParser valueParser) throws Exception {
		IMendixObject result = null;
		if (rs != null) {
			try {
				this.info.getTimeMeasurement().startPerformanceTest("Process resultset metadata");
				ResultSetMetaData md = rs.getMetaData();
				int nrOfColumns = md.getColumnCount();
				List<String> columns = new ArrayList<String>(nrOfColumns);
				List<String> referenceColumns = new ArrayList<String>(5);
				List<String> referenceSetColumns = new ArrayList<String>(1);
				String columnName;
				HashMap<String, PrimitiveType> colTypes = new HashMap<String, PrimitiveType>();
				PrimitiveType type;
				for( int i = 1; i <= nrOfColumns; i++ ) {
					columnName = md.getColumnLabel(i);
					type = this.settings.getMemberType(columnName);
					colTypes.put(columnName, type);
					final ILogNode logger = settings.getLogger();
					if (logger.isTraceEnabled())
						logger.trace(String.format("Column %d in ResultSet with name: %s of type: %s ", i, columnName, (type == null ? "(unknown)" : type)));

					if( this.settings.treatFieldAsReference(columnName) ) {
						referenceColumns.add(columnName);
					}
					else if( this.settings.treatFieldAsReferenceSet(columnName) ) {
						referenceSetColumns.add(columnName);
					}
					else {
						columns.add( columnName );
					}
				}
				this.info.getTimeMeasurement().endPerformanceTest(true,"Process resultset metadata");

				if( this.settings.useTransactions() )
					this.settings.getContext().startTransaction();
				try {
					//Interface which changes several times to the batch that should be used, which can be either the create or the change batch
					this.info.getTimeMeasurement().startPerformanceTest("Processed all records from resultset");
					while(rs.next() && this.state == RunningState.Running ) {
						String objectKey = valueParser.buildObjectKey(rs, this.settings.getMainObjectConfig());

						for( String colName : columns ) {
							type = this.settings.getMemberType(colName);
							if( type != null ) {
								this.info.addValue(objectKey, colName, valueParser.getValue(type, colName, rs));
							}
							else
								throw new MendixReplicationException(Messages.UNKNOWN_COLUMN.getMessage(colName)).addVersion();
						}

						for( String colName : referenceColumns ) {
							try {
								this.info.setAssociationValue(objectKey, colName, valueParser.getValue(colTypes.get(colName), colName, rs));
							}
							catch (ParseException e) {
								throw new ParseException("Error while parsing column: " + colName + ", exception: " + e.getMessage(), e);
							}
						}
						if( referenceSetColumns.size() > 0 ) {
							for( String colName : referenceSetColumns ) {
								this.info.addAssociationValue(objectKey, colName, valueParser.getValue(colTypes.get(colName), colName, rs));
							}
						}
					}

					if( this.state == RunningState.AbortRollback && rs.next() )
						throw new MendixReplicationException( "Aborting the import: " + this.replicationName).addVersion();


					this.info.getTimeMeasurement().endPerformanceTest("Processed all records from resultset");
					this.info.getTimeMeasurement().startPerformanceTest("Finishing and cleaning up");
					this.info.finish();
					result = this.callFinishingMicroflow( ReplicationStatusValues.Succesfull );
					this.info.clear();
					this.info.getTimeMeasurement().endPerformanceTest("Finishing and cleaning up");
				}
				catch (Exception e) {
					if( this.settings.useTransactions() )
						this.settings.getContext().rollbackTransaction();

					throw e;
				}

				if (this.settings.useTransactions())
					this.settings.getContext().endTransaction();
			} catch (SQLException e) {
				throw new MendixReplicationException(Messages.ERROR_READING_RESULTSET.getMessage(), e).addVersion();
			} finally {
				rs.close();
			}
		}
		return result;
	}

}
