package databasereplication.implementation;

import com.mendix.replication.MendixReplicationException;
import com.mendix.replication.ReplicationSettings;
import com.mendix.systemwideinterfaces.core.IContext;
import databasereplication.interfaces.IDatabaseSettings;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Objects;

public class DBReplicationSettings extends ReplicationSettings {

	protected IDatabaseSettings dbSettings;

	protected String tableName;
	protected String constraint;
	private ZoneId mappingTimeZoneId;

	/**
	 * 
	 * @param context
	 * @param dbSettings
	 * @param objectType
	 * @throws MendixReplicationException
	 */
	public DBReplicationSettings(IContext context, IDatabaseSettings dbSettings, String objectType) throws MendixReplicationException {
		super(context, objectType, "DBReplication");
		this.constraint = null;
		this.dbSettings = dbSettings;
		mappingTimeZoneId = defaultMappingTimeZone();
	}



	public String getConstraint() {
		return this.constraint;
	}

	/**
	 * Add any additional constraints that have to be used in this query.
	 * There are no limitations to what is allowed as constraint. As long as the constraint is valid SQL
	 */
	public void setConstraint(String constraint) {
			this.constraint = constraint;
	}

	public IDatabaseSettings getDbSettings() {
		return this.dbSettings;
	}

	/**
	 * Updates mapping timezone to use in replication with first non-null argument
	 *
	 * @param timeZones the prioritized arguments of System.Timezone mendix objects
	 */
	public void setMappingTimeZoneWithPriority(system.proxies.TimeZone... timeZones) {
		Arrays.stream(timeZones)
				.filter(Objects::nonNull)
				.findFirst()
				.ifPresent(this::setMappingTimeZoneId);
	}

	/**
	 * Returns default mapping timezone to use in replication defined by JVM.
	 *
	 * @return default time zone id
	 */
	public static ZoneId defaultMappingTimeZone() {
		return java.time.ZoneId.systemDefault();
	}

	/**
	 * Returns mapping timezone to use in replication. Defaults to JVM timezone if not set.
	 *
	 * @return the timezone
	 */
	public ZoneId getMappingTimeZoneId() {
		return mappingTimeZoneId;
	}

	/**
	 * Updates mapping timezone to use in replication.
	 *
	 * @param timeZone the mendix object of type System.TimeZone.
	 * @throws IllegalArgumentException if timeZone is not of type System.Timezone or if its zone code is empty
	 */
	public void setMappingTimeZoneId(system.proxies.TimeZone timeZone) throws java.lang.IllegalArgumentException {
		if ( timeZone == null ) {
			return;
		}

		String zoneIdString = timeZone.getCode();
		if ( zoneIdString == null || zoneIdString.trim().isEmpty() ) {
			throw new java.lang.IllegalArgumentException("Code of timezone object is empty");
		}

		setMappingTimeZoneId(ZoneId.of(zoneIdString));
	}

	/**
	 * Updates mapping timezone to use in replication.
	 *
	 * @param zoneId the statistics level to set
	 */
	public void setMappingTimeZoneId(ZoneId zoneId) {
		if ( zoneId != null ) {
			mappingTimeZoneId = zoneId;
		}
	}


}
