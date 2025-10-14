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
package org.apache.sedona.proj.core;

import java.util.Objects;
import org.apache.sedona.proj.mgrs.Mgrs;

/**
 * Represents a coordinate point with x, y, z, and m (measure) components. This is the Java
 * equivalent of the JavaScript Point class.
 */
public class Point {
  /** X coordinate */
  public double x;
  /** Y coordinate */
  public double y;
  /** Z coordinate */
  public double z;
  /** M coordinate (measure) */
  public double m;

  /** Creates a new Point with default values (0, 0, 0, 0). */
  public Point() {
    this(0.0, 0.0, 0.0, 0.0);
  }

  /**
   * Creates a new Point with x and y coordinates.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   */
  public Point(double x, double y) {
    this(x, y, 0.0, 0.0);
  }

  /**
   * Creates a new Point with x, y, and z coordinates.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   */
  public Point(double x, double y, double z) {
    this(x, y, z, 0.0);
  }

  /**
   * Creates a new Point with all coordinates.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @param m the measure coordinate
   */
  public Point(double x, double y, double z, double m) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.m = m;
  }

  /**
   * Creates a Point from an array of coordinates.
   *
   * @param coords array of coordinates [x, y, z?, m?]
   * @return new Point instance
   */
  public static Point fromArray(double[] coords) {
    if (coords == null || coords.length < 2) {
      throw new IllegalArgumentException("Coordinates array must have at least 2 elements");
    }
    double z = coords.length > 2 ? coords[2] : 0.0;
    double m = coords.length > 3 ? coords[3] : 0.0;
    return new Point(coords[0], coords[1], z, m);
  }

  /**
   * Creates a Point from a string representation "x,y,z,m".
   *
   * @param coordString string representation of coordinates
   * @return new Point instance
   */
  public static Point fromString(String coordString) {
    if (coordString == null || coordString.trim().isEmpty()) {
      throw new IllegalArgumentException("Coordinate string cannot be null or empty");
    }
    String[] parts = coordString.split(",");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Coordinate string must have at least x,y");
    }
    double x = Double.parseDouble(parts[0].trim());
    double y = Double.parseDouble(parts[1].trim());
    double z = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 0.0;
    double m = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : 0.0;
    return new Point(x, y, z, m);
  }

  /**
   * Converts this point to an array representation.
   *
   * @return array [x, y, z, m]
   */
  public double[] toArray() {
    return new double[] {x, y, z, m};
  }

  /**
   * Converts this point to an array with only x and y coordinates.
   *
   * @return array [x, y]
   */
  public double[] toXYArray() {
    return new double[] {x, y};
  }

  /**
   * Creates a copy of this point.
   *
   * @return new Point with same coordinates
   */
  public Point copy() {
    return new Point(x, y, z, m);
  }

  /**
   * Checks if this point has a z coordinate.
   *
   * @return true if z is defined (not NaN)
   */
  public boolean hasZ() {
    return !Double.isNaN(z);
  }

  /**
   * Checks if this point has a measure coordinate.
   *
   * @return true if m is defined (not NaN)
   */
  public boolean hasM() {
    return !Double.isNaN(m);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Point point = (Point) obj;
    return Double.compare(point.x, x) == 0
        && Double.compare(point.y, y) == 0
        && Double.compare(point.z, z) == 0
        && Double.compare(point.m, m) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y, z, m);
  }

  @Override
  public String toString() {
    if (hasM()) {
      return String.format("Point{x=%.6f, y=%.6f, z=%.6f, m=%.6f}", x, y, z, m);
    } else if (hasZ()) {
      return String.format("Point{x=%.6f, y=%.6f, z=%.6f}", x, y, z);
    } else {
      return String.format("Point{x=%.6f, y=%.6f}", x, y);
    }
  }

  // ===== MGRS Support =====

  /**
   * Create a Point from MGRS string.
   *
   * @param mgrs MGRS string
   * @return Point with longitude and latitude in degrees
   */
  public static Point fromMGRS(String mgrs) {
    double[] coords = Mgrs.toPoint(mgrs);
    return new Point(coords[0], coords[1]);
  }

  /**
   * Convert this point to MGRS string with specified accuracy.
   *
   * @param accuracy accuracy in digits (5 for 1 m, 4 for 10 m, 3 for 100 m, 2 for 1 km, 1 for 10 km
   *     or 0 for 100 km)
   * @return MGRS string
   */
  public String toMGRS(int accuracy) {
    return Mgrs.forward(x, y, accuracy);
  }

  /**
   * Convert this point to MGRS string with default accuracy (5 digits = 1 meter).
   *
   * @return MGRS string
   */
  public String toMGRS() {
    return Mgrs.forward(x, y);
  }
}
