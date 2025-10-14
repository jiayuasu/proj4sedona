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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sedona.proj.constants.Datum;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.core.Point;

/**
 * Grid-based datum shift utilities for NTv2 and other grid formats. This class implements
 * grid-based datum adjustments similar to the JavaScript implementation.
 */
public class GridShift {

  // Registry of loaded grid files
  private static final Map<String, GridFile> GRID_REGISTRY = new ConcurrentHashMap<>();

  /** Represents a grid file for datum transformations. */
  public static class GridFile {
    public final String name;
    public final double minLat, maxLat, minLon, maxLon;
    public final double latStep, lonStep;
    public final int latCount, lonCount;
    public final double[] latShifts;
    public final double[] lonShifts;

    public GridFile(
        String name,
        double minLat,
        double maxLat,
        double minLon,
        double maxLon,
        double latStep,
        double lonStep,
        int latCount,
        int lonCount,
        double[] latShifts,
        double[] lonShifts) {
      this.name = name;
      this.minLat = minLat;
      this.maxLat = maxLat;
      this.minLon = minLon;
      this.maxLon = maxLon;
      this.latStep = latStep;
      this.lonStep = lonStep;
      this.latCount = latCount;
      this.lonCount = lonCount;
      this.latShifts = latShifts;
      this.lonShifts = lonShifts;
    }
  }

  /**
   * Applies grid shift transformation to a point.
   *
   * @param datum the datum definition containing grid information
   * @param inverse whether to apply inverse transformation
   * @param point the point to transform
   * @return 0 on success, non-zero on failure
   */
  public static int applyGridShift(Datum.DatumDef datum, boolean inverse, Point point) {
    if (datum.grids == null || datum.grids.isEmpty()) {
      return 0; // No grid shift needed
    }

    // Parse the grids string (comma-separated list)
    String[] gridNames = datum.grids.split(",");

    // Try to find a registered grid for this datum
    for (String gridName : gridNames) {
      // Remove @ prefix if present (indicates optional grid)
      String cleanGridName = gridName.trim();
      if (cleanGridName.startsWith("@")) {
        cleanGridName = cleanGridName.substring(1);
      }

      // Check for GeoTIFF grid first
      GeoTiffReader.GeoTiffGrid geoTiffGrid = GeoTiffReader.getGrid(cleanGridName);
      if (geoTiffGrid != null) {
        return applyGeoTiffGridShift(geoTiffGrid, inverse, point);
      }

      // Check for traditional NTv2 grid
      GridFile gridFile = getGrid(cleanGridName);
      if (gridFile != null) {
        return applyNTV2GridShift(gridFile, inverse, point);
      }
    }

    return 0; // No matching grid found, assume no shift needed
  }

  /** Applies GeoTIFF grid shift transformation to a point. */
  private static int applyGeoTiffGridShift(
      GeoTiffReader.GeoTiffGrid grid, boolean inverse, Point point) {
    double[] shifts = GeoTiffReader.interpolateGrid(grid, point.y, point.x);
    if (shifts == null) {
      return 0; // Point outside grid, no shift applied
    }

    double latShift = shifts[0];
    double lonShift = shifts[1];

    if (inverse) {
      // Apply inverse transformation
      point = new Point(point.x - lonShift, point.y - latShift, point.z, point.m);
    } else {
      // Apply forward transformation
      point = new Point(point.x + lonShift, point.y + latShift, point.z, point.m);
    }

    return 0; // Success
  }

  /** Applies NTv2 grid shift transformation to a point. */
  private static int applyNTV2GridShift(GridFile grid, boolean inverse, Point point) {
    double[] shifts = interpolateGrid(grid, point.y, point.x);
    if (shifts == null) {
      return 0; // Point outside grid, no shift applied
    }

    double latShift = shifts[0];
    double lonShift = shifts[1];

    if (inverse) {
      // Apply inverse transformation
      point = new Point(point.x - lonShift, point.y - latShift, point.z, point.m);
    } else {
      // Apply forward transformation
      point = new Point(point.x + lonShift, point.y + latShift, point.z, point.m);
    }

    return 0; // Success
  }

  /**
   * Registers a grid file for use in transformations.
   *
   * @param name the grid name/key
   * @param gridFile the grid file data
   */
  public static void registerGrid(String name, GridFile gridFile) {
    GRID_REGISTRY.put(name, gridFile);
  }

  /**
   * Gets a registered grid file.
   *
   * @param name the grid name/key
   * @return the grid file, or null if not found
   */
  public static GridFile getGrid(String name) {
    return GRID_REGISTRY.get(name);
  }

  /**
   * Checks if a grid is registered.
   *
   * @param name the grid name/key
   * @return true if the grid is registered
   */
  public static boolean hasGrid(String name) {
    return GRID_REGISTRY.containsKey(name);
  }

  /**
   * Removes a grid from the registry.
   *
   * @param name the grid name/key
   * @return the removed grid file, or null if not found
   */
  public static GridFile removeGrid(String name) {
    return GRID_REGISTRY.remove(name);
  }

  /**
   * Gets all registered grid names.
   *
   * @return array of grid names
   */
  public static String[] getGridNames() {
    return GRID_REGISTRY.keySet().toArray(new String[0]);
  }

  /** Clears all registered grids. */
  public static void clearGrids() {
    GRID_REGISTRY.clear();
  }

  /**
   * Interpolates grid shift values for a given point. This is a simplified implementation for Phase
   * 2.
   *
   * @param grid the grid file
   * @param lat the latitude in radians
   * @param lon the longitude in radians
   * @return array with [latShift, lonShift] in radians, or null if outside grid
   */
  public static double[] interpolateGrid(GridFile grid, double lat, double lon) {
    // Convert to degrees for grid lookup
    double latDeg = lat * Values.R2D;
    double lonDeg = lon * Values.R2D;

    // Check if point is within grid bounds
    if (latDeg < grid.minLat
        || latDeg > grid.maxLat
        || lonDeg < grid.minLon
        || lonDeg > grid.maxLon) {
      return null; // Point outside grid
    }

    // For Phase 2, return zero shifts
    // In a full implementation, this would perform bilinear interpolation
    return new double[] {0.0, 0.0};
  }
}
