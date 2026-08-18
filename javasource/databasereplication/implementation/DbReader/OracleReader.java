package databasereplication.implementation.DbReader;

import com.mendix.core.CoreException;
import com.mendix.replication.ObjectStatistics.Level;
import com.mendix.replication.ReplicationSettings.ChangeTracking;
import com.mendix.replication.ReplicationSettings.KeyType;
import com.mendix.replication.ReplicationSettings.ObjectSearchAction;
import com.mendix.systemwideinterfaces.core.IContext;
import databasereplication.actions.SyncDatabaseInfo;
import databasereplication.implementation.DBReplicationExecutionSettings;
import databasereplication.implementation.IDataManager;
import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.Column;
import databasereplication.proxies.Database;
import databasereplication.proxies.Table;

public class OracleReader {

	public static void processTables( SyncDatabaseInfo action, IDatabaseSettings dbSettings, IContext context, Database CurDatabase ) throws CoreException {
		DBReplicationExecutionSettings settings = new DBReplicationExecutionSettings(context, dbSettings, Table.getType());
		String fromAlias = settings.setFromTable("ALL_TABLES");
		settings.addCustomAttributeMapping("'" + CurDatabase.getDbId() + "'", Table.MemberNames.DbId.toString(), KeyType.ObjectKey, true, null);
		settings.addCustomAttributeMapping("\"" + fromAlias + "\".OWNER || '.' || \"" + fromAlias + "\".TABLE_NAME", Table.MemberNames.Name.toString(),
				KeyType.ObjectKey, true, null);

		settings.addCustomAssociationMapping("'" + CurDatabase.getDbId() + "'", Table.MemberNames.Table_Database.toString(), Database.getType(),
				Database.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null)
				.setObjectSearchAction(ObjectSearchAction.FindIgnore);


		settings.getMainObjectConfig()
				.setObjectSearchAction(ObjectSearchAction.FindCreate).setCommitUnchangedObjects(true)
				.removeUnusedObjects(ChangeTracking.TrackChanges, Table.MemberNames.UpdateCounter.toString())
				.setPrintNotFoundMessages(true);

		settings.setStatisticsLevel(Level.AllStatistics);


		if ( dbSettings.getTableFilters().size() > 0 ) {
			String filterConstraint = "";

			for( String tableFilter : dbSettings.getTableFilters() ) {
				if ( !"".equals(filterConstraint) )
					filterConstraint += " OR ";
				filterConstraint += "\"" + fromAlias + "\".OWNER || '.' || \"" + fromAlias + "\".TABLE_NAME" +
						" like '" + tableFilter + "' ";
			}

			settings.addConstraint(filterConstraint);
		}


		IDataManager manager = IDataManager.instantiate(null, "Table", settings);
		manager.startSynchronizing(action, false);

		int tableRemoveIndicator = manager.getRemoveIndicatorValue();


		settings = new DBReplicationExecutionSettings(context, dbSettings, Table.getType());
		fromAlias = settings.setFromTable("ALL_VIEWS");
		settings.addCustomAttributeMapping("'" + CurDatabase.getDbId() + "'", Table.MemberNames.DbId.toString(), KeyType.ObjectKey, true, null);
		settings.addCustomAttributeMapping("\"" + fromAlias + "\".OWNER || '.' || \"" + fromAlias + "\".VIEW_NAME", Table.MemberNames.Name.toString(),
				KeyType.ObjectKey, true, null);
		settings.addCustomAssociationMapping("'" + CurDatabase.getDbId() + "'", Table.MemberNames.Table_Database.toString(), Database.getType(),
				Database.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null);


		settings.getMainObjectConfig()
				.setObjectSearchAction(ObjectSearchAction.FindCreate).setCommitUnchangedObjects(true)
				.removeUnusedObjects(ChangeTracking.TrackChanges, Table.MemberNames.UpdateCounter.toString())
				.setPrintNotFoundMessages(true);
		
		settings.setStatisticsLevel(Level.AllStatistics);

		if ( dbSettings.getTableFilters().size() > 0 ) {
			String filterConstraint = "";

			for( String tableFilter : dbSettings.getTableFilters() ) {
				if ( !"".equals(filterConstraint) )
					filterConstraint += " OR ";
				filterConstraint += "\"" + fromAlias + "\".OWNER || '.' || \"" + fromAlias + "\".VIEW_NAME" +
						" like '" + tableFilter + "' ";
			}

			settings.addConstraint(filterConstraint);
		}

		manager = IDataManager.instantiate(null, "Views", settings);
		manager.startSynchronizing(action, false);
		int viewRemoveIndicator = manager.getRemoveIndicatorValue();

		/*
		 * Prepare an OQL query that can be used in order to remove the unchanged tables
		 */
		String xPath = "//" + Table.getType() + "[" + Table.MemberNames.DbId.toString() + "=" + CurDatabase.getDbId() + "]" +
				"[(" + Table.MemberNames.UpdateCounter.toString() + "!=" + viewRemoveIndicator + " and " +
				Table.MemberNames.UpdateCounter.toString() + "!=" + tableRemoveIndicator + ") or " +
				Table.MemberNames.UpdateCounter.toString() + "=NULL]";
		manager.removeUnchangedObjectsByQuery(xPath);
	}

	public static void processColumns( SyncDatabaseInfo action, IDatabaseSettings dbSettings, IContext context, Database CurDatabase ) throws CoreException {

		DBReplicationExecutionSettings settings = new DBReplicationExecutionSettings(context, dbSettings, Column.getType());

		String fromAlias = settings.setFromTable("ALL_TAB_COLUMNS");

		settings.addCustomAttributeMapping("'" + CurDatabase.getDbId() + "' || \"" + fromAlias + "\".OWNER || \"" + fromAlias + "\".table_name",
				Column.MemberNames.TableId.toString(), KeyType.ObjectKey, true, null);
		// settings.addColumnMapping(fromAlias, "column_name", Column.MemberNames.Name.toString(), KeyType.ObjectKey,
		// true, null);
		
		settings.addCustomAttributeMapping("'" + CurDatabase.getDbId() + "'", Column.MemberNames.DbId.toString());
		settings.addCustomAttributeMapping("\"" + fromAlias + "\".column_name", Column.MemberNames.Name.toString(), KeyType.ObjectKey, true, null);
		settings.addCustomAttributeMapping("\"" + fromAlias + "\".data_type", Column.MemberNames.DataType.toString(), KeyType.NoKey, false, null);
		settings.addCustomAttributeMapping("''", Column.MemberNames.Length.toString());

		settings.addCustomAssociationMapping("\"" + fromAlias + "\".OWNER || '.' || \"" + fromAlias + "\".TABLE_NAME",
				Column.MemberNames.Column_Table.toString(), Table.getType(),
				Table.MemberNames.Name.toString(), KeyType.AssociationKey, false, null)
				.setPrintNotFoundMessages(true);
		settings.addCustomAssociationMapping("'" + CurDatabase.getDbId() + "'",
				Column.MemberNames.Column_Table.toString(), Table.getType(),
				Table.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null)
				.setObjectSearchAction(ObjectSearchAction.FindCreate)
				.setPrintNotFoundMessages(true);

		if ( dbSettings.getTableFilters().size() > 0 ) {
			String filterConstraint = "";

			for( String tableFilter : dbSettings.getTableFilters() ) {
				if ( !"".equals(filterConstraint) )
					filterConstraint += " OR ";
				filterConstraint += "\"" + fromAlias + "\".OWNER || '.' || \"" + fromAlias + "\".TABLE_NAME" +
						" like '" + tableFilter + "' ";
			}

			settings.addConstraint(filterConstraint);
		}


		settings.getMainObjectConfig()
				.setObjectSearchAction(ObjectSearchAction.FindCreate).setCommitUnchangedObjects(true)
				.removeUnusedObjects(ChangeTracking.TrackChanges, Column.MemberNames.UpdateCounter.toString())
				.setPrintNotFoundMessages(true);

		settings.setStatisticsLevel(Level.AllStatistics);

		IDataManager manager = IDataManager.instantiate(null, "Columns", settings);
		manager.startSynchronizing(action, false);

		/*
		 * Prepare an OQL query that can be used in order to remove the unchanged tables
		 */
		String xPath = "//" + Column.getType() + "[" + Column.MemberNames.DbId.toString() + "=" + CurDatabase.getDbId() + "]" +
				"[" + Column.MemberNames.UpdateCounter.toString() + "!=" + manager.getRemoveIndicatorValue() + " or " +
				Column.MemberNames.UpdateCounter.toString() + "=NULL]";
		manager.removeUnchangedObjectsByQuery(xPath);
	}
}
