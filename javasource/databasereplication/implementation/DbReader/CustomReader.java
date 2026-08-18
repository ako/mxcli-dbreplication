package databasereplication.implementation.DbReader;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.logging.ILogNode;
import com.mendix.replication.AbstractValueExtractor;
import com.mendix.replication.AssociationConfig.AssociationDataHandling;
import com.mendix.replication.MetaInfo;
import com.mendix.replication.ReplicationSettings.ChangeTracking;
import com.mendix.replication.ReplicationSettings.KeyType;
import com.mendix.replication.ReplicationSettings.ObjectSearchAction;
import com.mendix.systemwideinterfaces.core.IContext;
import databasereplication.implementation.DBReplicationSettings;
import databasereplication.implementation.DBValueParser;
import databasereplication.implementation.DatabaseConnector;
import databasereplication.implementation.ObjectBaseDBSettings;
import databasereplication.proxies.Column;
import databasereplication.proxies.Database;
import databasereplication.proxies.Table;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public class CustomReader {

	private static final ILogNode _logNode = Core.getLogger("CustomReader");

	public static void processDatabase(ObjectBaseDBSettings dbSettings, IContext sudoContext, Database curDatabase) throws CoreException {
		DatabaseConnector connector = new DatabaseConnector(dbSettings);

		Connection c = connector.getConnection();
		try {
			DBReplicationSettings settings = new DBReplicationSettings(sudoContext, dbSettings, Table.getType());
			settings.addAttributeMapping("dbId", Table.MemberNames.DbId.toString(), KeyType.ObjectKey, false, null);
			settings.addAttributeMapping("tName", Table.MemberNames.Name.toString(), KeyType.ObjectKey, false, null);
			settings.addAssociationMapping("dbAss", Table.MemberNames.Table_Database.toString(), Database.getType(),
					Database.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null)
					.setObjectSearchAction(ObjectSearchAction.FindIgnore);

			settings.addAssociationMapping("colDbId", Column.MemberNames.Column_Table.toString(), Column.getType(),
					Column.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null);
			settings.addAssociationMapping("colName", Column.MemberNames.Column_Table.toString(), Column.getType(),
					Column.MemberNames.Name.toString(), KeyType.AssociationKey, false, null);
			settings.addAssociationMapping("colLength", Column.MemberNames.Column_Table.toString(), Column.getType(),
					Column.MemberNames.Length.toString(), KeyType.NoKey, false, null);
			settings.addAssociationMapping("colTblId", Column.MemberNames.Column_Table.toString(), Column.getType(),
					Column.MemberNames.TableId.toString(), KeyType.AssociationKey, false, null);
			settings.addAssociationMapping("colDtype", Column.MemberNames.Column_Table.toString(), Column.getType(),
					Column.MemberNames.DataType.toString(), KeyType.NoKey, false, null);


			settings.getAssociationConfig(Column.MemberNames.Column_Table.toString())
					.setAssociationDataHandling(AssociationDataHandling.Overwrite)
					.setObjectSearchAction(ObjectSearchAction.FindCreate)
					.setCommitUnchangedObjects(false);

			settings.getMainObjectConfig()
					.setObjectSearchAction(ObjectSearchAction.FindCreate).setCommitUnchangedObjects(true)
					.removeUnusedObjects(ChangeTracking.TrackChanges, Table.MemberNames.UpdateCounter.toString());


			AbstractValueExtractor extractor = new DBValueParser(settings);
			MetaInfo info = new MetaInfo(settings, extractor, "CustomReader");

			DatabaseMetaData dbmd = c.getMetaData();
			boolean anyData = false;
			int schemaPassNr = 0;
			while( anyData == false && schemaPassNr < 3 ) {
				schemaPassNr++;
				ResultSet rs = null;
				switch (schemaPassNr) {
				case 1:
				case 3:
					rs = dbmd.getSchemas();
					break;
				case 2:
					rs = dbmd.getCatalogs();
					break;
				}

				while( rs.next() ) {
					String schemaName = "", catalogName = "";
					switch (schemaPassNr) {
					case 1:
						schemaName = rs.getString("TABLE_SCHEM");
						catalogName = rs.getString("TABLE_CATALOG");
						break;
					case 2:
						catalogName = rs.getString("TABLE_CAT");
						break;
					case 3:
						schemaName = rs.getString("TABLE_SCHEM");
						break;
					}

					List<String> schemaFilters = dbSettings.getSchemaFilters();
					if ( !schemaFilters.isEmpty() && !schemaFilters.contains(schemaName) ) continue;

					if (_logNode.isDebugEnabled())
						_logNode.debug("Schema: " + schemaName + " - Catalog: " + catalogName);

					if ( catalogName == null )
						catalogName = "";
					if ( schemaName == null )
						schemaName = "";

					ResultSet tableRs = null;
					switch (schemaPassNr) {
					case 1:
					case 3:
						tableRs = dbmd.getTables(catalogName, schemaName, "%", null);
						break;
					case 2:
						tableRs = dbmd.getTables("", schemaName, "%", null);
						break;
					}


					while( tableRs.next() ) {
						String tableName = tableRs.getString("TABLE_NAME");
						String fullName = (!isSchemaQualifierRequired(dbmd, schemaName) ? "" : schemaName + ".") + tableName;
						anyData = true;

						if ( dbSettings.getTableFilters().size() == 0 || dbSettings.getTableFilters().contains(fullName) ) {
							String key = AbstractValueExtractor.buildObjectKey(curDatabase.getDbId(), fullName.toLowerCase(Locale.ROOT));

							info.addValue(key, "dbId", curDatabase.getDbId());
							info.addValue(key, "tName", fullName);
							info.setAssociationValue(key, "dbAss", curDatabase.getDbId());

							if (_logNode.isDebugEnabled())
								_logNode.debug("Start getting columns for table: " + fullName);
							try {
								ResultSet colRs = dbmd.getColumns(catalogName, schemaName, tableName, "%");
								while( colRs.next() ) {
									String colName = colRs.getString("COLUMN_NAME");
									if (_logNode.isTraceEnabled())
										_logNode.trace("Adding column: " + colName + " for table: " + fullName);

									info.addAssociationValue(key, "colDbId", curDatabase.getDbId());
									info.addAssociationValue(key, "colName", colName);
									info.addAssociationValue(key, "colLength", colRs.getInt("COLUMN_SIZE"));
									info.addAssociationValue(key, "colTblId", key);
									info.addAssociationValue(key, "colDtype", colRs.getString("TYPE_NAME"));
								}
								colRs.close();
							}
							catch( SQLException e ) {
								_logNode.error(
										"An error occurred while processing columns for table: " + catalogName + "/" + schemaName + "/" + tableName + " - resuming the import",
										e);
							}
						}
						else
							if (_logNode.isDebugEnabled())
								_logNode.debug("Skipping table: " + fullName + " because it doesn't match the filters.");
					}
					tableRs.close();
				}
				rs.close();
				info.finish();
				info.clear();
			}

			/*
			 * Prepare an OQL query that can be used in order to remove the unchanged tables
			 */
			String xPath = "//" + Table.getType() + "[" + Table.MemberNames.DbId.toString() + "=" + curDatabase.getDbId() + "][" + Table.MemberNames.UpdateCounter
					.toString() + "!=" + settings.getMainObjectConfig().getNewRemoveIndicatorValue() + " or " + Table.MemberNames.UpdateCounter
					.toString() + "=NULL]";
			info.removeUnchangedObjectsByQuery(xPath);

		}
		catch( Exception e ) {
			_logNode.error(e);
		}
		finally {
			connector.close();
		}
	}

	private static boolean isSchemaQualifierRequired(DatabaseMetaData dbmd, String schema) throws SQLException {
	  boolean isHsqldbDefault = dbmd.getDatabaseProductName().equals("HSQL Database Engine") && schema.equalsIgnoreCase("PUBLIC");
	  return !(schema.isEmpty() || isHsqldbDefault);
    }

}
