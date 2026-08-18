package databasereplication.implementation.DbReader;

import com.mendix.core.CoreException;
import com.mendix.replication.ObjectStatistics.Level;
import com.mendix.replication.ReplicationSettings.ChangeTracking;
import com.mendix.replication.ReplicationSettings.KeyType;
import com.mendix.replication.ReplicationSettings.ObjectSearchAction;
import com.mendix.systemwideinterfaces.core.IContext;
import databasereplication.actions.SyncDatabaseInfo;
import databasereplication.implementation.DBReplicationExecutionSettings;
import databasereplication.implementation.DBReplicationExecutionSettings.JoinType;
import databasereplication.implementation.IDataManager;
import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.Column;
import databasereplication.proxies.Database;
import databasereplication.proxies.Table;

public class PostgresReader {

	public static void processTables( SyncDatabaseInfo action, IDatabaseSettings dbSettings, IContext context, Database CurDatabase ) throws CoreException {
		DBReplicationExecutionSettings settings = new DBReplicationExecutionSettings(context, dbSettings, Table.getType());

		String fromAlias = settings.setFromTable("pg_class");
		String joinAliasNamespace = settings.joinTable(JoinType.INNER, "pg_namespace", null).addConstraint("oid", fromAlias, "relnamespace")
				.getAlias();


		settings.addCustomAttributeMapping("'" + CurDatabase.getDbId() + "'", Table.MemberNames.DbId.toString(), KeyType.ObjectKey, true, null);
		settings.addCustomAttributeMapping(
				" (CASE WHEN \"" + joinAliasNamespace + "\".nspname ILIKE 'public' THEN '' ELSE \"" + joinAliasNamespace + "\".nspname || '.' END) || \"" + fromAlias + "\".relname",
				Table.MemberNames.Name.toString(), KeyType.ObjectKey, true, null);
		settings.addCustomAssociationMapping("'" + CurDatabase.getDbId() + "'", Table.MemberNames.Table_Database.toString(), Database.getType(),
				Database.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null)
				.setObjectSearchAction(ObjectSearchAction.FindIgnore);


		settings.getMainObjectConfig().setObjectSearchAction(ObjectSearchAction.FindCreate).setCommitUnchangedObjects(true)
				.removeUnusedObjects(ChangeTracking.TrackChanges, Table.MemberNames.UpdateCounter.toString())
				.setPrintNotFoundMessages(true);


		settings.addConstraint("\"" + fromAlias + "\".relkind = 'r' ");
		settings.addConstraint("\"" + joinAliasNamespace + "\".nspname NOT ILIKE 'pg_catalog' ");
		settings.addConstraint("\"" + joinAliasNamespace + "\".nspname NOT ILIKE 'information_schema'");
		if ( dbSettings.getTableFilters().size() > 0 ) {
			String filterConstraint = "";

			for( String tableFilter : dbSettings.getTableFilters() ) {
				if ( !"".equals(filterConstraint) )
					filterConstraint += " OR ";
				filterConstraint += " (CASE WHEN \"" + joinAliasNamespace + "\".nspname ILIKE 'public' THEN '' ELSE \"" + joinAliasNamespace + "\".nspname || '.' END) || \"" + fromAlias + "\".relname" +
						" = '" + tableFilter + "' ";
			}

			settings.addConstraint(filterConstraint);
		}

		settings.setStatisticsLevel(Level.AllStatistics);

		IDataManager manager = IDataManager.instantiate(null, "Table", settings);
		manager.startSynchronizing(action, false);

		/*
		 * Prepare an OQL query that can be used in order to remove the unchanged tables
		 */
		String xPath = "//" + Table.getType() + "[" + Table.MemberNames.DbId.toString() + "=" + CurDatabase.getDbId() + "]" +
				"[" + Table.MemberNames.UpdateCounter.toString() + "!=" + manager.getRemoveIndicatorValue() + " or " +
				Table.MemberNames.UpdateCounter.toString() + "=NULL]";
		manager.removeUnchangedObjectsByQuery(xPath);
	}

	public static void processColumns( SyncDatabaseInfo action, IDatabaseSettings dbSettings, IContext context, Database CurDatabase ) throws CoreException {

		DBReplicationExecutionSettings settings = new DBReplicationExecutionSettings(context, dbSettings, Column.getType());

		String fromAlias = settings.setFromTable("pg_class");
		String joinAliasNamespace = settings.joinTable(JoinType.INNER, "pg_namespace", null).addConstraint("oid", fromAlias, "relnamespace")
				.getAlias();

		String joinAliasAttribute = settings.joinTable(JoinType.INNER, "pg_attribute", null).addConstraint("attrelid", fromAlias, "oid").getAlias();
		String joinAliasType = settings.joinTable(JoinType.INNER, "pg_type", null).addConstraint("oid", joinAliasAttribute, "atttypid").getAlias();

		settings.addCustomAttributeMapping("'" + CurDatabase.getDbId() + "' || \"" + fromAlias + "\".relname", Column.MemberNames.TableId.toString(),
				KeyType.ObjectKey, true, null);


		settings.addCustomAttributeMapping("'" + CurDatabase.getDbId() + "'", Column.MemberNames.DbId.toString());
		settings.addAttributeMapping(joinAliasAttribute, "attname", Column.MemberNames.Name.toString(), KeyType.ObjectKey, true, null);
		settings.addAttributeMapping(joinAliasType, "typname", Column.MemberNames.DataType.toString());
		settings.addCustomAttributeMapping("''", Column.MemberNames.Length.toString());
		
		settings.addCustomAssociationMapping(
				" (CASE WHEN \"" + joinAliasNamespace + "\".nspname ILIKE 'public' THEN '' ELSE \"" + joinAliasNamespace + "\".nspname || '.' END) || \"" + fromAlias + "\".relname",
				Column.MemberNames.Column_Table.toString(), Table.getType(),
				Table.MemberNames.Name.toString(), KeyType.AssociationKey, false, null)
				.setPrintNotFoundMessages(true);
		settings.addCustomAssociationMapping("'" + CurDatabase.getDbId() + "'",
				Column.MemberNames.Column_Table.toString(), Table.getType(),
				Table.MemberNames.DbId.toString(), KeyType.AssociationKey, false, null)
				.setObjectSearchAction(ObjectSearchAction.FindCreate)
				.setPrintNotFoundMessages(true);


		settings.getMainObjectConfig()
				.setObjectSearchAction(ObjectSearchAction.FindCreate).setCommitUnchangedObjects(true)
				.removeUnusedObjects(ChangeTracking.TrackChanges, Column.MemberNames.UpdateCounter.toString())
				.setPrintNotFoundMessages(true);
		
		settings.addConstraint("\"" + fromAlias + "\".relkind = 'r' ");
		settings.addConstraint("\"" + joinAliasNamespace + "\".nspname NOT ILIKE 'pg_catalog' ");
		settings.addConstraint("\"" + joinAliasNamespace + "\".nspname NOT ILIKE 'information_schema' ");
		settings.addConstraint("\"" + joinAliasAttribute + "\".attnum>0 ");
		settings.addConstraint("(NOT \"" + joinAliasAttribute + "\".attisdropped) ");

		if ( dbSettings.getTableFilters().size() > 0 ) {
			String filterConstraint = "";

			for( String tableFilter : dbSettings.getTableFilters() ) {
				if ( !"".equals(filterConstraint) )
					filterConstraint += " OR ";
				filterConstraint += " (CASE WHEN \"" + joinAliasNamespace + "\".nspname ILIKE 'public' THEN '' ELSE \"" + joinAliasNamespace + "\".nspname || '.' END) || \"" + fromAlias + "\".relname" +
						" = '" + tableFilter + "' ";
			}

			settings.addConstraint(filterConstraint);
		}

		settings.setStatisticsLevel(Level.AllStatistics);


		IDataManager manager = IDataManager.instantiate(null, "Table", settings);
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
