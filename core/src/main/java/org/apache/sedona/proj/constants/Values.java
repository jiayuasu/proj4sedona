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
package org.apache.sedona.proj.constants;

/**
 * Mathematical constants used throughout the projection calculations. These constants are
 * equivalent to the values defined in the JavaScript version.
 */
public final class Values {

  // Mathematical constants
  /** Mathematical constant PI */
  public static final double PI = Math.PI;

  /** Half of PI */
  public static final double HALF_PI = PI / 2.0;

  /** Quarter of PI */
  public static final double FORTPI = PI / 4.0;

  /** Two times PI */
  public static final double TWO_PI = 2.0 * PI;

  /** Slightly greater than PI for longitude wrapping */
  public static final double SPI = 3.14159265359;

  // Degree to radian conversion
  /** Degrees to radians conversion factor */
  public static final double D2R = PI / 180.0;

  /** Radians to degrees conversion factor */
  public static final double R2D = 180.0 / PI;

  // Epsilon values for floating point comparisons
  /** Epsilon value for floating point comparisons */
  public static final double EPSLN = 1.0e-10;

  /** Tolerance value for floating point comparisons */
  public static final double TOL = 1.0e-14;

  // Datum transformation types
  /** 3-parameter datum transformation */
  public static final int PJD_3PARAM = 1;

  /** 7-parameter datum transformation */
  public static final int PJD_7PARAM = 2;

  /** Grid shift datum transformation */
  public static final int PJD_GRIDSHIFT = 3;

  /** WGS84 datum transformation */
  public static final int PJD_WGS84 = 4;

  /** No datum transformation */
  public static final int PJD_NODATUM = 5;

  // Axis order constants
  /** East-North-Up axis */
  public static final String AXIS_ENU = "enu";

  /** North-East-Up axis */
  public static final String AXIS_NEU = "neu";

  /** North-Up-East axis */
  public static final String AXIS_NUE = "nue";

  /** West-Up-North axis */
  public static final String AXIS_WUN = "wun";

  /** West-North-Up axis */
  public static final String AXIS_WNU = "wnu";

  /** Up-South-West axis */
  public static final String AXIS_USW = "usw";

  /** Up-West-South axis */
  public static final String AXIS_UWS = "uws";

  /** South-Up-East axis */
  public static final String AXIS_SUE = "sue";

  /** South-East-Up axis */
  public static final String AXIS_SEU = "seu";

  /** East-South-Up axis */
  public static final String AXIS_ESU = "esu";

  /** East-Up-South axis */
  public static final String AXIS_EUS = "eus";

  // Default values
  /** Default scale factor */
  public static final double DEFAULT_K0 = 1.0;

  /** Default latitude of origin */
  public static final double DEFAULT_LAT0 = 0.0;

  /** Default longitude of origin */
  public static final double DEFAULT_LONG0 = 0.0;

  /** Default false easting */
  public static final double DEFAULT_X0 = 0.0;

  /** Default false northing */
  public static final double DEFAULT_Y0 = 0.0;

  // Earth radius (approximate)
  /** Earth radius in meters */
  public static final double EARTH_RADIUS = 6378137.0;

  // Common ellipsoid parameters
  /** WGS84 semi-major axis */
  public static final double WGS84_A = 6378137.0;

  /** WGS84 semi-minor axis */
  public static final double WGS84_B = 6356752.314245;

  /** WGS84 reciprocal of flattening */
  public static final double WGS84_RF = 298.257223563;

  // Additional constants from JavaScript version
  /** WGS84 semi-major axis */
  public static final double SRS_WGS84_SEMIMAJOR = 6378137.0;

  /** WGS84 semi-minor axis */
  public static final double SRS_WGS84_SEMIMINOR = 6356752.314;

  /** WGS84 eccentricity squared */
  public static final double SRS_WGS84_ESQUARED = 0.0066943799901413165;

  /** Seconds to radians conversion factor */
  public static final double SEC_TO_RAD = 4.84813681109535993589914102357e-6;

  /** Arcseconds to radians conversion factor */
  public static final double ARCSEC_TO_RAD = SEC_TO_RAD;

  /** One sixth constant */
  public static final double SIXTH = 0.1666666666666666667;

  /** RA4 constant */
  public static final double RA4 = 0.04722222222222222222;

  /** RA6 constant */
  public static final double RA6 = 0.02215608465608465608;

  // Utility methods
  private Values() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks if a value is effectively zero within epsilon tolerance.
   *
   * @param value the value to check
   * @return true if the value is effectively zero
   */
  public static boolean isZero(double value) {
    return Math.abs(value) < EPSLN;
  }

  /**
   * Checks if two values are effectively equal within epsilon tolerance.
   *
   * @param a first value
   * @param b second value
   * @return true if the values are effectively equal
   */
  public static boolean equals(double a, double b) {
    return Math.abs(a - b) < EPSLN;
  }

  /**
   * Normalizes an angle to the range [-PI, PI].
   *
   * @param angle the angle in radians
   * @return normalized angle
   */
  public static double normalizeAngle(double angle) {
    while (angle > PI) {
      angle -= TWO_PI;
    }
    while (angle < -PI) {
      angle += TWO_PI;
    }
    return angle;
  }

  /**
   * Normalizes a longitude to the range [-180, 180] degrees.
   *
   * @param lon longitude in degrees
   * @return normalized longitude
   */
  public static double normalizeLongitude(double lon) {
    while (lon > 180.0) {
      lon -= 360.0;
    }
    while (lon < -180.0) {
      lon += 360.0;
    }
    return lon;
  }

  /**
   * Normalizes a latitude to the range [-90, 90] degrees.
   *
   * @param lat latitude in degrees
   * @return normalized latitude
   */
  public static double normalizeLatitude(double lat) {
    if (lat > 90.0) {
      lat = 90.0;
    } else if (lat < -90.0) {
      lat = -90.0;
    }
    return lat;
  }
}
