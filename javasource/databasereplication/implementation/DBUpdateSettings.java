package databasereplication.implementation;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.replication.ICustomValueParser;
import com.mendix.replication.MendixReplicationException;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.systemwideinterfaces.core.meta.IMetaPrimitive.PrimitiveType;
import databasereplication.implementation.DbTypes.IDatabaseConnector;
import databasereplication.implementation.DbWriter.UpdateQueryBuilder;
import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.UpdateType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map.Entry;

/**
 * This class contains all specific settings that can be used in the replication DataManager
 * When this object is created, database settings must be predefined, the object type must be set and the current action
 * context must be set
 * The complete column mapping must be set
 * 
 * 
 * @version 1.0
 */
public class DBUpdateSettings extends DBReplicationSettings {

	/**
	 * Initialize the settings, provide the starting context. If a new context has to be created the settings will be
	 * copied from the provided context.
	 * The last parameter ErrorHandler is optional. This parameter should contain a specfic project errorhandler or when
	 * null the default errorhandler will be used. This handler aborts the import for each exception
	 *
	 * 
	 * @param context
	 * @param dbSettings
	 * @param objectType
	 * @param updateType
	 * @throws MendixReplicationException
	 */
	public DBUpdateSettings(IContext context, IDatabaseSettings dbSettings, String objectType, UpdateType updateType) throws MendixReplicationException {
		super(context, dbSettings, objectType);
		this.columnMap = new HashMap<String, ColumnInfo>();
		this.updateType = updateType;
	}

	private UpdateType updateType;

	private enum ColumnType {
		staticValue,
		attributeValue,
		associationValue
	}

	// This attribute contains all selectClause for the current settings
	private HashMap<String, ColumnInfo> columnMap; // ColumnAlias, ColumnInfo

	public class ColumnInfo {

		private ColumnType colType;
		private String memberName;
		private String columnName;
		private String QueryName;
		private String dateType;
		private String staticValue;
		private PrimitiveType type;
		private String associatedObjectType;
		private String associationName;
		private boolean pushNullValue = false;

		public ColumnInfo( String memberName, String columnName, String queryStatement, String dataType ) {
			this.memberName = memberName;
			this.columnName = columnName;
			this.dateType = dataType;
			this.QueryName = queryStatement;
			this.colType = ColumnType.attributeValue;
		}

		public PrimitiveType getType() {
			if ( this.dateType == null )
				return null;

			this.dateType = this.dateType.toLowerCase(Locale.ROOT);
			if ( this.dateType.contains("char") || this.dateType.contains("varchar") || this.dateType.contains("nchar") || this.dateType
					.contains("string") )
				this.type = PrimitiveType.String;
			else if ( this.dateType.contains("int") || this.dateType.contains("number") )
				this.type = PrimitiveType.Integer;
			else if ( this.dateType.contains("long") )
				this.type = PrimitiveType.Long;
			else if (this.dateType.contains("decimal") )
				this.type = PrimitiveType.Decimal;
			else if ( this.dateType.contains("date") || this.dateType.contains("time") )
				this.type = PrimitiveType.DateTime;

			if ( this.type == null )
				Core.getLogger("DBUpdateSettings").info("REQUIRES IMPLEMENTATION!!! : " + this.dateType);

			return this.type;
		}

		public ColumnInfo setStaticValue( String staticValue ) {
			this.colType = ColumnType.staticValue;
			this.staticValue = staticValue;
			return this;
		}

		protected boolean isStaticValue() {
			return this.colType == ColumnType.staticValue;
		}

		protected boolean isAssociationValue() {
			return this.colType == ColumnType.associationValue;
		}

		public ColumnInfo setAssociationValue( String associationName, String associatedObjectType ) {
			this.associationName = associationName;
			this.associatedObjectType = associatedObjectType;
			this.colType = ColumnType.associationValue;

			return this;
		}

		public ColumnInfo shouldPushNullValue( boolean shouldPushNull ) {
			this.pushNullValue = shouldPushNull;
			return this;
		}

		public boolean shouldPushNullValue() {
			return this.pushNullValue;
		}

	}

	private DBValueParser vParser;

	public ColumnInfo addStaticColumnMapping( String tableName, String columnName, String staticValue, KeyType isKey, Boolean isCaseSensitive ) throws CoreException {
		String alias = tableName + "." + columnName;
		ColumnInfo columnInfo = new ColumnInfo(null, columnName, DatabaseConnector.procesSetStatement(this.dbSettings.getDatabaseConnection(),
				tableName, columnName).toString(), "string").setStaticValue(staticValue);
		this.columnMap.put(alias, columnInfo);

		if ( this.aliasIsMapped(alias) )
			throw new MendixReplicationException("This column alias: " + columnName + " already exists in this configuration").addVersion();

		this.addMappedAlias(alias);

		// Make sure case sensitive is not null
		isCaseSensitive = (isCaseSensitive == null ? false : isCaseSensitive);
		if ( isKey != KeyType.NoKey )
			this.getMainObjectConfig().getKeys().put(alias, isCaseSensitive);

		return columnInfo;
	}

	/**
	 * 
	 * @param tableName, The name or alias from the table in the external DB
	 * @param columnName, Name of the column in the external DB
	 * @param memberName, The name of the member where the value should be stored in
	 * @param keyType, Is this member a key column, i.e. should the DataManager search for any other objects with this
	 *        value
	 * @param parser, The parser that is going to be used to change the value of this member before storing it
	 * @throws CoreException
	 */
	public ColumnInfo addAttributeMapping(String tableName, String columnName, String dataType, String memberName, KeyType keyType, Boolean isCaseSensitive, ICustomValueParser parser ) {
		String alias = tableName + "." + columnName;
		ColumnInfo columnInfo = new ColumnInfo(memberName, columnName, DatabaseConnector.procesSetStatement(this.dbSettings.getDatabaseConnection(),
				tableName, columnName).toString(), dataType);
		this.columnMap.put(alias, columnInfo);

		super.addAttributeMapping(alias, memberName, keyType, isCaseSensitive, parser);

		return columnInfo;
	}

	public ColumnInfo addAssociationMapping(String tableName, String columnName, String dataType, String associationName, String associatedObjectType, String memberName, KeyType keyType, Boolean isCaseSensitive, ICustomValueParser parser) throws MendixReplicationException {
		String alias = tableName + "." + columnName;
		ColumnInfo columnInfo = new ColumnInfo(memberName, columnName, DatabaseConnector.procesSetStatement(this.dbSettings.getDatabaseConnection(),
				tableName, columnName).toString(), dataType);
		columnInfo.setAssociationValue(associationName, associatedObjectType);
		this.columnMap.put(alias, columnInfo);

		super.addAssociationMapping(alias, associationName, associatedObjectType, memberName, keyType, isCaseSensitive, parser);

		return columnInfo;
	}

	/**
	 * Set the table from where this query should fetch it's result
	 * 
	 * @param tableName
	 * @return tableAlias
	 * @throws CoreException
	 */
	public String setUpdateTable( String tableName ) throws MendixReplicationException {
		if ( this.tableName != null )
			throw new MendixReplicationException("The from table may only be defined once. If you wan't to use a second table you should join it.");

		this.tableName = tableName;

		return this.tableName;
	}

	public String getQuery( IMendixObject mappedObject ) throws MendixReplicationException {
		return this.getUpdateQuery(this.updateType, mappedObject);
	}


	/**
	 * @return the query that can be executed based on all mapped columns and associations
	 * @throws CoreException
	 */
	public String getUpdateQuery( UpdateType updateType, IMendixObject mappedObject ) throws MendixReplicationException {
		// Validate if the query can be created or if there is still any information missing
		if ( this.columnMap.size() == 0 )
			throw new MendixReplicationException("No set clause found for this query.");
		if ( this.tableName == null )
			throw new MendixReplicationException("The 'update table' is not defined.");

		if ( this.vParser == null )
			this.vParser = new DBValueParser(this);

		IDatabaseConnector dbConnector = this.dbSettings.getDatabaseConnection();
		
		UpdateQueryBuilder builder = new UpdateQueryBuilder(dbConnector, updateType);
		builder.setFromTable(this.tableName)
				.setWhereStatement(this.constraint);


		// Append all Select clauses to the query
		for( Entry<String, ColumnInfo> columnInfoEntry : this.columnMap.entrySet() )
		{
			ColumnInfo columnInfo = columnInfoEntry.getValue();
			String memberAlias = columnInfoEntry.getKey();
			Boolean isKey = this.getMainObjectConfig().getKeys().get(memberAlias);

			boolean useValueInQuery = true;

			String memberValueForQuery = null;
			if ( !columnInfo.isStaticValue() ) {
				Object tmpValue = this.vParser.getValueFromObject(mappedObject, memberAlias);
				String strValue = (String) this.vParser.getValue(PrimitiveType.String, memberAlias, tmpValue);
				PrimitiveType targetType = columnInfo.getType();
				PrimitiveType sourceType = this.getMemberType(memberAlias);
				if ( targetType == null )
					targetType = sourceType;
				
				if( sourceType == PrimitiveType.Boolean ) {
					Boolean boolValue = (Boolean) tmpValue;
					if( boolValue == true )
						strValue = "1";
					else 
						strValue = "0";
				}
				
				switch (targetType)
				{
				case AutoNumber:
				case Integer:
				case Long:
				case DateTime:
				case Decimal:
					memberValueForQuery = (String) dbConnector.formatValueForUpdate(targetType, strValue, tmpValue);
					useValueInQuery = memberValueForQuery != null;
					break;
						
				default:
					if ( strValue != null ) {
						if( strValue.contains("'") )
							strValue = strValue.replaceAll("'", "''");
						
						memberValueForQuery = "'" + strValue + "'";
						useValueInQuery = true;
					}
					else if ( columnInfo.shouldPushNullValue() )
						memberValueForQuery = " NULL ";
					else
						useValueInQuery = false;
					break;
				}
			}
			else {
				useValueInQuery = true;
				memberValueForQuery = columnInfo.staticValue;
			}

			if ( useValueInQuery ) {
				if ( isKey != null && (this.updateType == UpdateType.UpdateOnly || this.updateType == UpdateType.UpdateOrInsert) ) {
					builder.addWhereClause(columnInfo.QueryName, memberValueForQuery);
				}

				builder.addStatement(columnInfo.QueryName, memberValueForQuery);
			}
		}

		return builder.build();
	}

	public UpdateType getUpdateType() {
		return this.updateType;
	}
}
