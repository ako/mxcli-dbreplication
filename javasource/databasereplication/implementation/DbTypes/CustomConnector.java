package databasereplication.implementation.DbTypes;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.core.CoreRuntimeException;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixIdentifier;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import databasereplication.interfaces.IDatabaseSettings;
import databasereplication.proxies.CustomConnectionInfo;


public class CustomConnector extends IDatabaseConnector {

	protected CustomConnector( IDatabaseSettings settings ) {
		IMendixIdentifier dbConnectionId = null;
		try {
			dbConnectionId = settings.getCustomConnectionInfo();
		} catch ( CoreException e ) {
			throw new CoreRuntimeException(e);
		}
		if ( dbConnectionId != null ) {
			try {
				IContext context = settings.getContext();
				IMendixObject dbConnectionInfo = Core.retrieveId(context, dbConnectionId);
				this.driverClass = (String) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.DriverClass.toString());
				this.escapeOpen = (String) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.EscapeCharacterOpen.toString());
				this.escapeClose = (String) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.EscapeCharacterClose.toString());

				this.allowASToken = (Boolean) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.AllowsASToken.toString());
				this.connectionString = (String) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.ConnectionString.toString());
				this.escapeColumnAlias = (Boolean) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.EscapeColumnAlias.toString());
				this.escapeColumnNames = (Boolean) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.EscapeColumnNames.toString());
				this.escapeTableAlias = (Boolean) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.EscapeTableAlias.toString());
				this.escapeTableNames = (Boolean) dbConnectionInfo.getValue(context, CustomConnectionInfo.MemberNames.EscapeTableNames.toString());
				this.separatEscapeSchemaTable = (Boolean) dbConnectionInfo.getValue(context,CustomConnectionInfo.MemberNames.SeparateEscapeSchemaTableName.toString());
				this.closeConnectionAfterQuery = (Boolean) dbConnectionInfo.getValue(context,CustomConnectionInfo.MemberNames.CloseConnectionAfterQuery.toString());
			}
			catch( Exception e ) {
				throw new CoreRuntimeException(e);
			}
		}
		else
			throw new CoreRuntimeException("When type custom is selected there should be Custom connection information");
	}

	@Override
	public String getConnectionString() {
		if( this.connectionString == null )
			throw new CoreRuntimeException( "Connection string cannot be empty" );
		return this.connectionString.trim();
	}

	@Override
	public String getDriverClass() {
		return this.driverClass;
	}

	@Override
	public String getEscapeClose() {
		return this.escapeClose;
	}

	@Override
	public String getEscapeOpen() {
		return this.escapeOpen;
	}

	@Override
	public boolean shouldEscapeColumnNames() {
		return this.escapeColumnNames;
	}

	@Override
	public boolean shouldEscapeTableNames() {
		return this.escapeTableNames;
	}

	@Override
	public boolean shouldEscapeColumnAlias() {
		return this.escapeColumnAlias;
	}

	@Override
	public boolean shouldEscapeTableAlias() {
		return this.escapeTableAlias;
	}

	@Override
	public boolean allowASToken() {
		return this.allowASToken;
	}

	@Override
	public boolean separatEscapeSchemaTable() {
		return this.separatEscapeSchemaTable;
	}
	
	@Override
	public boolean closeConnectionAfterQuery() {
		return this.closeConnectionAfterQuery;
	}
}
