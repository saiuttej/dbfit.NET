package dbfit.environment;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import javax.sql.RowSet;

import dbfit.util.DbParameterAccessor;
import dbfit.util.NameNormaliser;

import java.io.*;

public class PostgresEnvironment extends AbstractDbEnvironment {
  private static HashMap<String, Integer> errorCodeHashMap = new HashMap<String, Integer>();
  
  public PostgresEnvironment() {
    populateErrorCodeHashMap();
  }
  
  public boolean supportsOuputOnInsert(){
    	return true;
  }
  public boolean supportsOuputOnInsertViaResultSet(){
    return true;
  }
	protected String getConnectionString(String dataSource) {
		return "jdbc:postgresql://"+dataSource;
	}

	protected String getConnectionString(String dataSource, String database) {
		return "jdbc:postgresql://"+dataSource+"/"+database;
	}

	private static String paramNamePattern="_:([A-Za-z0-9_]+)";
	private static Pattern paramsNames = Pattern.compile(paramNamePattern);
	public Pattern getParameterPattern(){
		return paramsNames;
	}
  
	// postgres jdbc driver does not support named parameters - so just map them
	// to standard jdbc question marks
  protected String parseCommandText(String commandText){
    commandText=commandText.replaceAll(paramNamePattern, "?");
    return super.parseCommandText(commandText);
  }
  
  protected String getDriverClassName() {
		return "org.postgresql.Driver";
	}
  public Map<String, DbParameterAccessor> getAllColumns(String tableOrViewName) throws SQLException
  {
		String[] qualifiers = NameNormaliser.normaliseName(tableOrViewName).split("\\.");
		String qry = " select column_name, data_type, character_maximum_length "+
		"	as direction from information_schema.columns where ";
    
		if (qualifiers.length == 2) {
			qry += " lower(table_schema)=? and lower(table_name)=? ";
		} else {
			qry += " (table_schema=current_schema() and lower(table_name)=?)";
		}
		qry+=" order by ordinal_position";
		return readIntoParams(qualifiers, qry);
	}
	private Map<String, DbParameterAccessor> readIntoParams(String[] queryParameters, String query) 
		throws SQLException{
		PreparedStatement dc=currentConnection.prepareStatement(query);
		for (int i = 0; i < queryParameters.length; i++) {
			dc.setString(i+1,NameNormaliser.normaliseName(queryParameters[i]));
		}
		ResultSet rs=dc.executeQuery();		
		Map<String, DbParameterAccessor>
			allParams = new HashMap<String, DbParameterAccessor>();
		int position=0;
		while (rs.next()) {
			String paramName=rs.getString(1);			
			if (paramName==null) paramName="";
			String dataType = rs.getString(2);
			DbParameterAccessor dbp=new DbParameterAccessor (paramName,DbParameterAccessor.INPUT,
					getSqlType(dataType), getJavaClass(dataType), position++);
			allParams.put(NameNormaliser.normaliseName(paramName),
				dbp);
		}
		rs.close();
		return allParams;
	}
	// List interface has sequential search, so using list instead of array to map types
	private static List<String> stringTypes = Arrays.asList(new String[] { "VARCHAR", "CHAR", "CHARACTER", "CHARACTER VARYING", "TEXT", "NAME", "XML", "BPCHAR" });
	private static List<String> intTypes = Arrays.asList(new String[] { "SMALLINT", "INT", "INT4", "INT2", "INTEGER", "SERIAL"});
	private static List<String> longTypes = Arrays.asList(new String[] { "BIGINT", "BIGSERIAL", "INT8"});
	private static List<String> floatTypes = Arrays.asList(new String[] { "REAL", "FLOAT4"});
	private static List<String> doubleTypes=Arrays.asList(new String[]{"DOUBLE PRECISION", "FLOAT8", "FLOAT"});	
	private static List<String> decimalTypes = Arrays.asList(new String[] { "DECIMAL", "NUMERIC" });
	private static List<String> dateTypes = Arrays.asList(new String[] { "DATE"});
	private static List<String> timestampTypes=Arrays.asList(new String[]{"TIMESTAMP", "TIMESTAMP WITHOUT TIME ZONE", "TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ"});
	private static List<String> refCursorTypes = Arrays.asList(new String[] { "REFCURSOR" });

	private static String normaliseTypeName(String dataType) {
		dataType = dataType.toUpperCase().trim();
		return dataType;
	}
	private static int getSqlType(String dataType) {
		//todo:strip everything from first blank
		dataType = normaliseTypeName(dataType);

		if (stringTypes.contains(dataType) ) return java.sql.Types.VARCHAR;
		if (decimalTypes.contains(dataType) )return java.sql.Types.NUMERIC;
		if (intTypes.contains(dataType) )return java.sql.Types.INTEGER;
		if (floatTypes.contains(dataType) )return java.sql.Types.FLOAT;
		if (doubleTypes.contains(dataType) )return java.sql.Types.DOUBLE;
		if (longTypes.contains(dataType) )return java.sql.Types.BIGINT;
		if (timestampTypes.contains(dataType)) return java.sql.Types.TIMESTAMP;
		if (dateTypes.contains(dataType) ) return java.sql.Types.DATE;
		if (refCursorTypes.contains(dataType) ) return java.sql.Types.REF;
		throw new UnsupportedOperationException("Type " + dataType + " is not supported");
	}
	public Class getJavaClass(String dataType) {
		dataType = normaliseTypeName(dataType);
		if (stringTypes.contains(dataType) ) return String.class;
		if (decimalTypes.contains(dataType) )return BigDecimal.class;
		if (intTypes.contains(dataType) )return Integer.class;
		if (floatTypes.contains(dataType) )return Float.class;
		if (dateTypes.contains(dataType) ) return java.sql.Date.class;
		if (refCursorTypes.contains(dataType) ) return RowSet.class;
		if (doubleTypes.contains(dataType) )return Double.class;
		if (longTypes.contains(dataType) )return Long.class;
		if (timestampTypes.contains(dataType)) return java.sql.Timestamp.class;
		throw new UnsupportedOperationException("Type " + dataType + " is not supported");
	}

	public Map<String, DbParameterAccessor> getAllProcedureParameters(
			String procName) throws SQLException {

		String[] qualifiers = NameNormaliser.normaliseName(procName).split("\\.");
		String qry = "select 'FUNCTION' as type, " +
                 "array_to_string(array(select coalesce(pro.proargnames[t.id+1],'') || ' ' || pt.typname from generate_series(0, array_upper(pro.proargtypes, 1)) as t(id), pg_type pt where pt.oid = pro.proargtypes[t.id] order by t.id), ',') as param_list, " +
                 "(select typname from pg_type pt where pt.oid = pro.prorettype) as returns " +
                 "from pg_proc pro, pg_namespace ns where ns.oid = pro.pronamespace";
		if (qualifiers.length == 2) {
			qry += " and lower(ns.nspname)=? and lower(proname)=? ";
		} else {
			qry += " and (lower(ns.nspname)=current_schema() and lower(proname)=?)";
		}
		
		PreparedStatement dc=currentConnection.prepareStatement(qry);
		for (int i = 0; i < qualifiers.length; i++) {
			dc.setString(i+1,NameNormaliser.normaliseName(qualifiers[i]));
		}
		ResultSet rs=dc.executeQuery();		
		if (!rs.next()) {
			throw new SQLException("Unknown procedure "   +procName );
		}
		Map<String, DbParameterAccessor>
			allParams = new HashMap<String, DbParameterAccessor>();
		String type=rs.getString(1);
		String paramList=rs.getString(2);
		String returns=rs.getString(3);
		rs.close();
    
		int position=0;
	  int direction=DbParameterAccessor.INPUT;
    String paramName;
    String dataType;
    String token;
      
		for (String param: paramList.split(",")) {
      StringTokenizer s=new StringTokenizer(param.trim().toLowerCase()," ()");
      
      token =s.nextToken();
      
      if (token.equals("in")) {
          token=s.nextToken();
      }
      else if (token.equals("inout")) {
        direction=DbParameterAccessor.INPUT_OUTPUT;
        token=s.nextToken();
      } 			
      else if (token.equals("out")){
        direction=DbParameterAccessor.OUTPUT;
        token=s.nextToken();
      }
      
      if (s.hasMoreTokens()) {
        paramName=token;
        dataType=s.nextToken();
      }
      else {
        paramName="$" + (position + 1);
        dataType=token;
      }
			
			DbParameterAccessor dbp=new DbParameterAccessor (paramName,direction,
				getSqlType(dataType), getJavaClass(dataType), position++);			
			allParams.put(NameNormaliser.normaliseName(paramName), dbp);
		}
    
		if ("FUNCTION".equals(type)){
			StringTokenizer s=new StringTokenizer(returns.trim().toLowerCase()," ()");
			dataType=s.nextToken();
      
      if (!dataType.equals("void")) {
  			allParams.put("",
					new DbParameterAccessor ("",DbParameterAccessor.RETURN_VALUE,
							getSqlType(dataType), getJavaClass(dataType), -1));
      }
		}
    
		return allParams;		
	}

  public String buildInsertCommand(String tableName, DbParameterAccessor[] accessors)
  {
    /* oracle jdbc interface with callablestatement has problems with returning into...
     * http://forums.oracle.com/forums/thread.jspa?threadID=438204&tstart=0&messageID=1702717
     * so begin/end block has to be built around it
     */
      StringBuilder sb = new StringBuilder("insert into ");
      sb.append(tableName).append("(");
      String comma = "";
      String retComma = "";

      StringBuilder values = new StringBuilder();
      StringBuilder retNames = new StringBuilder();
      StringBuilder retValues = new StringBuilder();

      for (DbParameterAccessor accessor : accessors)
      {
          if (accessor.getDirection()==DbParameterAccessor.INPUT)
          {
              sb.append(comma);
              values.append(comma);
              sb.append(accessor.getName());
              values.append("?");
              comma = ",";
          }
          else
          {
              retNames.append(retComma);
              retValues.append(retComma);
              retNames.append(accessor.getName());
              retValues.append("?");
              retComma = ",";
          }
      }
      sb.append(") values (");
      sb.append(values);
      sb.append(")");
      if (retValues.length() > 0)
      {
          //sb.append(" returning ").append(retNames).append(" into ").append(retValues);
          sb.append(" returning ").append(retNames);
      }
      return sb.toString();
  }

  private void populateErrorCodeHashMap() {
    //Class 00 � Successful Completion
    errorCodeHashMap.put("00000", new Integer(0)); // SUCCESSFUL COMPLETION successful_completion
    
    //Class 01 � Warning
    errorCodeHashMap.put("01000", new Integer(100)); // WARNING
    errorCodeHashMap.put("0100C", new Integer(101)); // DYNAMIC RESULT SETS RETURNED
    errorCodeHashMap.put("01008", new Integer(102)); // IMPLICIT ZERO BIT PADDING
    errorCodeHashMap.put("01003", new Integer(103)); // NULL VALUE ELIMINATED IN SET FUNCTION
    errorCodeHashMap.put("01007", new Integer(104)); // PRIVILEGE NOT GRANTED
    errorCodeHashMap.put("01006", new Integer(105)); // PRIVILEGE NOT REVOKED
    errorCodeHashMap.put("01004", new Integer(106)); // STRING DATA RIGHT TRUNCATION
    errorCodeHashMap.put("01P01", new Integer(107)); // DEPRECATED FEATURE                                                         
    
    //Class 02 � No Data (this is also a warning class per the SQL standard)
    errorCodeHashMap.put("02000", new Integer(200)); // NO DATA
    errorCodeHashMap.put("02001", new Integer(201)); // NO ADDITIONAL DYNAMIC RESULT SETS RETURNED
    
    //Class 03 � SQL Statement Not Yet Complete
    errorCodeHashMap.put("03000", new Integer(300)); // SQL STATEMENT NOT YET COMPLETE
    
    //Class 08 � Connection Exception
    errorCodeHashMap.put("08000", new Integer(800)); // CONNECTION EXCEPTION
    errorCodeHashMap.put("08003", new Integer(801)); // CONNECTION DOES NOT EXIST
    errorCodeHashMap.put("08006", new Integer(802)); // CONNECTION FAILURE 
    errorCodeHashMap.put("08001", new Integer(803)); // SQLCLIENT UNABLE TO ESTABLISH SQLCONNECTION
    errorCodeHashMap.put("08004", new Integer(804)); // SQLSERVER REJECTED ESTABLISHMENT OF SQLCONNECTION
    errorCodeHashMap.put("08007", new Integer(805)); // TRANSACTION RESOLUTION UNKNOWN
    errorCodeHashMap.put("08P01", new Integer(806)); // PROTOCOL VIOLATION
    
    //Class 09 � Triggered Action Exception
    errorCodeHashMap.put("09000", new Integer(900)); // TRIGGERED ACTION EXCEPTION
    
    //Class 0A � Feature Not Supported
    errorCodeHashMap.put("0A000", new Integer(1000)); // FEATURE NOT SUPPORTED
    
    //Class 0B � Invalid Transaction Initiation
    errorCodeHashMap.put("0B000", new Integer(1100)); // INVALID TRANSACTION INITIATION
                 
    //Class 0F � Locator Exception
    errorCodeHashMap.put("0F000", new Integer(1200)); // LOCATOR EXCEPTIONlocator_exception
    errorCodeHashMap.put("0F001", new Integer(1201)); // INVALID LOCATOR SPECIFICATION
    
    //Class 0L � Invalid Grantor
    errorCodeHashMap.put("0L000", new Integer(1300)); // INVALID GRANTORinvalid_grantor
    errorCodeHashMap.put("0LP01", new Integer(1301)); // INVALID GRANT OPERATION
    
    //abcdefghijklmnopqrstuvwxyz
    //01234567890123456789012345
    //Class 0P � Invalid Role Specification
    errorCodeHashMap.put("0P000", new Integer(1400)); // INVALID ROLE SPECIFICATION
    
    //Class 21 � Cardinality Violation
    errorCodeHashMap.put("21000", new Integer(2100)); // CARDINALITY VIOLATION
    
    //Class 22 � Data Exception
    errorCodeHashMap.put("22000", new Integer(2200)); // DATA EXCEPTION
    errorCodeHashMap.put("2202E", new Integer(2201)); // ARRAY SUBSCRIPT ERROR
    errorCodeHashMap.put("22021", new Integer(2202)); // CHARACTER NOT IN REPERTOIRE
    errorCodeHashMap.put("22008", new Integer(2203)); // DATETIME FIELD OVERFLOW
    errorCodeHashMap.put("22012", new Integer(2204)); // DIVISION BY ZERO
    errorCodeHashMap.put("22005", new Integer(2205)); // ERROR IN ASSIGNMENT
    errorCodeHashMap.put("2200B", new Integer(2206)); // ESCAPE CHARACTER CONFLICT
    errorCodeHashMap.put("22022", new Integer(2207)); // INDICATOR OVERFLOW
    errorCodeHashMap.put("22015", new Integer(2208)); // INTERVAL FIELD OVERFLOW
    errorCodeHashMap.put("2201E", new Integer(2209)); // INVALID ARGUMENT FOR LOGARITHM
    errorCodeHashMap.put("2201F", new Integer(2210)); // INVALID ARGUMENT FOR POWER FUNCTION
    errorCodeHashMap.put("2201G", new Integer(2211)); // INVALID ARGUMENT FOR WIDTH BUCKET FUNCTION
    errorCodeHashMap.put("22018", new Integer(2212)); // INVALID CHARACTER VALUE FOR CAST
    errorCodeHashMap.put("22007", new Integer(2213)); // INVALID DATETIME FORMAT
    errorCodeHashMap.put("22019", new Integer(2214)); // INVALID ESCAPE CHARACTER
    errorCodeHashMap.put("2200D", new Integer(2215)); // INVALID ESCAPE OCTET
    errorCodeHashMap.put("22025", new Integer(2216)); // INVALID ESCAPE SEQUENCE
    errorCodeHashMap.put("22P06", new Integer(2217)); // NONSTANDARD USE OF ESCAPE CHARACTER
    errorCodeHashMap.put("22010", new Integer(2218)); // INVALID INDICATOR PARAMETER VALUE
    errorCodeHashMap.put("22020", new Integer(2219)); // INVALID LIMIT VALUE
    errorCodeHashMap.put("22023", new Integer(2220)); // INVALID PARAMETER VALUE
    errorCodeHashMap.put("2201B", new Integer(2221)); // INVALID REGULAR EXPRESSION
    errorCodeHashMap.put("22009", new Integer(2222)); // INVALID TIME ZONE DISPLACEMENT VALUE
    errorCodeHashMap.put("2200C", new Integer(2223)); // INVALID USE OF ESCAPE CHARACTER
    errorCodeHashMap.put("2200G", new Integer(2224)); // MOST SPECIFIC TYPE MISMATCH
    errorCodeHashMap.put("22004", new Integer(2225)); // NULL VALUE NOT ALLOWED
    errorCodeHashMap.put("22002", new Integer(2226)); // NULL VALUE NO INDICATOR PARAMETER
    errorCodeHashMap.put("22003", new Integer(2227)); // NUMERIC VALUE OUT OF RANGE
    errorCodeHashMap.put("22026", new Integer(2228)); // STRING DATA LENGTH MISMATCH
    errorCodeHashMap.put("22001", new Integer(2229)); // STRING DATA RIGHT TRUNCATION
    errorCodeHashMap.put("22011", new Integer(2230)); // SUBSTRING ERROR
    errorCodeHashMap.put("22027", new Integer(2231)); // TRIM ERROR
    errorCodeHashMap.put("22024", new Integer(2232)); // UNTERMINATED C STRING
    errorCodeHashMap.put("2200F", new Integer(2233)); // ZERO LENGTH CHARACTER STRING
    errorCodeHashMap.put("22P01", new Integer(2234)); // FLOATING POINT EXCEPTION
    errorCodeHashMap.put("22P02", new Integer(2235)); // INVALID TEXT REPRESENTATION
    errorCodeHashMap.put("22P03", new Integer(2236)); // INVALID BINARY REPRESENTATION
    errorCodeHashMap.put("22P04", new Integer(2237)); // BAD COPY FILE FORMAT
    errorCodeHashMap.put("22P05", new Integer(2238)); // UNTRANSLATABLE CHARACTER
    errorCodeHashMap.put("2200L", new Integer(2239)); // NOT AN XML DOCUMENT
    errorCodeHashMap.put("2200M", new Integer(2240)); // INVALID XML DOCUMENT
    errorCodeHashMap.put("2200N", new Integer(2241)); // INVALID XML CONTENT
    errorCodeHashMap.put("2200S", new Integer(2242)); // INVALID XML COMMENT
    errorCodeHashMap.put("2200T", new Integer(2243)); // INVALID XML PROCESSING INSTRUCTION
    
    //Class 23 � Integrity Constraint Violation
    errorCodeHashMap.put("23000", new Integer(2300)); // INTEGRITY CONSTRAINT VIOLATION
    errorCodeHashMap.put("23001", new Integer(2301)); // RESTRICT VIOLATION
    errorCodeHashMap.put("23502", new Integer(2302)); // NOT NULL VIOLATION
    errorCodeHashMap.put("23503", new Integer(2303)); // FOREIGN KEY VIOLATION
    errorCodeHashMap.put("23505", new Integer(2304)); // UNIQUE VIOLATION
    errorCodeHashMap.put("23514", new Integer(2305)); // CHECK VIOLATION
    
    //Class 24 � Invalid Cursor State
    errorCodeHashMap.put("24000", new Integer(2400)); // INVALID CURSOR STATE
    
    //Class 25 � Invalid Transaction State
    errorCodeHashMap.put("25000", new Integer(2200)); // INVALID TRANSACTION STATE
    errorCodeHashMap.put("25001", new Integer(2201)); // ACTIVE SQL TRANSACTION
    errorCodeHashMap.put("25002", new Integer(2202)); // BRANCH TRANSACTION ALREADY ACTIVE
    errorCodeHashMap.put("25008", new Integer(2203)); // HELD CURSOR REQUIRES SAME ISOLATION LEVEL
    errorCodeHashMap.put("25003", new Integer(2204)); // INAPPROPRIATE ACCESS MODE FOR BRANCH TRANSACTION
    errorCodeHashMap.put("25004", new Integer(2205)); // INAPPROPRIATE ISOLATION LEVEL FOR BRANCH TRANSACTION
    errorCodeHashMap.put("25005", new Integer(2206)); // NO ACTIVE SQL TRANSACTION FOR BRANCH TRANSACTION
    errorCodeHashMap.put("25006", new Integer(2207)); // READ ONLY SQL TRANSACTION
    errorCodeHashMap.put("25007", new Integer(2208)); // SCHEMA AND DATA STATEMENT MIXING NOT SUPPORTED
    errorCodeHashMap.put("25P01", new Integer(2209)); // NO ACTIVE SQL TRANSACTION
    errorCodeHashMap.put("25P02", new Integer(2210)); // IN FAILED SQL TRANSACTION
    
    //Class 26 � Invalid SQL Statement Name
    errorCodeHashMap.put("26000", new Integer(2600)); // INVALID SQL STATEMENT NAME
    
    //Class 27 � Triggered Data Change Violation
    errorCodeHashMap.put("27000", new Integer(2700)); // TRIGGERED DATA CHANGE VIOLATION
    
    //Class 28 � Invalid Authorization Specification
    errorCodeHashMap.put("28000", new Integer(2800)); // INVALID AUTHORIZATION SPECIFICATION
    
    //Class 2B � Dependent Privilege Descriptors Still Exist
    errorCodeHashMap.put("2B000", new Integer(2900)); // DEPENDENT PRIVILEGE DESCRIPTORS STILL EXIST
    errorCodeHashMap.put("2BP01", new Integer(2901)); // DEPENDENT OBJECTS STILL EXIST
    
    //Class 2D � Invalid Transaction Termination
    errorCodeHashMap.put("2D000", new Integer(3000)); // INVALID TRANSACTION TERMINATION
    
    //Class 2F � SQL Routine Exception
    errorCodeHashMap.put("2F000", new Integer(3100)); // SQL ROUTINE EXCEPTION
    errorCodeHashMap.put("2F005", new Integer(3101)); // FUNCTION EXECUTED NO RETURN STATEMENT
    errorCodeHashMap.put("2F002", new Integer(3102)); // MODIFYING SQL DATA NOT PERMITTED
    errorCodeHashMap.put("2F003", new Integer(3103)); // PROHIBITED SQL STATEMENT ATTEMPTED
    errorCodeHashMap.put("2F004", new Integer(3104)); // READING SQL DATA NOT PERMITTED
    
    //Class 34 � Invalid Cursor Name
    errorCodeHashMap.put("34000", new Integer(3400)); // INVALID CURSOR NAME
    
    //Class 38 � External Routine Exception
    errorCodeHashMap.put("38000", new Integer(3800)); // EXTERNAL ROUTINE EXCEPTION
    errorCodeHashMap.put("38001", new Integer(3801)); // CONTAINING SQL NOT PERMITTED
    errorCodeHashMap.put("38002", new Integer(3802)); // MODIFYING SQL DATA NOT PERMITTED
    errorCodeHashMap.put("38003", new Integer(3803)); // PROHIBITED SQL STATEMENT ATTEMPTED
    errorCodeHashMap.put("38004", new Integer(3804)); // READING SQL DATA NOT PERMITTED
    
    //Class 39 � External Routine Invocation Exception
    errorCodeHashMap.put("39000", new Integer(3900)); // EXTERNAL ROUTINE INVOCATION EXCEPTION
    errorCodeHashMap.put("39001", new Integer(3901)); // INVALID SQLSTATE RETURNED
    errorCodeHashMap.put("39004", new Integer(3902)); // NULL VALUE NOT ALLOWED
    errorCodeHashMap.put("39P01", new Integer(3903)); // TRIGGER PROTOCOL VIOLATED
    errorCodeHashMap.put("39P02", new Integer(3904)); // SRF PROTOCOL VIOLATED
    
    //Class 3B � Savepoint Exception
    errorCodeHashMap.put("3B000", new Integer(3950)); // SAVEPOINT EXCEPTION
    errorCodeHashMap.put("3B001", new Integer(3951)); // INVALID SAVEPOINT SPECIFICATION
    
    //Class 3D � Invalid Catalog Name
    errorCodeHashMap.put("3D000", new Integer(3960)); // INVALID CATALOG NAME
    
    //Class 3F � Invalid Schema Name
    errorCodeHashMap.put("3F000", new Integer(3970)); // INVALID SCHEMA NAME
    
    //Class 40 � Transaction Rollback
    errorCodeHashMap.put("40000", new Integer(4000)); // TRANSACTION ROLLBACK
    errorCodeHashMap.put("40002", new Integer(4001)); // TRANSACTION INTEGRITY CONSTRAINT VIOLATION
    errorCodeHashMap.put("40001", new Integer(4002)); // SERIALIZATION FAILURE
    errorCodeHashMap.put("40003", new Integer(4003)); // STATEMENT COMPLETION UNKNOWN
    errorCodeHashMap.put("40P01", new Integer(4004)); // DEADLOCK DETECTED
    
    //Class 42 � Syntax Error or Access Rule Violation
    errorCodeHashMap.put("42000", new Integer(4200)); // SYNTAX ERROR OR ACCESS RULE VIOLATION
    errorCodeHashMap.put("42601", new Integer(4201)); // SYNTAX ERROR
    errorCodeHashMap.put("42501", new Integer(4202)); // INSUFFICIENT PRIVILEGE
    errorCodeHashMap.put("42846", new Integer(4203)); // CANNOT COERCE
    errorCodeHashMap.put("42803", new Integer(4204)); // GROUPING ERROR
    errorCodeHashMap.put("42830", new Integer(4205)); // INVALID FOREIGN KEY
    errorCodeHashMap.put("42602", new Integer(4206)); // INVALID NAME
    errorCodeHashMap.put("42622", new Integer(4207)); // NAME TOO LONG
    errorCodeHashMap.put("42939", new Integer(4208)); // RESERVED NAME
    errorCodeHashMap.put("42804", new Integer(4209)); // DATATYPE MISMATCH
    errorCodeHashMap.put("42P18", new Integer(4210)); // INDETERMINATE DATATYPE
    errorCodeHashMap.put("42809", new Integer(4211)); // WRONG OBJECT TYPE
    errorCodeHashMap.put("42703", new Integer(4212)); // UNDEFINED COLUMN
    errorCodeHashMap.put("42883", new Integer(4213)); // UNDEFINED FUNCTION
    errorCodeHashMap.put("42P01", new Integer(4214)); // UNDEFINED TABLE
    errorCodeHashMap.put("42P02", new Integer(4215)); // UNDEFINED PARAMETER
    errorCodeHashMap.put("42704", new Integer(4216)); // UNDEFINED OBJECT
    errorCodeHashMap.put("42701", new Integer(4217)); // DUPLICATE COLUMN
    errorCodeHashMap.put("42P03", new Integer(4218)); // DUPLICATE CURSOR
    errorCodeHashMap.put("42P04", new Integer(4219)); // DUPLICATE DATABASE
    errorCodeHashMap.put("42723", new Integer(4220)); // DUPLICATE FUNCTION
    errorCodeHashMap.put("42P05", new Integer(4221)); // DUPLICATE PREPARED STATEMENT
    errorCodeHashMap.put("42P06", new Integer(4222)); // DUPLICATE SCHEMA
    errorCodeHashMap.put("42P07", new Integer(4223)); // DUPLICATE TABLE
    errorCodeHashMap.put("42712", new Integer(4224)); // DUPLICATE ALIAS
    errorCodeHashMap.put("42710", new Integer(4225)); // DUPLICATE OBJECT
    errorCodeHashMap.put("42702", new Integer(4226)); // AMBIGUOUS COLUMN
    errorCodeHashMap.put("42725", new Integer(4227)); // AMBIGUOUS FUNCTION
    errorCodeHashMap.put("42P08", new Integer(4228)); // AMBIGUOUS PARAMETER
    errorCodeHashMap.put("42P09", new Integer(4229)); // AMBIGUOUS ALIAS
    errorCodeHashMap.put("42P10", new Integer(4230)); // INVALID COLUMN REFERENCE
    errorCodeHashMap.put("42611", new Integer(4231)); // INVALID COLUMN DEFINITION
    errorCodeHashMap.put("42P11", new Integer(4232)); // INVALID CURSOR DEFINITION
    errorCodeHashMap.put("42P12", new Integer(4233)); // INVALID DATABASE DEFINITION
    errorCodeHashMap.put("42P13", new Integer(4234)); // INVALID FUNCTION DEFINITION
    errorCodeHashMap.put("42P14", new Integer(4235)); // INVALID PREPARED STATEMENT DEFINITION
    errorCodeHashMap.put("42P15", new Integer(4236)); // INVALID SCHEMA DEFINITION
    errorCodeHashMap.put("42P16", new Integer(4237)); // INVALID TABLE DEFINITION
    errorCodeHashMap.put("42P17", new Integer(4238)); // INVALID OBJECT DEFINITION
                       
    //Class 44 � WITH CHECK OPTION Violation
    errorCodeHashMap.put("44000", new Integer(4400)); // WITH CHECK OPTION VIOLATION
    
    //Class 53 � Insufficient Resources
    errorCodeHashMap.put("53000", new Integer(5300)); // INSUFFICIENT RESOURCES
    errorCodeHashMap.put("53100", new Integer(5301)); // DISK FULL
    errorCodeHashMap.put("53200", new Integer(5302)); // OUT OF MEMORY
    errorCodeHashMap.put("53300", new Integer(5303)); // TOO MANY CONNECTIONS
    
    //Class 54 � Program Limit Exceeded
    errorCodeHashMap.put("54000", new Integer(5400)); // PROGRAM LIMIT EXCEEDED
    errorCodeHashMap.put("54001", new Integer(5401)); // STATEMENT TOO COMPLEX
    errorCodeHashMap.put("54011", new Integer(5402)); // TOO MANY COLUMNS
    errorCodeHashMap.put("54023", new Integer(5403)); // TOO MANY ARGUMENTS
    
    //Class 55 � Object Not In Prerequisite State
    errorCodeHashMap.put("55000", new Integer(5500)); // OBJECT NOT IN PREREQUISITE STATE
    errorCodeHashMap.put("55006", new Integer(5501)); // OBJECT IN USE
    errorCodeHashMap.put("55P02", new Integer(5502)); // CANT CHANGE RUNTIME PARAM
    errorCodeHashMap.put("55P03", new Integer(5503)); // LOCK NOT AVAILABLE
    
    //Class 57 � Operator Intervention
    errorCodeHashMap.put("57000", new Integer(5700)); // OPERATOR INTERVENTION
    errorCodeHashMap.put("57014", new Integer(5701)); // QUERY CANCELED
    errorCodeHashMap.put("57P01", new Integer(5702)); // ADMIN SHUTDOWN
    errorCodeHashMap.put("57P02", new Integer(5703)); // CRASH SHUTDOWN
    errorCodeHashMap.put("57P03", new Integer(5704)); // CANNOT CONNECT NOW
    
    //Class 58 � System Error (errors external to PostgreSQL itself)
    errorCodeHashMap.put("58030", new Integer(5800)); // IO ERROR
    errorCodeHashMap.put("58P01", new Integer(5801)); // UNDEFINED FILE
    errorCodeHashMap.put("58P02", new Integer(5802)); // DUPLICATE FILE
    
    //Class F0 � Configuration File Error
    errorCodeHashMap.put("F0000", new Integer(5900)); // CONFIG FILE ERROR
    errorCodeHashMap.put("F0001", new Integer(5901)); // LOCK FILE EXISTS
    
    //Class P0 � PL/pgSQL Error
    errorCodeHashMap.put("P0000", new Integer(6000)); // PLPGSQL ERROR
    errorCodeHashMap.put("P0001", new Integer(6001)); // RAISE EXCEPTION
    errorCodeHashMap.put("P0002", new Integer(6002)); // NO DATA FOUND
    errorCodeHashMap.put("P0003", new Integer(6003)); // TOO MANY ROWS
    
    //Class XX � Internal Error
    errorCodeHashMap.put("XX000", new Integer(6100)); // INTERNAL ERROR
    errorCodeHashMap.put("XX001", new Integer(6101)); // DATA CORRUPTED
    errorCodeHashMap.put("XX002", new Integer(6102)); // INDEX CORRUPTED
  }
  
  /* 
     Note: Postgres does not support the vendor-specific getErrorCode() API. 
     Instead, the some-what standardized getSQLState() API is implemented and
     is the only way to get error conditions back from the server thru the JDBC
     driver. Therefore, for Postgres environment, the SQL state is obtained and 
     mapped to sequential integer codes grouped by exception classes, as coded
     in the populateErrorCodeHashMap method above.
  */
  public int getExceptionCode(SQLException dbException){
      //return dbException.getErrorCode();
      return ((Integer) errorCodeHashMap.get(dbException.getSQLState())).intValue();
  }
}
