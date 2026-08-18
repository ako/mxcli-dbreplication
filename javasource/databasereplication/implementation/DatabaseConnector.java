package databasereplication.implementation;

import com.mendix.core.CoreException;
import com.mendix.replication.MendixReplicationException;
import databasereplication.implementation.DBReplicationExecutionSettings.JoinType;
import databasereplication.implementation.DbTypes.IDatabaseConnector;
import databasereplication.interfaces.IDatabaseSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class DatabaseConnector implements AutoCloseable {

	public IDatabaseSettings settings;
	private Connection connection;

	public DatabaseConnector( IDatabaseSettings dbSettings ) {
		this.settings = dbSettings;
	}

	public Connection getConnection() throws CoreException {
		IDatabaseConnector dbType = this.settings.getDatabaseConnection();

		try {
			Class.forName(dbType.getDriverClass());
		}
		catch( ClassNotFoundException e ) {
			throw new MendixReplicationException(Messages.COULD_NOT_LOCATE_DB_DRIVER.getMessage() + dbType.getDriverClass(), e);
		}

		String connectionString = dbType.getConnectionString();
		if ( connectionString.length() <= 0 ) {
			throw new MendixReplicationException(Messages.UNKNOWN_DATABASE_TYPE.getMessage(dbType.getConnectionString()));
		}
		try {
			if ( this.settings.useIntegratedAuthentication() )
				this.connection = DriverManager.getConnection(connectionString);
			else
				this.connection = DriverManager.getConnection(connectionString, this.settings.getUserName(), this.settings.getPassword());
			return this.connection;
		}
		catch( StringIndexOutOfBoundsException e ) {
			throw new MendixReplicationException(e.getMessage() + ". Are you sure you are connecting to the correct port?", e);
		}
		catch( ExceptionInInitializerError e ) {
			String msg = Messages.COULD_NOT_CONNECT_CLOUD_SECURITY.getMessage();
			throw new MendixReplicationException(msg);
		}
		catch( SQLException e ) {
			String msg = Messages.COULD_NOT_CONNECT_WITH_DB.getMessage() + connectionString;
			throw new MendixReplicationException(msg, e);
		}
	}
	
	public void close() throws MendixReplicationException {
		if( this.connection != null ) {
			try {
				if(!this.connection.isClosed() && this.settings.getDatabaseConnection().closeConnectionAfterQuery() )
					this.connection.close(); 

				this.connection = null;
			}
			catch( SQLException e ) {
				String msg = "Unable to close the connection to database: " + this.settings.getDatabaseConnection().getConnectionString(); 
				throw new MendixReplicationException(msg, e);
			}
		}
	}

	/**
	 * Create a string builder which contains the full select statement for the values in the parameters
	 * The stringbuilder is created fully according the database expectations.
	 * 
	 * @param dbType
	 * @param tableAlias
	 * @param columnName
	 * @param alias
	 * @return (select statement conform the db standard)
	 */
	public static StringBuilder procesSelectStatement( IDatabaseConnector dbType, String tableAlias, String columnName, String alias ) {
		StringBuilder builder = new StringBuilder();
		builder.append(" ");

		addTableAlias(builder, dbType, tableAlias);

		builder.append(".");
		if ( dbType.shouldEscapeColumnNames() ) {
			builder.append(dbType.getEscapeOpen());
			builder.append(getObjectNameForSyntax(dbType, columnName));
			builder.append(dbType.getEscapeClose());
		}
		else
			builder.append(getObjectNameForSyntax(dbType, columnName));

		// Oracle is the only one who has trouble with the AS statement, but it is allowed in the select
		builder.append(" AS ");

		if ( dbType.shouldEscapeColumnAlias() ) {
			builder.append(dbType.getEscapeOpen())
					.append(alias)
					.append(dbType.getEscapeClose())
					.append(" ");
		}
		else
			builder.append(alias);

		return builder;
	}

	public static String processTableAlias(IDatabaseConnector databaseType, String tableAlias) {
		final StringBuilder builder = new StringBuilder();
		addTableAlias(builder, databaseType, tableAlias);
		return builder.toString();
	}

	public static void addTableAlias(StringBuilder builder,  IDatabaseConnector databaseType, String tableAlias) {
		if (databaseType.shouldEscapeTableAlias())
			builder.append(databaseType.getEscapeOpen()).append(tableAlias).append(databaseType.getEscapeClose());
		else
			builder.append(tableAlias);
	}

	public static Object procesSelectStatement( IDatabaseConnector dbType, String selectClause, String alias ) {
		StringBuilder builder = new StringBuilder();
		builder.append(" ")
				.append(selectClause)

				// Oracle is the only one who has trouble with the AS statement, but it is allowed in the select
				.append(" AS ");

		if ( dbType.shouldEscapeColumnAlias() ) {
			builder.append(dbType.getEscapeOpen())
					.append(alias)
					.append(dbType.getEscapeClose())
					.append(" ");
		}
		else {
			builder.append(alias)
					.append(" ");
		}

		return builder;
	}

	public static StringBuilder procesFromTable( IDatabaseConnector databaseType, String tableName, String tableAlias ) {
		StringBuilder builder = new StringBuilder();
		builder.append(" FROM ");

		processTableName(databaseType, tableName, tableAlias, builder);

		return builder;
	}

	public static void processTableName( IDatabaseConnector databaseType, String tableName, String tableAlias, StringBuilder builder ) {
		if ( databaseType.shouldEscapeTableNames() ) {
			builder.append(databaseType.getEscapeOpen())
					.append(getObjectNameForSyntax(databaseType, tableName))
					.append(databaseType.getEscapeClose());
		}
		else {
			builder.append(getObjectNameForSyntax(databaseType, tableName))
					.append(" ");
		}

		if ( databaseType.allowASToken() )
			builder.append(" AS ");
		else
			builder.append(" ");

		addTableAlias(builder, databaseType, tableAlias);
		builder.append(" ");
	}

	public static String procesUpdateTable( IDatabaseConnector databaseType, String tableName ) {
		StringBuilder builder = new StringBuilder();

		if ( databaseType.shouldEscapeTableNames() ) {
			builder.append(databaseType.getEscapeOpen())
					.append(getObjectNameForSyntax(databaseType, tableName))
					.append(databaseType.getEscapeClose());
		}
		else {
			builder.append(getObjectNameForSyntax(databaseType, tableName));
		}

		builder.append(" ");

		return builder.toString();
	}

	public static StringBuilder procesJoinedTable( IDatabaseConnector databaseType, JoinType joinType, String joinTableName, String alias ) {
		StringBuilder builder = new StringBuilder();
		builder.append(joinType.getQueryPart()).append(" ");
		processTableName(databaseType, joinTableName, alias, builder);

		return builder;
	}

	public static StringBuilder procesConstraint( IDatabaseConnector databaseType, String tableAlias, String columnName, String constraintTableAlias, String constraintColumn ) {
		StringBuilder builder = new StringBuilder();

		builder.append(databaseType.getEscapeOpen())
				.append(tableAlias)
				.append(databaseType.getEscapeClose())

				.append(".")

				.append(databaseType.getEscapeOpen())
				.append(getObjectNameForSyntax(databaseType, columnName))
				.append(databaseType.getEscapeClose())

				.append(" = ")

				.append(databaseType.getEscapeOpen())
				.append(constraintTableAlias)
				.append(databaseType.getEscapeClose())
				.append(".")
				.append(databaseType.getEscapeOpen())
				.append(getObjectNameForSyntax(databaseType, constraintColumn))
				.append(databaseType.getEscapeClose())
				.append(" ");

		return builder;
	}

	/**
	 * Prepare the object name for the correct syntax
	 * Oracle is case sensitive (it uses upper case) and uses different schema's
	 * Therefore check which type it is and if it is oracle then change the name to uppercase
	 * Because oracle uses different schema's as well try split the name before surrounding with escape characters
	 * 
	 * Otherwise leave the table name as is and append the escape characters.
	 * 
	 * @param databaseType
	 * @param objectName
	 * @return the object name according the correct syntax
	 */
	private static String getObjectNameForSyntax( IDatabaseConnector databaseType, String objectName ) {
		if ( databaseType.separatEscapeSchemaTable() ) {
			if ( objectName.contains(".") ) {
				String[] names = objectName.split("\\.");
				objectName = names[0];
				objectName += databaseType.getEscapeClose() + "." + databaseType.getEscapeOpen();
				objectName += names[1];
			}
		}

		return objectName;
	}

	/**
	 * This action creates a query part for an update / SET statement
	 * 
	 * @param dbType
	 * @param tableName
	 * @param columnName
	 * @return
	 */
	@SuppressWarnings("unused")
	public static StringBuilder procesSetStatement( IDatabaseConnector dbType, String tableName, String columnName ) {

		StringBuilder sb = new StringBuilder();
		sb.append(columnName);
		return sb;
	}
}
