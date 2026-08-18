package databasereplication.implementation.DbTypes;

import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.DBType;


public class DbConnectionFactory {

	private DBType type;
	private IDatabaseConnector connector;
	private DbConnectionFactory( DBType type, IDatabaseSettings settings ) {
		this.type = type;
		
		switch (this.type) {
		case SQLServer2005:
		case SQLServer2008:
			this.connector = new SQLServerConnector(settings);
			break;

		case Postgres: 
			this.connector = new PostgreSQLConnector(settings);
			break;
		case Oracle:
			this.connector = new OracleConnector(settings);
			break;

		case Informix:
			this.connector = new InformixConnector(settings);
			break;
		case AS_400:
			this.connector = new AS400Connector(settings);
			break;
		case DMS2:
			this.connector = new DMS2Connector(settings);
			break;
		case MSAccess:
			this.connector = new MSAccessConnector(settings);
			break;
        case Custom:
			this.connector = new CustomConnector(settings);
			break;
			
		default:
			
			break;
		} 
	}
	
	public static DbConnectionFactory getInstance( DBType type, IDatabaseSettings settings ) {
		return new DbConnectionFactory(type, settings);
	}
	
	public IDatabaseConnector getConnector() {
		return this.connector;
	}
	
}
