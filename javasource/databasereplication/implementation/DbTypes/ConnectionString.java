package databasereplication.implementation.DbTypes;

import com.mendix.core.CoreRuntimeException;
import com.mendix.systemwideinterfaces.core.IContext;

import databasereplication.implementation.ObjectBaseDBSettings;
import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.DBType;
import databasereplication.proxies.Database;

public final class ConnectionString {

	public static String generate(IContext context, Database database) {
		return generateFromSettings(database.getDatabaseType(), new ObjectBaseDBSettings(context, database.getMendixObject()), false);
	}

	public static String generateFromSettings(DBType type, IDatabaseSettings settings, Boolean withPassword) {
		switch (type) {
			case SQLServer2005:
			case SQLServer2008:
				return sqlServer(settings);
			case Postgres:
				return postgres(settings);
			case Oracle:
				return oracle(settings, withPassword);
			case Informix:
				return informix(settings);
			case AS_400:
				return as400(settings);
			case DMS2:
				return dms2(settings, withPassword);
			case MSAccess:
				return msAccess(settings);
			case Custom:
			default:
				return "";
		}
	}

	private static String sqlServer(IDatabaseSettings settings) {
		String connectionString = "jdbc:sqlserver://" + settings.getAddress() +
				(empty(settings.getPort()) ? "" : ":" + settings.getPort()) +
				(empty(settings.getServiceName()) ? "" : "\\" + settings.getServiceName()) +
				";databaseName=" + settings.getDatabaseName() +
				(settings.useIntegratedAuthentication() ? ";integratedSecurity=true" : "");

		return connectionString;
	}

	private static String postgres(IDatabaseSettings settings) {
		String connectionString = "jdbc:postgresql://" + settings.getAddress() +
				(empty(settings.getPort()) ? "" : ":" + settings.getPort())
				+ "/" + settings.getDatabaseName();
		
		return connectionString;
	}

	private static String oracle(IDatabaseSettings settings, Boolean withPassword) {
		String connectionString = "jdbc:oracle:thin:" + settings.getUserName() + "/" +
				(withPassword ? settings.getPassword() : "********") +
				"@" + settings.getAddress();

		if (!empty(settings.getPort())) {
			connectionString += ":" + settings.getPort();
		}

		if (!empty(settings.getServiceName())) {
			if (!empty(settings.getDatabaseName())) {
				throw new CoreRuntimeException(
						"Invalid configuration, you cannot use both the SID(" + settings.getServiceName()
						+ ") and a Service Name (" + settings.getServiceName() + ")");
			}

			connectionString += "/" + settings.getServiceName();
		} else if (!empty(settings.getDatabaseName())) {
			connectionString += ":" + settings.getDatabaseName();
		}

		return connectionString;
	}

	private static String informix(IDatabaseSettings settings) {
		String connectionString = "jdbc:informix-sqli://" + settings.getAddress() +  ":" +
				(empty(settings.getPort()) ? "" : settings.getPort()) +
				(empty(settings.getDatabaseName()) ? "" : "/" + settings.getDatabaseName() + ":") +
				"informixserver=" + settings.getServiceName();

		return connectionString;
	}

	private static String as400(IDatabaseSettings settings) {
		String connectionString = "jdbc:as400://" + settings.getAddress() +
				(empty(settings.getDatabaseName()) ? "" : "/" + settings.getDatabaseName());

		return connectionString;
	}

	private static String dms2(IDatabaseSettings settings, Boolean withPassword) {
		String connectionString = "jdbc:unisys:dmsql:Unisys.DMSII:resource=" + settings.getDatabaseName() +
				(empty(settings.getAddress()) ? "" : ";host=" + settings.getAddress()) +
				(empty(settings.getPort()) ? "" : ";port=" + settings.getPort()) +
				";user=" + settings.getUserName() +
				";password=" + (withPassword ? settings.getPassword() : "********");

		return connectionString;
	}

	private static String msAccess(IDatabaseSettings settings) {
		String connectionString = "jdbc:access:/" + settings.getDatabaseName();

		return connectionString;
	}
	
	private static boolean empty(String s) {
		return s == null || s.isEmpty();
	}
}
