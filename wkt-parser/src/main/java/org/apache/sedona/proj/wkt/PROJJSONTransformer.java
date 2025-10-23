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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms PROJJSON to proj4-style parameter maps. Ported from the JavaScript wkt-parser
 * transformPROJJSON.js file.
 */
public class PROJJSONTransformer {

  /**
   * Processes a unit and extracts units name and to_meter conversion.
   *
   * @param unit the unit object (string or map)
   * @return a map with units and to_meter values
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> processUnit(Object unit) {
    Map<String, Object> result = new HashMap<>();
    result.put("units", null);
    result.put("to_meter", null);

    if (unit instanceof String) {
      String units = ((String) unit).toLowerCase();
      if ("metre".equals(units)) {
        units = "meter";
      }
      result.put("units", units);
      if ("meter".equals(units)) {
        result.put("to_meter", 1.0);
      }
    } else if (unit instanceof Map) {
      Map<String, Object> unitMap = (Map<String, Object>) unit;
      if (unitMap.containsKey("name")) {
        String units = String.valueOf(unitMap.get("name")).toLowerCase();
        if ("metre".equals(units)) {
          units = "meter";
        }
        result.put("units", units);
      }
      if (unitMap.containsKey("conversion_factor")) {
        result.put("to_meter", unitMap.get("conversion_factor"));
      }
    }

    return result;
  }

  /**
   * Converts a value or object with units to a plain value.
   *
   * @param valueOrObject the value or object
   * @return the converted value
   */
  @SuppressWarnings("unchecked")
  private static Double toValue(Object valueOrObject) {
    if (valueOrObject instanceof Map) {
      Map<String, Object> obj = (Map<String, Object>) valueOrObject;
      Double value = getDoubleValue(obj.get("value"));
      Map<String, Object> unit = (Map<String, Object>) obj.get("unit");
      if (unit != null && unit.containsKey("conversion_factor")) {
        Double factor = getDoubleValue(unit.get("conversion_factor"));
        return value * factor;
      }
      return value;
    }
    return getDoubleValue(valueOrObject);
  }

  /**
   * Calculates ellipsoid parameters.
   *
   * @param value the datum or datum ensemble with ellipsoid
   * @param result the result map to populate
   */
  @SuppressWarnings("unchecked")
  private static void calculateEllipsoid(Map<String, Object> value, Map<String, Object> result) {
    if (!value.containsKey("ellipsoid")) {
      return;
    }

    Map<String, Object> ellipsoid = (Map<String, Object>) value.get("ellipsoid");

    if (ellipsoid.containsKey("radius")) {
      result.put("a", toValue(ellipsoid.get("radius")));
      result.put("rf", 0.0);
    } else {
      if (ellipsoid.containsKey("semi_major_axis")) {
        result.put("a", toValue(ellipsoid.get("semi_major_axis")));
      }
      if (ellipsoid.containsKey("inverse_flattening")) {
        result.put("rf", getDoubleValue(ellipsoid.get("inverse_flattening")));
      } else if (ellipsoid.containsKey("semi_major_axis")
          && ellipsoid.containsKey("semi_minor_axis")) {
        Double a = toValue(ellipsoid.get("semi_major_axis"));
        Double b = toValue(ellipsoid.get("semi_minor_axis"));
        result.put("rf", a / (a - b));
      }
    }
  }

  /**
   * Transforms PROJJSON to proj4-style parameter map.
   *
   * @param projjson the PROJJSON object
   * @param result the result map to populate
   * @return the transformed parameter map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> transform(
      Map<String, Object> projjson, Map<String, Object> result) {
    if (projjson == null) {
      return result;
    }

    // Handle BoundCRS specially
    if ("BoundCRS".equals(projjson.get("type"))) {
      if (projjson.containsKey("source_crs")) {
        transform((Map<String, Object>) projjson.get("source_crs"), result);
      }

      if (projjson.containsKey("transformation")) {
        Map<String, Object> transformation = (Map<String, Object>) projjson.get("transformation");
        if (transformation != null) {
          Map<String, Object> method = (Map<String, Object>) transformation.get("method");
          if (method != null && "NTv2".equals(method.get("name"))) {
            // Set nadgrids to the filename from the parameterfile
            List<Map<String, Object>> parameters =
                (List<Map<String, Object>>) transformation.get("parameters");
            if (parameters != null && !parameters.isEmpty()) {
              result.put("nadgrids", parameters.get(0).get("value"));
            }
          } else {
            // Populate datum_params if no parameterfile is found
            List<Map<String, Object>> parameters =
                (List<Map<String, Object>>) transformation.get("parameters");
            if (parameters != null) {
              Double[] datumParams = new Double[parameters.size()];
              for (int i = 0; i < parameters.size(); i++) {
                datumParams[i] = getDoubleValue(parameters.get(i).get("value"));
              }
              result.put("datum_params", datumParams);
            }
          }
        }
      }
      return result;
    }

    // Process each key in the PROJJSON
    for (Map.Entry<String, Object> entry : projjson.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (value == null) {
        continue;
      }

      switch (key) {
        case "name":
          if (!result.containsKey("srsCode")) {
            result.put("name", value);
            result.put("srsCode", value);
          }
          break;

        case "type":
          if ("GeographicCRS".equals(value)) {
            result.put("projName", "longlat");
          } else if ("ProjectedCRS".equals(value) && projjson.containsKey("conversion")) {
            Map<String, Object> conversion = (Map<String, Object>) projjson.get("conversion");
            if (conversion != null && conversion.containsKey("method")) {
              Map<String, Object> method = (Map<String, Object>) conversion.get("method");
              if (method != null && method.containsKey("name")) {
                String methodName = String.valueOf(method.get("name"));
                result.put("projName", mapWKTMethodToProj4(methodName));
              }
            }
          }
          break;

        case "datum":
        case "datum_ensemble":
          Map<String, Object> datumValue = (Map<String, Object>) value;
          if (datumValue.containsKey("ellipsoid")) {
            Map<String, Object> ellipsoid = (Map<String, Object>) datumValue.get("ellipsoid");
            if (ellipsoid.containsKey("name")) {
              String ellpsName = String.valueOf(ellipsoid.get("name"));
              // Normalize ellipsoid name for lookup
              ellpsName = normalizeEllipsoidName(ellpsName);
              result.put("ellps", ellpsName);
            }
            calculateEllipsoid(datumValue, result);
          }
          if (datumValue.containsKey("prime_meridian")) {
            Map<String, Object> primeMeridian =
                (Map<String, Object>) datumValue.get("prime_meridian");
            if (primeMeridian.containsKey("longitude")) {
              Double longitude = getDoubleValue(primeMeridian.get("longitude"));
              result.put("from_greenwich", longitude * Math.PI / 180);
            }
          }
          if (datumValue.containsKey("name")) {
            String datumName = String.valueOf(datumValue.get("name"));
            result.put("datumCode", datumName);
          }
          break;

        case "ellipsoid":
          Map<String, Object> ellipsoidValue = (Map<String, Object>) value;
          if (ellipsoidValue.containsKey("name")) {
            result.put("ellps", ellipsoidValue.get("name"));
          }
          calculateEllipsoid(ellipsoidValue, result);
          break;

        case "prime_meridian":
          Map<String, Object> primeMeridian = (Map<String, Object>) value;
          Double longitude = getDoubleValue(primeMeridian.getOrDefault("longitude", 0.0));
          result.put("long0", longitude * Math.PI / 180);
          break;

        case "coordinate_system":
          Map<String, Object> coordSystem = (Map<String, Object>) value;
          if (coordSystem.containsKey("axis")) {
            List<Map<String, Object>> axes = (List<Map<String, Object>>) coordSystem.get("axis");
            StringBuilder axisStr = new StringBuilder();
            for (Map<String, Object> axis : axes) {
              String direction = String.valueOf(axis.get("direction"));
              switch (direction) {
                case "east":
                  axisStr.append("e");
                  break;
                case "north":
                  axisStr.append("n");
                  break;
                case "west":
                  axisStr.append("w");
                  break;
                case "south":
                  axisStr.append("s");
                  break;
                default:
                  throw new IllegalArgumentException("Unknown axis direction: " + direction);
              }
            }
            axisStr.append("u");
            result.put("axis", axisStr.toString());

            // Process unit
            if (coordSystem.containsKey("unit")) {
              Map<String, Object> unitResult = processUnit(coordSystem.get("unit"));
              if (unitResult.get("units") != null) {
                result.put("units", unitResult.get("units"));
              }
              if (unitResult.get("to_meter") != null) {
                result.put("to_meter", unitResult.get("to_meter"));
              }
            } else if (!axes.isEmpty() && axes.get(0).containsKey("unit")) {
              Map<String, Object> unitResult = processUnit(axes.get(0).get("unit"));
              if (unitResult.get("units") != null) {
                result.put("units", unitResult.get("units"));
              }
              if (unitResult.get("to_meter") != null) {
                result.put("to_meter", unitResult.get("to_meter"));
              }
            }
          }
          break;

        case "id":
          Map<String, Object> idValue = (Map<String, Object>) value;
          if (idValue.containsKey("authority") && idValue.containsKey("code")) {
            result.put("title", idValue.get("authority") + ":" + idValue.get("code"));
          }
          break;

        case "conversion":
          Map<String, Object> conversionValue = (Map<String, Object>) value;
          if (conversionValue.containsKey("method")) {
            Map<String, Object> method = (Map<String, Object>) conversionValue.get("method");
            if (method != null && method.containsKey("name")) {
              String methodName = String.valueOf(method.get("name"));
              result.put("projName", mapWKTMethodToProj4(methodName));
            }
          }
          if (conversionValue.containsKey("parameters")) {
            List<Map<String, Object>> parameters =
                (List<Map<String, Object>>) conversionValue.get("parameters");
            for (Map<String, Object> param : parameters) {
              String paramName =
                  String.valueOf(param.get("name")).toLowerCase().replaceAll("\\s+", "_");
              Double paramValue = getDoubleValue(param.get("value"));

              if (param.containsKey("unit")) {
                Map<String, Object> paramUnit = (Map<String, Object>) param.get("unit");
                if (paramUnit != null && paramUnit.containsKey("conversion_factor")) {
                  Double factor = getDoubleValue(paramUnit.get("conversion_factor"));
                  result.put(paramName, paramValue * factor);
                } else {
                  result.put(paramName, paramValue);
                }
              } else if ("degree".equals(param.get("unit"))) {
                result.put(paramName, paramValue * Math.PI / 180);
              } else {
                result.put(paramName, paramValue);
              }
            }
          }
          break;

        case "unit":
          Map<String, Object> unitValue = (Map<String, Object>) value;
          if (unitValue.containsKey("name")) {
            String units = String.valueOf(unitValue.get("name")).toLowerCase();
            if ("metre".equals(units)) {
              units = "meter";
            }
            result.put("units", units);
          }
          if (unitValue.containsKey("conversion_factor")) {
            result.put("to_meter", unitValue.get("conversion_factor"));
          }
          break;

        case "base_crs":
          Map<String, Object> baseCrs = (Map<String, Object>) value;
          transform(baseCrs, result);
          if (baseCrs.containsKey("id")) {
            Map<String, Object> baseCrsId = (Map<String, Object>) baseCrs.get("id");
            if (baseCrsId != null
                && baseCrsId.containsKey("authority")
                && baseCrsId.containsKey("code")) {
              result.put("datumCode", baseCrsId.get("authority") + "_" + baseCrsId.get("code"));
            }
          } else if (baseCrs.containsKey("name")) {
            result.put("datumCode", baseCrs.get("name"));
          }
          break;

        default:
          // Ignore irrelevant or unneeded properties
          break;
      }
    }

    // Additional calculated properties - map various parameter names to standard proj4 names
    // Do this mapping BEFORE applying defaults, and use forceMapParameter to override any existing
    // values
    forceMapParameter(result, "lat0", "latitude_of_false_origin");
    forceMapParameter(result, "long0", "longitude_of_false_origin");
    forceMapParameter(result, "lat1", "latitude_of_standard_parallel");
    forceMapParameter(result, "lat1", "latitude_of_1st_standard_parallel");
    forceMapParameter(result, "lat2", "latitude_of_2nd_standard_parallel");
    forceMapParameter(result, "lat0", "latitude_of_projection_centre");
    forceMapParameter(result, "longc", "longitude_of_projection_centre");
    forceMapParameter(result, "x0", "easting_at_false_origin");
    forceMapParameter(result, "y0", "northing_at_false_origin");
    forceMapParameter(result, "lat0", "latitude_of_natural_origin");
    forceMapParameter(result, "long0", "longitude_of_natural_origin");
    forceMapParameter(result, "long0", "longitude_of_origin");
    forceMapParameter(result, "x0", "false_easting");
    forceMapParameter(result, "x0", "easting_at_projection_centre");
    forceMapParameter(result, "y0", "false_northing");
    forceMapParameter(result, "y0", "northing_at_projection_centre");
    forceMapParameter(result, "lat1", "standard_parallel_1");
    forceMapParameter(result, "lat2", "standard_parallel_2");
    forceMapParameter(result, "k0", "scale_factor_at_natural_origin");
    forceMapParameter(result, "k0", "scale_factor_at_projection_centre");
    forceMapParameter(result, "k0", "scale_factor_on_pseudo_standard_parallel");
    forceMapParameter(result, "alpha", "azimuth");
    forceMapParameter(result, "alpha", "azimuth_at_projection_centre");
    forceMapParameter(result, "rectified_grid_angle", "angle_from_rectified_to_skew_grid");

    // Apply projection defaults AFTER mapping (from util.js)
    applyProjectionDefaults(result);

    return result;
  }

  /**
   * Maps a parameter from source to destination if it exists (only if dest doesn't exist).
   *
   * @param result the result map
   * @param destKey the destination key
   * @param sourceKey the source key
   */
  private static void mapParameter(Map<String, Object> result, String destKey, String sourceKey) {
    if (!result.containsKey(destKey) && result.containsKey(sourceKey)) {
      result.put(destKey, result.get(sourceKey));
    }
  }

  /**
   * Maps a parameter from source to destination if source exists (overwrites dest if it exists).
   *
   * @param result the result map
   * @param destKey the destination key
   * @param sourceKey the source key
   */
  private static void forceMapParameter(
      Map<String, Object> result, String destKey, String sourceKey) {
    if (result.containsKey(sourceKey)) {
      result.put(destKey, result.get(sourceKey));
    }
  }

  /**
   * Applies projection defaults.
   *
   * @param result the result map
   */
  private static void applyProjectionDefaults(Map<String, Object> result) {
    // Default values from util.js
    if (!result.containsKey("lat0")) {
      result.put("lat0", 0.0);
    }
    if (!result.containsKey("long0")) {
      result.put("long0", 0.0);
    }
    if (!result.containsKey("k0")) {
      result.put("k0", 1.0);
    }
    if (!result.containsKey("x0")) {
      result.put("x0", 0.0);
    }
    if (!result.containsKey("y0")) {
      result.put("y0", 0.0);
    }
  }

  /**
   * Transforms PROJJSON to proj4-style parameter map (convenience method).
   *
   * @param projjson the PROJJSON object
   * @return the transformed parameter map
   */
  public static Map<String, Object> transform(Map<String, Object> projjson) {
    return transform(projjson, new HashMap<>());
  }

  /**
   * Normalizes ellipsoid name for lookup.
   *
   * @param ellpsName the ellipsoid name from WKT
   * @return the normalized ellipsoid name
   */
  private static String normalizeEllipsoidName(String ellpsName) {
    if (ellpsName == null) {
      return null;
    }

    // Remove spaces and convert to uppercase for common ellipsoids
    String normalized = ellpsName.replaceAll("\\s+", "").toUpperCase();

    // Common mappings
    if (normalized.equals("WGS84")) {
      return "WGS84";
    } else if (normalized.equals("GRS1980")) {
      return "GRS80";
    } else if (normalized.equals("CLARKE1866")) {
      return "clrk66";
    } else if (normalized.equals("NAD83")) {
      return "GRS80"; // NAD83 uses GRS80 ellipsoid
    }

    // Return original if no mapping found
    return ellpsName;
  }

  /**
   * Maps WKT projection method names to proj4 projection names.
   *
   * @param wktMethodName the WKT method name
   * @return the proj4 projection name
   */
  private static String mapWKTMethodToProj4(String wktMethodName) {
    if (wktMethodName == null) {
      return null;
    }

    // Normalize to lowercase for comparison
    String normalized = wktMethodName.toLowerCase().replaceAll("\\s+", "_");

    // Map WKT method names to proj4 names
    switch (normalized) {
      case "popular_visualisation_pseudo_mercator":
      case "popular_visualization_pseudo_mercator":
      case "mercator_(variant_a)":
      case "mercator_(variant_b)":
      case "mercator_(1sp)":
      case "mercator_(2sp)":
      case "mercator":
        return "merc";

      case "transverse_mercator":
      case "transverse_mercator_(south_orientated)":
        return "tmerc";

      case "lambert_conformal_conic_(1sp)":
      case "lambert_conformal_conic_(2sp)":
      case "lambert_conformal_conic":
      case "lambert_conic_conformal_(1sp)":
      case "lambert_conic_conformal_(2sp)":
        return "lcc";

      case "albers_equal_area":
      case "albers_conic_equal_area":
        return "aea";

      case "equidistant_conic":
      case "equidistant_cylindrical":
        return "eqdc";

      case "hotine_oblique_mercator_(variant_a)":
      case "hotine_oblique_mercator_(variant_b)":
      case "hotine_oblique_mercator":
      case "oblique_mercator":
        return "omerc";

      case "sinusoidal":
        return "sinu";

      case "universal_transverse_mercator":
        return "utm";

      default:
        // Return the original name if no mapping is found
        return wktMethodName;
    }
  }

  /**
   * Safely converts an object to a Double value.
   *
   * @param value the value to convert
   * @return the Double value, or null if conversion fails
   */
  private static Double getDoubleValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
