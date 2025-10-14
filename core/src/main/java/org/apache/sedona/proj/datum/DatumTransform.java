/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sedona.proj.datum;

import org.apache.sedona.proj.constants.Datum;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.core.Point;

/**
 * Datum transformation utilities for converting between different geodetic datums. This class
 * implements the same functionality as the JavaScript datum_transform.js module.
 */
public class DatumTransform {

  /**
   * Transforms a point from one datum to another.
   *
   * @param source the source datum
   * @param dest the destination datum
   * @param point the point to transform
   * @return transformed point, or null if transformation fails
   */
  public static Point transform(Datum.DatumDef source, Datum.DatumDef dest, Point point) {
    // Short cut if the datums are identical
    if (compareDatums(source, dest)) {
      return point; // in this case, zero is success
    }

    // Explicitly skip datum transform by setting 'datum=none' as parameter for either source or
    // dest
    if (source.datumType == Values.PJD_NODATUM || dest.datumType == Values.PJD_NODATUM) {
      return point;
    }

    // If this datum requires grid shifts, then apply it to geodetic coordinates
    double source_a = source.a;
    double source_es = source.es;
    if (source.datumType == Values.PJD_GRIDSHIFT) {
      int gridShiftCode = GridShift.applyGridShift(source, false, point);
      if (gridShiftCode != 0) {
        return null;
      }
      source_a = Values.SRS_WGS84_SEMIMAJOR;
      source_es = Values.SRS_WGS84_ESQUARED;
    }

    double dest_a = dest.a;
    double dest_b = dest.b;
    double dest_es = dest.es;
    if (dest.datumType == Values.PJD_GRIDSHIFT) {
      dest_a = Values.SRS_WGS84_SEMIMAJOR;
      dest_b = Values.SRS_WGS84_SEMIMINOR;
      dest_es = Values.SRS_WGS84_ESQUARED;
    }

    // Do we need to go through geocentric coordinates?
    if (source_es == dest_es
        && source_a == dest_a
        && !checkParams(source.datumType)
        && !checkParams(dest.datumType)) {
      return point;
    }

    // Convert to geocentric coordinates
    point = geodeticToGeocentric(point, source_es, source_a);

    // Convert between datums
    if (checkParams(source.datumType)) {
      point = geocentricToWgs84(point, source.datumType, source.datum_params);
    }
    if (checkParams(dest.datumType)) {
      point = geocentricFromWgs84(point, dest.datumType, dest.datum_params);
    }

    point = geocentricToGeodetic(point, dest_es, dest_a, dest_b);

    if (dest.datumType == Values.PJD_GRIDSHIFT) {
      int destGridShiftResult = GridShift.applyGridShift(dest, true, point);
      if (destGridShiftResult != 0) {
        return null;
      }
    }

    return point;
  }

  /**
   * Compares two datums to see if they are identical.
   *
   * @param source the source datum
   * @param dest the destination datum
   * @return true if the datums are identical
   */
  public static boolean compareDatums(Datum.DatumDef source, Datum.DatumDef dest) {
    if (source.datumType != dest.datumType) {
      return false; // false, datums are not equal
    } else if (source.a != dest.a || Math.abs(source.es - dest.es) > 0.000000000050) {
      // the tolerance for es is to ensure that GRS80 and WGS84
      // are considered identical
      return false;
    } else if (source.datumType == Values.PJD_3PARAM) {
      return (source.datum_params[0] == dest.datum_params[0]
          && source.datum_params[1] == dest.datum_params[1]
          && source.datum_params[2] == dest.datum_params[2]);
    } else if (source.datumType == Values.PJD_7PARAM) {
      return (source.datum_params[0] == dest.datum_params[0]
          && source.datum_params[1] == dest.datum_params[1]
          && source.datum_params[2] == dest.datum_params[2]
          && source.datum_params[3] == dest.datum_params[3]
          && source.datum_params[4] == dest.datum_params[4]
          && source.datum_params[5] == dest.datum_params[5]
          && source.datum_params[6] == dest.datum_params[6]);
    } else {
      return true; // datums are equal
    }
  }

  /**
   * Checks if datum parameters are available for transformation.
   *
   * @param datumType the datum type
   * @return true if parameters are available
   */
  private static boolean checkParams(int datumType) {
    return datumType == Values.PJD_3PARAM || datumType == Values.PJD_7PARAM;
  }

  /**
   * Converts geodetic coordinates to geocentric coordinates.
   *
   * @param p the geodetic point (longitude, latitude, height)
   * @param es the eccentricity squared
   * @param a the semi-major axis
   * @return geocentric point (x, y, z)
   */
  public static Point geodeticToGeocentric(Point p, double es, double a) {
    double longitude = p.x;
    double latitude = p.y;
    double height = p.z; // Z value not always supplied

    double Rn; /*  Earth radius at location  */
    double sin_lat; /*  Math.sin(Latitude)  */
    double sin2_lat; /*  Square of Math.sin(Latitude)  */
    double cos_lat; /*  Math.cos(Latitude)  */

    /*
     ** Don't blow up if Latitude is just a little out of the value
     ** range as it may just be a rounding issue.  Also removed longitude
     ** test, it should be wrapped by Math.cos() and Math.sin().  NFW for PROJ.4, Sep/2001.
     */
    if (latitude < -Values.HALF_PI && latitude > -1.001 * Values.HALF_PI) {
      latitude = -Values.HALF_PI;
    } else if (latitude > Values.HALF_PI && latitude < 1.001 * Values.HALF_PI) {
      latitude = Values.HALF_PI;
    } else if (latitude < -Values.HALF_PI) {
      /* Latitude out of range */
      return new Point(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, p.z, p.m);
    } else if (latitude > Values.HALF_PI) {
      /* Latitude out of range */
      return new Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, p.z, p.m);
    }

    if (longitude > Math.PI) {
      longitude -= (2 * Math.PI);
    }
    sin_lat = Math.sin(latitude);
    cos_lat = Math.cos(latitude);
    sin2_lat = sin_lat * sin_lat;
    Rn = a / (Math.sqrt(1.0e0 - es * sin2_lat));
    return new Point(
        (Rn + height) * cos_lat * Math.cos(longitude),
        (Rn + height) * cos_lat * Math.sin(longitude),
        ((Rn * (1 - es)) + height) * sin_lat,
        p.m);
  }

  /**
   * Converts geocentric coordinates to geodetic coordinates.
   *
   * @param p the geocentric point (x, y, z)
   * @param es the eccentricity squared
   * @param a the semi-major axis
   * @param b the semi-minor axis
   * @return geodetic point (longitude, latitude, height)
   */
  public static Point geocentricToGeodetic(Point p, double es, double a, double b) {
    /* local definitions and variables */
    /* end-criterium of loop, accuracy of sin(Latitude) */
    double genau = 1e-12;
    double genau2 = (genau * genau);
    int maxiter = 30;

    double P; /* distance between semi-minor axis and location */
    double RR; /* distance between center and location */
    double CT; /* sin of geocentric latitude */
    double ST; /* cos of geocentric latitude */
    double RX;
    double RK;
    double RN; /* Earth radius at location */
    double CPHI0; /* cos of start or old geodetic latitude in iterations */
    double SPHI0; /* sin of start or old geodetic latitude in iterations */
    double CPHI; /* cos of searched geodetic latitude */
    double SPHI; /* sin of searched geodetic latitude */
    double SDPHI; /* end-criterium: addition-theorem of sin(Latitude(iter)-Latitude(iter-1)) */
    int iter; /* # of continuous iteration, max. 30 is always enough (s.a.) */

    double X = p.x;
    double Y = p.y;
    double Z = p.z; // Z value not always supplied
    double longitude;
    double latitude;
    double height;

    P = Math.sqrt(X * X + Y * Y);
    RR = Math.sqrt(X * X + Y * Y + Z * Z);

    /*      special cases for latitude and longitude */
    if (P / a < genau) {
      /*  special case, if P=0. (X=0., Y=0.) */
      longitude = 0.0;

      /*  if (X,Y,Z)=(0.,0.,0.) then Height becomes semi-minor axis
       *  of ellipsoid (=center of mass), Latitude becomes PI/2 */
      if (RR / a < genau) {
        latitude = Values.HALF_PI;
        height = -b;
        return new Point(p.x, p.y, p.z, p.m);
      }
    } else {
      /*  ellipsoidal (geodetic) longitude
       *  interval: -PI < Longitude <= +PI */
      longitude = Math.atan2(Y, X);
    }

    /* --------------------------------------------------------------
     * Following iterative algorithm was developed by
     * "Institut for Erdmessung", University of Hannover, July 1988.
     * Internet: www.ife.uni-hannover.de
     * Iterative computation of CPHI,SPHI and Height.
     * Iteration of CPHI and SPHI to 10**-12 radian resp.
     * 2*10**-7 arcsec.
     * --------------------------------------------------------------
     */
    CT = Z / RR;
    ST = P / RR;
    RX = 1.0 / Math.sqrt(1.0 - es * (2.0 - es) * ST * ST);
    CPHI0 = ST * (1.0 - es) * RX;
    SPHI0 = CT * RX;
    iter = 0;

    /* loop to find sin(Latitude) resp. Latitude
     * until |sin(Latitude(iter)-Latitude(iter-1))| < genau */
    do {
      iter++;
      RN = a / Math.sqrt(1.0 - es * SPHI0 * SPHI0);

      /*  ellipsoidal (geodetic) height */
      height = P * CPHI0 + Z * SPHI0 - RN * (1.0 - es * SPHI0 * SPHI0);

      RK = es * RN / (RN + height);
      RX = 1.0 / Math.sqrt(1.0 - RK * (2.0 - RK) * ST * ST);
      CPHI = ST * (1.0 - RK) * RX;
      SPHI = CT * RX;
      SDPHI = SPHI * CPHI0 - CPHI * SPHI0;
      CPHI0 = CPHI;
      SPHI0 = SPHI;
    } while (SDPHI * SDPHI > genau2 && iter < maxiter);

    /*      ellipsoidal (geodetic) latitude */
    latitude = Math.atan(SPHI / Math.abs(CPHI));
    return new Point(longitude, latitude, height, p.m);
  }

  /**
   * Transforms geocentric coordinates to WGS84.
   *
   * @param p the geocentric point
   * @param datumType the datum type
   * @param datumParams the datum parameters
   * @return transformed point
   */
  public static Point geocentricToWgs84(Point p, int datumType, double[] datumParams) {
    if (datumType == Values.PJD_3PARAM) {
      return new Point(p.x + datumParams[0], p.y + datumParams[1], p.z + datumParams[2], p.m);
    } else if (datumType == Values.PJD_7PARAM) {
      double Dx_BF = datumParams[0];
      double Dy_BF = datumParams[1];
      double Dz_BF = datumParams[2];
      double Rx_BF = datumParams[3];
      double Ry_BF = datumParams[4];
      double Rz_BF = datumParams[5];
      double M_BF = datumParams[6];

      return new Point(
          M_BF * (p.x - Rz_BF * p.y + Ry_BF * p.z) + Dx_BF,
          M_BF * (Rz_BF * p.x + p.y - Rx_BF * p.z) + Dy_BF,
          M_BF * (-Ry_BF * p.x + Rx_BF * p.y + p.z) + Dz_BF,
          p.m);
    }
    return p;
  }

  /**
   * Transforms geocentric coordinates from WGS84.
   *
   * @param p the geocentric point
   * @param datumType the datum type
   * @param datumParams the datum parameters
   * @return transformed point
   */
  public static Point geocentricFromWgs84(Point p, int datumType, double[] datumParams) {
    if (datumType == Values.PJD_3PARAM) {
      return new Point(p.x - datumParams[0], p.y - datumParams[1], p.z - datumParams[2], p.m);
    } else if (datumType == Values.PJD_7PARAM) {
      double Dx_BF = datumParams[0];
      double Dy_BF = datumParams[1];
      double Dz_BF = datumParams[2];
      double Rx_BF = datumParams[3];
      double Ry_BF = datumParams[4];
      double Rz_BF = datumParams[5];
      double M_BF = datumParams[6];
      double x_tmp = (p.x - Dx_BF) / M_BF;
      double y_tmp = (p.y - Dy_BF) / M_BF;
      double z_tmp = (p.z - Dz_BF) / M_BF;

      return new Point(
          x_tmp + Rz_BF * y_tmp - Ry_BF * z_tmp,
          -Rz_BF * x_tmp + y_tmp + Rx_BF * z_tmp,
          Ry_BF * x_tmp - Rx_BF * y_tmp + z_tmp,
          p.m);
    }
    return p;
  }
}
