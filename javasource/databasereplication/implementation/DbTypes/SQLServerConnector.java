package databasereplication.implementation.DbTypes;

import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.DBType;


public class SQLServerConnector extends IDatabaseConnector {

	protected SQLServerConnector( IDatabaseSettings settings ) {
		this.connectionString = ConnectionString.generateFromSettings(DBType.SQLServer2008, settings, true);
	}


	@Override
	public String getConnectionString() {
		return this.connectionString;
	}

	@Override
	public String getDriverClass() {
		return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
	}

	@Override
	public String getEscapeClose() {
		return "]";
	}

	@Override
	public String getEscapeOpen() {
		return "[";
	}

	@Override
	public boolean shouldEscapeColumnNames() {
		return true;
	}

	@Override
	public boolean shouldEscapeTableNames() {
		return true;
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
		return true;
	}

	@Override
	public boolean separatEscapeSchemaTable() {
		return false;
	}
	
	@Override
	public boolean closeConnectionAfterQuery() {
		return true;
	}
}
