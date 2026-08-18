package databasereplication.implementation;

import com.mendix.replication.AbstractValueExtractor;
import com.mendix.replication.ICustomValueParser;
import com.mendix.replication.ParseException;
import com.mendix.systemwideinterfaces.core.meta.IMetaPrimitive.PrimitiveType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Calendar;
import java.util.TimeZone;

public class DBValueParser extends AbstractValueExtractor {

	DBReplicationSettings settings;

	public DBValueParser(DBReplicationSettings settings) {
		super(settings);
		this.settings = settings;
	}

	@Override
	protected DBReplicationSettings getSettings() {
		return settings;
	}

	@Override
	public Object getValue( PrimitiveType type, String columnAlias, Object value ) throws ParseException {
		if ( value instanceof ResultSet ) {
			try {
				return super.getValue(type, columnAlias, readDateTimeColumnsWithTimezone((ResultSet) value, columnAlias));
			}
			catch( SQLException e ) {
				throw new ParseException("Could not get the value for column: " + columnAlias, e);
			}
		}
		
		return super.getValue(type, columnAlias, value);
	}

	public Object getValueFromDataSet( String keyAlias, PrimitiveType type, Object dataSet ) throws ParseException {
		try {
			return readDateTimeColumnsWithTimezone((ResultSet) dataSet, keyAlias);
		}
		catch( SQLException e ) {
			throw new ParseException("Unable to find field: " + keyAlias + " in the resultSet", e);
		}
	}

	@Override
	public String getKeyValueFromAlias(Object recordDataSet, String keyAlias) throws ParseException {
		String keyValue;
		
		if ( this.customValueParsers.containsKey(keyAlias) ) {
			ICustomValueParser vp = this.customValueParsers.get(keyAlias);
			Object value = vp.parseValue(getValueFromDataSet(keyAlias, PrimitiveType.String, recordDataSet));

			keyValue = getTrimmedValue(value, keyAlias);
		}
		else
			keyValue = getKeyValueByPrimitiveType(this.settings.getMemberType(keyAlias), keyAlias,
					getValueFromDataSet(keyAlias, this.settings.getMemberType(keyAlias), recordDataSet));
		
		return keyValue;
	}

	private Object readDateTimeColumnsWithTimezone(ResultSet resultSet, String column) throws SQLException {
		Calendar calendarTimezone = Calendar.getInstance(TimeZone.getTimeZone(this.settings.getMappingTimeZoneId()));
		switch ( resultSet.getMetaData().getColumnType(resultSet.findColumn(column)) ) {
			case Types.TIMESTAMP:
				return resultSet.getTimestamp(column, calendarTimezone);
			case Types.DATE:
				return resultSet.getDate(column, calendarTimezone);
			case Types.TIME:
				return resultSet.getTime(column, calendarTimezone);
			default:
				return resultSet.getObject(column);
		}

	}
}
