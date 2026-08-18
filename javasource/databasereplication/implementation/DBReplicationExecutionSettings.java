package databasereplication.implementation;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.replication.AssociationConfig;
import com.mendix.replication.ICustomValueParser;
import com.mendix.replication.MendixReplicationException;
import com.mendix.replication.ObjectConfig;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IDataType;
import com.mendix.systemwideinterfaces.core.IDataType.DataTypeEnum;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.AdditionalJoins;
import databasereplication.proxies.ReplicationStatus;
import databasereplication.proxies.TableMapping;

import java.util.*;
import java.util.Map.Entry;

/**
 * This class contains all specific settings that can be used in the replication DataManager
 * When this object is created, database settings must be predefined.
 * The object type must be set and the current action context must be set
 * The complete column mapping must be set and
 *
 * @author Jasper
 * @version 1.0
 */
public class DBReplicationExecutionSettings extends DBReplicationSettings {

	/**
	 * Initialize the settings, provide the starting context. If a new context has to be created the settings will be
	 * copied from the provided context.
	 * The last parameter ErrorHandler is optional. This parameter should contain a specfic project errorhandler or when
	 * null the default errorhandler will be used. This handler aborts the import for each exception
	 *
	 * @param context
	 * @param dbSettings
	 * @param objectType
	 * @throws MendixReplicationException
	 */
	public DBReplicationExecutionSettings(IContext context, IDatabaseSettings dbSettings, String objectType)
			throws MendixReplicationException {
		super(context, dbSettings, objectType);
		this.selectClauses = new HashSet<String>();
		this.originalContext = context;
	}

	private String tableAlias;
	private final IContext originalContext;
	private String finishingMicroflowName = null;
	private String finishingMicroflowStatParamName = null;
	private String finishingMicroflowTMParamName = null;

	private boolean importInNewContext = false;
	private boolean useTransactions = true;

	private List<JoinedTable> additionalFromTables = new ArrayList<JoinedTable>();


	private List<String> aliasList = new ArrayList<String>(); // Table alias
	private LinkedHashMap<String, JoinedTable> joinedTableObjects = new LinkedHashMap<String, JoinedTable>();

	// This attribute contains all selectClause for the current settings
	private Set<String> selectClauses;
	private boolean selectDistinct = false;

	public enum JoinType {
		FROM(" FROM "),
		INNER(" INNER JOIN "),
		LEFT(" LEFT JOIN "),
		RIGHT(" RIGHT JOIN "),
		OUTER(" OUTER JOIN "),
		LEFT_OUTER(" LEFT OUTER JOIN "),
		RIGHT_OUTER(" RIGHT OUTER JOIN ");

		private String queryPart = "";

		JoinType(String queryPart) {
			this.queryPart = queryPart;
		}

		protected String getQueryPart() {
			return this.queryPart;
		}
	}

	public void addAttributeMapping(String tableName, String columnName, String memberName) throws CoreException {
		this.addAttributeMapping(tableName, columnName, memberName, KeyType.NoKey, false, null);
	}

	/**
	 * Add a new mapping for a column in the external database to a Member in the MxDatabase.
	 * Just set the table where the member is located in. That table can be de default from table, or it can be any
	 * other joined table.
	 * Set the membername where the column should be mapped to, this memberName must be a member in the MetaObject type
	 * which was specified in the constructor.
	 * Also specify if this column is an key, when true the DataManager will use the value from this column to compare
	 * the MetaObjects with.
	 * <p>
	 * The Value parser given in this column will be called before the value from the column is stored in the
	 * MetaObject, the result from the ValueParser will be used to store.
	 * If the member does not exists in the specified MetaObject an exception will be thrown. Or when the tableName
	 * wasn't specified earlier, an exception will be thrown as well.
	 *
	 * @param tableName,  The name or alias from the table in the external DB
	 * @param columnName, Name of the column in the external DB
	 * @param memberName, The name of the member where the value should be stored in
	 * @param isKey,      Is this member a key column, i.e. should the DataManager search for any other objects with this
	 *                    value
	 * @param parser,     The parser that is going to be used to change the value of this member before storing it
	 * @throws CoreException
	 * @throws CoreException
	 */
	public void addAttributeMapping(String tableName, String columnName, String memberName, KeyType isKey, Boolean isCaseSensitive,
																	ICustomValueParser parser) throws CoreException {
		String tableAlias = this.validateTableAlias(tableName);
		this.selectClauses.add(DatabaseConnector.procesSelectStatement(this.dbSettings.getDatabaseConnection(), tableAlias, columnName,
				memberName).toString());

		this.addAttributeMapping(memberName, memberName, isKey, isCaseSensitive, parser);
	}


	public void addCustomAttributeMapping(String selectClause, String memberName) {
		this.addCustomAttributeMapping(selectClause, memberName, KeyType.NoKey, false, null);
	}

	/**
	 * Add a new mapping to a Member in the MxDatabase. This function won't use any column directly, any select
	 * statement can be given to map to the member.
	 * The selectClause can be any valid SQL statement, there are a few things you should think of when implementing
	 * this statement. The selectClause should be written in the database dialect of the specific database that is going
	 * to be used. And it's not allowed to use an Alias
	 * When creating the SQL query an own alias will be added to this select clause
	 * The result of the selectClause will be mapped to the member given in the parameter, this memberName must be a
	 * member in the MetaObject type which was specified in the constructor.
	 * Also specify if this column is an key, when true the DataManager will use the value from this column to compare
	 * the MetaObjects with.
	 * <p>
	 * The given ValueParser will be called before the value from the SelectClause is stored in the MetaObject, the
	 * result from the ValueParser will be stored.
	 * If the member does not exists in the specified MetaObject an exception will be thrown. Or when the tableName
	 * wasn't specified earlier, an exception will be thrown as well.
	 *
	 * @param selectClause, this can be any valid SQL select statement WITHOUT an alias.
	 * @param memberName,   The name of the member where the value should be stored in
	 * @param keyType,      Is this member a key column, i.e. should the DataManager search for any other objects with this
	 *                      value
	 * @param parser,       The parser that is going to be used to change the value of this member before storing it
	 * @return
	 * @throws CoreException
	 */
	public ObjectConfig addCustomAttributeMapping(String selectClause, String memberName, KeyType keyType, Boolean isCaseSensitive,
																								ICustomValueParser parser) {
		this.selectClauses.add(DatabaseConnector.procesSelectStatement(this.dbSettings.getDatabaseConnection(), selectClause, memberName)
				.toString());

		return super.addAttributeMapping(memberName, memberName, keyType, isCaseSensitive, parser);
	}


	public AssociationConfig addAssociationMapping(String tableName, String columnName, String associationName, String associatedObjectType,
																								 String memberName, KeyType keyType, Boolean isCaseSensitive, ICustomValueParser parser)
			throws MendixReplicationException {
		String alias = this.createRandomAlias();
		String tableAlias = this.validateTableAlias(tableName);

		this.selectClauses.add(DatabaseConnector.procesSelectStatement(this.dbSettings.getDatabaseConnection(), tableAlias, columnName, alias)
				.toString());

		return this.addAssociationMapping(alias, associationName, associatedObjectType, memberName, keyType, isCaseSensitive, parser);
	}


	private String validateTableAlias(String tableName) throws MendixReplicationException {
		String tableAlias = "";
		if ( tableName == this.tableName )
			tableAlias = this.tableAlias;
		if ( tableName == this.tableAlias )
			tableAlias = this.tableAlias;
		else if ( this.aliasList.contains(tableName) )
			tableAlias = tableName;
		else
			throw new MendixReplicationException(
					"The table: " + tableName + " can't be used for this replication. You should probably join it first");

		return tableAlias;
	}

	public AssociationConfig addCustomAssociationMapping(String selectClause, String associationName, String associatedObjectType,
																											 String memberName, KeyType isKey, Boolean isCaseSensitive, ICustomValueParser parser)
			throws MendixReplicationException {
		String alias = this.createRandomAlias();

		this.selectClauses.add(
				DatabaseConnector.procesSelectStatement(this.dbSettings.getDatabaseConnection(), selectClause, alias).toString());

		return super.addAssociationMapping(alias, associationName, associatedObjectType, memberName, isKey, isCaseSensitive, parser);
	}


	/**
	 * Set the table from where this query should fetch it's result
	 *
	 * @param tableName
	 * @return the alias for this table
	 * @throws CoreException
	 */
	public String setFromTable(String tableName) throws MendixReplicationException {
		return this.setFromTable(tableName, "mainTable");
	}

	/**
	 * Set the table from where this query should fetch it's result
	 *
	 * @param tableName
	 * @param tableAlias
	 * @return tableAlias
	 * @throws CoreException
	 */
	public String setFromTable(String tableName, String tableAlias) throws MendixReplicationException {
		if ( this.tableName != null )
			throw new MendixReplicationException(
					"The from table may only be defined once. If you wan't to use a second table you should join it.");
		if ( tableAlias == null || tableName == null )
			throw new MendixReplicationException("The from table name or alias may not be null.");

		this.tableName = tableName;
		this.tableAlias = tableAlias;

		return this.tableAlias;
	}

	public String createRandomAlias() {
		UUID uuid = UUID.randomUUID();
		String alias = uuid.toString().substring(0, 10);
		while ( this.aliasList.contains(alias) ) {
			alias = UUID.randomUUID().toString().substring(0, 10);
		}

		this.aliasList.add(alias);

		return alias;
	}

	public JoinedTable joinTable(JoinType joinType, String tableName, String alias) {
		if ( alias == null )
			alias = /* ( joinLevel < 10 ? "0" + joinLevel : joinLevel ) + */"tbl_" + (this.aliasList.size() + 1);

		this.aliasList.add(alias);

		JoinedTable table = new JoinedTable(joinType, tableName, alias);
		if ( joinType == JoinType.FROM ) {
			this.additionalFromTables.add(table);
		} else {
			this.joinedTableObjects.put(alias, table);
		}
		return table;
	}

	public class JoinedTable {

		private String alias;
		private String joinTableName;
		private JoinType joinType;
		private String joinConstraint;

		protected JoinedTable(JoinType joinType, String tableName, String alias) {
			this.joinType = joinType;
			this.joinTableName = tableName;
			this.alias = alias;
			this.joinConstraint = "";
		}

		public void setAlias(String alias) throws CoreException {
			if ( DBReplicationExecutionSettings.this.joinedTableObjects.containsValue(alias) )
				throw new CoreException("The alias: " + alias + " already exists.");

			DBReplicationExecutionSettings.this.joinedTableObjects.remove(this.alias);
			DBReplicationExecutionSettings.this.joinedTableObjects.put(alias, this);

			DBReplicationExecutionSettings.this.aliasList.remove(this.alias);
			DBReplicationExecutionSettings.this.aliasList.add(alias);
			this.alias = alias;
		}

		public void setConstraint(String constraint) {
			this.joinConstraint = constraint;
		}

		public JoinedTable addConstraint(String columnName, String constraintTableAlias, String constraintColumn)
				throws MendixReplicationException {
			if ( DBReplicationExecutionSettings.this.tableName.equals(constraintTableAlias) )
				constraintTableAlias = DBReplicationExecutionSettings.this.tableAlias;
			else if ( !DBReplicationExecutionSettings.this.tableAlias.equals(constraintTableAlias)
					&& !DBReplicationExecutionSettings.this.aliasList
					.contains(constraintTableAlias) )
				throw new MendixReplicationException(
						"The table(" + constraintTableAlias + ") which should be used as constraining table could not be found in the settings.");


			StringBuilder constraint = DatabaseConnector.procesConstraint(DBReplicationExecutionSettings.this.dbSettings.getDatabaseConnection(),
					this.alias, columnName, constraintTableAlias, constraintColumn);
			if ( !"".equals(this.joinConstraint) )
				constraint.insert(0, " AND ");

			this.joinConstraint += constraint;

			return this;
		}

		protected String getQueryPart() {
			StringBuilder queryPart = DatabaseConnector.procesJoinedTable(DBReplicationExecutionSettings.this.dbSettings.getDatabaseConnection(),
					this.joinType, this.joinTableName, this.alias);

			if ( !"".equals(this.joinConstraint) )
				queryPart
						.append(" ON ")
						.append((this.joinConstraint.trim().endsWith("AND") ? this.joinConstraint.trim().substring(0,
								(this.joinConstraint.trim().length() - 4)) : this.joinConstraint)).append(" ");

			return queryPart.toString();
		}

		public String getAlias() {
			return this.alias;
		}

		public String getTableName() {
			return this.joinTableName;
		}
	}


	/**
	 * Add the parameter constraint to the query, always uses an AND operator
	 *
	 * @param constraint
	 */
	public void addConstraint(String constraint) {
		if ( constraint != null && !constraint.trim().isEmpty() ) {
			// If the constraint variable is empty just assign the constraint
			if ( this.constraint == null || this.constraint.trim().isEmpty() ) {
				this.constraint = constraint;
			}
			// If there is already a constraint specified add the given constraint with an AND operator
			else {
				this.constraint += " AND " + constraint;
			}
		}
	}

	/**
	 * @return the query that can be executed based on all mapped columns and associations
	 * @throws CoreException
	 */
	public String getQuery() throws MendixReplicationException {
		StringBuilder query = new StringBuilder();

		// Validate if the query can be created or if there is still any information missing
		if ( this.selectClauses.size() == 0 )
			throw new MendixReplicationException("No select clause found for this query.");
		if ( this.tableName == null )
			throw new MendixReplicationException("The 'from table' is not defined.");
		if ( this.tableAlias == null )
			throw new MendixReplicationException("The alias of the 'from table' may not be null.");

		// Append all Select clauses to the query
		query.append("SELECT ");
		if ( this.selectDistinct )
			query.append("DISTINCT ");
		Iterator<String> selectClauseIter = this.selectClauses.iterator();
		while ( selectClauseIter.hasNext() ) {
			query.append(selectClauseIter.next());

			if ( selectClauseIter.hasNext() )
				query.append(", ");
		}

		query.append(DatabaseConnector.procesFromTable(this.dbSettings.getDatabaseConnection(), this.tableName, this.tableAlias));
		for ( JoinedTable table : this.additionalFromTables ) {
			query.append(", ");
			DatabaseConnector.processTableName(this.dbSettings.getDatabaseConnection(), table.getTableName(), table.getAlias(), query);
		}


		// If there are joined tables, append the joins to the query
		for ( Entry<String, JoinedTable> join : this.joinedTableObjects.entrySet() ) {
			query.append(join.getValue().getQueryPart());
		}

		// When a constraint is set, append the constraint to the query
		if ( this.constraint != null && this.constraint.length() > 0 )
			query.append(" WHERE ").append(this.constraint).append(" ");

		return query.toString();
	}

	public String getFromTableName() {
		return this.tableName;
	}

	public String getFromTableAlias() {
		return this.tableAlias;
	}

	public void selectDistinct(boolean distinct) {
		this.selectDistinct = distinct;
	}


	public String getFinishingMicroflowName() {
		return this.finishingMicroflowName;
	}

	public String getFinishingMicroflowStatParamName() {
		return this.finishingMicroflowStatParamName;
	}

	public String getFinishingMicroflowTMParamName() {
		return this.finishingMicroflowTMParamName;
	}

	public void addFinishingMicroflow(String microflowName) throws MendixReplicationException {
		this.finishingMicroflowName = microflowName;
		Map<String, IDataType> paramMap = Core.getInputParameters(microflowName);
		if ( paramMap == null )
			throw new MendixReplicationException("Unable to find the finishing microflow with name: " + microflowName);

		for ( Entry<String, IDataType> entry : paramMap.entrySet() ) {
			IDataType dType = entry.getValue();
			if ( dType.getType() == DataTypeEnum.Object ) {
				if ( dType.getObjectType().equals(ReplicationStatus.getType()) ) {
					this.finishingMicroflowStatParamName = entry.getKey();
				} else if ( dType.getObjectType().equals(TableMapping.getType()) ) {
					this.finishingMicroflowTMParamName = entry.getKey();
				}
			}
		}

	}

	/**
	 * Acquire the correct alias for a joined table
	 *
	 * @param context
	 * @param joinedTable
	 * @return
	 */
	public String getAliasForJoinedTable(IContext context, IMendixObject joinedTable) {
		if ( (Boolean) joinedTable.getValue(context, AdditionalJoins.MemberNames.IsFromTable.toString()) )
			return this.getFromTableAlias();
		else
			return (String) joinedTable.getValue(context, AdditionalJoins.MemberNames.Alias.toString());
	}

	public boolean hasTableAlias(String tableAlias) {
		return this.aliasList.contains(tableAlias);
	}

	/**
	 * Default the DataManager uses a new context for each run
	 *
	 * @param useNewContext
	 */
	public void setImportInNewContext(boolean useNewContext) {
		this.importInNewContext = useNewContext;
	}

	/**
	 * Should the DataManager create a new Context when importing.
	 *
	 * @return, default TRUE
	 */
	public boolean importInNewContext() {
		return this.importInNewContext;
	}

	/**
	 * Should transactions be used when importing
	 * Default is TRUE
	 *
	 * @param useTransactions
	 */
	public void setUseTransactions(boolean useTransactions) {
		this.useTransactions = useTransactions;
	}

	/**
	 * Should transactions be used when importing
	 *
	 * @return Default is TRUE
	 */
	public boolean useTransactions() {
		return this.useTransactions;
	}

	public IContext getOriginalContext() {
		return this.originalContext;
	}
}
