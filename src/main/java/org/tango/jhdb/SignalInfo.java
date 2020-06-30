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

/**
 * Signal info structure
 */
public class SignalInfo {

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

  public String  name;          // Attribute name
  public String  sigId;         // Identifier
  public Format  format;        // Data type
  public Type    dataType;      // Data type
  public String  tableName;     // Table name
  public boolean isWO;          // Write only flag
  public int     queryConfig=0; // Flag to query config
  public Access  access;        // Write only flag

  public SignalInfo()
  {
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

    if (stype.equalsIgnoreCase("devulong64")) {
      dataType = Type.ULONG64;
    } else if (stype.equalsIgnoreCase("devstring")) {
      dataType = Type.STRING;
    } else if (stype.equalsIgnoreCase("devlong64")) {
      dataType = Type.LONG64;
    } else if (stype.equalsIgnoreCase("devfloat")) {
      dataType = Type.FLOAT;
    } else if (stype.equalsIgnoreCase("devdouble")) {
      dataType = Type.DOUBLE;
    } else if (stype.equalsIgnoreCase("devlong")) {
      dataType = Type.LONG;
    } else if (stype.equalsIgnoreCase("devuchar")) {
      dataType = Type.UCHAR;
    } else if (stype.equalsIgnoreCase("devencoded")) {
      dataType = Type.ENCODED;
    } else if (stype.equalsIgnoreCase("devushort")) {
      dataType = Type.USHORT;
    } else if (stype.equalsIgnoreCase("devboolean")) {
      dataType = Type.BOOLEAN;
    } else if (stype.equalsIgnoreCase("devstate")) {
      dataType = Type.STATE;
    } else if (stype.equalsIgnoreCase("devshort")) {
      dataType = Type.SHORT;
    }  else if (stype.equalsIgnoreCase("devulong")) {
      dataType = Type.ULONG;
    } else if (stype.equalsIgnoreCase("devchar")) {
      dataType = Type.CHAR;
    } else if (stype.equalsIgnoreCase("devenum")) {
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

  public String toString() {
    return "Id=" + sigId + ", Type=" + dataType.toString() + ", Format=" + format.toString() + ", Access=" + access.toString();
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
    return o.format == format && o.dataType == dataType && o.access == access;
  }

  @Override
  public int hashCode()
  {
    return 1000* dataType.ordinal()+ 10 * format.ordinal()+ access.ordinal();
  }

  public boolean isFloating()
  {
    return dataType == Type.DOUBLE || dataType == Type.FLOAT;
  }
}