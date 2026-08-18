package databasereplication.implementation;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.core.CoreRuntimeException;
import com.mendix.replication.MendixReplicationException;
import com.mendix.replication.MetaInfo;
import com.mendix.replication.ObjectStatistics;
import com.mendix.replication.ObjectStatistics.Stat;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.systemwideinterfaces.core.UserAction;
import databasereplication.proxies.ReplicationStatus;
import databasereplication.proxies.ReplicationStatusValues;

import java.util.HashMap;
import java.util.Map;

public abstract class IDataManager {

	private UserAction<?> currentAction;
	protected String replicationName;
	protected IMendixObject tableMapping;
	protected DBReplicationExecutionSettings settings;
	protected MetaInfo info;
	protected DBValueParser valueParser;
	protected RunningState state;

	protected enum RunningState {
			Running,
			AbortRollback,
			AbortCommit,
			Completed
		}

	protected static Map<String, IDataManager> _instances = new HashMap<String, IDataManager>();

	public static IDataManager getIntance(String mappingName) {
		return _instances.get(mappingName);
	}

	public static IDataManager instantiate(IMendixObject tableMapping, String replicationName, DBReplicationExecutionSettings settings) {
		if (settings.getDbSettings().getDatabaseConnection().getConnectionString().contains("jdbc:access")) {
			throw new CoreRuntimeException("Synchronization for MS Access is not implemented");
		}
		DatabaseDataManager dbDataManager = new DatabaseDataManager( tableMapping, replicationName, settings );

		_instances.put(replicationName, dbDataManager);
		return dbDataManager;
	}

	protected IDataManager(IMendixObject tableMapping, String replicationName, DBReplicationExecutionSettings settings) {
		this.tableMapping = tableMapping;
		this.replicationName = replicationName;
		this.settings = settings;
		this.state = RunningState.Running;
	}

	/**
	 * Start synchronizing the objects
	 *  First a connection will be made with the database based on the database type and connection information which was declared in the SynchronizerHandler
	 *  Execute the query and create or synchronize a MendixObject for each row in the result
	 *
	 * @param action this action can be used to return any feedback to the current user
	 * @param applyEntityAccess
	 *
	 * @throws CoreException
	 */
	public abstract IMendixObject startSynchronizing(UserAction<?> action, Boolean applyEntityAccess) throws CoreException;

	protected void prepareForSynchronization(UserAction<?> action, Boolean applyEntityAccess) throws MendixReplicationException {
		IContext context = this.settings.getContext();
    	if (this.settings.importInNewContext()) {
			context = context.getSession().createContext();
        }
		if (!applyEntityAccess) {
			context = context.createSudoClone();
		}
		this.settings.setContext(context);

		this.valueParser = new DBValueParser(this.settings);
		this.info = new MetaInfo( this.settings, this.valueParser, this.replicationName );
		this.info.getTimeMeasurement().startPerformanceTest("Over all");
		this.currentAction = action;
	}

	protected IMendixObject callFinishingMicroflow(ReplicationStatusValues status) throws MendixReplicationException {
		IMendixObject replStatus = null;
		try {
			if( this.settings.getFinishingMicroflowName() != null ) {
				final IContext originalContext = this.getSettings().getOriginalContext();
				HashMap<String, Object> params = new HashMap<String, Object>();
				if( this.settings.getFinishingMicroflowStatParamName() != null ) {
					replStatus = Core.instantiate(originalContext, ReplicationStatus.getType());
					if( this.info != null ) {
						ObjectStatistics stats = this.info.getObjectStats();
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.NrOfObjectsCreated.toString(), stats.getObjectStats( Stat.Created ) );
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.NrOfObjectsNotFound.toString(), stats.getObjectStats( Stat.NotFound) );
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.NrOfObjectsRemoved.toString(), stats.getObjectStats( Stat.Removed) );
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.NrOfObjectsSkipped.toString(), stats.getObjectStats( Stat.ObjectsSkipped) );
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.NrOfObjectsSynchronized.toString(), stats.getObjectStats( Stat.Synchronized) );
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.NewRemoveIndicatorValue.toString(), this.settings.getMainObjectConfig().getNewRemoveIndicatorValue());
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.PreviousRemoveIndicatorValue.toString(), this.settings.getMainObjectConfig().getCurrentRemoveIndicatorValue());
					}
					replStatus.setValue(originalContext, ReplicationStatus.MemberNames.ReplicationStatus.toString(), status.toString());

					if( this.tableMapping != null )
						replStatus.setValue(originalContext, ReplicationStatus.MemberNames.ReplicationStatus_TableMapping.toString(), this.tableMapping.getId());

					params.put(this.settings.getFinishingMicroflowStatParamName(), replStatus);
				}
				if( this.settings.getFinishingMicroflowTMParamName() != null ) {
					params.put(this.settings.getFinishingMicroflowTMParamName(), this.tableMapping);
				}

				Core.microflowCall(this.settings.getFinishingMicroflowName()).withParams(params).execute(originalContext);
			}
		}
		catch (Exception e) {
			throw new MendixReplicationException("Unable to execute finishing microflow: " + this.settings.getFinishingMicroflowName() + " because of the following exception: ", e).addVersion();
		}

		return replStatus;
	}

	public DBReplicationExecutionSettings getSettings() {
		return this.settings;
	}

	public UserAction<?> getCurrentAction() {
		return this.currentAction;
	}

	public String getSynchronizerName() {
		return this.replicationName;
	}

	public void abortAndCommit() {
		this.state = RunningState.AbortCommit;
	}

	public void abortAndRollback() {
		this.state = RunningState.AbortRollback;
	}

	public Integer getRemoveIndicatorValue() {
		if( this.info == null )
			return null;

		return this.settings.getMainObjectConfig().getNewRemoveIndicatorValue();
	}

	public void removeUnchangedObjectsByQuery(String xPath) throws MendixReplicationException {
		this.info.removeUnchangedObjectsByQuery( xPath );
	}

	public void abortImport(boolean abortWithCommit) {
		if( abortWithCommit )
			this.state = RunningState.AbortCommit;
		else
			this.state = RunningState.AbortRollback;
	}

}