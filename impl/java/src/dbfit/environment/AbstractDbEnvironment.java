package dbfit.environment;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dbfit.util.BigDecimalParseDelegate;
import dbfit.util.DbConnectionProperties;
import dbfit.util.DbParameterAccessor;
import dbfit.util.Options;
import dbfit.util.SqlDateParseDelegate;
import dbfit.util.SqlTimestampParseDelegate;
import fit.TypeAdapter;

public abstract class AbstractDbEnvironment implements DBEnvironment {

		protected Connection currentConnection;
        protected abstract String getDriverClassName();
        private boolean driverRegistered=false;
        public AbstractDbEnvironment() {
    		TypeAdapter.registerParseDelegate(BigDecimal.class, BigDecimalParseDelegate.class);
    		TypeAdapter.registerParseDelegate(java.sql.Date.class, SqlDateParseDelegate.class);
    		TypeAdapter.registerParseDelegate(java.sql.Timestamp.class, SqlTimestampParseDelegate.class);
        }
        private void registerDriver() throws SQLException{
        	String driverName=getDriverClassName();
        	try {
        	if (driverRegistered) return;
        	DriverManager.registerDriver((Driver) Class.forName(driverName).newInstance());
        	driverRegistered=true;
        	}
        	catch (Exception e){
        		throw new Error ("Cannot register SQL driver "+driverName);
        	}
        }
        public void connect(String connectionString) throws SQLException
        {
        	registerDriver();
            currentConnection= DriverManager.getConnection(connectionString);
            currentConnection.setAutoCommit(false);
        }
        public void connect(String dataSource, String username, String password) throws SQLException
        {
        	registerDriver();
            currentConnection= DriverManager.getConnection(getConnectionString(dataSource),username, password);
            currentConnection.setAutoCommit(false);
        }
        public void connect(String dataSource, String username, String password, String database) throws SQLException
        {
        	//if (true) throw new Error("S");
        	registerDriver();
            currentConnection= DriverManager.getConnection(getConnectionString(dataSource, database),username, password);
            currentConnection.setAutoCommit(false);        
        }
        public void connectUsingFile(String file) throws SQLException, IOException, FileNotFoundException{
            DbConnectionProperties dbp = DbConnectionProperties.CreateFromFile(file);
            if (dbp.FullConnectionString != null) connect(dbp.FullConnectionString);
            else if (dbp.DbName != null) connect(dbp.Service, dbp.Username, dbp.Password, dbp.DbName);
            else connect(dbp.Service, dbp.Username, dbp.Password);	
        }
        /**
         * any processing required to turn a string into something jdbc driver can process, 
         * can be used to clean up CRLF, externalise parameters if required etc.  
         */
        protected String parseCommandText(String commandText){
        	commandText=commandText.replace("\n", " ");
        	commandText=commandText.replace("\r", " ");
        	return commandText;
        }
    	/* 
    	* from .Net, not needed since JDBC has a better interface
    	* protected static void AddInput(CallableStatement dbCommand, String name, Object value)
       	* can be directly invoked using
        * dbCommand.setObject(parameterName, x, targetSqlType)
		*/
        public final PreparedStatement createStatementWithBoundFixtureSymbols(String commandText) throws SQLException
        {
//            System.out.println("paramNames length "+paramNames.length);
            if (Options.isBindSymbols()){
            	PreparedStatement cs=currentConnection.prepareStatement(parseCommandText(commandText));
                String paramNames[]=extractParamNames(commandText);
	        	for (int i=0; i<paramNames.length; i++ )
	            {
	        		Object value=dbfit.util.SymbolUtil.getSymbol(paramNames[i]);
	        		cs.setObject(i+1, value);
	            }
	            return cs;
            }
            else{
            	// no parsing, return directly what is there and execute as native code
            	PreparedStatement cs=currentConnection.prepareStatement(commandText);
            	return cs;
            }
        }
        public void closeConnection() throws SQLException{
        	if (currentConnection!=null){
        		currentConnection.rollback();
        		currentConnection.close();
        	}
        }
        public void commit() throws SQLException
        {
        	currentConnection.commit();
            currentConnection.setAutoCommit(false);
        }
        public void rollback() throws SQLException
        {
        	checkConnectionValid(currentConnection);
        	currentConnection.rollback();
        }

		/*****/
        protected abstract String getConnectionString(String dataSource);
        protected abstract String getConnectionString(String dataSource, String database);
        
		 public Connection getConnection() {
			 return currentConnection;
		 }
		  public int getExceptionCode(SQLException dbException){
		    	return dbException.getErrorCode();
		  }
	        /** MUST RETURN PARAMETER NAMES IN EXACT ORDER AS IN STATEMENT. 
	         * IF SINGLE PARAMETER APPEARS MULTIPLE TIMES, MUST BE LISTED 
	         * MULTIPLE TIMES IN THE ARRAY ALSO
	         */
		  public String[] extractParamNames(String commandText)
		    {
				ArrayList<String> hs=new ArrayList<String>();
				Matcher mc =getParameterPattern().matcher(commandText);
				while (mc.find()){
					hs.add(mc.group(1));
				}    	
				String[] array = new String[hs.size()];
				return hs.toArray(array);
			}
		  protected abstract Pattern getParameterPattern();
		  /** 
		   * by default, this will support retrieving a single autogenerated key via JDBC. DB environments
		   * which support automated column retrieval after insert, like oracle, should override this and put
		   * in parameters for OUT accessors
		   */
		  public String buildInsertCommand(String tableName, DbParameterAccessor[] accessors)
		    {
		    	/*
		    	 * currently only supports retrieving the primary key column
		    	 * 
		    	 * maybe change later to implement:
		    	 * 
		    	 *  http://dev.mysql.com/doc/refman/5.0/en/comparison-operators.html
		    	 * 
		    	 * You can find the row that contains the most recent AUTO_INCREMENT value by issuing a statement of the following form immediately after generating the value:
		    	 * SELECT * FROM tbl_name WHERE auto_col IS NULL
		    	 * This behavior can be disabled by setting SQL_AUTO_IS_NULL=0. See Section 13.5.3, “SET Syntax.
		    	 */
		        StringBuilder sb = new StringBuilder("insert into ");
		        sb.append(tableName).append("(");
		        String comma = "";
		   
		        StringBuilder values = new StringBuilder();
		   
		        for (DbParameterAccessor accessor : accessors)
		        {
		            if (accessor.getDirection()==DbParameterAccessor.INPUT)
		            {
		                sb.append(comma);
		                values.append(comma);
		                sb.append(accessor.getName());
		                //values.append(":").append(accessor.getName());
		                values.append("?");
		                comma = ",";
		            }
		        }
		        sb.append(") values (");
		        sb.append(values);
		        sb.append(")");
		        return sb.toString();
		    }
		  	/**
		  	 * by default, this is set to false.
		  	 * @see dbfit.environment.DBEnvironment#supportsOuputOnInsert()
		  	 */
		    public boolean supportsOuputOnInsert(){
		    	return false;
		    }
		  	/**
		  	 * by default, this is set to false.
		  	 * @see dbfit.environment.DBEnvironment#supportsOuputOnInsertViaResultSet()
		  	 */
		    public boolean supportsOuputOnInsertViaResultSet(){
		    	return false;
		    }
		    
	        /** Check the validity of the supplied connection. */
	        public static void checkConnectionValid(final Connection conn) throws SQLException {
	        	if (conn == null || conn.isClosed()) {
	    			throw new IllegalArgumentException("No open connection to a database is available. "
	    					+ "Make sure your database is running and that you have connected before performing any queries.");
	    		}
			}

}