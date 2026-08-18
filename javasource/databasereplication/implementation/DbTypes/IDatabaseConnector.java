package databasereplication.implementation.DbTypes;

import com.mendix.replication.ParseException;
import com.mendix.systemwideinterfaces.core.meta.IMetaPrimitive.PrimitiveType;

public abstract class IDatabaseConnector {
	protected String driverClass = "";
	protected String escapeOpen = "";
	protected String escapeClose = "";
	protected boolean escapeColumnNames = false;
	protected boolean escapeTableNames = false;
	protected boolean escapeColumnAlias = true;
	protected boolean escapeTableAlias = true;
	protected boolean allowASToken = true;
	protected boolean separatEscapeSchemaTable = false;
	protected String connectionString = "";
	protected boolean closeConnectionAfterQuery = false;

	public abstract String getConnectionString();
	public abstract String getDriverClass();
	public abstract String getEscapeClose();
	public abstract String getEscapeOpen();
	public abstract boolean shouldEscapeColumnNames();
	public abstract boolean shouldEscapeTableNames();
	public abstract boolean shouldEscapeColumnAlias();
	public abstract boolean shouldEscapeTableAlias();
	public abstract boolean allowASToken();
	public abstract boolean separatEscapeSchemaTable();
	public abstract boolean closeConnectionAfterQuery();

	public String formatValueForUpdate( PrimitiveType type, String strValue, Object objectValue ) throws ParseException {
		return strValue;
	}
}
