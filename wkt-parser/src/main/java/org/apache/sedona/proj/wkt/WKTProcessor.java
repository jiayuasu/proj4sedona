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
package org.apache.sedona.proj.wkt;

import java.util.*;

/**
 * Main WKT processor that combines parsing and processing. Ported from the JavaScript wkt-parser
 * index.js file.
 */
public class WKTProcessor {

  private static final String[] KNOWN_TYPES = {
    "PROJECTEDCRS",
    "PROJCRS",
    "GEOGCS",
    "GEOCCS",
    "PROJCS",
    "LOCAL_CS",
    "GEODCRS",
    "GEODETICCRS",
    "GEODETICDATUM",
    "ENGCRS",
    "ENGINEERINGCRS"
  };

  /**
   * Processes a WKT string or object and returns a normalized projection definition.
   *
   * @param wkt the WKT string or object to process
   * @return the processed projection definition
   * @throws WKTParseException if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> process(Object wkt) throws WKTParseException {
    if (wkt instanceof Map) {
      // TODO: Handle PROJJSON transformation
      return (Map<String, Object>) wkt;
    }

    if (!(wkt instanceof String)) {
      throw new IllegalArgumentException("WKT must be a string or Map");
    }

    String wktString = (String) wkt;

    // Parse the WKT string
    List<Object> lisp = WKTParser.parseString(wktString);

    // Convert to object structure
    Map<String, Object> obj = new HashMap<>();
    SExpressionProcessor.sExpr(lisp, obj);

    // Clean and normalize the WKT
    cleanWKT(obj);

    // Return the main type object
    String type = (String) lisp.get(0);
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) obj.get(type);
    if (result == null) {
      result = new HashMap<>();
    }

    // Extract the name from the LISP structure if it exists
    if (lisp.size() > 1 && lisp.get(1) instanceof String) {
      result.put("name", lisp.get(1));
    }

    return result;
  }

  /**
   * Cleans and normalizes WKT objects recursively.
   *
   * @param wkt the WKT object to clean
   */
  public static void cleanWKT(Map<String, Object> wkt) {
    for (String key : wkt.keySet()) {
      if (Arrays.asList(KNOWN_TYPES).contains(key)) {
        @SuppressWarnings("unchecked")
        Map<String, Object> subWkt = (Map<String, Object>) wkt.get(key);
        if (subWkt != null) {
          // Set the type for the sub-object
          subWkt.put("type", key);
          setPropertiesFromWkt(subWkt);
        }
      }
      Object value = wkt.get(key);
      if (value instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> subMap = (Map<String, Object>) value;
        cleanWKT(subMap);
      }
    }
  }

  /**
   * Sets properties from WKT structure.
   *
   * @param wkt the WKT object to process
   */
  @SuppressWarnings("unchecked")
  private static void setPropertiesFromWkt(Map<String, Object> wkt) {
    // Set title from AUTHORITY
    if (wkt.containsKey("AUTHORITY")) {
      Map<String, Object> authority = (Map<String, Object>) wkt.get("AUTHORITY");
      for (String authKey : authority.keySet()) {
        wkt.put("title", authKey + ":" + authority.get(authKey));
        break;
      }
    }

    // Set projection name
    if ("GEOGCS".equals(wkt.get("type"))) {
      wkt.put("projName", "longlat");
    } else if ("LOCAL_CS".equals(wkt.get("type"))) {
      wkt.put("projName", "identity");
      wkt.put("local", true);
    } else {
      if (wkt.containsKey("PROJECTION")) {
        Object projection = wkt.get("PROJECTION");
        String projName;
        if (projection instanceof Map) {
          Map<String, Object> projMap = (Map<String, Object>) projection;
          projName = projMap.keySet().iterator().next();
        } else {
          projName = projection.toString();
        }

        // Map WKT projection names to proj4 names
        if ("Hotine_Oblique_Mercator_Azimuth_Center".equals(projName)
            || "Hotine_Oblique_Mercator".equals(projName)
            || "Hotine_Oblique_Mercator_variant_A".equals(projName)) {
          projName = "omerc";
        } else if ("Lambert_Conformal_Conic_2SP".equals(projName)
            || "Lambert_Conformal_Conic".equals(projName)) {
          projName = "lcc";
        } else if ("Albers_Conic_Equal_Area".equals(projName) || "Albers".equals(projName)) {
          projName = "aea";
        } else if ("Transverse_Mercator".equals(projName)) {
          // Check if this is a UTM projection
          if (isUTMProjection(wkt)) {
            projName = "utm";
          } else {
            projName = "tmerc";
          }
        } else if ("Equidistant_Conic".equals(projName)) {
          projName = "eqdc";
        } else if ("Sinusoidal".equals(projName)) {
          projName = "sinu";
        } else if ("Lambert_Azimuthal_Equal_Area".equals(projName)) {
          projName = "laea";
        } else if ("Mollweide".equals(projName)) {
          projName = "moll";
        } else if ("Azimuthal_Equidistant".equals(projName)) {
          projName = "aeqd";
        }

        wkt.put("projName", projName);
      }
    }

    // Process AXIS information
    if (wkt.containsKey("AXIS")) {
      List<Object> axisList = (List<Object>) wkt.get("AXIS");
      StringBuilder axisOrder = new StringBuilder();

      for (Object axisObj : axisList) {
        if (axisObj instanceof List) {
          List<Object> axis = (List<Object>) axisObj;
          if (axis.size() >= 2) {
            String name = axis.get(0).toString().toLowerCase();
            String direction = axis.get(1).toString().toLowerCase();

            // Check direction first, then name
            if ("north".equals(direction) || name.contains("north") || name.contains("lat")) {
              axisOrder.append("n");
            } else if ("south".equals(direction) || name.contains("south")) {
              axisOrder.append("s");
            } else if ("east".equals(direction) || name.contains("east") || name.contains("lon")) {
              axisOrder.append("e");
            } else if ("west".equals(direction) || name.contains("west")) {
              axisOrder.append("w");
            }
          }
        }
      }

      if (axisOrder.length() == 2) {
        axisOrder.append("u");
      }
      if (axisOrder.length() == 3) {
        wkt.put("axis", axisOrder.toString());
      }
    }

    // Process UNIT information
    if (wkt.containsKey("UNIT")) {
      Map<String, Object> unit = (Map<String, Object>) wkt.get("UNIT");
      String unitName = unit.get("name").toString().toLowerCase();
      if ("metre".equals(unitName)) {
        unitName = "meter";
      }
      wkt.put("units", unitName);

      if (unit.containsKey("convert")) {
        double convert = ((Number) unit.get("convert")).doubleValue();
        if ("GEOGCS".equals(wkt.get("type"))) {
          // For geographic coordinate systems, multiply by semi-major axis
          if (wkt.containsKey("DATUM")) {
            Map<String, Object> datum = (Map<String, Object>) wkt.get("DATUM");
            if (datum.containsKey("SPHEROID")) {
              Map<String, Object> spheroid = (Map<String, Object>) datum.get("SPHEROID");
              if (spheroid.containsKey("a")) {
                double a = ((Number) spheroid.get("a")).doubleValue();
                wkt.put("to_meter", convert * a);
              }
            }
          }
        } else {
          wkt.put("to_meter", convert);
        }
      }
    }

    // Process geographic coordinate system information
    Map<String, Object> geogcs = (Map<String, Object>) wkt.get("GEOGCS");
    if ("GEOGCS".equals(wkt.get("type"))) {
      geogcs = wkt;
    }

    if (geogcs != null) {
      // Set datum code
      if (geogcs.containsKey("DATUM")) {
        Map<String, Object> datum = (Map<String, Object>) geogcs.get("DATUM");
        wkt.put("datumCode", datum.get("name").toString().toLowerCase());
      } else {
        wkt.put("datumCode", geogcs.get("name").toString().toLowerCase());
      }

      String datumCode = wkt.get("datumCode").toString();
      if (datumCode.startsWith("d_")) {
        datumCode = datumCode.substring(2);
      }

      // Normalize datum codes
      if ("new_zealand_1949".equals(datumCode)) {
        datumCode = "nzgd49";
      } else if ("wgs_1984".equals(datumCode) || "world_geodetic_system_1984".equals(datumCode)) {
        if ("Mercator_Auxiliary_Sphere".equals(wkt.get("PROJECTION"))) {
          wkt.put("sphere", true);
        }
        datumCode = "wgs84";
      } else if ("belge_1972".equals(datumCode)) {
        datumCode = "rnb72";
      } else if (datumCode.contains("osgb_1936")) {
        datumCode = "osgb36";
      } else if (datumCode.contains("osni_1952")) {
        datumCode = "osni52";
      } else if (datumCode.contains("tm65") || datumCode.contains("geodetic_datum_of_1965")) {
        datumCode = "ire65";
      } else if ("ch1903+".equals(datumCode)) {
        datumCode = "ch1903";
      } else if (datumCode.contains("israel")) {
        datumCode = "isr93";
      }

      wkt.put("datumCode", datumCode);

      // Process spheroid information
      if (geogcs.containsKey("DATUM")) {
        Map<String, Object> datum = (Map<String, Object>) geogcs.get("DATUM");
        if (datum.containsKey("SPHEROID")) {
          Map<String, Object> spheroid = (Map<String, Object>) datum.get("SPHEROID");
          String ellpsName =
              spheroid.get("name").toString().replace("_19", "").replaceAll("[Cc]larke_18", "clrk");
          if (ellpsName.toLowerCase().startsWith("international")) {
            ellpsName = "intl";
          }
          wkt.put("ellps", ellpsName);
          wkt.put("a", spheroid.get("a"));
          wkt.put("rf", Double.parseDouble(spheroid.get("rf").toString()));

          // Calculate derived ellipsoid parameters
          double a = ((Number) spheroid.get("a")).doubleValue();
          double rf = Double.parseDouble(spheroid.get("rf").toString());
          double b = a * (1.0 - 1.0 / rf);
          double es = 1.0 - (b * b) / (a * a);
          double e = Math.sqrt(es);

          wkt.put("b", b);
          wkt.put("es", es);
          wkt.put("e", e);
        }

        // Process TOWGS84 parameters
        if (datum.containsKey("TOWGS84")) {
          wkt.put("datum_params", datum.get("TOWGS84"));
        }
      }
    }

    // Ensure b is finite
    if (wkt.containsKey("b")) {
      double b = ((Number) wkt.get("b")).doubleValue();
      if (!Double.isFinite(b)) {
        wkt.put("b", wkt.get("a"));
      }
    }

    // Convert rectified grid angle to radians
    if (wkt.containsKey("rectified_grid_angle")) {
      double angle = ((Number) wkt.get("rectified_grid_angle")).doubleValue();
      wkt.put("rectified_grid_angle", WKTUtils.d2r(angle));
    }

    // Apply parameter renaming
    applyParameterRenaming(wkt);

    // Apply projection defaults
    WKTUtils.applyProjectionDefaults(wkt);
  }

  /**
   * Applies parameter renaming based on common WKT parameter names.
   *
   * @param wkt the WKT object to process
   */
  @SuppressWarnings("unchecked")
  private static void applyParameterRenaming(Map<String, Object> wkt) {
    // Define parameter mappings
    String[][] mappings = {
      {"standard_parallel_1", "Standard_Parallel_1"},
      {"standard_parallel_1", "Latitude of 1st standard parallel"},
      {"standard_parallel_2", "Standard_Parallel_2"},
      {"standard_parallel_2", "Latitude of 2nd standard parallel"},
      {"lat1", "standard_parallel_1"},
      {"lat2", "standard_parallel_2"},
      {"false_easting", "False_Easting"},
      {"false_easting", "False easting"},
      {"false-easting", "Easting at false origin"},
      {"false_northing", "False_Northing"},
      {"false_northing", "False northing"},
      {"false_northing", "Northing at false origin"},
      {"central_meridian", "Central_Meridian"},
      {"central_meridian", "Longitude of natural origin"},
      {"central_meridian", "Longitude of false origin"},
      {"latitude_of_origin", "Latitude_Of_Origin"},
      {"latitude_of_origin", "Central_Parallel"},
      {"latitude_of_origin", "Latitude of natural origin"},
      {"latitude_of_origin", "Latitude of false origin"},
      {"scale_factor", "Scale_Factor"},
      {"k0", "scale_factor"},
      {"latitude_of_center", "Latitude_Of_Center"},
      {"latitude_of_center", "Latitude_of_center"},
      {"longitude_of_center", "Longitude_Of_Center"},
      {"longitude_of_center", "Longitude_of_center"},
      {"azimuth", "Azimuth"},
      {"srsCode", "name"},
      // Hotine Oblique Mercator specific mappings
      {"lat0", "latitude_of_center"},
      {"longc", "longitude_of_center"},
      {"alpha", "azimuth"}
    };

    for (String[] mapping : mappings) {
      String outName = mapping[0];
      String inName = mapping[1];

      if (!wkt.containsKey(outName) && wkt.containsKey(inName)) {
        Object value = wkt.get(inName);

        // Apply transformations for specific parameters
        if ("lat0".equals(outName)
            && ("latitude_of_center".equals(inName)
                || "latitude_of_origin".equals(inName)
                || "standard_parallel_1".equals(inName))) {
          value = WKTUtils.d2r(((Number) value).doubleValue());
        } else if ("longc".equals(outName) && "longitude_of_center".equals(inName)) {
          value = WKTUtils.d2r(((Number) value).doubleValue());
        } else if ("alpha".equals(outName) && "azimuth".equals(inName)) {
          value = WKTUtils.d2r(((Number) value).doubleValue());
        } else if ("x0".equals(outName)
            && ("false_easting".equals(inName) || "false-easting".equals(inName))) {
          double toMeter =
              wkt.containsKey("to_meter") ? ((Number) wkt.get("to_meter")).doubleValue() : 1.0;
          value = ((Number) value).doubleValue() * toMeter;
        } else if ("y0".equals(outName)
            && ("false_northing".equals(inName) || "false_northing".equals(inName))) {
          double toMeter =
              wkt.containsKey("to_meter") ? ((Number) wkt.get("to_meter")).doubleValue() : 1.0;
          value = ((Number) value).doubleValue() * toMeter;
        } else if ("long0".equals(outName)
            && ("central_meridian".equals(inName) || "longitude_of_center".equals(inName))) {
          value = WKTUtils.d2r(((Number) value).doubleValue());
        } else if ("lat1".equals(outName) && "standard_parallel_1".equals(inName)) {
          value = WKTUtils.d2r(((Number) value).doubleValue());
        } else if ("lat2".equals(outName) && "standard_parallel_2".equals(inName)) {
          value = WKTUtils.d2r(((Number) value).doubleValue());
        }

        wkt.put(outName, value);
      }
    }

    // Handle direct parameter mappings that are already in the WKT
    if (wkt.containsKey("latitude_of_origin") && !wkt.containsKey("lat0")) {
      wkt.put("lat0", WKTUtils.d2r(((Number) wkt.get("latitude_of_origin")).doubleValue()));
    }
    if (wkt.containsKey("central_meridian") && !wkt.containsKey("long0")) {
      wkt.put("long0", WKTUtils.d2r(((Number) wkt.get("central_meridian")).doubleValue()));
    }
    if (wkt.containsKey("scale_factor") && !wkt.containsKey("k0")) {
      wkt.put("k0", wkt.get("scale_factor"));
    }
    if (wkt.containsKey("false_easting") && !wkt.containsKey("x0")) {
      double toMeter =
          wkt.containsKey("to_meter") ? ((Number) wkt.get("to_meter")).doubleValue() : 1.0;
      wkt.put("x0", ((Number) wkt.get("false_easting")).doubleValue() * toMeter);
    }
    if (wkt.containsKey("false_northing") && !wkt.containsKey("y0")) {
      double toMeter =
          wkt.containsKey("to_meter") ? ((Number) wkt.get("to_meter")).doubleValue() : 1.0;
      wkt.put("y0", ((Number) wkt.get("false_northing")).doubleValue() * toMeter);
    }

    // Handle UTM-specific parameters
    if ("utm".equals(wkt.get("projName"))) {
      // Extract UTM zone from central meridian
      if (wkt.containsKey("central_meridian")) {
        double centralMeridian = ((Number) wkt.get("central_meridian")).doubleValue();
        int zone = (int) Math.round((centralMeridian + 177.0) / 6.0) + 1;
        wkt.put("zone", zone);
      }

      // Determine if this is southern hemisphere based on false northing
      if (wkt.containsKey("y0")) {
        double y0 = ((Number) wkt.get("y0")).doubleValue();
        wkt.put("utmSouth", y0 > 5000000.0); // Southern hemisphere has false northing of 10,000,000
      }
    }
  }

  /**
   * Determines if a Transverse Mercator projection is actually a UTM projection. UTM projections
   * have specific characteristics: - Scale factor of 0.9996 - False easting of 500000 - False
   * northing of 0 (northern hemisphere) or 10000000 (southern hemisphere) - Central meridian that
   * corresponds to a UTM zone
   *
   * @param wkt the WKT object to check
   * @return true if this is a UTM projection
   */
  @SuppressWarnings("unchecked")
  private static boolean isUTMProjection(Map<String, Object> wkt) {
    // Check for UTM-specific parameters
    double scaleFactor = 0.9996;
    double falseEasting = 500000.0;

    // Get scale factor
    double k0 = 1.0;
    if (wkt.containsKey("scale_factor")) {
      k0 = ((Number) wkt.get("scale_factor")).doubleValue();
    } else if (wkt.containsKey("k0")) {
      k0 = ((Number) wkt.get("k0")).doubleValue();
    }

    // Get false easting
    double x0 = 0.0;
    if (wkt.containsKey("false_easting")) {
      x0 = ((Number) wkt.get("false_easting")).doubleValue();
    } else if (wkt.containsKey("x0")) {
      x0 = ((Number) wkt.get("x0")).doubleValue();
    }

    // Get false northing
    double y0 = 0.0;
    if (wkt.containsKey("false_northing")) {
      y0 = ((Number) wkt.get("false_northing")).doubleValue();
    } else if (wkt.containsKey("y0")) {
      y0 = ((Number) wkt.get("y0")).doubleValue();
    }

    // Check if parameters match UTM characteristics
    boolean hasUTMScaleFactor = Math.abs(k0 - scaleFactor) < 1e-6;
    boolean hasUTMFalseEasting = Math.abs(x0 - falseEasting) < 1e-6;
    boolean hasUTMFalseNorthing = Math.abs(y0) < 1e-6 || Math.abs(y0 - 10000000.0) < 1e-6;

    // Check if central meridian corresponds to a UTM zone
    boolean hasUTMCentralMeridian = false;
    if (wkt.containsKey("central_meridian")) {
      double centralMeridian = ((Number) wkt.get("central_meridian")).doubleValue();
      // UTM zones have central meridians at -177, -171, -165, ..., 177 degrees
      // (every 6 degrees starting from -177)
      double zoneMeridian = (centralMeridian + 177.0) / 6.0;
      hasUTMCentralMeridian = Math.abs(zoneMeridian - Math.round(zoneMeridian)) < 1e-6;
    }

    // Also check the name for UTM indicators
    boolean hasUTMName = false;
    if (wkt.containsKey("name")) {
      String name = wkt.get("name").toString().toLowerCase();
      hasUTMName = name.contains("utm") || name.contains("universal transverse mercator");
    }

    return (hasUTMScaleFactor && hasUTMFalseEasting && hasUTMFalseNorthing)
        || (hasUTMName && hasUTMScaleFactor && hasUTMFalseEasting);
  }
}
