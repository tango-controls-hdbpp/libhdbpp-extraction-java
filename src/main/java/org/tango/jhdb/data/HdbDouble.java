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

import java.util.ArrayList;

/**
 * HDB double data
 */
public class HdbDouble extends HdbScalarData {

  double value = Double.NaN;
  double wvalue = Double.NaN;

  public static HdbData createData(SignalInfo info) throws HdbFailed
  {
    switch (info.format)
    {
      case SCALAR:
        return new HdbDouble(info);
      case SPECTRUM:
        return new HdbDoubleArray(info);
      default:
        throw new HdbFailed("Format :" + info.format + " not supported.");
    }
  }
  public HdbDouble(SignalInfo info) {
    super(info);
  }

  public HdbDouble(SignalInfo info, double value) {
    this(info);
    this.value = value;
  }

  public HdbDouble(SignalInfo info, double value, double wvalue) {
    this(info);
    this.value = value;
    this.wvalue = wvalue;
  }

  @Override
  public HdbDouble copyData()
  {
    return new HdbDouble(info, value, wvalue);
  }

  public double getValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return value;

  }

  public double getWriteValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return wvalue;

  }

  public void parseValue(ArrayList<Object> value) throws HdbFailed {

    this.value = parseDouble(value.get(0));

  }

  public void parseWriteValue(ArrayList<Object> value) throws HdbFailed {

    if(value!=null)
      this.wvalue = parseDouble(value.get(0));

  }

  private double parseDouble(Object value) throws HdbFailed {

    double ret;

    if (value instanceof String) {

      // Value given as String
      try {
        String str = (String)value;
        if (str == null)
          ret = Double.NaN;
        else
          ret = Double.parseDouble(str);
      } catch (NumberFormatException e) {
        throw new HdbFailed("parseDouble: Invalid number syntax for write value");
      }

    } else {

      Double d = (Double)value;
      if(d==null)
        ret = Double.NaN;
      else
        ret = d.doubleValue();

    }

    return ret;

  }

  public String toString() {

    if(hasFailed())
      return timeToStr(dataTime)+": "+errorMessage;

    if(!hasWriteValue())
      return timeToStr(dataTime)+": "+Double.toString(value)+" "+qualitytoStr(qualityFactor);
    else
      return timeToStr(dataTime)+": "+Double.toString(value)+";"+Double.toString(wvalue)+" "+
             qualitytoStr(qualityFactor);

  }

  // Convenience function
  public void applyConversionFactor(double f) {
    value = (value * f);
    wvalue = (wvalue * f);
  }
  int dataSize() {
    return 1;
  }
  int dataSizeW() {
    if(hasWriteValue())
      return 1;
    else
      return 0;
  }

  public String getValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(isInvalid())
      return "ATTR_INVALID";
    return Double.toString(value);
  }

  public String getWriteValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(hasWriteValue()) {
      if(isInvalid())
        return "ATTR_INVALID";
      return Double.toString(wvalue);
    } else
      return "";
  }

  public double getValueAsDouble() throws HdbFailed {
    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    if(isInvalid())
      return Double.NaN;
    return value;
  }

  public double getWriteValueAsDouble() throws HdbFailed {
    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    if(hasWriteValue()) {
      if(isInvalid())
        return Double.NaN;
      return wvalue;
    } else {
      throw new HdbFailed("This datum has no write value");
    }
  }

  public double[] getValueAsDoubleArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an array");
  }

  public double[] getWriteValueAsDoubleArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an array");
  }

  public long getValueAsLong() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }

  public long getWriteValueAsLong() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }

  public long[] getValueAsLongArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }

  public long[] getWriteValueAsLongArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }

}
