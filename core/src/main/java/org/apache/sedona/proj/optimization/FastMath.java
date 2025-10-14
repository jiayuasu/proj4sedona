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
package org.apache.sedona.proj.optimization;

/**
 * Optimized mathematical utilities for coordinate transformations. Provides fast approximations and
 * lookup tables for common operations.
 */
public class FastMath {

  // Precomputed values for common angles (in radians)
  private static final double PI_2 = Math.PI / 2.0;
  private static final double PI_4 = Math.PI / 4.0;
  private static final double PI_6 = Math.PI / 6.0;
  private static final double PI_3 = Math.PI / 3.0;
  private static final double TWO_PI = 2.0 * Math.PI;

  // Degree to radian conversion factor
  private static final double DEG_TO_RAD = Math.PI / 180.0;
  private static final double RAD_TO_DEG = 180.0 / Math.PI;

  // Fast sine/cosine lookup table for small angles
  private static final int LOOKUP_SIZE = 1000;
  private static final double[] SIN_LOOKUP = new double[LOOKUP_SIZE];
  private static final double[] COS_LOOKUP = new double[LOOKUP_SIZE];

  static {
    // Initialize lookup tables
    for (int i = 0; i < LOOKUP_SIZE; i++) {
      double angle = (i * Math.PI) / (2 * LOOKUP_SIZE);
      SIN_LOOKUP[i] = Math.sin(angle);
      COS_LOOKUP[i] = Math.cos(angle);
    }
  }

  /**
   * Fast degree to radian conversion.
   *
   * @param degrees angle in degrees
   * @return angle in radians
   */
  public static double degToRad(double degrees) {
    return degrees * DEG_TO_RAD;
  }

  /**
   * Fast radian to degree conversion.
   *
   * @param radians angle in radians
   * @return angle in degrees
   */
  public static double radToDeg(double radians) {
    return radians * RAD_TO_DEG;
  }

  /**
   * Fast sine approximation for small angles (0 to π/2). Uses lookup table for better performance.
   *
   * @param angle angle in radians
   * @return sine value
   */
  public static double fastSin(double angle) {
    // Normalize angle to [0, 2π]
    angle = normalizeAngle(angle);

    // Handle different quadrants
    if (angle <= PI_2) {
      return lookupSin(angle);
    } else if (angle <= Math.PI) {
      return lookupSin(Math.PI - angle);
    } else if (angle <= 3 * PI_2) {
      return -lookupSin(angle - Math.PI);
    } else {
      return -lookupSin(TWO_PI - angle);
    }
  }

  /**
   * Fast cosine approximation for small angles (0 to π/2). Uses lookup table for better
   * performance.
   *
   * @param angle angle in radians
   * @return cosine value
   */
  public static double fastCos(double angle) {
    // Normalize angle to [0, 2π]
    angle = normalizeAngle(angle);

    // Handle different quadrants
    if (angle <= PI_2) {
      return lookupCos(angle);
    } else if (angle <= Math.PI) {
      return -lookupCos(Math.PI - angle);
    } else if (angle <= 3 * PI_2) {
      return -lookupCos(angle - Math.PI);
    } else {
      return lookupCos(TWO_PI - angle);
    }
  }

  /**
   * Fast tangent approximation.
   *
   * @param angle angle in radians
   * @return tangent value
   */
  public static double fastTan(double angle) {
    double sin = fastSin(angle);
    double cos = fastCos(angle);
    return cos != 0 ? sin / cos : Double.POSITIVE_INFINITY;
  }

  /**
   * Fast atan2 approximation.
   *
   * @param y y coordinate
   * @param x x coordinate
   * @return angle in radians
   */
  public static double fastAtan2(double y, double x) {
    if (x == 0) {
      return y > 0 ? PI_2 : (y < 0 ? -PI_2 : 0);
    }

    double ratio = y / x;
    double angle = Math.atan(ratio);

    if (x < 0) {
      angle += (y >= 0) ? Math.PI : -Math.PI;
    }

    return angle;
  }

  /**
   * Fast square root approximation using Newton's method.
   *
   * @param value the value to take square root of
   * @return square root
   */
  public static double fastSqrt(double value) {
    if (value < 0) return Double.NaN;
    if (value == 0) return 0;

    // Initial guess
    double x = value;
    double prev = 0;

    // Newton's method iterations
    for (int i = 0; i < 4 && Math.abs(x - prev) > 1e-10; i++) {
      prev = x;
      x = (x + value / x) / 2;
    }

    return x;
  }

  /**
   * Fast power function for integer exponents.
   *
   * @param base the base
   * @param exponent the exponent (must be integer)
   * @return base raised to the power of exponent
   */
  public static double fastPow(double base, int exponent) {
    if (exponent == 0) return 1;
    if (exponent == 1) return base;
    if (exponent < 0) return 1.0 / fastPow(base, -exponent);

    double result = 1;
    double current = base;

    while (exponent > 0) {
      if ((exponent & 1) == 1) {
        result *= current;
      }
      current *= current;
      exponent >>= 1;
    }

    return result;
  }

  /**
   * Normalizes an angle to [0, 2π].
   *
   * @param angle angle in radians
   * @return normalized angle
   */
  public static double normalizeAngle(double angle) {
    while (angle < 0) angle += TWO_PI;
    while (angle >= TWO_PI) angle -= TWO_PI;
    return angle;
  }

  /**
   * Normalizes longitude to [-π, π].
   *
   * @param longitude longitude in radians
   * @return normalized longitude
   */
  public static double normalizeLongitude(double longitude) {
    while (longitude > Math.PI) longitude -= TWO_PI;
    while (longitude < -Math.PI) longitude += TWO_PI;
    return longitude;
  }

  /**
   * Clamps latitude to [-π/2, π/2].
   *
   * @param latitude latitude in radians
   * @return clamped latitude
   */
  public static double clampLatitude(double latitude) {
    if (latitude > PI_2) return PI_2;
    if (latitude < -PI_2) return -PI_2;
    return latitude;
  }

  // Private helper methods

  private static double lookupSin(double angle) {
    if (angle < 0 || angle > PI_2) {
      return Math.sin(angle); // Fallback to standard implementation
    }

    int index = (int) (angle * (2 * LOOKUP_SIZE) / Math.PI);
    if (index >= LOOKUP_SIZE) index = LOOKUP_SIZE - 1;

    return SIN_LOOKUP[index];
  }

  private static double lookupCos(double angle) {
    if (angle < 0 || angle > PI_2) {
      return Math.cos(angle); // Fallback to standard implementation
    }

    int index = (int) (angle * (2 * LOOKUP_SIZE) / Math.PI);
    if (index >= LOOKUP_SIZE) index = LOOKUP_SIZE - 1;

    return COS_LOOKUP[index];
  }
}
