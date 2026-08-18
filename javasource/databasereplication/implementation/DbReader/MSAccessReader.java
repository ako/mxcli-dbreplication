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
import java.util.Locale;

public class MSAccessReader {

	private static ILogNode _logNode = Core.getLogger("MSAccessReader");

	public static void processDatabase( ObjectBaseDBSettings dbSettings, IContext sudoContext, Database curDatabase ) throws CoreException {
		DatabaseConnector connector = new DatabaseConnector(dbSettings);

		Connection c = connector.getConnection();
		try {
			DBReplicationSettings settings = new DBReplicationSettings(sudoContext, dbSettings, Table.getType());
			settings.addAttributeMapping("dbId", Table.MemberNames.DbId.toString(), KeyType.ObjectKey, false, null);
			settings.addAttributeMapping("tName", Table.MemberNames.Name.toString(), KeyType.ObjectKey, false, null);
			settings.getMainObjectConfig().setObjectSearchAction(ObjectSearchAction.FindCreate);

			settings.addAssociationMapping("dbAss", Table.MemberNames.Table_Database.toString(), Database.getType(),
					Database.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null)
					.setObjectSearchAction(ObjectSearchAction.FindIgnore);

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
			MetaInfo info = new MetaInfo(settings, extractor, "MSAccessReader");

			DatabaseMetaData dbmd = c.getMetaData();
			// ResultSet rs = dbmd.getSchemas();
			ResultSet rs = dbmd.getTables(null, null, null, null);


			while( rs.next() ) {

				if ( _logNode.isDebugEnabled() ) {
					for( int i = 1; i <= rs.getMetaData().getColumnCount(); i++ ) {
						try {
							_logNode.debug(i + " - " + rs.getMetaData().getColumnLabel(i) + " - " + rs.getString(i));
						}
						catch( Exception e ) {
							_logNode.debug(i + " - " + e.getMessage());
						}
					}
				}


				String tableName = rs.getString(3);
				if ( dbSettings.getTableFilters().size() == 0 || dbSettings.getTableFilters().contains(tableName) ) {
					String key = AbstractValueExtractor.buildObjectKey(curDatabase.getDbId(), tableName.toLowerCase(Locale.ROOT));
					info.addValue(key, "dbId", curDatabase.getDbId());
					info.addValue(key, "tName", tableName);
					info.setAssociationValue(key, "dbAss", curDatabase.getDbId());

					try {
						ResultSet colRs = dbmd.getColumns(null, null, tableName, null);
						while( colRs.next() ) {
							if ( _logNode.isDebugEnabled() ) {
								for( int i = 1; i <= colRs.getMetaData().getColumnCount(); i++ ) {

									try {
										_logNode.debug(i + " - " + colRs.getMetaData().getColumnLabel(i) + " - " + colRs.getString(i));
									}
									catch( Exception e ) {
										_logNode.debug(i + " - " + e.getMessage());
									}
								}
							}
							info.addAssociationValue(key, "colName", colRs.getString("COLUMN_NAME"));
							info.addAssociationValue(key, "colLength", colRs.getInt("COLUMN_SIZE"));
							info.addAssociationValue(key, "colTblId", key);
							info.addAssociationValue(key, "colDtype", colRs.getString("TYPE_NAME"));
						}
					}
					catch( Exception e ) {
						_logNode.error("Skipping table: " + tableName + " because of exception: ", e);
					}
				}
				else
					_logNode.debug("Skipping table: " + tableName + " because it doesn't match the filters.");
			}
			info.finish();
			info.clear();

		}
		catch( SQLException e ) {
			_logNode.error(e);
			_logNode.error(e.getNextException());
		}
		catch( Exception e ) {
			_logNode.error(e);
		}
		finally {
			connector.close();
		}
	}
}
