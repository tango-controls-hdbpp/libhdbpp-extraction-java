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
import org.tango.jhdb.HdbSigInfo;

import java.util.ArrayList;

/**
 * HDB short data (16 bits integer)
 */
public class HdbShort extends HdbData {

  short value = 0;
  short wvalue = 0;

  public HdbShort(int type) {
    this.type = type;
  }

  public HdbShort(int type,short value) {
    this.type = type;
    this.value = value;
  }

  public short getValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return value;

  }

  public short getWriteValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return wvalue;

  }

  public void parseValue(ArrayList<Object> value) throws HdbFailed {

    this.value = parseShort(value.get(0));

  }

  public void parseWriteValue(ArrayList<Object> value) throws HdbFailed {

    if(value!=null)
      this.wvalue = parseShort(value.get(0));

  }

  private short parseShort(Object value) throws HdbFailed {

    short ret;

    if (value instanceof String) {

      // Value given as string
      try {
        String str = (String) value;
        if (str == null)
          ret = 0;
        else
          ret = Short.parseShort(str);
      } catch (NumberFormatException e) {
        throw new HdbFailed("parseShort: Invalid number syntax for value");
      }

    } else {

      Short s = (Short) value;
      ret = s.shortValue();

    }

    return ret;

  }

  public String toString() {

    if(hasFailed())
      return timeToStr(dataTime)+": "+errorMessage;

    if(type== HdbSigInfo.TYPE_SCALAR_SHORT_RO)
      return timeToStr(dataTime)+": "+Short.toString(value)+" "+qualitytoStr(qualityFactor);
    else
      return timeToStr(dataTime)+": "+Short.toString(value)+";"+Short.toString(wvalue)+" "+
          qualitytoStr(qualityFactor);

  }

  // Convenience function
  public void applyConversionFactor(double f) {
    value = (short)(value * f);
    wvalue = (short)(wvalue * f);
  }
  int dataSize() {
    return 1;
  }
  int dataSizeW() {
    if(HdbSigInfo.isRWType(type))
      return 1;
    else
      return 0;
  }


  void copyData(HdbData src) {
    this.value = ((HdbShort)src).value;
    this.wvalue = ((HdbShort)src).wvalue;
  }

  public String getValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(isInvalid())
      return "ATTR_INVALID";
    return Short.toString(value);
  }

  public String getWriteValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(hasWriteValue()) {
      if(isInvalid())
        return "ATTR_INVALID";
      return Short.toString(wvalue);
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

  public double[] getValueAsDoubleArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an array");
  }

  public double[] getWriteValueAsDoubleArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an array");
  }

  public long getValueAsLong() throws HdbFailed {
    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return (long)value;
  }

  public long getWriteValueAsLong() throws HdbFailed {
    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    if(hasWriteValue()) {
      return (long)wvalue;
    } else {
      throw new HdbFailed("This datum has no write value");
    }
  }

  public long[] getValueAsLongArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an array");
  }

  public long[] getWriteValueAsLongArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an array");
  }

}
