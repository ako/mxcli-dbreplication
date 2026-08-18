package databasereplication.interfaces;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.mendix.replication.ICustomValueParser;
import com.mendix.replication.ParseException;

public abstract class IValueParser implements ICustomValueParser {

	@Deprecated
	public Object parseValue(String columnAlias, ResultSet rs) throws ParseException {
		try {
			return parseValue( rs.getObject(columnAlias) );
        }
        catch (SQLException e) {
        	throw new ParseException(e);
        }
	}
	
}
