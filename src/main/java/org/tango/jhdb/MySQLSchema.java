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
import java.util.Properties;

/**
 * MySQL database access
 */
public class MySQLSchema extends HdbReader {

  public static final String DEFAULT_DB_URL_PREFIX = "jdbc:mysql://";
  public static final int    DEFAULT_DB_PORT = 3306;

  protected final static String[] tableNames = {

      "",
      "att_scalar_devdouble_ro",
      "att_scalar_devdouble_rw",
      "att_array_devdouble_ro",
      "att_array_devdouble_rw",
      "att_scalar_devlong64_ro",
      "att_scalar_devlong64_rw",
      "att_array_devlong64_ro",
      "att_array_devlong64_rw",
      // OLD INT8 type (no longer used)
      "",
      "",
      "",
      "",
      "att_scalar_devstring_ro",
      "att_scalar_devstring_rw",
      "att_array_devstring_ro",
      "att_array_devstring_rw",
      "att_scalar_devfloat_ro",
      "att_scalar_devfloat_rw",
      "att_array_devfloat_ro",
      "att_array_devfloat_rw",
      "att_scalar_devuchar_ro",
      "att_scalar_devuchar_rw",
      "att_array_devuchar_ro",
      "att_array_devuchar_rw",
      "att_scalar_devshort_ro",
      "att_scalar_devshort_rw",
      "att_array_devshort_ro",
      "att_array_devshort_rw",
      "att_scalar_devushort_ro",
      "att_scalar_devushort_rw",
      "att_array_devushort_ro",
      "att_array_devushort_rw",
      "att_scalar_devlong_ro",
      "att_scalar_devlong_rw",
      "att_array_devlong_ro",
      "att_array_devlong_rw",
      "att_scalar_devulong_ro",
      "att_scalar_devulong_rw",
      "att_array_devulong_ro",
      "att_array_devulong_rw",
      "att_scalar_devstate_ro",
      "att_scalar_devstate_rw",
      "att_array_devstate_ro",
      "att_array_devstate_rw",
      "att_scalar_devboolean_ro",
      "att_scalar_devboolean_rw",
      "att_array_devboolean_ro",
      "att_array_devboolean_rw",
      "att_scalar_devencoded_ro",
      "att_scalar_devencoded_rw",
      "att_array_devencoded_ro",
      "att_array_devencoded_rw",
      "att_scalar_devulong64_ro",
      "att_scalar_devulong64_rw",
      "att_array_devulong64_ro",
      "att_array_devulong64_rw"

  };

  // Notify every PROGRESS_NBROW rows
  protected final static int PROGRESS_NBROW =10000;
  protected Connection connection;
  protected AttributeBrowser browser=null;
  protected String dbURL;

  private static boolean checkLightSchema(Connection c) throws HdbFailed
  {
    ArrayList<String> list = new ArrayList<>();

    String query = "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_NAME = 'att_conf';";

    try {
      Statement statement = c.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      ResultSet resultSet = statement.executeQuery(query);
      while (resultSet.next())
        list.add(resultSet.getString(1));
      statement.close();
    } catch (SQLException e) {
      throw new HdbFailed("Failed to retrieve engine mode: "+e.getMessage());
    }

    return list.get(0).equalsIgnoreCase("InnoDB");
  }

  public static MySQLSchema createSchema(String host, String db, String user, String passwd, short port) throws HdbFailed
  {
    if(host==null || host.isEmpty()) {
      host = System.getenv("HDB_MYSQL_HOST");
      if (host==null || host.isEmpty()) {
        host = System.getProperty("HDB_MYSQL_HOST");
        if (host==null || host.isEmpty())
          throw new HdbFailed("host input parameter cannot be null if HDB_MYSQL_HOST variable is not defined");
      }
    }

    if(user==null || user.isEmpty()) {
      user = System.getenv("HDB_USER");
      if (user==null || user.isEmpty()) {
        user = System.getProperty("HDB_USER");
        if (user==null || user.isEmpty())
          user = DEFAULT_DB_USER;
      }
    }

    if(passwd==null || passwd.isEmpty()) {
      passwd = System.getenv("HDB_PASSWORD");
      if (passwd==null || passwd.isEmpty()) {
        passwd = System.getProperty("HDB_PASSWORD");
        if (passwd==null || passwd.isEmpty())
          passwd = DEFAULT_DB_PASSWORD;
      }
    }

    if(db==null || db.isEmpty()) {
      db = System.getenv("HDB_NAME");
      if (db==null || db.isEmpty()) {
        db = System.getProperty("HDB_NAME");
        if (db==null || db.isEmpty())
          db = DEFAULT_DB_NAME;
      }
    }

    if(port==0) {
      String pStr = System.getenv("HDB_MYSQL_PORT");
      if(pStr==null || passwd.isEmpty())
        port = DEFAULT_DB_PORT;
      else {
        try {
          port = (short)Integer.parseInt(pStr);
        } catch (NumberFormatException e) {
          throw new HdbFailed("Invalid HDB_MYSQL_PORT variable " + e.getMessage());
        }
      }
    }

    try {

      Properties connectProperties = new Properties();
      connectProperties.setProperty("user", user);
      connectProperties.setProperty("password", passwd);
      connectProperties.setProperty("loginTimeout", Integer.toString(10));
      connectProperties.setProperty("tcpKeepAlive ", "true"); //Enable TCP keep-alive probe

      // URL example: jdbc:postgresql://host:port/database
      String dbURL = DEFAULT_DB_URL_PREFIX + host + ":" +
          Integer.toString(port) + "/" + db;

      Connection connection = DriverManager.getConnection(dbURL, connectProperties);

        boolean isLightSchema = checkLightSchema(connection);
        if(isLightSchema)
        {
            return new MySQLLightSchema(dbURL, connection);
        }
        else
        {
            return new MySQLSchema(dbURL, connection);
        }

    } catch (SQLException e) {
      throw new HdbFailed("Failed to connect to MySQL: "+e.getMessage());
    }
  }


  public MySQLSchema(String url, Connection c) throws HdbFailed
  {
      connection = c;
      dbURL = url;
  }

  /**
   * Connects to a MySQL HDB.
   * @param host MySQL hostname
   * @param db Database name (default is "hdb")
   * @param user MySQL user name
   * @param passwd MySQL user password
   * @param port MySQL databse port (pass 0 for default Mysql port)
   * @throws HdbFailed in case of failure
   */

  public MySQLSchema(String host,String db,String user,String passwd,short port) throws HdbFailed
  {

    if(host==null || host.isEmpty()) {
      host = System.getenv("HDB_MYSQL_HOST");
      if (host==null || host.isEmpty()) {
        host = System.getProperty("HDB_MYSQL_HOST");
        if (host==null || host.isEmpty())
          throw new HdbFailed("host input parameter cannot be null if HDB_MYSQL_HOST variable is not defined");
      }
    }

    if(user==null || user.isEmpty()) {
      user = System.getenv("HDB_USER");
      if (user==null || user.isEmpty()) {
        user = System.getProperty("HDB_USER");
        if (user==null || user.isEmpty())
          user = DEFAULT_DB_USER;
      }
    }

    if(passwd==null || passwd.isEmpty()) {
      passwd = System.getenv("HDB_PASSWORD");
      if (passwd==null || passwd.isEmpty()) {
        passwd = System.getProperty("HDB_PASSWORD");
        if (passwd==null || passwd.isEmpty())
          passwd = DEFAULT_DB_PASSWORD;
      }
    }

    if(db==null || db.isEmpty()) {
      db = System.getenv("HDB_NAME");
      if (db==null || db.isEmpty()) {
        db = System.getProperty("HDB_NAME");
        if (db==null || db.isEmpty())
          db = DEFAULT_DB_NAME;
      }
    }

    if(port==0) {
      String pStr = System.getenv("HDB_MYSQL_PORT");
      if(pStr==null || passwd.isEmpty())
        port = DEFAULT_DB_PORT;
      else {
        try {
          port = (short)Integer.parseInt(pStr);
        } catch (NumberFormatException e) {
          throw new HdbFailed("Invalid HDB_MYSQL_PORT variable " + e.getMessage());
        }
      }
    }

    try {

      Properties connectProperties = new Properties();
      connectProperties.setProperty("user", user);
      connectProperties.setProperty("password", passwd);
      connectProperties.setProperty("loginTimeout", Integer.toString(10));
      connectProperties.setProperty("tcpKeepAlive ", "true"); //Enable TCP keep-alive probe

      // URL example: jdbc:postgresql://host:port/database
      dbURL = DEFAULT_DB_URL_PREFIX + host + ":" +
          Integer.toString(port) + "/" + db;

      connection = DriverManager.getConnection(dbURL, connectProperties);

    } catch (SQLException e) {
      throw new HdbFailed("Failed to connect to MySQL: "+e.getMessage());
    }

  }

  public void disconnect () {
    try {
      connection.close();
    } catch (SQLException e) {
      System.out.println("Warning closing connection : " + e.getMessage());
    }
  }

  public String getInfo() throws HdbFailed {

    String version =  "MySQL HDB++ API v" + Hdb.getVersion() + "\n";
    String url = "URL:" + dbURL;
    return version + url;

  }

  public String[] getAttributeList() throws HdbFailed {

    ArrayList<String> list = new ArrayList<String>();

    String query = "SELECT att_name FROM att_conf ORDER BY att_name";

    try {
      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      ResultSet resultSet = statement.executeQuery(query);
      while (resultSet.next())
        list.add(resultSet.getString(1));
      statement.close();
    } catch (SQLException e) {
      throw new HdbFailed("Failed to retrieve attribute list: "+e.getMessage());
    }

    String[] retStr = new String[list.size()];
    for(int i=0;i<retStr.length;i++)
      retStr[i]=list.get(i);

    return retStr;

  }

  private void constructBrowser() throws HdbFailed {
    if( browser==null )
      browser = AttributeBrowser.constructBrowser(this);
  }

  public String[] getHosts() throws HdbFailed {
    constructBrowser();
    return browser.getHosts();
  }

  public String[] getDomains(String host) throws HdbFailed {
    constructBrowser();
    return browser.getDomains(host);
  }

  public String[] getFamilies(String host,String domain) throws HdbFailed {
    constructBrowser();
    return browser.getFamilies(host, domain);
  }

  public String[] getMembers(String host,String domain,String family) throws HdbFailed {
    constructBrowser();
    return browser.getMembers(host, domain, family);
  }

  public String[] getNames(String host,String domain,String family,String member) throws HdbFailed {
    constructBrowser();
    return browser.getNames(host, domain, family, member);
  }

  public HdbSigInfo getSigInfo(String attName) throws HdbFailed {

    SignalInfo ret = prepareSigInfo(attName);
    attName = ret.name;

    String query = "SELECT att_conf.att_conf_id,att_conf_data_type.data_type FROM att_conf,att_conf_data_type WHERE " +
                   "att_conf.att_conf_data_type_id=att_conf_data_type.att_conf_data_type_id " +
                   "AND att_name='" + attName + "'";

    try {
      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      ResultSet resultSet = statement.executeQuery(query);
      if(resultSet.next()) {
        ret.sigId = resultSet.getString(1);
        String data_type = resultSet.getString(2);
        ret.setTypeAccessFormatFromName(data_type);
        ret.tableName = "att_" + data_type;
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

    if(sigInfo==null)
      throw new HdbFailed("sigInfo input parameters is null");

    checkDates(start_date,stop_date);

    if(sigInfo.isArray()) {
      return getArrayData(sigInfo, sigInfo.sigId, start_date, stop_date);
    } else {
      return getScalarData(sigInfo, sigInfo.sigId, start_date, stop_date);
    }

  }

  public  HdbSigParam getLastParam(SignalInfo sigInfo) throws HdbFailed {

    String query = "SELECT recv_time,insert_time,label,unit,standard_unit,display_unit,format,"+
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
        ret.insertTime = timeValue(rs.getTimestamp(2));
        ret.label = rs.getString(3);
        ret.unit = rs.getString(4);
        try {
          ret.standard_unit = Double.parseDouble(rs.getString(5));
        } catch (NumberFormatException e) {
          ret.standard_unit = 1.0;
        }
        try {
          ret.display_unit = Double.parseDouble( rs.getString(6));
        } catch (NumberFormatException e) {
          ret.display_unit = 1.0;
        }
        ret.format = rs.getString(7);
        ret.archive_rel_change = rs.getString(8);
        ret.archive_abs_change = rs.getString(9);
        ret.archive_period = rs.getString(10);
        ret.description = rs.getString(11);

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

    String query = "SELECT recv_time,insert_time,label,unit,standard_unit,display_unit,format,"+
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
        hd.insertTime = timeValue(rs.getTimestamp(2));
        hd.label = rs.getString(3);
        hd.unit = rs.getString(4);
        try {
          hd.standard_unit = Double.parseDouble(rs.getString(5));
        } catch (NumberFormatException e) {
          hd.standard_unit = 1.0;
        }
        try {
          hd.display_unit = Double.parseDouble( rs.getString(6));
        } catch (NumberFormatException e) {
          hd.display_unit = 1.0;
        }
        hd.format = rs.getString(7);
        hd.archive_rel_change = rs.getString(8);
        hd.archive_abs_change = rs.getString(9);
        hd.archive_period = rs.getString(10);
        hd.description = rs.getString(11);

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

  protected HdbDataSet getArrayData(SignalInfo info,
                                  String sigId,
                                  String start_date,
                                  String stop_date) throws HdbFailed {

    boolean isRW = info.isRW();

    String query;
    int queryCount=0;
    String tablename = info.tableName;
    if (hasProgressListener()) {

      // Get a count of the request
      query = "SELECT count(*) FROM " + tablename +
          " WHERE att_conf_id='" + sigId + "'" +
          " AND data_time>='" + toDBDate(start_date) + "'" +
          " AND data_time<='" + toDBDate(stop_date) + "'";

      try {

        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

        ResultSet rs = statement.executeQuery(query);
        rs.next();
        queryCount = rs.getInt(1);
        statement.close();

      } catch (SQLException e) {
        throw new HdbFailed("Failed to get data: " + e.getMessage());
      }

    }

    // Fetch data

    String rwField = isRW?",value_w":"";
        query = "SELECT data_time,recv_time,insert_time,att_error_desc.error_desc as error_desc,quality,idx,value_r"+rwField+
        " FROM " + tablename +
        " left outer join att_error_desc on "+ tablename+".att_error_desc_id = att_error_desc.att_error_desc_id" +
        " WHERE att_conf_id='" + sigId + "'" +
        " AND data_time>='" + toDBDate(start_date) + "'" +
        " AND data_time<='" + toDBDate(stop_date) + "'" +
        " ORDER BY data_time,idx ASC";

    ArrayList<HdbData> ret = new ArrayList<HdbData>();
    ArrayList<Object> value = new ArrayList<Object>();
    ArrayList<Object> wvalue = null;
    if(isRW) wvalue = new ArrayList<Object>();

    try {

      long dTime = 0;
      long newTime = 0;
      long recvTime = 0;
      long insertTime = 0;
      String errorMsg = null;
      int quality = 0;
      int nbRow = 0;
      boolean newItem = false;

      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      statement.setFetchSize(arrayFetchSize);
      ResultSet rs = statement.executeQuery(query);
      while(rs.next()) {

        newTime = timeValue(rs.getTimestamp(1));

        if( dTime!=newTime ) {

          // Store current Item
          if(newItem) {

            HdbData hd = HdbData.createData(info);
            hd.parse(
                dTime,     //Tango timestamp
                recvTime,  //Event recieve timestamp
                insertTime,//Recording timestamp
                errorMsg,  // Error string
                quality,   // Quality value
                value,     // Read value
                wvalue     // Write value
            );
            ret.add(hd);

          }

          // New Item
          recvTime = timeValue(rs.getTimestamp(2));
          insertTime = timeValue(rs.getTimestamp(3));
          errorMsg = rs.getString(4);
          quality = rs.getInt(5);
          newItem = true;

          value.clear();
          if(isRW) wvalue.clear();

          dTime = newTime;

        }

        value.add(rs.getString(7));
        if(isRW)
            wvalue.add(rs.getString(8));

        if(hasProgressListener() && (nbRow% PROGRESS_NBROW ==0))
          fireProgressListener((double)nbRow/(double)queryCount);

        nbRow++;

      }

      if( newItem ) {

        // Store last item
        HdbData hd = HdbData.createData(info);
        hd.parse(
            dTime,     // Tango timestamp
            recvTime,  // Event receive timestamp
            insertTime,// Recording timestamp
            errorMsg,  // Error string
            quality,   // Quality value
            value,     // Read value
            wvalue     // Write value
        );
        ret.add(hd);

      }

      statement.close();

    } catch (SQLException e) {
      throw new HdbFailed("Failed to get data: "+e.getMessage());
    }

    return new HdbDataSet(ret);

  }


  // ---------------------------------------------------------------------------------------

  protected HdbDataSet getScalarData(SignalInfo info,
                                   String sigId,
                                   String start_date,
                                   String stop_date) throws HdbFailed {

    String query;
    int queryCount=0;
    String tablename = info.tableName;
    if (hasProgressListener()) {

      // Get a count of the request
      query = "SELECT count(*) FROM " + tablename +
          " WHERE att_conf_id='" + sigId + "'" +
          " AND data_time>='" + toDBDate(start_date) + "'" +
          " AND data_time<='" + toDBDate(stop_date) + "'";

      try {

        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ResultSet rs = statement.executeQuery(query);
        rs.next();
        queryCount = rs.getInt(1);
        statement.close();

      } catch (SQLException e) {
        throw new HdbFailed("Failed to get data: " + e.getMessage());
      }

    }

    boolean isRW = info.isRW();
    String rwField = isRW?",value_w":"";
    query = "SELECT data_time,recv_time,insert_time, att_error_desc.error_desc as error_desc,quality,value_r"+rwField+
        " FROM " + tablename +
        " left outer join att_error_desc on "+ tablename+".att_error_desc_id = att_error_desc.att_error_desc_id" +
        " WHERE att_conf_id='" + sigId + "'" +
        " AND data_time>'" + toDBDate(start_date) + "'" +
        " AND data_time<'" + toDBDate(stop_date) + "'" +
        " ORDER BY data_time ASC";

    ArrayList<HdbData> ret = new ArrayList<HdbData>();
    ArrayList<Object> value = new ArrayList<Object>();
    ArrayList<Object> wvalue = null;
    if(isRW) wvalue = new ArrayList<Object>();
    int nbRow=0;

    try {

      Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
      statement.setFetchSize(fetchSize);
      ResultSet rs = statement.executeQuery(query);
      while(rs.next()) {

        HdbData hd = HdbData.createData(info);
        value.clear();
        value.add(rs.getString(6));
        if(isRW) {
          wvalue.clear();
          wvalue.add(rs.getString(7));
        }

        hd.parse(
            timeValue(rs.getTimestamp(1)),     //Tango timestamp
            timeValue(rs.getTimestamp(2)),     //Event recieve timestamp
            timeValue(rs.getTimestamp(3)),     //Recording timestamp
            rs.getString(4),                   // Error string
            rs.getInt(5),                      // Quality value
            value,                             // Read value
            wvalue                             // Write value
        );

        ret.add(hd);

        if(hasProgressListener() && (nbRow% PROGRESS_NBROW==0))
          fireProgressListener((double)nbRow/(double)queryCount);

        nbRow++;

      }

      statement.close();

    } catch (SQLException e) {
      throw new HdbFailed("Failed to get data: "+e.getMessage());
    }

    return new HdbDataSet(ret);

  }


  protected long timeValue(Timestamp ts) {

    long ret = ts.getTime();
    ret = ret / 1000;
    long us = ts.getNanos();
    us = us / 1000;
    ret = ret * 1000000;
    ret += us;
    return ret;

  }

  protected String toDBDate(String date) {

   // In:   09/07/2015 12:00:00
   // Out:  2015-07-09 12:00:00
   return date.substring(6,10) + "-" + date.substring(3,5) + "-" +
          date.substring(0,2) + " " + date.substring(11,19);

  }

}
