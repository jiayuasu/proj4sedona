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
package org.apache.sedona.proj.parse;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.sedona.proj.constants.PrimeMeridian;
import org.apache.sedona.proj.constants.Values;

/**
 * Parser for PROJ string definitions. This class parses PROJ strings like "+proj=merc +lat_ts=0
 * +lon_0=0 +k=1.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs"
 */
public class ProjStringParser {

  private static final Pattern PARAM_PATTERN = Pattern.compile("([^=]+)=([^\\s]+)");

  /**
   * Parses a PROJ string and returns a map of parameters.
   *
   * @param projString the PROJ string to parse
   * @return map of parameter names to values
   */
  public static Map<String, Object> parse(String projString) {
    Map<String, Object> params = new HashMap<>();

    if (projString == null || projString.trim().isEmpty()) {
      return params;
    }

    // Split by + and process each parameter
    String[] parts = projString.split("\\+");
    for (String part : parts) {
      part = part.trim();
      if (part.isEmpty()) {
        continue;
      }

      // Handle parameters with = sign
      if (part.contains("=")) {
        Matcher matcher = PARAM_PATTERN.matcher(part);
        if (matcher.matches()) {
          String key = matcher.group(1).toLowerCase();
          String value = matcher.group(2);
          params.put(key, parseValue(key, value));
        }
      } else {
        // Handle boolean parameters (like "no_defs")
        params.put(part.toLowerCase(), true);
      }
    }

    return params;
  }

  /**
   * Parses a parameter value based on its type.
   *
   * @param key the parameter key
   * @param value the string value
   * @return parsed value
   */
  private static Object parseValue(String key, String value) {
    switch (key) {
      case "proj":
        return value;
      case "datum":
        return value;
      case "ellps":
        return value;
      case "units":
        return value;
      case "axis":
        return value;
      case "title":
        return value;
      case "nadgrids":
        return value;
      case "towgs84":
        return value;
      case "no_defs":
        return true;
      case "no_off":
        return true;
      case "no_rot":
        return true;
      case "no_uoff":
        return true;
      case "type":
        return value;
      case "rf":
      case "lat_0":
      case "lat_1":
      case "lat_2":
      case "lat_ts":
      case "lon_0":
      case "lon_1":
      case "lon_2":
      case "alpha":
      case "longc":
      case "rectified_grid_angle":
      case "x_0":
      case "y_0":
      case "k":
      case "k_0":
      case "a":
      case "b":
      case "to_meter":
      case "from_greenwich":
      case "zone":
        try {
          return Double.parseDouble(value);
        } catch (NumberFormatException e) {
          return value; // Return as string if not a number
        }
      case "south":
        return "true".equalsIgnoreCase(value) || "1".equals(value);
      default:
        // Try to parse as number first
        try {
          return Double.parseDouble(value);
        } catch (NumberFormatException e) {
          return value; // Return as string if not a number
        }
    }
  }

  /**
   * Converts parsed parameters to a projection definition map.
   *
   * @param params the parsed parameters
   * @return projection definition map
   */
  public static Map<String, Object> toProjectionDefinition(Map<String, Object> params) {
    Map<String, Object> def = new HashMap<>();

    // Copy basic parameters
    if (params.containsKey("proj")) {
      def.put("projName", params.get("proj"));
    }
    if (params.containsKey("datum")) {
      def.put("datumCode", params.get("datum"));
    }
    if (params.containsKey("ellps")) {
      def.put("ellps", params.get("ellps"));
    }
    if (params.containsKey("units")) {
      def.put("units", params.get("units"));
    }
    if (params.containsKey("axis")) {
      def.put("axis", params.get("axis"));
    }
    if (params.containsKey("title")) {
      def.put("title", params.get("title"));
    }
    if (params.containsKey("nadgrids")) {
      def.put("nadgrids", params.get("nadgrids"));
    }
    if (params.containsKey("towgs84")) {
      def.put("datum_params", params.get("towgs84"));
    }

    // Convert angle parameters from degrees to radians
    if (params.containsKey("lat_0")) {
      def.put("lat0", parseDouble(params.get("lat_0")) * Values.D2R);
    }
    if (params.containsKey("lat_1")) {
      def.put("lat1", parseDouble(params.get("lat_1")) * Values.D2R);
    }
    if (params.containsKey("lat_2")) {
      def.put("lat2", parseDouble(params.get("lat_2")) * Values.D2R);
    }
    if (params.containsKey("lat_ts")) {
      def.put("lat_ts", parseDouble(params.get("lat_ts")) * Values.D2R);
    }
    if (params.containsKey("lon_0")) {
      def.put("long0", parseDouble(params.get("lon_0")) * Values.D2R);
    }
    if (params.containsKey("lon_1")) {
      def.put("long1", parseDouble(params.get("lon_1")) * Values.D2R);
    }
    if (params.containsKey("lon_2")) {
      def.put("long2", parseDouble(params.get("lon_2")) * Values.D2R);
    }
    if (params.containsKey("alpha")) {
      def.put("alpha", parseDouble(params.get("alpha")) * Values.D2R);
    }
    if (params.containsKey("longc")) {
      def.put("longc", parseDouble(params.get("longc")) * Values.D2R);
    }
    if (params.containsKey("rectified_grid_angle")) {
      def.put("rectifiedGridAngle", parseDouble(params.get("rectified_grid_angle")) * Values.D2R);
    }

    // Copy boolean parameters
    if (params.containsKey("no_off")) {
      def.put("noOff", true);
    }
    if (params.containsKey("no_rot")) {
      def.put("noRot", true);
    }
    if (params.containsKey("no_uoff")) {
      def.put("noUoff", true);
    }

    // Copy other parameters
    if (params.containsKey("x_0")) {
      def.put("x0", parseDouble(params.get("x_0")));
    }
    if (params.containsKey("y_0")) {
      def.put("y0", parseDouble(params.get("y_0")));
    }
    if (params.containsKey("k")) {
      def.put("k0", parseDouble(params.get("k")));
    }
    if (params.containsKey("k_0")) {
      def.put("k0", parseDouble(params.get("k_0")));
    }
    if (params.containsKey("a")) {
      def.put("a", parseDouble(params.get("a")));
    }
    if (params.containsKey("b")) {
      def.put("b", parseDouble(params.get("b")));
    }
    if (params.containsKey("rf")) {
      def.put("rf", parseDouble(params.get("rf")));
    }
    if (params.containsKey("to_meter")) {
      def.put("to_meter", parseDouble(params.get("to_meter")));
    }
    if (params.containsKey("from_greenwich")) {
      def.put("from_greenwich", parseDouble(params.get("from_greenwich")));
    }
    if (params.containsKey("zone")) {
      def.put("zone", parseDouble(params.get("zone")));
    }
    if (params.containsKey("south")) {
      def.put("utmSouth", params.get("south"));
    }

    // Handle prime meridian
    if (params.containsKey("pm")) {
      String pmName = (String) params.get("pm");
      Double pmOffset = PrimeMeridian.get(pmName);
      if (pmOffset != null) {
        def.put("from_greenwich", pmOffset * Values.D2R);
      }
    }

    // Handle special cases
    if (params.containsKey("R_A")) {
      def.put("R_A", true);
    }
    if (params.containsKey("sphere")) {
      def.put("sphere", true);
    }

    return def;
  }

  /**
   * Parses a PROJ string and returns a projection definition.
   *
   * @param projString the PROJ string
   * @return projection definition map
   */
  public static Map<String, Object> parseToDefinition(String projString) {
    Map<String, Object> params = parse(projString);
    return toProjectionDefinition(params);
  }

  /**
   * Validates a PROJ string.
   *
   * @param projString the PROJ string to validate
   * @return true if valid, false otherwise
   */
  public static boolean isValid(String projString) {
    if (projString == null || projString.trim().isEmpty()) {
      return false;
    }

    try {
      Map<String, Object> params = parse(projString);
      return params.containsKey("proj");
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Gets the projection name from a PROJ string.
   *
   * @param projString the PROJ string
   * @return projection name, or null if not found
   */
  public static String getProjectionName(String projString) {
    Map<String, Object> params = parse(projString);
    return (String) params.get("proj");
  }

  /**
   * Gets the datum from a PROJ string.
   *
   * @param projString the PROJ string
   * @return datum name, or null if not found
   */
  public static String getDatum(String projString) {
    Map<String, Object> params = parse(projString);
    return (String) params.get("datum");
  }

  /**
   * Gets the ellipsoid from a PROJ string.
   *
   * @param projString the PROJ string
   * @return ellipsoid name, or null if not found
   */
  public static String getEllipsoid(String projString) {
    Map<String, Object> params = parse(projString);
    return (String) params.get("ellps");
  }

  /**
   * Safely parses a value as a double.
   *
   * @param value the value to parse
   * @return the double value
   */
  private static double parseDouble(Object value) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    } else if (value instanceof String) {
      return Double.parseDouble((String) value);
    } else {
      throw new IllegalArgumentException("Cannot parse value as double: " + value);
    }
  }
}
