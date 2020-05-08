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
 * HDB string array data
 */
public class HdbStringArray extends HdbArrayData {

  String[] value = null;
  String[] wvalue = null;

  public HdbStringArray(HdbSigInfo info) {
    super(info);
  }

  public HdbStringArray(HdbSigInfo info, String[] value) {
    this(info);
    this.value = value.clone();
  }

  public String[] getValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return value;

  }

  public String[] getWriteValue() throws HdbFailed {

    if(hasFailed())
      throw new HdbFailed(this.errorMessage);
    return wvalue;

  }

  public void parseValue(ArrayList<Object> value) throws HdbFailed {

    this.value = parseArray(value);

  }

  public void parseWriteValue(ArrayList<Object> value) throws HdbFailed {

    if(value!=null)
      this.wvalue = parseArray(value);

  }

  private String[] parseArray(ArrayList<Object> value) throws HdbFailed {

    String[] ret = new String[value.size()];

    for(int i=0;i<value.size();i++) {
      String str = (String)value.get(i);
      if(str==null) {
        ret[i] = "NULL";
      } else {
        ret[i] = str;
      }
    }

    return ret;

  }

  // Convenience function
  public void applyConversionFactor(double f) {
    // Do nothing here
  }
  int dataSize() {
    if(value==null)
      return 0;
    else
      return value.length;
  }
  int dataSizeW() {
    if(hasWriteValue())
      if(wvalue==null)
        return 0;
      else
        return wvalue.length;
    else
      return 0;
  }

  void copyData(HdbData src) {
    value = ((HdbStringArray)src).value.clone();
    if(((HdbStringArray)src).wvalue!=null)
      wvalue = ((HdbStringArray)src).wvalue.clone();
  }

  public String getValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(isInvalid())
      return "ATTR_INVALID";
    return arrayValue(value);
  }

  public String getWriteValueAsString() {
    if(hasFailed())
      return errorMessage;
    if(hasWriteValue()) {
      if(isInvalid())
        return "ATTR_INVALID";
      return arrayValue(wvalue);
    } else
      return "";
  }

  private String arrayValue(String[] b) {
    StringBuffer ret = new StringBuffer();
    ret.append("String["+b.length+"]\n");
    for(int i=0;i<b.length;i++) {
      ret.append(b[i]);
      if(i<b.length-1)
        ret.append("\n");
    }
    return ret.toString();
  }

  public double[] getValueAsDoubleArray() throws HdbFailed {
    throw new HdbFailed("This datum cannot be converted to double");
  }

  public double[] getWriteValueAsDoubleArray() throws HdbFailed {
    throw new HdbFailed("This datum cannot be converted to double");
  }

  public long[] getValueAsLongArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }

  public long[] getWriteValueAsLongArray() throws HdbFailed {
    throw new HdbFailed("This datum is not an integer");
  }

}
