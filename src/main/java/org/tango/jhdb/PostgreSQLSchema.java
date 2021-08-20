//+======================================================================
// $Source: $
//
// Project:   Tango
//
// Description:  java source code for HDB extraction library.
//
// $Author: pons $
//
// Copyright (C) :      2015
//						European Synchrotron Radiation Facility
//                      BP 220, Grenoble 38043
//                      FRANCE
//
// This file is part of Tango.
//
// Tango is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Tango is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Tango.  If not, see <http://www.gnu.org/licenses/>.
//
// $Revision $
//
//-======================================================================
package org.tango.jhdb;

import org.tango.jhdb.data.HdbData;
import org.tango.jhdb.data.HdbDataSet;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.List;

/**
 * PostgreSQL database access
 */
public class PostgreSQLSchema extends HdbReader {

  public static final String DEFAULT_DB_URL_PREFIX = "jdbc:postgresql://";
  public static final int    DEFAULT_DB_PORT = 5432;

  private static final HashMap<Integer, SignalInfo.Access> INT_TO_ACCESS = new HashMap<>();
  private static final HashMap<Integer, SignalInfo.Format> INT_TO_FORMAT = new HashMap<>();
  private static final HashMap<Integer, SignalInfo.Type> INT_TO_TYPE = new HashMap<>();

  static {
    INT_TO_ACCESS.put(0, SignalInfo.Access.RO);
    INT_TO_ACCESS.put(2, SignalInfo.Access.WO);
    INT_TO_ACCESS.put(3, SignalInfo.Access.RW);

    INT_TO_FORMAT.put(0, SignalInfo.Format.SCALAR);
    INT_TO_FORMAT.put(1, SignalInfo.Format.SPECTRUM);
    INT_TO_FORMAT.put(2, SignalInfo.Format.IMAGE);

    INT_TO_TYPE.put(1, SignalInfo.Type.BOOLEAN);
    INT_TO_TYPE.put(2, SignalInfo.Type.SHORT);
    INT_TO_TYPE.put(3, SignalInfo.Type.LONG);
    INT_TO_TYPE.put(4, SignalInfo.Type.FLOAT);
    INT_TO_TYPE.put(5, SignalInfo.Type.DOUBLE);
    INT_TO_TYPE.put(6, SignalInfo.Type.USHORT);
    INT_TO_TYPE.put(7, SignalInfo.Type.ULONG);
    INT_TO_TYPE.put(8, SignalInfo.Type.STRING);
    INT_TO_TYPE.put(19, SignalInfo.Type.STATE);
    INT_TO_TYPE.put(22, SignalInfo.Type.UCHAR);
    INT_TO_TYPE.put(23, SignalInfo.Type.LONG64);
    INT_TO_TYPE.put(24, SignalInfo.Type.ULONG64);
    INT_TO_TYPE.put(28, SignalInfo.Type.ENCODED);
    INT_TO_TYPE.put(30, SignalInfo.Type.ENUM);
  }


  private static HashMap<SignalInfo, PreparedStatement> prepQueries = new HashMap<>();

  // Notify every PROGRESS_NBROW rows
  private final static int PROGRESS_NBROW =10000;
  private Connection connection;
  private AttributeBrowser browser=null;
  private String dbURL;
  private String user;
  private String passwd;

  /**
   * Connects to a postgresql HDB.
   * @param host postgresql hostname
   * @param db Database name (default is "hdb")
   * @param _user postgresql user name
   * @param _passwd postgresql user password
   * @param port postgresql databse port (pass 0 for default postgresql port)
   * @throws HdbFailed in case of failure
   */

  public PostgreSQLSchema(String host,String db,String _user,String _passwd,short port) throws HdbFailed {

    host = getPropertyOrDefault("HDB_POSTGRESQL_HOST", host, null);
    if (host == null)
    {
      throw new HdbFailed("host input parameter cannot be null if HDB_POSTGRESQL_HOST variable is not defined");
    }

    user = getPropertyOrDefault("HDB_USER", _user, DEFAULT_DB_USER);

    passwd = getPropertyOrDefault("HDB_PASSWORD", _passwd, DEFAULT_DB_PASSWORD);

    db = getPropertyOrDefault("HDB_NAME", db, DEFAULT_DB_NAME);

    if(port==0) {
      String pStr = getPropertyOrDefault("HDB_POSTGRESQL_PORT", null, null);
      if(pStr==null || passwd.isEmpty())
        port = DEFAULT_DB_PORT;
      else {
        try {
          port = (short)Integer.parseInt(pStr);
        } catch (NumberFormatException e) {
          throw new HdbFailed("Invalid HDB_POSTGRESQL_PORT variable " + e.getMessage());
        }
      }
    }

    // URL example: jdbc:postgresql://host:port/database
    dbURL = DEFAULT_DB_URL_PREFIX + host + ":" + Integer.toString(port) + "/" + db;

    connect();

  }

  private String getPropertyOrDefault(String property, String value, String default_value)
  {
    String ret = value;
    if(ret==null || ret.isEmpty()) {
      ret = System.getenv(property);
      if (ret==null || ret.isEmpty()) {
        ret = System.getProperty(property);
        if (ret==null || ret.isEmpty())
          ret = default_value;
      }
    }
    return ret;
  }
  
  private void connect() throws HdbFailed {

    try {

      Properties connectProperties = new Properties();
      connectProperties.setProperty("user", user);
      connectProperties.setProperty("password", passwd);
      connectProperties.setProperty("loginTimeout", Integer.toString(10));
      connectProperties.setProperty("tcpKeepAlive ", "true"); //Enable TCP keep-alive probe
      connection = DriverManager.getConnection(dbURL, connectProperties);

    } catch (SQLException e) {
      throw new HdbFailed("Failed to connect to PostgreSQL: "+e.getMessage());
    }

  }

  public void disconnect () {

    try {
      for(PreparedStatement statement : prepQueries.values())
      {
        statement.close();
      }
      prepQueries.clear();
      connection.close();
    } catch (SQLException e) {
      System.out.println("Warning closing connection : " + e.getMessage());
    }

  }

  public String getInfo() throws HdbFailed {

    String version =  "PostgreSQL HDB++ API v" + Hdb.getVersion() + "\n";
    String url = "URL:" + dbURL;
    return version + url;

  }

  private void connectionCheck() throws HdbFailed {

    // Execute a dummy request to check the connection with the database,
    // In case failure, make a new connection
    try {
      connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY).execute("select 1;");
    } catch (SQLException e) {
      System.out.println("Reconnecting to "  + this.dbURL);
      disconnect();
      connect();
    }

  }

  public String[] getAttributeList() throws HdbFailed {

    return getList("SELECT att_name FROM att_conf ORDER BY att_name");

  }

  public String[] getHosts() throws HdbFailed {

    return getList("select distinct cs_name from att_conf order by cs_name");

  }

  public String[] getDomains(String host) throws HdbFailed {

    return getList("select distinct domain from att_conf where cs_name='"+host+"' order by domain");

  }

  public String[] getFamilies(String host,String domain) throws HdbFailed {

    return getList("select distinct family from att_conf where cs_name='"+host+
        "' and domain='" + domain + "' order by family");

  }

  public String[] getMembers(String host,String domain,String family) throws HdbFailed {

    return getList("select distinct member from att_conf where cs_name='"+host+
        "' and domain='" + domain + "' and family='" + family + "'  order by member");

  }

  public String[] getNames(String host,String domain,String family,String member) throws HdbFailed {

    return getList("select distinct name from att_conf where cs_name='"+host+
        "' and domain='" + domain + "' and family='" + family + "' and member='" + member + "'  order by name");

  }


  public HdbSigInfo getSigInfo(String attName) throws HdbFailed {

    connectionCheck();

    SignalInfo ret = prepareSigInfo(attName);
    attName = ret.name;
    String query = "SELECT att_conf_id, table_name, write_num, type_num, format_num " +
            "FROM att_conf join att_conf_format on (att_conf.att_conf_format_id=att_conf_format.att_conf_format_id) " +
            "join att_conf_write on (att_conf.att_conf_write_id=att_conf_write.att_conf_write_id) " +
            "join att_conf_type on (att_conf.att_conf_type_id=att_conf_type.att_conf_type_id) " +
            "WHERE att_name='" + attName + "'";

    try {
      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      ResultSet resultSet = statement.executeQuery(query);
      if(resultSet.next()) {
        ret.sigId = resultSet.getString(1);
        ret.tableName = resultSet.getString(2);
        ret.access = INT_TO_ACCESS.getOrDefault(resultSet.getInt(3), SignalInfo.Access.UNKNOWN);
        ret.dataType = INT_TO_TYPE.getOrDefault(resultSet.getInt(4), SignalInfo.Type.UNKNOWN);
        ret.format = INT_TO_FORMAT.getOrDefault(resultSet.getInt(5), SignalInfo.Format.UNKNOWN);
      } else {
        throw new HdbFailed("Signal not found");
      }
      statement.close();
    } catch (SQLException e) {
      throw new HdbFailed("Failed to retrieve signal id: "+e.getMessage());
    }

    return new HdbSigInfo(ret);

  }

  HdbDataSet getDataFromDB(SignalInfo sigInfo,
                           String start_date,
                           String stop_date) throws HdbFailed {

    if (sigInfo == null)
      throw new HdbFailed("sigInfo input parameters is null");

    checkDates(start_date, stop_date);

    boolean isRW = sigInfo.isRW();
    boolean isWO = sigInfo.access == SignalInfo.Access.WO;
    boolean isAggregate = sigInfo.isAggregate();

    String query;
    int queryCount = 0;

    connectionCheck();

    String tablename;
    if(isAggregate)
    {
      tablename = "cagg_" + sigInfo.tableName.substring(4) + "_" + sigInfo.interval.toString();
    }
    else
    {
      tablename = sigInfo.tableName;
    }

    if (hasProgressListener()) {

      // Get a count of the request
        query = "SELECT count(*) FROM " + tablename +
                " WHERE att_conf_id='" + sigInfo.sigId + "'" +
                " AND data_time>='" + toDBDate(start_date) + "'" +
                " AND data_time<='" + toDBDate(stop_date) + "'";

      try {

        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

        ResultSet rs = statement.executeQuery(query);
        rs.next();
        queryCount = rs.getInt(1);
        statement.close();

      } catch (SQLException e) {
        throw new HdbFailed("Failed to get data for query: " + query + "\n" + e.getMessage());
      }

    }

    // Fetch data
    PreparedStatement statement;
    if(!prepQueries.containsKey(sigInfo)) {
      if(isAggregate) {
        switch (sigInfo.dataType) {
          case DOUBLE:
          case FLOAT:
            query = "SELECT data_time, count_rows, count_errors, count_r, count_nan_r, mean_r, min_r, max_r, stddev_r" +
                    ", count_w, count_nan_w, mean_w, min_w, max_w, stddev_w" +
                    " FROM " + tablename +
                    " WHERE att_conf_id= ?" +
                    " AND data_time>= ?" +
                    " AND data_time<= ?" +
                    " ORDER BY data_time ASC";
            break;
          case LONG:
          case ULONG:
          case LONG64:
          case ULONG64:
          case SHORT:
          case USHORT:
            query = "SELECT data_time, count_rows, count_errors, count_r, mean_r, min_r, max_r, stddev_r" +
                    ", count_w, mean_w, min_w, max_w, stddev_w" +
                    " FROM " + tablename +
                    " WHERE att_conf_id= ?" +
                    " AND data_time>= ?" +
                    " AND data_time<= ?" +
                    " ORDER BY data_time ASC";
            break;
          default:
            throw new HdbFailed("Aggregates are not supported for type: " + sigInfo.dataType);
        }
      }
      else
      {
        String rwField = (isRW | isWO) ? ",value_w" : "";
        query = "SELECT data_time,att_error_desc.error_desc as error_desc,quality,value_r" + rwField +
                " FROM " + tablename +
                " left outer join att_error_desc on " + sigInfo.tableName + ".att_error_desc_id = att_error_desc.att_error_desc_id" +
                " WHERE att_conf_id= ?" +
                " AND data_time>= ?" +
                " AND data_time<= ?" +
                " ORDER BY data_time ASC";
      }

      try {
        prepQueries.put(sigInfo, connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
      } catch (SQLException e) {
        throw new HdbFailed("An error occurred upon query preparation for query: " + query);
      }
    }

    ArrayList<HdbData> ret = new ArrayList<>();


    //retrieve prepared statement
    statement = prepQueries.get(sigInfo);
    try {

      //fill the placeholders
      statement.setInt(1, Integer.parseInt(sigInfo.sigId));
      statement.setTimestamp(2, Timestamp.valueOf(toDBDate(start_date)));
      statement.setTimestamp(3, Timestamp.valueOf(toDBDate(stop_date)));
      
      if(sigInfo.isArray())
        statement.setFetchSize(arrayFetchSize);
      else
        statement.setFetchSize(fetchSize);

      //execute query
      ResultSet rs = statement.executeQuery();

      if(isAggregate)
      {
        extractAggregateData(rs, sigInfo, isRW, isWO, queryCount, ret);
      }
      else
      {
        extractRawData(rs, sigInfo, isRW, isWO, queryCount, ret);
      }

    } catch (SQLException e) {
      throw new HdbFailed("Failed to get data: " + e.getMessage());
    }

    return new HdbDataSet(ret);
  }

  private void extractRawData(ResultSet rs, SignalInfo sigInfo, boolean isRW, boolean isWO, int queryCount, List<HdbData> data) throws SQLException, HdbFailed
  {
    long dTime = 0;
    String errorMsg = null;
    int quality = 0;
    int nbRow = 0;

    ArrayList<Object> value = new ArrayList<>();
    ArrayList<Object> wvalue = new ArrayList<>();

    boolean isW = isRW | isWO;
    while (rs.next()) {

      dTime = timeValue(rs.getTimestamp(1));
      errorMsg = rs.getString(2);
      quality = rs.getInt(3);
      value.clear();
      if(isW)
        wvalue.clear();
      if(sigInfo.isArray())
      {
        if(!isWO)
          convertArray(value, rs.getArray(4));
        if(isW) convertArray(wvalue, rs.getArray(5));
      }
      else
      {
        switch(sigInfo.dataType)
        {
          case BOOLEAN:
            if(!isWO)
              value.add(rs.getBoolean(4));
            if(isW) wvalue.add(rs.getBoolean(5));
            break;
          case SHORT:
          case UCHAR:
          case LONG:
          case USHORT:
          case STATE:
          case LONG64:
          case ULONG:
            if(!isWO)
              value.add(rs.getLong(4));
            if(isW) wvalue.add(rs.getLong(5));
            break;
          case DOUBLE:
            if(!isWO)
              value.add(rs.getDouble(4));
            if(isW) wvalue.add(rs.getDouble(5));
            break;
          case FLOAT:
            if(!isWO) value.add(rs.getFloat(4));
            if(isW) wvalue.add(rs.getFloat(5));
            break;
          case STRING:
            if(!isWO) value.add(rs.getString(4));
            if(isW) wvalue.add(rs.getString(5));
            break;
        }
      }

      // Write only attribute, copy write data to read data
      if(isWO)
        value.addAll(wvalue);

      HdbData hd = HdbData.createData(sigInfo);

      hd.parse(
              dTime,     //Tango timestamp
              0,         // Event receive timestamp
              0,         // Recording timestamp
              errorMsg,  // Error string
              quality,   // Quality value
              value, // Read value
              wvalue // Write value
      );
      data.add(hd);

      if (hasProgressListener() && (nbRow % PROGRESS_NBROW == 0))
        fireProgressListener((double) nbRow / (double) queryCount);

      nbRow++;

    }
  }

  private void extractAggregateData(ResultSet rs, SignalInfo info, boolean isRW, boolean isWO, int queryCount, List<HdbData> data) throws SQLException, HdbFailed
  {
    int nbRow = 0;
    boolean isFloating = info.dataType == HdbSigInfo.Type.DOUBLE || info.dataType == HdbSigInfo.Type.FLOAT;
    boolean isArray = info.isArray();
    int floatingOffset1 = 0;
    int floatingOffset2 = 0;
    if(!isFloating)
    {
      floatingOffset1 = 1;
      floatingOffset2 = 2;
    }

    long dTime = 0;
    long count_rows;
    long count_errors;

    while (rs.next()) {
      ArrayList<Long> count_r = new ArrayList<>();
      ArrayList<Long> count_nan_r = new ArrayList<>();
      ArrayList<Double> mean_r = new ArrayList<>();
      ArrayList<Number> min_r = new ArrayList<>();
      ArrayList<Number> max_r = new ArrayList<>();
      ArrayList<Double> stddev_r = new ArrayList<>();
      ArrayList<Long> count_w = new ArrayList<>();
      ArrayList<Long> count_nan_w = new ArrayList<>();
      ArrayList<Double> mean_w = new ArrayList<>();
      ArrayList<Number> min_w = new ArrayList<>();
      ArrayList<Number> max_w = new ArrayList<>();
      ArrayList<Double> stddev_w = new ArrayList<>();
      dTime = timeValue(rs.getTimestamp(1));
      count_rows = rs.getLong(2);
      count_errors = rs.getLong(3);
      if (isArray) {
        convertLongArray(count_r, rs.getArray(4));
        convertDoubleArray(mean_r, rs.getArray(6 - floatingOffset1));
        convertNumberArray(min_r, rs.getArray(7 - floatingOffset1), info.dataType);
        convertNumberArray(max_r, rs.getArray(8 - floatingOffset1), info.dataType);
        convertDoubleArray(stddev_r, rs.getArray(9 - floatingOffset1));
        convertLongArray(count_w, rs.getArray(10 - floatingOffset1));
        convertDoubleArray(mean_w, rs.getArray(12 - floatingOffset2));
        convertNumberArray(min_w, rs.getArray(13 - floatingOffset2), info.dataType);
        convertNumberArray(max_w, rs.getArray(14 - floatingOffset2), info.dataType);
        convertDoubleArray(stddev_w, rs.getArray(15 - floatingOffset2));
        if(isFloating)
        {
          convertLongArray(count_nan_r, rs.getArray(5));
          convertLongArray(count_nan_w, rs.getArray(11));
        }
      }
      else
      {
        count_r.add(rs.getLong(4));
        mean_r.add(rs.getDouble(6 - floatingOffset1));
        min_r.add(extractNumber(rs, 7 - floatingOffset1, info.dataType));
        max_r.add(extractNumber(rs, 8 - floatingOffset1, info.dataType));
        stddev_r.add(rs.getDouble(9 - floatingOffset1));
        count_w.add(rs.getLong(10 - floatingOffset1));
        mean_w.add(rs.getDouble(12 - floatingOffset2));
        min_w.add(extractNumber(rs, 13-floatingOffset2, info.dataType));
        max_w.add(extractNumber(rs, 14-floatingOffset2, info.dataType));
        stddev_w.add(rs.getDouble(15 - floatingOffset2));
        if(isFloating) {
          count_nan_r.add(rs.getLong(5));
          count_nan_w.add(rs.getLong(11));
        }
      }

      HdbData hd = HdbData.createData(info);

      hd.parseAggregate(
              dTime,     //Tango timestamp
              count_rows,
              count_errors,
              count_r,
              count_nan_r,
              mean_r,
              min_r,
              max_r,
              stddev_r,
              count_w,
              count_nan_w,
              mean_w,
              min_w,
              max_w,
              stddev_w
      );

      data.add(hd);

      if (hasProgressListener() && (nbRow % PROGRESS_NBROW == 0))
        fireProgressListener((double) nbRow / (double) queryCount);

      nbRow++;
    }
  }

  public HdbSigParam getLastParam(SignalInfo sigInfo) throws HdbFailed {

    connectionCheck();

    String query = "SELECT recv_time,label,unit,standard_unit,display_unit,format,"+
        "archive_rel_change,archive_abs_change,archive_period,description" +
        " FROM att_parameter " +
        " WHERE att_conf_id='" + sigInfo.sigId + "'" +
        " ORDER BY recv_time DESC limit 1";

    HdbSigParam ret = new HdbSigParam(sigInfo);

    try {

      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      ResultSet rs = statement.executeQuery(query);
      if(rs.next()) {

        ret.recvTime = timeValue(rs.getTimestamp(1));
        ret.insertTime = 0;
        ret.label = rs.getString(2);
        ret.unit = rs.getString(3);
        try {
          ret.standard_unit = Double.parseDouble(rs.getString(4));
        } catch (NumberFormatException e) {
          ret.standard_unit = 1.0;
        }
        try {
          ret.display_unit = Double.parseDouble( rs.getString(5));
        } catch (NumberFormatException e) {
          ret.display_unit = 1.0;
        }
        ret.format = rs.getString(6);
        ret.archive_rel_change = rs.getString(7);
        ret.archive_abs_change = rs.getString(8);
        ret.archive_period = rs.getString(9);
        ret.description = rs.getString(10);

      } else {
        throw new HdbFailed("Cannot get parameter for " + sigInfo.name);
      }

      statement.close();

    } catch (SQLException e) {
      throw new HdbFailed("Failed to get parameter history: "+e.getMessage());
    }

    return ret;

  }

  public ArrayList<HdbSigParam> getParams(String attName,
                                          String start_date,
                                          String stop_date) throws HdbFailed {
    SignalInfo sigInfo = getSigInfo(attName);
    return getParams(sigInfo,start_date,stop_date);
  }

  public ArrayList<HdbSigParam> getParams(SignalInfo sigInfo,
                                          String start_date,
                                          String stop_date) throws HdbFailed {

    checkDates(start_date,stop_date);
    connectionCheck();

    String query = "SELECT recv_time,label,unit,standard_unit,display_unit,format,"+
        "archive_rel_change,archive_abs_change,archive_period,description" +
        " FROM att_parameter " +
        " WHERE att_conf_id='" + sigInfo.sigId + "'" +
        " AND recv_time>='" + toDBDate(start_date) + "'" +
        " AND recv_time<='" + toDBDate(stop_date) + "'" +
        " ORDER BY recv_time ASC";

    ArrayList<HdbSigParam> ret = new ArrayList<HdbSigParam>();

    try {

      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      ResultSet rs = statement.executeQuery(query);
      while(rs.next()) {

        HdbSigParam hd = new HdbSigParam(sigInfo);
        hd.recvTime = timeValue(rs.getTimestamp(1));
        hd.insertTime = 0;
        hd.label = rs.getString(2);
        hd.unit = rs.getString(3);
        try {
          hd.standard_unit = Double.parseDouble(rs.getString(4));
        } catch (NumberFormatException e) {
          hd.standard_unit = 1.0;
        }
        try {
          hd.display_unit = Double.parseDouble( rs.getString(5));
        } catch (NumberFormatException e) {
          hd.display_unit = 1.0;
        }
        hd.format = rs.getString(6);
        hd.archive_rel_change = rs.getString(7);
        hd.archive_abs_change = rs.getString(8);
        hd.archive_period = rs.getString(9);
        hd.description = rs.getString(10);

        ret.add(hd);

      }

      statement.close();

    } catch (SQLException e) {
      throw new HdbFailed("Failed to get parameter history: "+e.getMessage());
    }

    return ret;
  }

  public HdbDataSet findErrors(String attName,
                               String start_date,
                               String stop_date) throws HdbFailed {
    throw new HdbFailed("Not implemented");
  }

  // ---------------------------------------------------------------------------------------

  private void convertArray(ArrayList<Object> v,Array a) throws SQLException {

    if(a!=null) {

      // postgre JDBC does not handle unsigned array !
      if( a.getBaseTypeName().startsWith("u")) {
        String value = a.toString();
        if(value.startsWith("{") && value.endsWith("}")) {
          value = value.substring(1,value.length()-1);
          String[] valStr = value.split(",");
          try {
            for (int i = 0; i < valStr.length; i++)
              v.add(new Long(Long.parseLong(valStr[i])));
          } catch (NumberFormatException e) {
            throw new SQLException("Postgre JDBC driver does not handle " + a.getBaseType());
          }
        } else {
          // Cannot perform best effort
          throw new SQLException("Postgre JDBC driver does not handle " + a.getBaseTypeName());
        }
      } else {
        Object[] objects = (Object[]) a.getArray();
        for (int i = 0; i < objects.length; i++)
          v.add(objects[i]);
      }

    }

  }

  private void convertDoubleArray(ArrayList<Double> v,Array a) throws SQLException {

    if(a!=null) {
      Double[] objects = (Double[]) a.getArray();
      for(int i=0;i<objects.length;i++) {
        v.add(objects[i]);
      }
    }

  }

  private void convertNumberArray(ArrayList<Number> v,Array a, HdbSigInfo.Type type) throws SQLException {
    if(a!=null) {
      Number[] numbers;
      switch(type) {
        case FLOAT:
        case DOUBLE:
        case LONG:
        case LONG64:
        case SHORT:
        case ULONG:
        case ULONG64:
        case USHORT:
          numbers = (Number[]) a.getArray();
          break;
        default:
          numbers = new Number[0];
      }
      for (int i = 0; i < numbers.length; i++) {
        v.add(numbers[i]);
      }
    }
  }

  private void convertLongArray(ArrayList<Long> v,Array a) throws SQLException {

    if(a!=null) {
      Long[] objects = (Long[]) a.getArray();
      for(int i=0;i<objects.length;i++) {
        v.add(objects[i]);
      }
    }

  }

  private Number extractNumber(ResultSet rs, int index, HdbSigInfo.Type type) throws SQLException {
    if(rs!=null) {
      switch(type) {
        case FLOAT:
          return rs.getFloat(index);
        case DOUBLE:
          return rs.getDouble(index);
        case LONG:
          return rs.getInt(index);
        case LONG64:
          return rs.getLong(index);
        case SHORT:
          return rs.getShort(index);
        case ULONG:
          return rs.getLong(index);
        case ULONG64:
          return rs.getLong(index);
        case USHORT:
          return rs.getInt(index);
        default:
      }
    }
    return Double.NaN;
  }

  private long timeValue(Timestamp ts) {

    if(ts==null)
      return 0;

    long ret = ts.getTime();
    ret = ret / 1000;
    long us = ts.getNanos();
    us = us / 1000;
    ret = ret * 1000000;
    ret += us;
    return ret;

  }

  private String[] getList(String query) throws HdbFailed {

    connectionCheck();

    ArrayList<String> list = new ArrayList<>();

    try {
      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      ResultSet resultSet = statement.executeQuery(query);
      while (resultSet.next())
        list.add(resultSet.getString(1));
      statement.close();
    } catch (SQLException e) {
      throw new HdbFailed("Failed to retrieve attribute list: "+e.getMessage());
    }

    String[] ret = new String[list.size()];

    return list.toArray(ret);
  }

  private String toDBDate(String date) {

    // In:   09/07/2015 12:00:00
    // Out:  2015-07-09 12:00:00
    return date.substring(6,10) + "-" + date.substring(3,5) + "-" +
        date.substring(0,2) + " " + date.substring(11,19);

  }

  @Override
  public boolean isFeatureSupported(Feature feat)
  {
    switch(feat)
    {
      case AGGREGATES:
        return true;
      default:
        return false;
    }
  }
}
