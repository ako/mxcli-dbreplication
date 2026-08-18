package databasereplication.implementation.DbTypes;

import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.DBType;


public class AS400Connector extends IDatabaseConnector {

	protected AS400Connector( IDatabaseSettings settings ) {
		this.connectionString = ConnectionString.generateFromSettings(DBType.AS_400, settings, true);
	}

	@Override
	public String getConnectionString() {
		return this.connectionString;
	}

	@Override
	public String getDriverClass() {
		return "com.ibm.as400.access.AS400JDBCDriver";
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
		return false;
	}

	@Override
	public boolean separatEscapeSchemaTable() {
		return true;
	}
	
	@Override
	public boolean closeConnectionAfterQuery() {
		return false;
	}
}
