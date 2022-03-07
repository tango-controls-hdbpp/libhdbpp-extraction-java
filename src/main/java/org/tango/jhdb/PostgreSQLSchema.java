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
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.SortedSet;

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

  /**
   * Build the aggragate list for the query, and store the column indexes for later processing..
   */
  private String getAggregateQueryList(SignalInfo sigInfo, Map<HdbData.Aggregate, Integer> indexes) {
    int idx = 0;
    int nbAggregates = sigInfo.aggregates.size();
    StringBuffer queryList = new StringBuffer();
    
    if(indexes == null)
    {
      indexes = new HashMap<>();
    }
    
    for(HdbData.Aggregate agg : sigInfo.aggregates)
    {
      queryList.append(agg.toString());
      
      if(idx != nbAggregates - 1)
      {
        queryList.append(", ");
      }
      
      indexes.put(agg, idx);
      ++idx;
    }
    
    return queryList.toString();
  }


  private String buildQuery(SignalInfo sigInfo, String tablename, SortedSet<SignalInfo.Range> ranges, Map<HdbData.Aggregate, Map<Integer, Boolean>> agg_idxes, boolean isAggregate, boolean isRW, boolean isWO)
  {
      String query;
      if(isAggregate)
      {
          int idx = 0;
          if( agg_idxes == null)
          {
              agg_idxes = new HashMap<>();
          }

          StringBuilder queryBuilder = new StringBuilder();
          queryBuilder.append("SELECT data_time");

          // special cases for count_rows and count_errors, as they are never arrrays.
          if(sigInfo.aggregates.contains(HdbData.Aggregate.ROWS_COUNT))
          {
              queryBuilder.append(", ");
              queryBuilder.append(HdbData.Aggregate.ROWS_COUNT.toString());
              Map<Integer, Boolean> ret = new HashMap<>();
              ret.put(idx++, false);
              agg_idxes.put(HdbData.Aggregate.ROWS_COUNT, ret);
          }
          if(sigInfo.aggregates.contains(HdbData.Aggregate.ERRORS_COUNT))
          {
              queryBuilder.append(", ");
              queryBuilder.append(HdbData.Aggregate.ERRORS_COUNT.toString());
              Map<Integer, Boolean> ret = new HashMap<>();
              ret.put(idx++, false);
              agg_idxes.put(HdbData.Aggregate.ERRORS_COUNT, ret);
          }

          for(SignalInfo.Range r : ranges)
          {
              for(HdbData.Aggregate agg : sigInfo.aggregates)
              {
                  // Do not treat rows and error count
                  if(agg == HdbData.Aggregate.ROWS_COUNT || agg == HdbData.Aggregate.ERRORS_COUNT)
                      continue;

                  queryBuilder.append(", ");
                  queryBuilder.append(agg.toString());
                  queryBuilder.append(r.toString());
                  if(!agg_idxes.containsKey(agg))
                  {
                      agg_idxes.put(agg, new HashMap<>());
                  }
                  boolean isArray = (sigInfo.isArray() && r == SignalInfo.Range.FULL_RANGE) || r.size() > 1;
                  agg_idxes.get(agg).put(idx++, isArray);
              }
          }

          queryBuilder.append(" FROM " + tablename +
                " WHERE att_conf_id= ?" +
                " AND data_time>= ?" +
                " AND data_time<= ?" +
                " ORDER BY data_time ASC");

          query = queryBuilder.toString();
      }
      else
      {
          if(ranges.isEmpty())
          {
              String rwField = (isRW | isWO) ? ",value_w" : "";
              query = "SELECT data_time,att_error_desc.error_desc as error_desc,quality,value_r" + rwField +
                  " FROM " + tablename +
                  " left outer join att_error_desc on " + tablename + ".att_error_desc_id = att_error_desc.att_error_desc_id" +
                  " WHERE att_conf_id= ?" +
                  " AND data_time>= ?" +
                  " AND data_time<= ?" +
                  " ORDER BY data_time ASC";
          }
          else
          {
              StringBuilder queryBuilder = new StringBuilder();
              queryBuilder.append("SELECT data_time, att_error_desc.error_desc as error_desc, quality");
              for(SignalInfo.Range i : ranges)
              {
                  queryBuilder.append(", value_r");
                  queryBuilder.append(i);
                  if(isRW | isWO)
                  {
                      queryBuilder.append(", value_w");
                      queryBuilder.append(i);
                  }
              }
              queryBuilder.append(" FROM ");
              queryBuilder.append(tablename);
              queryBuilder.append(" left outer join att_error_desc on ");
              queryBuilder.append(tablename);
              queryBuilder.append(".att_error_desc_id = att_error_desc.att_error_desc_id");
              queryBuilder.append(" WHERE att_conf_id=? AND data_time>= ? AND data_time<= ? ORDER BY data_time ASC");

              query = queryBuilder.toString();
          }
      }

      return query;
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
      SortedSet<SignalInfo.Range> ranges = sigInfo.getRanges();
    
      // Cache the prepared statement only for not aggregates quries that target a full range. 
      boolean isCacheable = !isAggregate && ranges.contains(SignalInfo.Range.FULL_RANGE);

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

    Map<HdbData.Aggregate, Map<Integer, Boolean>> agg_idxes = new HashMap<>();
    if(isCacheable)
    {
        if(!prepQueries.containsKey(sigInfo)) {

            query = buildQuery(sigInfo, tablename, ranges, agg_idxes, isAggregate, isRW, isWO);

            try {
                prepQueries.put(sigInfo, connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
            } catch (SQLException e) {
                throw new HdbFailed("An error occurred upon query preparation for query: " + query);
            }
        }

        //retrieve prepared statement
        statement = prepQueries.get(sigInfo);

    }
    else
    {
        query = buildQuery(sigInfo, tablename, ranges, agg_idxes, isAggregate, isRW, isWO);
        try
        {
            statement = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        } catch (SQLException e) {
          throw new HdbFailed("An error occurred upon query preparation for query: " + query);
        }
    }

    ArrayList<HdbData> ret = new ArrayList<>();

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
        extractAggregateData(rs, sigInfo, ranges, isRW, isWO, queryCount, agg_idxes, ret);
	// For aggregates we do not cache the statement, so we have to close it.
	statement.close();
      }
      else
      {
        extractRawData(rs, sigInfo, ranges, isRW, isWO, queryCount, ret);

        if(!isCacheable)
	    statement.close();
      }

    } catch (SQLException e) {
      throw new HdbFailed("Failed to get data for query:\n" + statement.toString() + "\n" + e.getMessage());
    }

    return new HdbDataSet(ret);
  }

  private void extractValue(SignalInfo sigInfo, ResultSet rs, int idx, List<Object> value, List<Object> wvalue, boolean isWO, boolean isW) throws SQLException
  {
      extractValue(sigInfo, rs, idx, value, wvalue, isWO, isW, false);
  }

  private int extractValue(SignalInfo sigInfo, ResultSet rs, int idx, List<Object> value, List<Object> wvalue, boolean isWO, boolean isW, boolean increment) throws SQLException
  {
      int ret_idx = idx;
        switch(sigInfo.dataType)
        {
          case BOOLEAN:
            if(!isWO)
            {
              value.add(rs.getBoolean(idx));
              ++ret_idx;
            }
            if(isW)
            {
                if(increment)
                {
                    wvalue.add(rs.getBoolean(ret_idx));
                    ++ret_idx;
                }
                else
                {
                    wvalue.add(rs.getBoolean(idx + 1));
                }
            }
            break;
          case SHORT:
          case UCHAR:
          case LONG:
          case USHORT:
          case STATE:
          case LONG64:
          case ULONG:
            if(!isWO)
            {
              value.add(rs.getLong(idx));
              ++ret_idx;
            }
            if(isW)
            {
                if(increment)
                {
                    wvalue.add(rs.getLong(ret_idx));
                    ++ret_idx;
                }
                else
                {
                    wvalue.add(rs.getLong(idx + 1));
                }
            }
            break;
          case DOUBLE:
            if(!isWO)
            {
              value.add(rs.getDouble(idx));
              ++ret_idx;
            }
            if(isW)
            {
                if(increment)
                {
                    wvalue.add(rs.getDouble(ret_idx));
                    ++ret_idx;
                }
                else
                {
                    wvalue.add(rs.getDouble(idx + 1));
                }
            }
            break;
          case FLOAT:
            if(!isWO)
            {
              value.add(rs.getFloat(idx));
              ++ret_idx;
            }
            if(isW)
            {
                if(increment)
                {
                    wvalue.add(rs.getFloat(ret_idx));
                    ++ret_idx;
                }
                else
                {
                    wvalue.add(rs.getFloat(idx + 1));
                }
            }
            break;
          case STRING:
            if(!isWO)
            {
              value.add(rs.getString(idx));
              ++ret_idx;
            }
            if(isW)
            {
                if(increment)
                {
                    wvalue.add(rs.getString(ret_idx));
                    ++ret_idx;
                }
                else
                {
                    wvalue.add(rs.getString(idx + 1));
                }
            }
            break;
        }
      return ret_idx;
  }

  private void extractAggregates(Map<HdbData.Aggregate, Map<Integer, Boolean>> indexes, ResultSet rs, SignalInfo.Type dataType, List<Long> count_rows, List<Long> count_errors, List<Long> count_r, List<Long> count_nan_r, List<Double> mean_r, List<Number> min_r, List<Number> max_r, List<Double> stddev_r, List<Long> count_w, List<Long> count_nan_w, List<Double> mean_w, List<Number> min_w, List<Number> max_w, List<Double> stddev_w) throws SQLException
  {
      for(Map.Entry<HdbData.Aggregate, Map<Integer, Boolean>> agg_idx : indexes.entrySet())
      {
          HdbData.Aggregate agg = agg_idx.getKey();
          for(Map.Entry<Integer, Boolean> i : agg_idx.getValue().entrySet())
          {
              // We add 2 because indexing start at 1 for sql result
              // and the first one is always the timestamp.
              int idx = i.getKey() + 2;
              boolean isArray = i.getValue();
              switch(agg)
              {
                  case ROWS_COUNT:
                      {
                          count_rows.add(rs.getLong(idx));
                          break;
                      }
                  case ERRORS_COUNT:
                      {
                          count_errors.add(rs.getLong(idx));
                          break;
                      }
                  case COUNT_R:
                      {
                          if(isArray)
                          {
                              convertLongArray(count_r, rs.getArray(idx));
                          }
                          else
                          {
                              count_r.add(rs.getLong(idx));
                          }
                          break;
                      }
                  case NAN_COUNT_R:
                      {
                          if(isArray)
                          {
                              convertLongArray(count_nan_r, rs.getArray(idx));
                          }
                          else
                          {
                              count_nan_r.add(rs.getLong(idx));
                          }
                          break;
                      }
                  case MEAN_R:
                      {
                          if(isArray)
                          {
                              convertDoubleArray(mean_r, rs.getArray(idx));
                          }
                          else
                          {
                              mean_r.add(rs.getDouble(idx));
                          }
                          break;
                      }
                  case MIN_R:
                      {
                          if(isArray)
                          {
                              convertNumberArray(min_r, rs.getArray(idx), dataType);
                          }
                          else
                          {
                              min_r.add(extractNumber(rs, idx, dataType));
                          }
                          break;
                      }
                  case MAX_R:
                      {
                          if(isArray)
                          {
                              convertNumberArray(max_r, rs.getArray(idx), dataType);
                          }
                          else
                          {
                              max_r.add(extractNumber(rs, idx, dataType));
                          }
                          break;
                      }
                  case STDDEV_R:
                      {
                          if(isArray)
                          {
                              convertDoubleArray(stddev_r, rs.getArray(idx));
                          }
                          else
                          {
                              stddev_r.add(rs.getDouble(idx));
                          }
                          break;
                      }
                  case COUNT_W:
                      {
                          if(isArray)
                          {
                              convertLongArray(count_w, rs.getArray(idx));
                          }
                          else
                          {
                              count_w.add(rs.getLong(idx));
                          }
                          break;
                      }
                  case NAN_COUNT_W:
                      {
                          if(isArray)
                          {
                              convertLongArray(count_nan_w, rs.getArray(idx));
                          }
                          else
                          {
                              count_nan_w.add(rs.getLong(idx));
                          }
                          break;
                      }
                  case MEAN_W:
                      {
                          if(isArray)
                          {
                              convertDoubleArray(mean_w, rs.getArray(idx));
                          }
                          else
                          {
                              mean_w.add(rs.getDouble(idx));
                          }
                          break;
                      }
                  case MIN_W:
                      {
                          if(isArray)
                          {
                              convertNumberArray(min_w, rs.getArray(idx), dataType);
                          }
                          else
                          {
                              min_w.add(extractNumber(rs, idx, dataType));
                          }
                          break;
                      }
                  case MAX_W:
                      {
                          if(isArray)
                          {
                              convertNumberArray(max_w, rs.getArray(idx), dataType);
                          }
                          else
                          {
                              max_w.add(extractNumber(rs, idx, dataType));
                          }
                          break;
                      }
                  case STDDEV_W:
                      {
                          if(isArray)
                          {
                              convertDoubleArray(stddev_w, rs.getArray(idx));
                          }
                          else
                          {
                              stddev_w.add(rs.getDouble(idx));
                          }
                          break;
                      }
              }
          }
      }
  }

  private void extractRawData(ResultSet rs, SignalInfo sigInfo, SortedSet<SignalInfo.Range> ranges, boolean isRW, boolean isWO, int queryCount, List<HdbData> data) throws SQLException, HdbFailed
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
          if(ranges.contains(SignalInfo.Range.FULL_RANGE))
          {
            if(!isWO)
              convertArray(value, rs.getArray(4));
            if(isW) convertArray(wvalue, rs.getArray(5));
          }
          else
          {
              int idx = 4;
              for(SignalInfo.Range r : ranges)
              {
                  if(r.size() > 1)
                  {
                      if(!isWO)
                      {
                        convertArray(value, rs.getArray(idx));
                        ++idx;
                      }
                      if(isW)
                      {
                        convertArray(wvalue, rs.getArray(idx));
                        ++idx;
                      }
                  }
                  else
                  {
                      idx = extractValue(sigInfo, rs, idx, value, wvalue, isWO, isW, true);
                  }

              }
          }
      }
      else
      {
          extractValue(sigInfo, rs, 4, value, wvalue, isWO, isW);
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

  private void extractAggregateData(ResultSet rs, SignalInfo info, SortedSet<SignalInfo.Range> ranges, boolean isRW, boolean isWO, int queryCount, Map<HdbData.Aggregate, Map<Integer, Boolean>> indexes, List<HdbData> data) throws SQLException, HdbFailed
  {
    int nbRow = 0;

    long dTime = 0;

    while (rs.next()) {
      ArrayList<Long> count_rows = new ArrayList<>();
      ArrayList<Long> count_errors = new ArrayList<>();
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

      extractAggregates(indexes, rs, info.dataType, count_rows, count_errors, count_r, count_nan_r, mean_r, min_r, max_r, stddev_r, count_w, count_nan_w, mean_w, min_w, max_w, stddev_w);

        long count_rows_l = 0;
        long count_errors_l = 0;

        if(!count_rows.isEmpty())
            count_rows_l = count_rows.get(0);
        if(!count_errors.isEmpty())
            count_errors_l = count_errors.get(0);

      HdbData hd = HdbData.createData(info);

      hd.parseAggregate(
              dTime,     //Tango timestamp
              count_rows_l,
              count_errors_l,
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

  private void convertArray(List<Object> v,Array a) throws SQLException {

    if(a!=null) {
      Object[] objects = (Object[]) a.getArray();
      for(int i=0;i<objects.length;i++)
        v.add(objects[i]);
    }

  }

  private void convertDoubleArray(List<Double> v,Array a) throws SQLException {

    if(a!=null) {
      Double[] objects = (Double[]) a.getArray();
      for(int i=0;i<objects.length;i++) {
        v.add(objects[i]);
      }
    }

  }

  private void convertNumberArray(List<Number> v,Array a, HdbSigInfo.Type type) throws SQLException {
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

  private void convertLongArray(List<Long> v,Array a) throws SQLException {

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
