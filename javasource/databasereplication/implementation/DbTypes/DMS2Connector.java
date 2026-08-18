package databasereplication.implementation.DbTypes;

import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.DBType;


public class DMS2Connector extends IDatabaseConnector {

	protected DMS2Connector( IDatabaseSettings settings ) {
		this.connectionString = ConnectionString.generateFromSettings(DBType.DMS2, settings, true);
	}

	@Override
	public String getConnectionString() {
		return this.connectionString;
	}

	@Override
	public String getDriverClass() {
		return "com.unisys.jdbc.dmsql.Driver";
	}

	@Override
	public String getEscapeClose() {
		return "\"";
	}

	@Override
	public String getEscapeOpen() {
		return "\"";
	}

	@Override
	public boolean shouldEscapeColumnNames() {
		return true;
	}

	@Override
	public boolean shouldEscapeTableNames() {
		return false;
	}

	@Override
	public boolean shouldEscapeColumnAlias() {
		return true;
	}

	@Override
	public boolean shouldEscapeTableAlias() {
		return true;
	}

	@Override
	public boolean allowASToken() {
		return false;
	}

	@Override
	public boolean separatEscapeSchemaTable() {
		return false;
	}
	
	@Override
	public boolean closeConnectionAfterQuery() {
		return false;
	}
}
