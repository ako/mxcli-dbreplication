package databasereplication.implementation.DbTypes;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.mendix.replication.ParseException;
import com.mendix.replication.helpers.ValueParserUtils;
import com.mendix.systemwideinterfaces.core.meta.IMetaPrimitive.PrimitiveType;

import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.DBType;

public class OracleConnector extends IDatabaseConnector {

	protected OracleConnector( IDatabaseSettings settings ) {
		this.connectionString = ConnectionString.generateFromSettings(DBType.Oracle, settings, true);
	}

	@Override
	public String getConnectionString() {
		return this.connectionString;
	}

	@Override
	public String getDriverClass() {
		return "oracle.jdbc.OracleDriver";
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
	
	private static final SimpleDateFormat format = new SimpleDateFormat( "yyyyMMdd");
	@Override
	public String formatValueForUpdate( PrimitiveType type, String strValue, Object objectValue ) throws ParseException {
		if( objectValue == null )
			return null;
		
		
		if( type == PrimitiveType.DateTime ) {
			Date dValue = ValueParserUtils.getDateValue(objectValue, null, null);
			
			return "to_date('" + format.format(dValue) + "','YYYYMMDD')";
		}
		else 
			return strValue;
	}
	
	@Override
	public boolean closeConnectionAfterQuery() {
		return true;
	}
}
