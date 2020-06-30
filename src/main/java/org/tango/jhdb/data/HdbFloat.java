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
 * HDB float data
 */
public class HdbFloat extends HdbScalarData {

  float value = Float.NaN;
  float wvalue = Float.NaN;

  public static HdbData createData(SignalInfo info) throws HdbFailed
  {
    switch (info.format)
    {
      case SCALAR:
        return new HdbFloat(info);
      case SPECTRUM:
        return new HdbFloatArray(info);
      default:
        throw new HdbFailed("Format :" + info.format + " not supported.");
    }
  }
  public HdbFloat(SignalInfo info) {
    super(info);
  }

  public HdbFloat(SignalInfo info, float value) {
    this(info);
    this.value = value;
  }

  public float getValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return value;

  }

  public float getWriteValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return wvalue;

  }

  public void parseValue(ArrayList<Object> value) throws HdbFailed {

    this.value = parseFloat(value.get(0));

  }

  public void parseWriteValue(ArrayList<Object> value) throws HdbFailed {

    if(value!=null)
      this.wvalue = parseFloat(value.get(0));

  }

  private float parseFloat(Object value) throws HdbFailed {

    float ret;

    if (value instanceof String) {

      // Value given as String
      try {
        String str = (String)value;
        if (str == null)
          ret = Float.NaN;
        else
          ret = Float.parseFloat(str);
      } catch (NumberFormatException e) {
        throw new HdbFailed("parseFloat: Invalid number syntax for write value");
      }

    } else {

      Float f = (Float)value;
      if(f==null)
        ret = Float.NaN;
      else
        ret = f.floatValue();

    }

    return ret;

  }

  // Convenience function
  public void applyConversionFactor(double f) {
    value = (float)(value * f);
    wvalue = (float)(wvalue * f);
  }

  void copyData(HdbData src) {
    this.value = ((HdbFloat)src).value;
    this.wvalue = ((HdbFloat)src).wvalue;
  }

  public String getValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(isInvalid())
      return "ATTR_INVALID";
    return Float.toString(value);
  }

  public String getWriteValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(hasWriteValue()) {
      if(isInvalid())
        return "ATTR_INVALID";
      return Float.toString(wvalue);
    } else
      return "";
  }

  public double getValueAsDouble() throws HdbFailed {
    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    if(isInvalid())
      return Double.NaN;
    return (double)value;
  }

  public double getWriteValueAsDouble() throws HdbFailed {
    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    if(hasWriteValue()) {
      if(isInvalid())
        return Double.NaN;
      return (double)wvalue;
    } else {
      throw new HdbFailed("This datum has no write value");
    }
  }

  public long getValueAsLong() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }

  public long getWriteValueAsLong() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }
}
