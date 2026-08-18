package databasereplication.interfaces;

import com.mendix.core.CoreException;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixIdentifier;
import databasereplication.implementation.DbTypes.IDatabaseConnector;

import java.util.ArrayList;
import java.util.List;

public abstract class IDatabaseSettings {
	/**
	 * @return The address where the database is located, this can either be an ip-address or any dns name
	 */
	public abstract String getAddress( );
	/**
	 * @return (Optional) If the database has an specific port return the portnumber. If no number should be used this function should return "" or null
	 */
	public abstract String getPort( );
	/**
	 * @return (Optional) The name of the service or instance
	 */
	public abstract String getServiceName( );
	/**
	 * @return The name of the database
	 */
	public abstract String getDatabaseName( );
	/**
	 * @return The name of the user that can access the database
	 */
	public abstract String getUserName( );
	/**
	 * @return The password for the user that is used to connect to the database
	 */
	public abstract String getPassword( );

	/**
	 * @return whether integrated authentication should be used
	 */
	public abstract boolean useIntegratedAuthentication( );

	public abstract IContext getContext( );

//	public abstract DatabaseConnectorType getDatabaseConnectionType();
	
	/**
	 * Retrieves the filters that are configured in the database settings.
	 * @return
	 * @throws CoreException 
	 */
	public abstract FilterList getTableFilters( ) throws CoreException;

	/**
	 * @return schema filters
	 * @throws CoreException
	 */
	public abstract FilterList getSchemaFilters() throws CoreException;
	
	public class FilterList extends ArrayList<String> {
		private static final long serialVersionUID = 1L;

		public FilterList() {
			super();
		}

		public FilterList(List<String> list) {
			super(list);
		}
		@Override
	    public boolean contains(Object o) {
	        String paramStr = (String)o;
	        for (String s : this) {
	            if (paramStr.equalsIgnoreCase(s)) return true;
	        }
	        return false;
	    }
	}

	public abstract IMendixIdentifier getCustomConnectionInfo() throws CoreException;
	public abstract IDatabaseConnector getDatabaseConnection();

	public abstract system.proxies.TimeZone getDatabaseTimezone() throws CoreException;

}
