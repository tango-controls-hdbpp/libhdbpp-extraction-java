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
import java.util.List;
import java.util.Properties;

/**
 * MySQL database access
 */
public class MySQLLightSchema extends MySQLSchema {

  public MySQLLightSchema(String url, Connection c) throws HdbFailed
  {
      super(url, c);
  }

  private List<String> parseJson(String json)
  {
      ArrayList<String> ret = new ArrayList<>();
      // This is not ideal but does not require extra dependencies to do the json parsing.
      int start = json.indexOf('[') + 1;
      int end = json.lastIndexOf(']');
      String[] array = json.substring(start, end).split(",");
      for(String val : array)
      {
          ret.add(val.trim());
      }
      return ret;
  }

  @Override
  public  HdbSigParam getLastParam(SignalInfo sigInfo) throws HdbFailed {

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

  @Override
  public ArrayList<HdbSigParam> getParams(SignalInfo sigInfo,
                                          String start_date,
                                          String stop_date) throws HdbFailed {

    checkDates(start_date,stop_date);

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

  // ---------------------------------------------------------------------------------------
  @Override
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
        query = "SELECT data_time,recv_time,att_error_desc.error_desc as error_desc,quality,value_r"+rwField+
        " FROM " + tablename +
        " left outer join att_error_desc on "+ tablename+".att_error_desc_id = att_error_desc.att_error_desc_id" +
        " WHERE att_conf_id='" + sigId + "'" +
        " AND data_time>='" + toDBDate(start_date) + "'" +
        " AND data_time<='" + toDBDate(stop_date) + "'" +
        " ORDER BY data_time ASC";

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

        HdbData hd = HdbData.createData(info);
        value.clear();
        value.addAll(parseJson(rs.getString(5)));
        if(isRW) {
          wvalue.clear();
          wvalue.addAll(parseJson(rs.getString(6)));
        }

        hd.parse(
            timeValue(rs.getTimestamp(1)),     //Tango timestamp
            0,                                 //Event recieve timestamp
            timeValue(rs.getTimestamp(2)),     //Recording timestamp
            rs.getString(3),                   // Error string
            rs.getInt(4),                      // Quality value
            value,                             // Read value
            wvalue                             // Write value
        );

        ret.add(hd);

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
  @Override
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
    query = "SELECT data_time,recv_time, att_error_desc.error_desc as error_desc,quality,value_r"+rwField+
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
        value.add(rs.getString(5));
        if(isRW) {
          wvalue.clear();
          wvalue.add(rs.getString(6));
        }

        hd.parse(
            timeValue(rs.getTimestamp(1)),     //Tango timestamp
            0,                                 //Event recieve timestamp
            timeValue(rs.getTimestamp(2)),     //Recording timestamp
            rs.getString(3),                   // Error string
            rs.getInt(4),                      // Quality value
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
}
