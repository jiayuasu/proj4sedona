package org.proj4sedona.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts WKT2 AST (List structure) to PROJJSON-like Map structure.
 * Mirrors: wkt-parser/buildPROJJSON.js and wkt-parser/PROJJSONBuilderBase.js
 * 
 * This class handles WKT2 (both 2015 and 2019 versions) structures like:
 *   PROJCRS["name", BASEGEOGCRS[...], CONVERSION[...], CS[...], ...]
 * 
 * And converts them to PROJJSON-like Map structures for further transformation
 * into ProjectionDef objects.
 */
public final class ProjJsonBuilder {

    private ProjJsonBuilder() {
        // Utility class
    }

    /**
     * Build a PROJJSON-like Map from a parsed WKT2 AST.
     * 
     * @param root The root WKT node (List structure from WktTokenizer)
     * @return Map representing the PROJJSON structure
     */
    public static Map<String, Object> build(List<Object> root) {
        if (root == null || root.isEmpty()) {
            return new HashMap<>();
        }
        return convert(root, new HashMap<>());
    }

    /**
     * Convert a WKT2 node to PROJJSON Map.
     * 
     * @param node The WKT node to convert
     * @param result The result Map to populate
     * @return The populated result Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> convert(List<Object> node, Map<String, Object> result) {
        if (node == null || node.isEmpty()) {
            return result;
        }

        String keyword = node.get(0).toString();

        switch (keyword) {
            case "PROJCRS":
                convertProjCrs(node, result);
                break;

            case "BASEGEOGCRS":
            case "GEOGCRS":
                convertGeogCrs(node, result);
                break;

            case "DATUM":
                convertDatum(node, result);
                break;

            case "ENSEMBLE":
                convertEnsemble(node, result);
                break;

            case "ELLIPSOID":
                convertEllipsoid(node, result);
                break;

            case "CONVERSION":
                convertConversion(node, result);
                break;

            case "METHOD":
                convertMethod(node, result);
                break;

            case "PARAMETER":
                convertParameter(node, result);
                break;

            case "BOUNDCRS":
                convertBoundCrs(node, result);
                break;

            case "ABRIDGEDTRANSFORMATION":
                convertAbridgedTransformation(node, result);
                break;

            case "AXIS":
                convertAxis(node, result);
                break;

            case "LENGTHUNIT":
                convertLengthUnit(node, result);
                break;

            default:
                result.put("keyword", keyword);
                break;
        }

        return result;
    }

    /**
     * Convert PROJCRS node.
     */
    @SuppressWarnings("unchecked")
    private static void convertProjCrs(List<Object> node, Map<String, Object> result) {
        result.put("type", "ProjectedCRS");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }

        // Find and convert BASEGEOGCRS
        List<Object> baseCrsNode = findNode(node, "BASEGEOGCRS");
        if (baseCrsNode != null) {
            result.put("base_crs", convert(baseCrsNode, new HashMap<>()));
        }

        // Find and convert CONVERSION
        List<Object> conversionNode = findNode(node, "CONVERSION");
        if (conversionNode != null) {
            result.put("conversion", convert(conversionNode, new HashMap<>()));
        }

        // Find and convert CS (coordinate system)
        List<Object> csNode = findNode(node, "CS");
        if (csNode != null) {
            Map<String, Object> coordSystem = new HashMap<>();
            if (csNode.size() > 1) {
                coordSystem.put("type", csNode.get(1));
            }
            coordSystem.put("axis", extractAxes(node));
            result.put("coordinate_system", coordSystem);
        }

        // Find and convert LENGTHUNIT
        List<Object> lengthUnitNode = findNode(node, "LENGTHUNIT");
        if (lengthUnitNode != null) {
            Map<String, Object> unit = convertUnit(lengthUnitNode);
            Map<String, Object> coordSystem = (Map<String, Object>) result.get("coordinate_system");
            if (coordSystem != null) {
                coordSystem.put("unit", unit);
            }
        }

        // Find ID
        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * Convert GEOGCRS or BASEGEOGCRS node.
     */
    @SuppressWarnings("unchecked")
    private static void convertGeogCrs(List<Object> node, Map<String, Object> result) {
        result.put("type", "GeographicCRS");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }

        // Find and convert DATUM or ENSEMBLE
        List<Object> datumNode = findNode(node, "DATUM");
        List<Object> ensembleNode = findNode(node, "ENSEMBLE");

        if (datumNode != null) {
            Map<String, Object> datum = convert(datumNode, new HashMap<>());
            result.put("datum", datum);
            
            // Check for PRIMEM
            List<Object> primemNode = findNode(node, "PRIMEM");
            if (primemNode != null && primemNode.size() > 1 && !"Greenwich".equals(primemNode.get(1))) {
                Map<String, Object> primeMeridian = new HashMap<>();
                primeMeridian.put("name", primemNode.get(1));
                if (primemNode.size() > 2) {
                    primeMeridian.put("longitude", parseDouble(primemNode.get(2)));
                }
                datum.put("prime_meridian", primeMeridian);
            }
        } else if (ensembleNode != null) {
            result.put("datum_ensemble", convert(ensembleNode, new HashMap<>()));
        }

        // Coordinate system
        Map<String, Object> coordSystem = new HashMap<>();
        coordSystem.put("type", "ellipsoidal");
        coordSystem.put("axis", extractAxes(node));
        result.put("coordinate_system", coordSystem);

        // Find ID
        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * Convert DATUM node.
     */
    private static void convertDatum(List<Object> node, Map<String, Object> result) {
        result.put("type", "GeodeticReferenceFrame");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }

        // Find and convert ELLIPSOID
        List<Object> ellipsoidNode = findNode(node, "ELLIPSOID");
        if (ellipsoidNode != null) {
            result.put("ellipsoid", convert(ellipsoidNode, new HashMap<>()));
        }
    }

    /**
     * Convert ENSEMBLE node.
     */
    @SuppressWarnings("unchecked")
    private static void convertEnsemble(List<Object> node, Map<String, Object> result) {
        result.put("type", "DatumEnsemble");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }

        // Extract members
        List<Map<String, Object>> members = new ArrayList<>();
        for (Object child : node) {
            if (child instanceof List) {
                List<Object> childList = (List<Object>) child;
                if (!childList.isEmpty() && "MEMBER".equals(childList.get(0))) {
                    Map<String, Object> member = new HashMap<>();
                    member.put("type", "DatumEnsembleMember");
                    if (childList.size() > 1) {
                        member.put("name", childList.get(1));
                    }
                    Map<String, Object> memberId = getId(childList);
                    if (memberId != null) {
                        member.put("id", memberId);
                    }
                    members.add(member);
                }
            }
        }
        result.put("members", members);

        // Extract accuracy
        List<Object> accuracyNode = findNode(node, "ENSEMBLEACCURACY");
        if (accuracyNode != null && accuracyNode.size() > 1) {
            result.put("accuracy", parseDouble(accuracyNode.get(1)));
        }

        // Extract ellipsoid
        List<Object> ellipsoidNode = findNode(node, "ELLIPSOID");
        if (ellipsoidNode != null) {
            result.put("ellipsoid", convert(ellipsoidNode, new HashMap<>()));
        }

        // Find ID
        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * Convert ELLIPSOID node.
     */
    private static void convertEllipsoid(List<Object> node, Map<String, Object> result) {
        result.put("type", "Ellipsoid");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }
        if (node.size() > 2) {
            result.put("semi_major_axis", parseDouble(node.get(2)));
        }
        if (node.size() > 3) {
            result.put("inverse_flattening", parseDouble(node.get(3)));
        }
    }

    /**
     * Convert CONVERSION node.
     */
    @SuppressWarnings("unchecked")
    private static void convertConversion(List<Object> node, Map<String, Object> result) {
        result.put("type", "Conversion");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }

        // Find METHOD
        List<Object> methodNode = findNode(node, "METHOD");
        if (methodNode != null) {
            result.put("method", convert(methodNode, new HashMap<>()));
        }

        // Extract PARAMETER nodes
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (Object child : node) {
            if (child instanceof List) {
                List<Object> childList = (List<Object>) child;
                if (!childList.isEmpty() && "PARAMETER".equals(childList.get(0))) {
                    parameters.add(convert(childList, new HashMap<>()));
                }
            }
        }
        result.put("parameters", parameters);
    }

    /**
     * Convert METHOD node.
     */
    private static void convertMethod(List<Object> node, Map<String, Object> result) {
        result.put("type", "Method");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }
        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * Convert PARAMETER node.
     */
    private static void convertParameter(List<Object> node, Map<String, Object> result) {
        result.put("type", "Parameter");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }
        if (node.size() > 2) {
            result.put("value", parseDouble(node.get(2)));
        }

        // Find unit (LENGTHUNIT, ANGLEUNIT, or SCALEUNIT)
        List<Object> unitNode = findNodeAny(node, "LENGTHUNIT", "ANGLEUNIT", "SCALEUNIT");
        if (unitNode != null) {
            result.put("unit", convertUnit(unitNode));
        }

        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * Convert BOUNDCRS node.
     */
    @SuppressWarnings("unchecked")
    private static void convertBoundCrs(List<Object> node, Map<String, Object> result) {
        result.put("type", "BoundCRS");

        // Process SOURCECRS
        List<Object> sourceCrsNode = findNode(node, "SOURCECRS");
        if (sourceCrsNode != null) {
            // Find the actual CRS content within SOURCECRS
            List<Object> sourceCrsContent = findNodeAny(sourceCrsNode, "PROJCRS", "GEOGCRS");
            if (sourceCrsContent != null) {
                result.put("source_crs", convert(sourceCrsContent, new HashMap<>()));
            }
        }

        // Process TARGETCRS
        List<Object> targetCrsNode = findNode(node, "TARGETCRS");
        if (targetCrsNode != null) {
            List<Object> targetCrsContent = findNodeAny(targetCrsNode, "PROJCRS", "GEOGCRS");
            if (targetCrsContent != null) {
                result.put("target_crs", convert(targetCrsContent, new HashMap<>()));
            }
        }

        // Process ABRIDGEDTRANSFORMATION
        List<Object> transformationNode = findNode(node, "ABRIDGEDTRANSFORMATION");
        if (transformationNode != null) {
            result.put("transformation", convert(transformationNode, new HashMap<>()));
        }
    }

    /**
     * Convert ABRIDGEDTRANSFORMATION node.
     */
    @SuppressWarnings("unchecked")
    private static void convertAbridgedTransformation(List<Object> node, Map<String, Object> result) {
        result.put("type", "Transformation");
        if (node.size() > 1) {
            result.put("name", node.get(1));
        }

        // Find METHOD
        List<Object> methodNode = findNode(node, "METHOD");
        if (methodNode != null) {
            result.put("method", convert(methodNode, new HashMap<>()));
        }

        // Extract PARAMETER and PARAMETERFILE nodes
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (Object child : node) {
            if (child instanceof List) {
                List<Object> childList = (List<Object>) child;
                if (!childList.isEmpty()) {
                    String childKey = childList.get(0).toString();
                    if ("PARAMETER".equals(childKey)) {
                        parameters.add(convert(childList, new HashMap<>()));
                    } else if ("PARAMETERFILE".equals(childKey)) {
                        Map<String, Object> param = new HashMap<>();
                        if (childList.size() > 1) {
                            param.put("name", childList.get(1));
                        }
                        if (childList.size() > 2) {
                            param.put("value", childList.get(2));
                        }
                        Map<String, Object> paramId = new HashMap<>();
                        paramId.put("authority", "EPSG");
                        paramId.put("code", 8656);
                        param.put("id", paramId);
                        parameters.add(param);
                    }
                }
            }
        }

        // Adjust Scale difference parameter if present (for 7-param transforms)
        if (parameters.size() == 7) {
            Map<String, Object> scaleDiff = parameters.get(6);
            if ("Scale difference".equals(scaleDiff.get("name"))) {
                Object valueObj = scaleDiff.get("value");
                if (valueObj instanceof Number) {
                    double value = ((Number) valueObj).doubleValue();
                    scaleDiff.put("value", Math.round((value - 1) * 1e12) / 1e6);
                }
            }
        }

        result.put("parameters", parameters);

        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * Convert AXIS node.
     */
    @SuppressWarnings("unchecked")
    private static void convertAxis(List<Object> node, Map<String, Object> result) {
        if (!result.containsKey("coordinate_system")) {
            Map<String, Object> coordSystem = new HashMap<>();
            coordSystem.put("type", "unspecified");
            coordSystem.put("axis", new ArrayList<Map<String, Object>>());
            result.put("coordinate_system", coordSystem);
        }

        Map<String, Object> axisInfo = convertAxisNode(node);
        List<Map<String, Object>> axisList = (List<Map<String, Object>>) 
            ((Map<String, Object>) result.get("coordinate_system")).get("axis");
        axisList.add(axisInfo);
    }

    /**
     * Convert LENGTHUNIT node for top-level units.
     */
    @SuppressWarnings("unchecked")
    private static void convertLengthUnit(List<Object> node, Map<String, Object> result) {
        Map<String, Object> unit = convertUnit(node);
        Map<String, Object> coordSystem = (Map<String, Object>) result.get("coordinate_system");
        if (coordSystem != null) {
            List<Map<String, Object>> axisList = (List<Map<String, Object>>) coordSystem.get("axis");
            if (axisList != null) {
                for (Map<String, Object> axis : axisList) {
                    if (!axis.containsKey("unit")) {
                        axis.put("unit", unit);
                    }
                }
            }
        }
        // Handle semi_major_axis scaling
        if (result.containsKey("semi_major_axis") && unit.containsKey("conversion_factor")) {
            Object convFactor = unit.get("conversion_factor");
            if (convFactor instanceof Number && ((Number) convFactor).doubleValue() != 1.0) {
                Map<String, Object> smaObj = new HashMap<>();
                smaObj.put("value", result.get("semi_major_axis"));
                smaObj.put("unit", unit);
                result.put("semi_major_axis", smaObj);
            }
        }
    }

    // Helper methods

    /**
     * Find a child node with the specified keyword.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> findNode(List<Object> parent, String keyword) {
        for (Object child : parent) {
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
     * Find a child node with any of the specified keywords.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> findNodeAny(List<Object> parent, String... keywords) {
        for (Object child : parent) {
            if (child instanceof List) {
                List<Object> childList = (List<Object>) child;
                if (!childList.isEmpty()) {
                    String childKeyword = childList.get(0).toString();
                    for (String keyword : keywords) {
                        if (keyword.equals(childKeyword)) {
                            return childList;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract ID from a node.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getId(List<Object> node) {
        List<Object> idNode = findNode(node, "ID");
        if (idNode != null && idNode.size() >= 3) {
            Map<String, Object> id = new HashMap<>();
            id.put("authority", idNode.get(1));
            Object codeObj = idNode.get(2);
            if (codeObj instanceof Number) {
                id.put("code", ((Number) codeObj).intValue());
            } else {
                try {
                    id.put("code", Integer.parseInt(codeObj.toString()));
                } catch (NumberFormatException e) {
                    id.put("code", codeObj);
                }
            }
            return id;
        }
        return null;
    }

    /**
     * Convert a unit node to Map.
     */
    private static Map<String, Object> convertUnit(List<Object> node) {
        Map<String, Object> unit = new HashMap<>();
        if (node == null || node.size() < 3) {
            unit.put("type", "unit");
            unit.put("name", "unknown");
            unit.put("conversion_factor", null);
            return unit;
        }

        // Determine type from keyword
        String keyword = node.get(0).toString();
        if ("LENGTHUNIT".equals(keyword)) {
            unit.put("type", "LinearUnit");
        } else if ("ANGLEUNIT".equals(keyword)) {
            unit.put("type", "AngularUnit");
        } else if ("SCALEUNIT".equals(keyword)) {
            unit.put("type", "ScaleUnit");
        } else {
            unit.put("type", "unit");
        }

        unit.put("name", node.get(1));
        unit.put("conversion_factor", parseDouble(node.get(2)));

        Map<String, Object> id = getId(node);
        if (id != null) {
            unit.put("id", id);
        }

        return unit;
    }

    /**
     * Convert an AXIS node to Map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertAxisNode(List<Object> node) {
        Map<String, Object> axis = new HashMap<>();
        
        String name = node.size() > 1 ? node.get(1).toString() : "Unknown";
        axis.put("name", name);

        // Determine direction
        String direction;
        // Check for abbreviation pattern like "(E)" or "(N)"
        if (name.matches("^\\([A-Za-z]\\)$")) {
            String abbrev = name.substring(1, 2).toUpperCase();
            switch (abbrev) {
                case "E": direction = "east"; break;
                case "N": direction = "north"; break;
                case "U": direction = "up"; break;
                case "W": direction = "west"; break;
                case "S": direction = "south"; break;
                default: direction = "unknown"; break;
            }
        } else if (node.size() > 2) {
            direction = node.get(2).toString().toLowerCase();
        } else {
            direction = "unknown";
        }
        axis.put("direction", direction);

        // Find ORDER
        List<Object> orderNode = findNode(node, "ORDER");
        if (orderNode != null && orderNode.size() > 1) {
            Object orderVal = orderNode.get(1);
            if (orderVal instanceof Number) {
                axis.put("order", ((Number) orderVal).intValue());
            } else {
                try {
                    axis.put("order", Integer.parseInt(orderVal.toString()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }

        // Find unit
        List<Object> unitNode = findNodeAny(node, "LENGTHUNIT", "ANGLEUNIT", "SCALEUNIT");
        if (unitNode != null) {
            axis.put("unit", convertUnit(unitNode));
        }

        return axis;
    }

    /**
     * Extract all AXIS nodes from a parent node.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractAxes(List<Object> parent) {
        List<Map<String, Object>> axes = new ArrayList<>();
        for (Object child : parent) {
            if (child instanceof List) {
                List<Object> childList = (List<Object>) child;
                if (!childList.isEmpty() && "AXIS".equals(childList.get(0))) {
                    axes.add(convertAxisNode(childList));
                }
            }
        }
        // Sort by order if present
        axes.sort((a, b) -> {
            Integer orderA = (Integer) a.get("order");
            Integer orderB = (Integer) b.get("order");
            if (orderA == null) orderA = 0;
            if (orderB == null) orderB = 0;
            return orderA.compareTo(orderB);
        });
        return axes;
    }

    /**
     * Safely parse a value to double.
     */
    private static Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
