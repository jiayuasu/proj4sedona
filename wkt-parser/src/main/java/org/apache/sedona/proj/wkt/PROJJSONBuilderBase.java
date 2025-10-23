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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for building PROJJSON from WKT2 structures. Ported from the JavaScript wkt-parser
 * PROJJSONBuilderBase.js file.
 */
public class PROJJSONBuilderBase {

  /**
   * Extracts the ID from a WKT node.
   *
   * @param node the WKT node
   * @return the ID map with authority and code, or null if not found
   */
  @SuppressWarnings("unchecked")
  protected static Map<String, Object> getId(List<Object> node) {
    for (Object child : node) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && "ID".equals(childList.get(0)) && childList.size() >= 3) {
          Map<String, Object> id = new HashMap<>();
          id.put("authority", childList.get(1));
          id.put("code", parseIntSafe(childList.get(2)));
          return id;
        }
      }
    }
    return null;
  }

  /**
   * Converts a unit node to a map.
   *
   * @param node the unit node
   * @param type the type of unit
   * @return the unit map
   */
  @SuppressWarnings("unchecked")
  protected static Map<String, Object> convertUnit(List<Object> node, String type) {
    Map<String, Object> unit = new HashMap<>();
    unit.put("type", type);

    if (node == null || node.size() < 3) {
      unit.put("name", "unknown");
      unit.put("conversion_factor", null);
      return unit;
    }

    String name = String.valueOf(node.get(1));
    Double conversionFactor = parseDoubleSafe(node.get(2));

    unit.put("name", name);
    unit.put("conversion_factor", conversionFactor);

    Map<String, Object> id = getId(node);
    if (id != null) {
      unit.put("id", id);
    }

    return unit;
  }

  /**
   * Converts an axis node to a map.
   *
   * @param node the axis node
   * @return the axis map
   */
  @SuppressWarnings("unchecked")
  protected static Map<String, Object> convertAxis(List<Object> node) {
    Map<String, Object> axis = new HashMap<>();

    String name = node.size() > 1 ? String.valueOf(node.get(1)) : "Unknown";
    axis.put("name", name);

    // Determine the direction
    String direction = "unknown";
    if (name.matches("^\\([A-Z]\\)$")) {
      // Match abbreviations like "(E)" or "(N)"
      String abbreviation = name.substring(1, 2).toUpperCase();
      switch (abbreviation) {
        case "E":
          direction = "east";
          break;
        case "N":
          direction = "north";
          break;
        case "U":
          direction = "up";
          break;
        default:
          throw new IllegalArgumentException("Unknown axis abbreviation: " + abbreviation);
      }
    } else if (node.size() > 2) {
      direction = String.valueOf(node.get(2)).toLowerCase();
    }
    axis.put("direction", direction);

    // Extract ORDER
    for (Object child : node) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && "ORDER".equals(childList.get(0)) && childList.size() >= 2) {
          axis.put("order", parseIntSafe(childList.get(1)));
        }
      }
    }

    // Extract unit
    Map<String, Object> unitNode = findUnitNode(node);
    if (unitNode != null) {
      axis.put("unit", unitNode);
    }

    return axis;
  }

  /**
   * Finds a unit node (LENGTHUNIT, ANGLEUNIT, or SCALEUNIT) in a WKT node.
   *
   * @param node the WKT node
   * @return the unit map, or null if not found
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> findUnitNode(List<Object> node) {
    for (Object child : node) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty()) {
          String keyword = String.valueOf(childList.get(0));
          if ("LENGTHUNIT".equals(keyword)
              || "ANGLEUNIT".equals(keyword)
              || "SCALEUNIT".equals(keyword)) {
            return convertUnit(childList, "unit");
          }
        }
      }
    }
    return null;
  }

  /**
   * Extracts all AXIS nodes from a WKT node and sorts them by order.
   *
   * @param node the WKT node
   * @return the list of axis maps
   */
  @SuppressWarnings("unchecked")
  protected static List<Map<String, Object>> extractAxes(List<Object> node) {
    List<Map<String, Object>> axes = new ArrayList<>();
    for (Object child : node) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && "AXIS".equals(childList.get(0))) {
          axes.add(convertAxis(childList));
        }
      }
    }

    // Sort by order
    axes.sort(Comparator.comparingInt(a -> (Integer) a.getOrDefault("order", 0)));
    return axes;
  }

  /**
   * Finds a child node with the specified keyword.
   *
   * @param node the WKT node
   * @param keyword the keyword to search for
   * @return the child node, or null if not found
   */
  @SuppressWarnings("unchecked")
  protected static List<Object> findNode(List<Object> node, String keyword) {
    for (Object child : node) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && keyword.equals(childList.get(0))) {
          return childList;
        }
      }
    }
    return null;
  }

  /**
   * Finds all child nodes with the specified keyword.
   *
   * @param node the WKT node
   * @param keyword the keyword to search for
   * @return the list of child nodes
   */
  @SuppressWarnings("unchecked")
  protected static List<List<Object>> findAllNodes(List<Object> node, String keyword) {
    List<List<Object>> nodes = new ArrayList<>();
    for (Object child : node) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && keyword.equals(childList.get(0))) {
          nodes.add(childList);
        }
      }
    }
    return nodes;
  }

  /**
   * Converts a WKT node to PROJJSON.
   *
   * @param node the WKT node
   * @param result the result map to populate
   * @return the PROJJSON map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> convert(List<Object> node, Map<String, Object> result) {
    if (node == null || node.isEmpty()) {
      return result;
    }

    String keyword = String.valueOf(node.get(0));

    switch (keyword) {
      case "PROJCRS":
        convertPROJCRS(node, result);
        break;

      case "BASEGEOGCRS":
      case "GEOGCRS":
        convertGEOGCRS(node, result);
        break;

      case "DATUM":
        convertDATUM(node, result);
        break;

      case "ENSEMBLE":
        convertENSEMBLE(node, result);
        break;

      case "ELLIPSOID":
        convertELLIPSOID(node, result);
        break;

      case "CONVERSION":
        convertCONVERSION(node, result);
        break;

      case "METHOD":
        convertMETHOD(node, result);
        break;

      case "PARAMETER":
        convertPARAMETER(node, result);
        break;

      case "BOUNDCRS":
        convertBOUNDCRS(node, result);
        break;

      case "ABRIDGEDTRANSFORMATION":
        convertABRIDGEDTRANSFORMATION(node, result);
        break;

      case "AXIS":
        convertAXIS(node, result);
        break;

      case "LENGTHUNIT":
        convertLENGTHUNIT(node, result);
        break;

      default:
        result.put("keyword", keyword);
        break;
    }

    return result;
  }

  /** Converts a PROJCRS node. */
  @SuppressWarnings("unchecked")
  private static void convertPROJCRS(List<Object> node, Map<String, Object> result) {
    result.put("type", "ProjectedCRS");
    result.put("name", node.size() > 1 ? node.get(1) : "");

    // Base CRS
    List<Object> baseGeogCRS = findNode(node, "BASEGEOGCRS");
    if (baseGeogCRS != null) {
      Map<String, Object> baseCrsResult = new HashMap<>();
      convert(baseGeogCRS, baseCrsResult);
      result.put("base_crs", baseCrsResult);
    }

    // Conversion
    List<Object> conversion = findNode(node, "CONVERSION");
    if (conversion != null) {
      Map<String, Object> conversionResult = new HashMap<>();
      convert(conversion, conversionResult);
      result.put("conversion", conversionResult);
    }

    // Coordinate system
    List<Object> csNode = findNode(node, "CS");
    if (csNode != null && csNode.size() > 1) {
      Map<String, Object> coordSystem = new HashMap<>();
      coordSystem.put("type", csNode.get(1));
      coordSystem.put("axis", extractAxes(node));
      result.put("coordinate_system", coordSystem);
    }

    // Length unit
    List<Object> lengthUnitNode = findNode(node, "LENGTHUNIT");
    if (lengthUnitNode != null && result.containsKey("coordinate_system")) {
      Map<String, Object> unit = convertUnit(lengthUnitNode, "unit");
      ((Map<String, Object>) result.get("coordinate_system")).put("unit", unit);
    }

    // ID
    Map<String, Object> id = getId(node);
    if (id != null) {
      result.put("id", id);
    }
  }

  /** Converts a GEOGCRS or BASEGEOGCRS node. */
  @SuppressWarnings("unchecked")
  private static void convertGEOGCRS(List<Object> node, Map<String, Object> result) {
    result.put("type", "GeographicCRS");
    result.put("name", node.size() > 1 ? node.get(1) : "");

    // Handle DATUM or ENSEMBLE
    List<Object> datumNode = findNode(node, "DATUM");
    List<Object> ensembleNode = findNode(node, "ENSEMBLE");

    if (ensembleNode != null) {
      Map<String, Object> datumEnsemble = new HashMap<>();
      convert(ensembleNode, datumEnsemble);
      result.put("datum_ensemble", datumEnsemble);

      // Add prime meridian to ensemble if not Greenwich
      List<Object> primem = findNode(node, "PRIMEM");
      if (primem != null && primem.size() > 1 && !"Greenwich".equals(primem.get(1))) {
        Map<String, Object> primeMeridian = new HashMap<>();
        primeMeridian.put("name", primem.get(1));
        primeMeridian.put("longitude", parseDoubleSafe(primem.get(2)));
        datumEnsemble.put("prime_meridian", primeMeridian);
      }
    } else if (datumNode != null) {
      Map<String, Object> datum = new HashMap<>();
      convert(datumNode, datum);
      result.put("datum", datum);

      // Add prime meridian to datum if not Greenwich
      List<Object> primem = findNode(node, "PRIMEM");
      if (primem != null && primem.size() > 1 && !"Greenwich".equals(primem.get(1))) {
        Map<String, Object> primeMeridian = new HashMap<>();
        primeMeridian.put("name", primem.get(1));
        primeMeridian.put("longitude", parseDoubleSafe(primem.get(2)));
        datum.put("prime_meridian", primeMeridian);
      }
    }

    // Coordinate system
    Map<String, Object> coordSystem = new HashMap<>();
    coordSystem.put("type", "ellipsoidal");
    coordSystem.put("axis", extractAxes(node));
    result.put("coordinate_system", coordSystem);

    // ID
    Map<String, Object> id = getId(node);
    if (id != null) {
      result.put("id", id);
    }
  }

  /** Converts a DATUM node. */
  private static void convertDATUM(List<Object> node, Map<String, Object> result) {
    result.put("type", "GeodeticReferenceFrame");
    result.put("name", node.size() > 1 ? node.get(1) : "");

    // Ellipsoid
    List<Object> ellipsoid = findNode(node, "ELLIPSOID");
    if (ellipsoid != null) {
      Map<String, Object> ellipsoidResult = new HashMap<>();
      convert(ellipsoid, ellipsoidResult);
      result.put("ellipsoid", ellipsoidResult);
    }
  }

  /** Converts an ENSEMBLE node. */
  @SuppressWarnings("unchecked")
  private static void convertENSEMBLE(List<Object> node, Map<String, Object> result) {
    result.put("type", "DatumEnsemble");
    result.put("name", node.size() > 1 ? node.get(1) : "");

    // Extract ensemble members
    List<Map<String, Object>> members = new ArrayList<>();
    for (List<Object> member : findAllNodes(node, "MEMBER")) {
      Map<String, Object> memberMap = new HashMap<>();
      memberMap.put("type", "DatumEnsembleMember");
      memberMap.put("name", member.size() > 1 ? member.get(1) : "");
      Map<String, Object> id = getId(member);
      if (id != null) {
        memberMap.put("id", id);
      }
      members.add(memberMap);
    }
    result.put("members", members);

    // Extract accuracy
    List<Object> accuracyNode = findNode(node, "ENSEMBLEACCURACY");
    if (accuracyNode != null && accuracyNode.size() > 1) {
      result.put("accuracy", parseDoubleSafe(accuracyNode.get(1)));
    }

    // Extract ellipsoid
    List<Object> ellipsoidNode = findNode(node, "ELLIPSOID");
    if (ellipsoidNode != null) {
      Map<String, Object> ellipsoid = new HashMap<>();
      convert(ellipsoidNode, ellipsoid);
      result.put("ellipsoid", ellipsoid);
    }

    // ID
    Map<String, Object> id = getId(node);
    if (id != null) {
      result.put("id", id);
    }
  }

  /** Converts an ELLIPSOID node. */
  private static void convertELLIPSOID(List<Object> node, Map<String, Object> result) {
    result.put("type", "Ellipsoid");
    result.put("name", node.size() > 1 ? node.get(1) : "");
    if (node.size() > 2) {
      result.put("semi_major_axis", parseDoubleSafe(node.get(2)));
    }
    if (node.size() > 3) {
      result.put("inverse_flattening", parseDoubleSafe(node.get(3)));
    }

    // Length unit (if present)
    List<Object> lengthUnit = findNode(node, "LENGTHUNIT");
    if (lengthUnit != null) {
      convert(lengthUnit, result);
    }
  }

  /** Converts a CONVERSION node. */
  @SuppressWarnings("unchecked")
  private static void convertCONVERSION(List<Object> node, Map<String, Object> result) {
    result.put("type", "Conversion");
    result.put("name", node.size() > 1 ? node.get(1) : "");

    // Method
    List<Object> method = findNode(node, "METHOD");
    if (method != null) {
      Map<String, Object> methodResult = new HashMap<>();
      convert(method, methodResult);
      result.put("method", methodResult);
    }

    // Parameters
    List<Map<String, Object>> parameters = new ArrayList<>();
    for (List<Object> param : findAllNodes(node, "PARAMETER")) {
      Map<String, Object> paramResult = new HashMap<>();
      convert(param, paramResult);
      parameters.add(paramResult);
    }
    result.put("parameters", parameters);
  }

  /** Converts a METHOD node. */
  private static void convertMETHOD(List<Object> node, Map<String, Object> result) {
    result.put("type", "Method");
    result.put("name", node.size() > 1 ? node.get(1) : "");

    Map<String, Object> id = getId(node);
    if (id != null) {
      result.put("id", id);
    }
  }

  /** Converts a PARAMETER node. */
  private static void convertPARAMETER(List<Object> node, Map<String, Object> result) {
    result.put("type", "Parameter");
    result.put("name", node.size() > 1 ? node.get(1) : "");
    if (node.size() > 2) {
      result.put("value", parseDoubleSafe(node.get(2)));
    }

    // Unit
    Map<String, Object> unitNode = findUnitNode(node);
    if (unitNode != null) {
      result.put("unit", unitNode);
    }

    Map<String, Object> id = getId(node);
    if (id != null) {
      result.put("id", id);
    }
  }

  /** Converts a BOUNDCRS node. */
  @SuppressWarnings("unchecked")
  private static void convertBOUNDCRS(List<Object> node, Map<String, Object> result) {
    result.put("type", "BoundCRS");

    // Source CRS
    List<Object> sourceCrsNode = findNode(node, "SOURCECRS");
    if (sourceCrsNode != null) {
      for (Object child : sourceCrsNode) {
        if (child instanceof List) {
          Map<String, Object> sourceCrs = new HashMap<>();
          convert((List<Object>) child, sourceCrs);
          result.put("source_crs", sourceCrs);
          break;
        }
      }
    }

    // Target CRS
    List<Object> targetCrsNode = findNode(node, "TARGETCRS");
    if (targetCrsNode != null) {
      for (Object child : targetCrsNode) {
        if (child instanceof List) {
          Map<String, Object> targetCrs = new HashMap<>();
          convert((List<Object>) child, targetCrs);
          result.put("target_crs", targetCrs);
          break;
        }
      }
    }

    // Transformation
    List<Object> transformationNode = findNode(node, "ABRIDGEDTRANSFORMATION");
    if (transformationNode != null) {
      Map<String, Object> transformation = new HashMap<>();
      convert(transformationNode, transformation);
      result.put("transformation", transformation);
    } else {
      result.put("transformation", null);
    }
  }

  /** Converts an ABRIDGEDTRANSFORMATION node. */
  @SuppressWarnings("unchecked")
  private static void convertABRIDGEDTRANSFORMATION(List<Object> node, Map<String, Object> result) {
    result.put("type", "Transformation");
    result.put("name", node.size() > 1 ? node.get(1) : "");

    // Method
    List<Object> method = findNode(node, "METHOD");
    if (method != null) {
      Map<String, Object> methodResult = new HashMap<>();
      convert(method, methodResult);
      result.put("method", methodResult);
    }

    // Parameters (including PARAMETERFILE)
    List<Map<String, Object>> parameters = new ArrayList<>();
    for (List<Object> param : findAllNodes(node, "PARAMETER")) {
      Map<String, Object> paramResult = new HashMap<>();
      convert(param, paramResult);
      parameters.add(paramResult);
    }
    for (List<Object> paramFile : findAllNodes(node, "PARAMETERFILE")) {
      Map<String, Object> paramResult = new HashMap<>();
      paramResult.put("name", paramFile.size() > 1 ? paramFile.get(1) : "");
      paramResult.put("value", paramFile.size() > 2 ? paramFile.get(2) : "");
      Map<String, Object> id = new HashMap<>();
      id.put("authority", "EPSG");
      id.put("code", 8656);
      paramResult.put("id", id);
      parameters.add(paramResult);
    }

    // Adjust the Scale difference parameter if present
    if (parameters.size() == 7) {
      Map<String, Object> scaleDifference = parameters.get(6);
      if ("Scale difference".equals(scaleDifference.get("name"))) {
        Double value = (Double) scaleDifference.get("value");
        scaleDifference.put("value", Math.round((value - 1) * 1e12) / 1e6);
      }
    }

    result.put("parameters", parameters);

    Map<String, Object> id = getId(node);
    if (id != null) {
      result.put("id", id);
    }
  }

  /** Converts an AXIS node (for coordinate system). */
  @SuppressWarnings("unchecked")
  private static void convertAXIS(List<Object> node, Map<String, Object> result) {
    if (!result.containsKey("coordinate_system")) {
      Map<String, Object> coordSystem = new HashMap<>();
      coordSystem.put("type", "unspecified");
      coordSystem.put("axis", new ArrayList<Map<String, Object>>());
      result.put("coordinate_system", coordSystem);
    }
    Map<String, Object> coordSystem = (Map<String, Object>) result.get("coordinate_system");
    List<Map<String, Object>> axes = (List<Map<String, Object>>) coordSystem.get("axis");
    axes.add(convertAxis(node));
  }

  /** Converts a LENGTHUNIT node. */
  @SuppressWarnings("unchecked")
  private static void convertLENGTHUNIT(List<Object> node, Map<String, Object> result) {
    Map<String, Object> unit = convertUnit(node, "LinearUnit");

    // Apply unit to axes if coordinate system exists
    if (result.containsKey("coordinate_system")) {
      Map<String, Object> coordSystem = (Map<String, Object>) result.get("coordinate_system");
      if (coordSystem.containsKey("axis")) {
        List<Map<String, Object>> axes = (List<Map<String, Object>>) coordSystem.get("axis");
        for (Map<String, Object> axis : axes) {
          if (!axis.containsKey("unit")) {
            axis.put("unit", unit);
          }
        }
      }
    }

    // Handle semi_major_axis conversion if needed
    Double conversionFactor = (Double) unit.get("conversion_factor");
    if (conversionFactor != null
        && conversionFactor != 1.0
        && result.containsKey("semi_major_axis")) {
      Map<String, Object> semiMajorAxis = new HashMap<>();
      semiMajorAxis.put("value", result.get("semi_major_axis"));
      semiMajorAxis.put("unit", unit);
      result.put("semi_major_axis", semiMajorAxis);
    }
  }

  /** Safely parses a double value. */
  private static Double parseDoubleSafe(Object value) {
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

  /** Safely parses an integer value. */
  private static Integer parseIntSafe(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
