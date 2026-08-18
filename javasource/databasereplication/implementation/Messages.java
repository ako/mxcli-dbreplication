package databasereplication.implementation;

public enum Messages {
	COULD_NOT_CONNECT_CLOUD_SECURITY("Could not connect with the database, check if emulate cloud security is turned off."),
	COULD_NOT_CONNECT_WITH_DB("Could not connect with the database at location: "),
	UNKNOWN_DATABASE_TYPE("Unkown database type: %s"),
	COULD_NOT_LOCATE_DB_DRIVER("Could not locatie the driver for the current database type, the type is: "),
	COULD_NOT_EXECUTE_QUERY("Could not execute query on the external database, the message is: %s, the executed query is: %s"),
	ERROR_READING_RESULTSET("Could not get the result from the query"),
	UNKNOWN_COLUMN("The column: %s could not be located in the query even though it was defined.");

	private final String message;

	Messages(String msg) {
		this.message = msg;
	}

	public String getMessage(Object... texts) {
		return String.format(this.message, texts);
	}

	public String getMessage() {
		return this.message;
	}
}
