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

package org.tango.jhdb.data;

import org.tango.jhdb.HdbFailed;
import org.tango.jhdb.SignalInfo;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 *  HdbData base class
 */
public abstract class HdbData {

  public static enum Aggregate
  {
    ROWS_COUNT("count_rows"),
    ERRORS_COUNT("count_errors"),
    COUNT_R("count_r"),
    NAN_COUNT_R("count_nan_r"),
    MEAN_R("mean_r"),
    MIN_R("min_r"),
    MAX_R("max_r"),
    STDDEV_R("stddev_r"),
    COUNT_W("count_w"),
    NAN_COUNT_W("count_nan_w"),
    MEAN_W("mean_w"),
    MIN_W("min_w"),
    MAX_W("max_w"),
    STDDEV_W("stddev_w");

    String description;

    private Aggregate(String desc)
    {
      description = desc;
    }

    public String toString()
    {
      return description;
    }
  }

  final protected static Map<Aggregate, List<Number>> EMPTY_AGGREGATE = new EnumMap<>(Aggregate.class);

  final static SimpleDateFormat dfr = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

  public SignalInfo info;
  long   dataTime;
  long   recvTime;
  long   insertTime;
  int    qualityFactor;
  String errorMessage=null;

  public HdbData(SignalInfo info)
  {
    this.info = info;
  }
  /**
   * Return time of this datum
   */
  public long getDataTime() {
    return dataTime;
  }

  /**
   * Set the dataTime
   */
  public void setDataTime(long time) {
    dataTime = time;
  }

  /**
   * Return receive time of this datum
   */
  public long getRecvTime() {
    return recvTime;
  }

  /**
   * Set the receive time
   */
  public void setRecvTime(long time) {
    recvTime = time;
  }

  /**
   * Return receive time of this datum
   */
  public long getInsertTime() {
    return insertTime;
  }

  /**
   * Returns quality factor
   */
  public int getQualityFactor() {
    return qualityFactor;
  }

  /**
   * Returns true if this record has failed
   */
  public boolean hasFailed() {
    return errorMessage!=null;
  }

  /**
   * Return number of item of the read value of this HdbData (1 is returned in case of scalar data).
   */
  public int size() {

    if(hasFailed())
      return 0;
    return dataSize();

  }

  /**
   * Return number of item of the write value of this HdbData (1 is returned in case of scalar data).
   */
  public int sizeW() {

    if(hasFailed())
      return 0;
    return dataSizeW();

  }

  /**
   * Returns error message if this record has failed
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Returns true whether this datum has a write value
   */
  public boolean hasWriteValue() {
    return info.isRW();
  }

  /**
   * Returns true whether this datum is an array
   */
  public boolean isArray() {
    return info.isArray();
  }

  /**
   * Returns the value as double if it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract double getValueAsDouble() throws HdbFailed;

  /**
   * Returns the value as double array it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract double[] getValueAsDoubleArray() throws HdbFailed;

  /**
   * Returns the write value as double if it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract double getWriteValueAsDouble() throws HdbFailed;

  /**
   * Returns the write value as double array it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract double[] getWriteValueAsDoubleArray() throws HdbFailed;

  /**
   * Returns the value as long if it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract long getValueAsLong() throws HdbFailed;

  /**
   * Returns the value as long array it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract long[] getValueAsLongArray() throws HdbFailed;

  /**
   * Returns the write value as long if it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract long getWriteValueAsLong() throws HdbFailed;

  /**
   * Returns the write value as long array it can be converted.
   * @throws HdbFailed In case of failure
   */
  public abstract long[] getWriteValueAsLongArray() throws HdbFailed;

  /**
   * Returns the aggregate values.
   * @throws HdbFailed In case of failure
   */
  public abstract Map<Aggregate, List<Number>> getAggregate() throws HdbFailed;

  /**
   * Parse value
   * @param value Value to be parsed
   */
  public abstract void parseValue(ArrayList<Object> value) throws HdbFailed;

  /**
   * Parse write value
   * @param value Value to be parsed
   */
  public abstract void parseWriteValue(ArrayList<Object> value) throws HdbFailed;


  /**
   * Returns the value as a String
   */
  public abstract String getValueAsString();

  /**
   * Returns the write value as a String
   */
  public abstract String getWriteValueAsString();

  /**
   * Parse value
   * @param data_time Tango timestamp
   * @param recv_time Event recieve timestamp
   * @param insert_time Recording timestamp
   * @param error_desc Error string
   * @param quality Quality value
   * @param value_r Read value
   * @param value_w Write value
   */
  public void parse(long data_time,long recv_time,long insert_time,String error_desc,int quality,
                    ArrayList<Object> value_r,ArrayList<Object> value_w) throws HdbFailed {

    dataTime = data_time;
    recvTime = recv_time;
    insertTime = insert_time;
    if(error_desc!=null)
      if(error_desc.isEmpty())
        error_desc = null;
    errorMessage = error_desc;
    qualityFactor = quality;

    if(!hasFailed()) {
      parseValue(value_r);
      parseWriteValue(value_w.isEmpty() ? null : value_w);
    }

  }

  public void parseAggregate(long dTime, long count_rows, long count_errors
          , ArrayList<Long> count_r, ArrayList<Long> count_nan_r, ArrayList<Double> mean_r, ArrayList<Number> min_r, ArrayList<Number> max_r, ArrayList<Double> stddev_r
          , ArrayList<Long> count_w, ArrayList<Long> count_nan_w, ArrayList<Double> mean_w, ArrayList<Number> min_w, ArrayList<Number> max_w, ArrayList<Double> stddev_w)
  {
    dataTime = dTime;
    doParseAggregate(count_rows, count_errors, count_r, count_nan_r, mean_r, min_r, max_r, stddev_r, count_w, count_nan_w, mean_w, min_w, max_w, stddev_w);
  }

  protected abstract void doParseAggregate(long count_rows, long count_errors
          , ArrayList<Long> count_r, ArrayList<Long> count_nan_r, ArrayList<Double> mean_r, ArrayList<Number> min_r, ArrayList<Number> max_r, ArrayList<Double> stddev_r
          , ArrayList<Long> count_w, ArrayList<Long> count_nan_w, ArrayList<Double> mean_w, ArrayList<Number> min_w, ArrayList<Number> max_w, ArrayList<Double> stddev_w);


  /**
   * Return time representation of the give time (ex: 22/07/2015 08:12:15.718908)
   * @param time Number of micro second since epoch
   */
  public String timeToStr(long time) {

    long ms = time/1000;
    Date d = new Date(ms);
    String dStr = dfr.format(d);
    String sStr = String.format("%06d",time%1000000);
    return dStr+"."+sStr;

  }

  /**
   * Returns true if this value is ATTR_INVALID
   */
  public boolean isInvalid() {
    return qualityFactor==1;
  }

  public String qualitytoStr(int quality) {

    switch(quality) {
      case 0:
        return "ATTR_VALID";
      case 1:
        return "ATTR_INVALID";
      case 2:
        return "ATTR_ALARM";
      case 3:
        return "ATTR_CHANGING";
      case 4:
        return "ATTR_WARNING";
      default:
        return "UNKNOWN QUALITY";
    }

  }

  // Convenience functions
  abstract int dataSize();
  abstract int dataSizeW();

  abstract HdbData copyData();

  public HdbData copy() throws HdbFailed {

    HdbData ret = copyData();
    ret.dataTime=dataTime;
    ret.recvTime=recvTime;
    ret.insertTime=insertTime;
    ret.qualityFactor=qualityFactor;
    ret.errorMessage=errorMessage;
    return ret;

  }

  public abstract void applyConversionFactor(double f);

  long parseInteger(Object obj) throws HdbFailed {

    long ret = 0;

    if(obj==null)
      return 0;

    if (obj instanceof Long) {

      return ((Long) obj).longValue();

    } else if (obj instanceof Integer) {

      return ((Integer) obj).longValue();

    } else if (obj instanceof Short) {

      return ((Short) obj).longValue();

    } else if (obj instanceof Byte) {

      return ((Byte) obj).longValue();

    } else if (obj instanceof String) {

      // Value given as string
      try {
        String str = (String) obj;
        ret = Long.parseLong(str);
      } catch (NumberFormatException e) {
        throw new HdbFailed("parseInteger: Invalid number syntax for value");
      }

    } else {

      throw new HdbFailed("parseInteger: Unexpected integer object " + obj.getClass().getName());

    }

    return ret;

  }

  /**
   * Create HdbData according to the given type
   * @param info Data type
   * @throws HdbFailed In case of failure
   */
  public static HdbData createData(SignalInfo info) throws HdbFailed {
    if(info.interval == SignalInfo.Interval.NONE) {
      switch (info.dataType) {
        case DOUBLE:
          return HdbDouble.createData(info);
        case FLOAT:
          return HdbFloat.createData(info);
        case BOOLEAN:
          return HdbBoolean.createData(info);
        case LONG:
          return HdbLong.createData(info);
        case LONG64:
          return HdbLong64.createData(info);
        case ULONG:
          return HdbULong.createData(info);
        case SHORT:
          return HdbShort.createData(info);
        case USHORT:
          return HdbUShort.createData(info);
        case UCHAR:
          return HdbUChar.createData(info);
        case STRING:
          return HdbString.createData(info);
        case STATE:
          return HdbState.createData(info);
        case ENCODED:
        case ENUM:
        case CHAR:
        case ULONG64:
          throw new HdbFailed("Type " + info.dataType.toString() + " not supported yet !");

        default:
          throw new HdbFailed("Unknown signal type=" + info.dataType + ", format=" + info.format);
      }
    }
    else
    {
      return new HdbAggregateData(info);
    }

  }

}
