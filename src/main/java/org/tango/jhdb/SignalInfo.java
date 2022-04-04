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
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Signal info structure
 */
public class SignalInfo {

  /***
   * Format of the stored attribute.
   */
  public static enum Format
  {
    SCALAR,
    SPECTRUM,
    IMAGE,
    UNKNOWN;

    public static boolean isArray(Format type) {
      switch(type)
      {
        case SPECTRUM:
          return true;
        default:
          return false;
      }
    }

    }

  /***
   * Data type of the stored attribute.
   * This is contains any types that can be stored in hdb
   */
  public static enum Type
  {
    DOUBLE,
    FLOAT,
    LONG,
    LONG64,
    SHORT,
    CHAR,
    ULONG,
    ULONG64,
    USHORT,
    UCHAR,
    BOOLEAN,
    STRING,
    ENUM,
    STATE,
    ENCODED,
    UNKNOWN;

    public static boolean isState(Type type)
    {
      switch (type) {
        case STATE:
          return true;
        default:
          return false;
      }
    }
    public static boolean isInteger(Type type) {
      switch(type) {
        case LONG64:
        case CHAR:
        case UCHAR:
        case SHORT:
        case USHORT:
        case LONG:
        case ULONG:
        case STATE:
        case ENUM:
        case BOOLEAN:
        case ULONG64:
          return true;
        default:
          return false;
      }

    }
    public static boolean isString(Type type) {

      switch(type) {
        case STRING:
          return true;
        default:
          return false;
      }

    }
    public static boolean isNumeric(Type type) {

      switch(type) {
        case ENCODED:
        case STRING:
        case UNKNOWN:
          return false;
        default:
          return true;
      }

    }
  }

  /***
   * Accessibility level of the stored attribute.
   */
  public static enum Access
  {
    RO,
    RW,
    WO,
    UNKNOWN;

    public static boolean isRW(Access type) {
      switch(type)
      {
        case RW:
          return true;
        default:
          return false;
      }
    }
  }

  public static enum Interval
  {
    NONE("RAW"),
    ONE_MIN("1min"),
    TEN_MIN("10min"),
    ONE_HOUR("1hour"),
    EIGHT_HOUR("8hour"),
    ONE_DAY("1day");

    private String desc;

    private Interval(String desc)
    {
      this.desc = desc;
    }

    private static boolean isAggregate(Interval interval)
    {
        return interval != NONE;
    }

    public String toString()
    {
      return desc;
    }
  }

  public static class Range implements Comparable<Range>
  {
      int start_idx;
      int end_idx;

      public static Range FULL_RANGE = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

      public Range(int start, int end)
      {
          start_idx = start;
          end_idx = end;
      }

      public String toString()
      {
          if(this == FULL_RANGE)
              return "";
          // We set +1 as is postgres indexing starts at 1, it should be done differently.
          if(start_idx == end_idx)
              return "[" + (start_idx+1) + "]";
          return "[" + (start_idx+1) + ":" + (end_idx+1) + "]";
      }

      public int size()
      {
          return end_idx - start_idx + 1;
      }

      @Override
      public int compareTo(Range o)
      {
          if(start_idx != o.start_idx)
          {
              return start_idx - o.start_idx;
          }
          return end_idx - o.end_idx;
      }
  }

  public String  name;          // Attribute name
  public String  sigId;         // Identifier
  public Format  format;        // Data type
  public Type    dataType;      // Data type
  public String  tableName;     // Table name
  public boolean isWO;          // Write only flag
  public int     queryConfig=0; // Flag to query config
  public Access  access;        // Write only flag
  public Interval interval = Interval.NONE; // interval, for aggregates
  public Set<HdbData.Aggregate> aggregates;
  public SortedSet<Integer> indexes;  // For arrays, indexes to be extracted

  public SignalInfo()
  {
  }

  public SignalInfo(SignalInfo parent)
  {
    this.name = parent.name;
    this.sigId = parent.sigId;
    this.format = parent.format;
    this.dataType = parent.dataType;
    this.tableName = parent.tableName;
    this.isWO = parent.isWO;
    this.queryConfig = parent.queryConfig;
    this.access = parent.access;
    this.interval = parent.interval;
    this.aggregates = parent.aggregates;
    this.indexes = parent.indexes;
  }

  protected SignalInfo(Type type, Format fmt, Access acc)
  {
    dataType = type;
    format = fmt;
    access = acc;
  }
  
  public void setTypeAccessFormatFromName(String stype) throws HdbFailed
  {
    boolean error = false;
    String[] confs = stype.toLowerCase().split("_");
    if(confs[0].equalsIgnoreCase("scalar"))
    {
      format = Format.SCALAR;
    }
    else if (confs[0].equalsIgnoreCase("array"))
    {
      format = Format.SPECTRUM;
    }
    else
    {
      error = true;
      format = Format.UNKNOWN;
    }

    if(confs[2].equalsIgnoreCase("ro"))
    {
      access = Access.RO;
    }
    else if (confs[2].equalsIgnoreCase("rw"))
    {
      access = Access.RW;
    }
    else
    {
      error = true;
      access = Access.UNKNOWN;
    }

    if (confs[1].equalsIgnoreCase("devulong64")) {
      dataType = Type.ULONG64;
    } else if (confs[1].equalsIgnoreCase("devstring")) {
      dataType = Type.STRING;
    } else if (confs[1].equalsIgnoreCase("devlong64")) {
      dataType = Type.LONG64;
    } else if (confs[1].equalsIgnoreCase("devfloat")) {
      dataType = Type.FLOAT;
    } else if (confs[1].equalsIgnoreCase("devdouble")) {
      dataType = Type.DOUBLE;
    } else if (confs[1].equalsIgnoreCase("devlong")) {
      dataType = Type.LONG;
    } else if (confs[1].equalsIgnoreCase("devuchar")) {
      dataType = Type.UCHAR;
    } else if (confs[1].equalsIgnoreCase("devencoded")) {
      dataType = Type.ENCODED;
    } else if (confs[1].equalsIgnoreCase("devushort")) {
      dataType = Type.USHORT;
    } else if (confs[1].equalsIgnoreCase("devboolean")) {
      dataType = Type.BOOLEAN;
    } else if (confs[1].equalsIgnoreCase("devstate")) {
      dataType = Type.STATE;
    } else if (confs[1].equalsIgnoreCase("devshort")) {
      dataType = Type.SHORT;
    }  else if (confs[1].equalsIgnoreCase("devulong")) {
      dataType = Type.ULONG;
    } else if (confs[1].equalsIgnoreCase("devchar")) {
      dataType = Type.CHAR;
    } else if (confs[1].equalsIgnoreCase("devenum")) {
      dataType = Type.ENUM;
    } else {
      dataType = Type.UNKNOWN;
      error = true;
    }
    if(error)
    {
      throw new HdbFailed("'" + stype + "' : Unknown type");
    }
  }

  /**
   * Returns true if this attribute is read/write.
   */
  public boolean isRW() {
    return Access.isRW(access);
  }

  /**
   * Returns true if this attribute is an array.
   */
  public boolean isArray() {
    return Format.isArray(format);
  }

  /**
   * Returns true if this attribute is numeric.
   */
  public boolean isNumeric() {
    return Type.isNumeric(dataType);
  }

  /**
   * Returns true if this attribute is an integer or integer array.
   */
  public boolean isInteger() {
    return Type.isInteger(dataType);
  }

  /**
   * Returns true if this attribute is a string or string array.
   */
  public boolean isString() {
    return Type.isString(dataType);
  }

  /**
   * Returns true if this attribute is a state or state array.
   */
  public boolean isState() {
    return Type.isState(dataType);
  }

  /**
   * Returns true if this signal is aggregated data, false if it is raw.
   */
  public boolean isAggregate() {
    return Interval.isAggregate(interval);
  }

  public String toString() {
    return "Id=" + sigId + ", Type=" + dataType.toString() + ", Format=" + format.toString() + ", Access=" + access.toString()+ ", Interval=" + interval.toString();
  }

  public SortedSet<Range> getRanges()
  {
      SortedSet<Range> ret = new TreeSet<>();
      if(indexes == null || indexes.isEmpty())
      {
          ret.add(Range.FULL_RANGE);
      }
      else
      {
          int start = -2;
          int prev = -2;
          boolean inRange = false;
          for(int idx : indexes)
          {
              if(idx - prev > 1)
              {
                  if(inRange)
                  {
                      ret.add(new Range(start, prev));
                      start = idx;
                  }
                  else
                  {
                      inRange = true;
                      start = idx;
                  }
              }
              prev = idx;
          }
          if(inRange)
              ret.add(new Range(start, prev));
      }
      return ret;
  }

  @Override
  public boolean equals(Object info)
  {
    if(this == info)
      return true;
    if(info == null)
      return false;
    if(getClass() != info.getClass())
      return false;
    SignalInfo o = (SignalInfo) info;
    return o.format == format && o.dataType == dataType && o.access == access && o.interval == interval;
  }

  @Override
  public int hashCode()
  {
    return 1000 * dataType.ordinal() + 100 * interval.ordinal() + 10 * format.ordinal() + access.ordinal();
  }

  public boolean isFloating()
  {
    return dataType == Type.DOUBLE || dataType == Type.FLOAT;
  }
}
