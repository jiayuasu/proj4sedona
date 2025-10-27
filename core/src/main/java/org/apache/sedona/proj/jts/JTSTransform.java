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
package org.apache.sedona.proj.jts;

import org.apache.sedona.proj.Proj4Sedona;
import org.apache.sedona.proj.core.Point;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryEditor;

/**
 * Transform JTS geometries between coordinate reference systems using JTS's GeometryEditor pattern.
 *
 * <p>This class provides integration between Proj4Sedona and JTS (Java Topology Suite), using the
 * proper JTS idiom of GeometryEditor for transforming geometries. This approach is inspired by both
 * Python's shapely.ops.transform design and JTS's own transformation patterns.
 *
 * <p><b>Basic Usage:</b>
 *
 * <pre>{@code
 * // Create a JTS polygon
 * GeometryFactory gf = new GeometryFactory();
 * Coordinate[] coords = new Coordinate[] {
 *     new Coordinate(-71.0, 41.0),
 *     new Coordinate(-71.0, 42.0),
 *     new Coordinate(-70.0, 42.0),
 *     new Coordinate(-70.0, 41.0),
 *     new Coordinate(-71.0, 41.0)
 * };
 * Polygon polygon = gf.createPolygon(coords);
 *
 * // Transform from WGS84 to Web Mercator
 * Polygon transformed = (Polygon) JTSTransform.transform(
 *     polygon,
 *     "EPSG:4326",
 *     "EPSG:3857",
 *     gf
 * );
 * }</pre>
 *
 * <p><b>Using Converter for Multiple Geometries:</b>
 *
 * <pre>{@code
 * // Create a converter for repeated transformations
 * Proj4Sedona.Converter converter = Proj4Sedona.converter("EPSG:4326", "EPSG:3857");
 *
 * // Transform multiple geometries efficiently
 * Geometry g1 = JTSTransform.transform(polygon1, converter, gf);
 * Geometry g2 = JTSTransform.transform(polygon2, converter, gf);
 * }</pre>
 *
 * <p><b>Design:</b> This implementation uses JTS's {@link GeometryEditor} with a custom {@link
 * GeometryEditor.CoordinateOperation} to transform coordinate arrays. This is the proper JTS
 * pattern for creating modified copies of immutable geometries, handling all geometry types (Point,
 * LineString, Polygon, MultiPoint, MultiLineString, MultiPolygon, GeometryCollection)
 * automatically.
 *
 * @see org.apache.sedona.proj.Proj4Sedona
 * @see org.locationtech.jts.geom.Geometry
 * @see org.locationtech.jts.geom.util.GeometryEditor
 */
public class JTSTransform {

  /**
   * Transform a JTS geometry from one CRS to another using projection strings.
   *
   * @param geometry the geometry to transform
   * @param fromCRS source CRS (EPSG code, PROJ string, or WKT)
   * @param toCRS target CRS (EPSG code, PROJ string, or WKT)
   * @param factory geometry factory for creating new geometries
   * @return transformed geometry
   * @throws IllegalArgumentException if geometry or inputs are null
   */
  public static Geometry transform(
      Geometry geometry, String fromCRS, String toCRS, GeometryFactory factory) {
    if (geometry == null) {
      throw new IllegalArgumentException("Geometry cannot be null");
    }
    if (factory == null) {
      factory = geometry.getFactory();
    }

    // Create converter for this transformation
    Proj4Sedona.Converter converter = Proj4Sedona.converter(fromCRS, toCRS);
    return transform(geometry, converter, factory);
  }

  /**
   * Transform a JTS geometry from one EPSG code to another.
   *
   * @param geometry the geometry to transform
   * @param fromEPSG source EPSG code (e.g., 4326 for WGS84)
   * @param toEPSG target EPSG code (e.g., 3857 for Web Mercator)
   * @param factory geometry factory for creating new geometries
   * @return transformed geometry
   * @throws IllegalArgumentException if geometry is null
   */
  public static Geometry transform(
      Geometry geometry, int fromEPSG, int toEPSG, GeometryFactory factory) {
    return transform(geometry, "EPSG:" + fromEPSG, "EPSG:" + toEPSG, factory);
  }

  /**
   * Transform a JTS geometry from a CRS string to an EPSG code.
   *
   * @param geometry the geometry to transform
   * @param fromCRS source CRS (PROJ string or WKT)
   * @param toEPSG target EPSG code (e.g., 3857 for Web Mercator)
   * @param factory geometry factory for creating new geometries
   * @return transformed geometry
   * @throws IllegalArgumentException if geometry is null
   */
  public static Geometry transform(
      Geometry geometry, String fromCRS, int toEPSG, GeometryFactory factory) {
    return transform(geometry, fromCRS, "EPSG:" + toEPSG, factory);
  }

  /**
   * Transform a JTS geometry from an EPSG code to a CRS string.
   *
   * @param geometry the geometry to transform
   * @param fromEPSG source EPSG code (e.g., 4326 for WGS84)
   * @param toCRS target CRS (PROJ string or WKT)
   * @param factory geometry factory for creating new geometries
   * @return transformed geometry
   * @throws IllegalArgumentException if geometry is null
   */
  public static Geometry transform(
      Geometry geometry, int fromEPSG, String toCRS, GeometryFactory factory) {
    return transform(geometry, "EPSG:" + fromEPSG, toCRS, factory);
  }

  /**
   * Transform a JTS geometry using a Proj4Sedona converter.
   *
   * <p>This method is more efficient when transforming multiple geometries with the same CRS pair,
   * as the converter can be reused.
   *
   * <p>This implementation uses JTS's {@link GeometryEditor} pattern, which is the standard JTS
   * approach for creating transformed copies of geometries. The editor applies a {@link
   * GeometryEditor.CoordinateOperation} that transforms each coordinate array using Proj4Sedona.
   *
   * @param geometry the geometry to transform
   * @param converter the Proj4Sedona converter to use
   * @param factory geometry factory for creating new geometries
   * @return transformed geometry
   * @throws IllegalArgumentException if geometry or converter are null
   */
  public static Geometry transform(
      Geometry geometry, Proj4Sedona.Converter converter, GeometryFactory factory) {
    if (geometry == null) {
      throw new IllegalArgumentException("Geometry cannot be null");
    }
    if (converter == null) {
      throw new IllegalArgumentException("Converter cannot be null");
    }
    if (factory == null) {
      factory = geometry.getFactory();
    }

    if (geometry.isEmpty()) {
      return geometry;
    }

    // Use GeometryEditor with a custom CoordinateOperation
    GeometryEditor editor = new GeometryEditor(factory);
    GeometryEditor.CoordinateOperation operation = new TransformCoordinateOperation(converter);
    return editor.edit(geometry, operation);
  }

  /**
   * A CoordinateOperation that transforms coordinates using Proj4Sedona.
   *
   * <p>This is the JTS-idiomatic way to transform coordinate arrays within a geometry. The
   * GeometryEditor applies this operation to all coordinate arrays in the geometry, handling all
   * geometry types (Point, LineString, Polygon, Multi*, GeometryCollection) automatically.
   */
  private static class TransformCoordinateOperation extends GeometryEditor.CoordinateOperation {
    private final Proj4Sedona.Converter converter;

    public TransformCoordinateOperation(Proj4Sedona.Converter converter) {
      this.converter = converter;
    }

    @Override
    public Coordinate[] edit(Coordinate[] coordinates, Geometry geometry) {
      // Transform each coordinate
      Coordinate[] transformed = new Coordinate[coordinates.length];

      for (int i = 0; i < coordinates.length; i++) {
        // Get the original coordinate
        double x = coordinates[i].x;
        double y = coordinates[i].y;

        // Create Proj4Sedona point (handle Z coordinate if present)
        Point p;
        if (!Double.isNaN(coordinates[i].getZ())) {
          p = new Point(x, y, coordinates[i].getZ());
        } else {
          p = new Point(x, y);
        }

        // Transform the point
        Point transformedPoint = converter.forward(p);

        // Create new coordinate with transformed values
        if (!Double.isNaN(coordinates[i].getZ())) {
          // Preserve Z coordinate
          transformed[i] =
              new Coordinate(transformedPoint.x, transformedPoint.y, transformedPoint.z);
        } else {
          transformed[i] = new Coordinate(transformedPoint.x, transformedPoint.y);
        }
      }

      return transformed;
    }
  }
}
