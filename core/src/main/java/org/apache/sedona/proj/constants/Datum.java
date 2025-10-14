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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Datum definitions and registry for coordinate system datums. This class provides a registry
 * pattern for managing datum definitions rather than hardcoding all datums in the source code.
 */
public final class Datum {

  /** Represents a datum definition with transformation parameters. */
  public static class DatumDef {
    public final String towgs84; // Helmert transformation parameters
    public final String nadgrids; // Grid-based transformation files
    public final String ellipse; // Associated ellipsoid
    public final String datumName; // Human-readable name
    public final int datumType; // Type of datum transformation
    public double a; // Semi-major axis
    public double b; // Semi-minor axis
    public double es; // Eccentricity squared
    public double ep2; // Second eccentricity squared
    public final double[] datum_params; // Parsed datum parameters
    public final String grids; // Grid files string

    public DatumDef(
        String towgs84, String nadgrids, String ellipse, String datumName, int datumType) {
      this.towgs84 = towgs84;
      this.nadgrids = nadgrids;
      this.ellipse = ellipse;
      this.datumName = datumName;
      this.datumType = datumType;
      this.grids = nadgrids;

      // Parse datum parameters
      if (towgs84 != null && !towgs84.isEmpty()) {
        String[] params = towgs84.split(",");
        this.datum_params = new double[params.length];
        for (int i = 0; i < params.length; i++) {
          this.datum_params[i] = Double.parseDouble(params[i].trim());
        }

        // Convert rotation parameters from arcseconds to radians for 7-parameter
        if (datumType == Values.PJD_7PARAM && params.length > 3) {
          for (int i = 3; i < 6; i++) {
            this.datum_params[i] *= Values.SEC_TO_RAD;
          }
          // Scale factor: convert from ppm to unitless
          if (params.length > 6) {
            this.datum_params[6] = (this.datum_params[6] / 1000000.0) + 1.0;
          }
        }
      } else {
        this.datum_params = new double[0];
      }

      // Set ellipsoid parameters (will be set by caller)
      this.a = Double.NaN;
      this.b = Double.NaN;
      this.es = Double.NaN;
      this.ep2 = Double.NaN;
    }

    public DatumDef(String towgs84, String ellipse, String datumName) {
      this(
          towgs84,
          null,
          ellipse,
          datumName,
          towgs84 != null && towgs84.contains(",")
              ? (towgs84.split(",").length > 3 ? Values.PJD_7PARAM : Values.PJD_3PARAM)
              : Values.PJD_WGS84);
    }

    /**
     * Sets the ellipsoid parameters for this datum.
     *
     * @param a semi-major axis
     * @param b semi-minor axis
     * @param es eccentricity squared
     * @param ep2 second eccentricity squared
     */
    public void setEllipsoidParams(double a, double b, double es, double ep2) {
      this.a = a;
      this.b = b;
      this.es = es;
      this.ep2 = ep2;
    }
  }

  private static final Map<String, DatumDef> DATUMS = new HashMap<>();

  static {
    // Initialize only the most commonly used datums
    registerDatum("wgs84", new DatumDef("0,0,0", "WGS84", "WGS84"));
    registerDatum("nad83", new DatumDef("0,0,0", "GRS80", "North_American_Datum_1983"));
    registerDatum(
        "nad27",
        new DatumDef(
            null,
            "@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat",
            "clrk66",
            "North_American_Datum_1927",
            Values.PJD_GRIDSHIFT));
    registerDatum("ch1903", new DatumDef("674.374,15.056,405.346", "bessel", "swiss"));
    registerDatum(
        "ggrs87",
        new DatumDef("-199.87,74.79,246.62", "GRS80", "Greek_Geodetic_Reference_System_1987"));
    registerDatum(
        "potsdam",
        new DatumDef(
            "598.1,73.7,418.2,0.202,0.045,-2.455,6.7", "bessel", "Potsdam Rauenberg 1950 DHDN"));
    registerDatum("carthage", new DatumDef("-263.0,6.0,431.0", "clark80", "Carthage 1934 Tunisia"));
    registerDatum(
        "hermannskogel",
        new DatumDef("577.326,90.129,463.919,5.137,1.474,5.297,2.4232", "bessel", "Hermannskogel"));
    registerDatum(
        "mgi",
        new DatumDef(
            "577.326,90.129,463.919,5.137,1.474,5.297,2.4232",
            "bessel",
            "Militar-Geographische Institut"));
    registerDatum(
        "osni52",
        new DatumDef(
            "482.530,-130.596,564.557,-1.042,-0.214,-0.631,8.15", "airy", "Irish National"));
  }

  private Datum() {
    // Utility class - prevent instantiation
  }

  /**
   * Registers a new datum definition.
   *
   * @param name the datum name
   * @param definition the datum definition
   */
  public static void registerDatum(String name, DatumDef definition) {
    DATUMS.put(name, definition);
  }

  /**
   * Gets a datum definition by name.
   *
   * @param name the datum name
   * @return the datum definition, or null if not found
   */
  public static DatumDef get(String name) {
    return DATUMS.get(name);
  }

  /**
   * Gets all registered datum names.
   *
   * @return set of datum names
   */
  public static Set<String> getNames() {
    return DATUMS.keySet();
  }

  /**
   * Checks if a datum with the given name exists.
   *
   * @param name the datum name
   * @return true if the datum exists
   */
  public static boolean exists(String name) {
    return DATUMS.containsKey(name);
  }

  /**
   * Gets the WGS84 datum definition.
   *
   * @return WGS84 datum definition
   */
  public static DatumDef getWGS84() {
    return DATUMS.get("wgs84");
  }

  /**
   * Gets the NAD83 datum definition.
   *
   * @return NAD83 datum definition
   */
  public static DatumDef getNAD83() {
    return DATUMS.get("nad83");
  }

  /**
   * Gets the NAD27 datum definition.
   *
   * @return NAD27 datum definition
   */
  public static DatumDef getNAD27() {
    return DATUMS.get("nad27");
  }

  /**
   * Loads additional datum definitions from a configuration source. This method can be extended to
   * load from JSON, XML, or database.
   *
   * @param source the configuration source
   */
  public static void loadFromSource(String source) {
    // TODO: Implement loading from external sources
    // This could load from JSON files, databases, or web services
    throw new UnsupportedOperationException("Loading from external sources not yet implemented");
  }
}
