package databasereplication.implementation.DbWriter;

import databasereplication.implementation.DatabaseConnector;
import databasereplication.implementation.DbTypes.IDatabaseConnector;
import databasereplication.proxies.UpdateType;


public class UpdateQueryBuilder {

	private UpdateType updateType;
	private IDatabaseConnector dbConnector;
	private String tableName;
	private StringBuilder values = new StringBuilder(200);
	private StringBuilder fieldList = new StringBuilder(200);
	private StringBuilder whereStatement = new StringBuilder(200);

	public UpdateQueryBuilder( IDatabaseConnector iDatabaseConnector, UpdateType updateType ) {
		this.updateType = updateType;
		this.dbConnector = iDatabaseConnector;
	}

	public UpdateQueryBuilder setFromTable( String tableName ) {
		this.tableName = DatabaseConnector.procesUpdateTable(this.dbConnector, tableName);

		return this;
	}

	public UpdateQueryBuilder addStatement( String queryName, String value ) {
		String column = DatabaseConnector.processTableAlias(this.dbConnector, queryName);

		switch (this.updateType) {
		case AlwaysInsert:

			if ( this.values.length() != 0 ) {
				this.fieldList.append(", ");
				this.values.append(", ");
			}

			this.fieldList.append(column);
			break;
		case UpdateOnly:
		case UpdateOrInsert:
			if ( this.values.length() != 0 )
				this.values.append(", ");

			this.values.append(" ").append(column).append(" = ");
			break;
		}

		this.values.append(value);

		return this;
	}

	public UpdateQueryBuilder addWhereClause( String queryName, String value ) {
		if ( this.whereStatement.length() == 0 )
			this.whereStatement.append(" WHERE ");
		else
			this.whereStatement.append(" AND ");


		this.whereStatement.append(DatabaseConnector.processTableAlias(this.dbConnector, queryName)).append("=").append(value);

		return this;
	}

	public UpdateQueryBuilder setWhereStatement( String constraint ) {
		// When a constraint is set, append the constraint to the query
		if ( constraint != null && constraint.trim().length() > 0 )
			this.whereStatement.append(constraint.trim());

		return this;
	}

	public String build() {
		StringBuilder query = new StringBuilder(400);

		switch (this.updateType) {
		case AlwaysInsert:
			query.append("INSERT INTO ").append(this.tableName).append(" ( ").append(this.fieldList).append(" ) VALUES ( ").append(this.values)
					.append(" ) ");
			break;
		case UpdateOnly:
		case UpdateOrInsert:
			query.append("UPDATE ").append(this.tableName).append(" SET ").append(this.values).append(this.whereStatement);
			break;
		}

		return query.toString();
	}

	@Override
	public String toString() {
		return this.build();
	}

}