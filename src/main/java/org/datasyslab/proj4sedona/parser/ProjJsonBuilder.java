package org.datasyslab.proj4sedona.parser;

import org.datasyslab.proj4sedona.constants.Values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern AXIS_NAME_WITH_ABBREVIATION =
        Pattern.compile("^(.*?)\\s*\\(([^()]*)\\)$");

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
            case "BASEGEODCRS":
            case "GEOGCRS":
            case "GEODCRS":
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

        // Find and convert the base CRS: BASEGEOGCRS (WKT2-2019) or BASEGEODCRS
        // (WKT2-2015 — PROJ's WKT2:2015 output uses it for every projected CRS).
        List<Object> baseCrsNode = findNode(node, "BASEGEOGCRS");
        if (baseCrsNode == null) {
            baseCrsNode = findNode(node, "BASEGEODCRS");
        }
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
                coordSystem.put("subtype", csNode.get(1));
            }
            coordSystem.put("axis", extractAxes(node));
            result.put("coordinate_system", coordSystem);
        }

        // Find and convert the coordinate-system unit: LENGTHUNIT, or the plain
        // UNIT keyword the WKT2 SIMPLIFIED conventions emit.
        List<Object> lengthUnitNode = findNode(node, "LENGTHUNIT");
        if (lengthUnitNode == null) {
            lengthUnitNode = findNode(node, "UNIT");
        }
        if (lengthUnitNode != null) {
            Map<String, Object> unit = convertUnit(lengthUnitNode);
            Map<String, Object> coordSystem = (Map<String, Object>) result.get("coordinate_system");
            if (coordSystem != null) {
                coordSystem.put("unit", unit);
            }
        }

        applySimplifiedConversionUnits(result);

        // Find ID
        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * Convert a geodetic CRS node: GEOGCRS/BASEGEOGCRS (WKT2-2019) or
     * GEODCRS/BASEGEODCRS (WKT2-2015; GEODCRS also covers geocentric CRSs via
     * the Cartesian coordinate-system subtype).
     */
    @SuppressWarnings("unchecked")
    private static void convertGeogCrs(List<Object> node, Map<String, Object> result) {
        // The WKT2-2015 GEODCRS keyword covers both geographic and geocentric CRSs;
        // GEOGCRS (2019) is geographic only. The PROJJSON type is decided by the
        // coordinate-system subtype, not the keyword: PROJ rejects a GeodeticCRS
        // document with an ellipsoidal coordinate system ("expected a Cartesian or
        // spherical CS") and itself normalizes an ellipsoidal GEODCRS to
        // GeographicCRS. Divergence from wkt-parser 1.5.5, which stamps GeodeticCRS
        // on every GEODCRS — its intermediate PROJJSON is internal, while ours is
        // exposed via WktParser.parseWkt2ToProjJson. (The transformer still accepts
        // GeodeticCRS + ellipsoidal leniently on input.)
        boolean isGeodetic = "GEODCRS".equals(node.get(0).toString());
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
            if (primemNode != null && primemNode.size() > 1
                    && (!"Greenwich".equals(primemNode.get(1))
                        || primeMeridianIsNumericallyNonZero(primemNode))) {
                Map<String, Object> primeMeridian = new HashMap<>();
                primeMeridian.put("name", primemNode.get(1));
                if (primemNode.size() > 2) {
                    // The PRIMEM value is in its own ANGLEUNIT when present, else in
                    // the CRS's angular unit (the SIMPLIFIED form drops the local
                    // unit — EPSG:4807's Paris meridian is 2.5969213 grads, not
                    // degrees), else degrees. Normalized to degrees here, the plain-
                    // number form of the PROJJSON prime_meridian.longitude field.
                    // Divergence from wkt-parser 1.5.5, which reads the raw value as
                    // degrees (~0.26 deg / 29 km error for grads meridians).
                    double raw = parseDouble(primemNode.get(2));
                    Double toRadians = angularUnitFactor(findNode(primemNode, "ANGLEUNIT"));
                    if (toRadians == null) {
                        toRadians = angularUnitFactor(findNode(primemNode, "UNIT"));
                    }
                    if (toRadians == null) {
                        toRadians = angularUnitFactor(findNode(node, "ANGLEUNIT"));
                    }
                    if (toRadians == null) {
                        toRadians = angularUnitFactor(findNode(node, "UNIT"));
                    }
                    double degrees = toRadians != null
                        ? Math.round(raw * toRadians * Values.R2D * 1e9) / 1e9
                        : raw;
                    primeMeridian.put("longitude", degrees);
                }
                datum.put("prime_meridian", primeMeridian);
            }
        } else if (ensembleNode != null) {
            result.put("datum_ensemble", convert(ensembleNode, new HashMap<>()));
        }

        // Coordinate system. For GEODCRS the CS node's subtype decides geographic
        // (ellipsoidal) vs geocentric (Cartesian) in the downstream transformer.
        // Backported ahead of wkt-parser 1.5.6 (b7abacf), which now honors the CS
        // node in WKT2-2015 too. PROJ's own WKT2:2015 output for EPSG:4978 carries
        // CS[Cartesian,3] and no USAGE node (USAGE is 2019-only), and both parsers
        // now classify it as geocentric.
        Map<String, Object> coordSystem = new HashMap<>();
        String subtype = "ellipsoidal";
        List<Object> csNode = findNode(node, "CS");
        if (isGeodetic && csNode != null && csNode.size() > 1) {
            subtype = csNode.get(1).toString();
        }
        result.put("type",
            isGeodetic && "Cartesian".equals(subtype) ? "GeodeticCRS" : "GeographicCRS");
        coordSystem.put("subtype", subtype);
        coordSystem.put("axis", extractAxes(node));
        // The WKT2 SIMPLIFIED conventions carry a single CS-level unit (plain UNIT
        // keyword) instead of per-axis units; without it a non-metre simplified
        // geocentric CRS would silently lose its scale.
        List<Object> csUnitNode = findNode(node, "LENGTHUNIT");
        if (csUnitNode == null) {
            csUnitNode = findNode(node, "ANGLEUNIT");
        }
        if (csUnitNode == null) {
            csUnitNode = findNode(node, "UNIT");
        }
        if (csUnitNode != null) {
            coordSystem.put("unit", convertUnit(csUnitNode));
        }
        result.put("coordinate_system", coordSystem);

        // Find ID
        Map<String, Object> id = getId(node);
        if (id != null) {
            result.put("id", id);
        }
    }

    /**
     * A WKT producer can label a non-zero meridian "Greenwich".  The numeric value
     * is authoritative; dropping it by name changes every longitude in the CRS.
     */
    private static boolean primeMeridianIsNumericallyNonZero(List<Object> primemNode) {
        return primemNode.size() > 2 && parseDouble(primemNode.get(2)) != 0.0;
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
     * WKT2 SIMPLIFIED omits each conversion parameter's local unit. Angular
     * parameters inherit the base geographic CRS unit, linear parameters inherit
     * the projected coordinate-system unit, and scale parameters use unity.
     */
    @SuppressWarnings("unchecked")
    private static void applySimplifiedConversionUnits(Map<String, Object> projectedCrs) {
        Object conversionValue = projectedCrs.get("conversion");
        if (!(conversionValue instanceof Map)) {
            return;
        }
        Object parametersValue =
            ((Map<String, Object>) conversionValue).get("parameters");
        if (!(parametersValue instanceof List)) {
            return;
        }

        Object angularUnit = coordinateSystemUnit(projectedCrs.get("base_crs"));
        Object linearUnit = coordinateSystemUnit(projectedCrs);
        for (Object parameterValue : (List<?>) parametersValue) {
            if (!(parameterValue instanceof Map)) {
                continue;
            }
            Map<String, Object> parameter = (Map<String, Object>) parameterValue;
            if (parameter.containsKey("unit") || parameter.get("name") == null) {
                continue;
            }
            String name =
                parameter.get("name").toString().toLowerCase(Locale.ROOT);
            if (isAngularConversionParameter(name) && angularUnit != null) {
                parameter.put("unit", angularUnit);
            } else if (isLinearConversionParameter(name) && linearUnit != null) {
                parameter.put("unit", linearUnit);
            } else if (name.contains("scale factor")) {
                Map<String, Object> scaleUnit = new HashMap<>();
                scaleUnit.put("type", "ScaleUnit");
                scaleUnit.put("name", "unity");
                scaleUnit.put("conversion_factor", 1.0);
                parameter.put("unit", scaleUnit);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object coordinateSystemUnit(Object crsValue) {
        if (!(crsValue instanceof Map)) {
            return null;
        }
        Object coordinateSystem =
            ((Map<String, Object>) crsValue).get("coordinate_system");
        if (!(coordinateSystem instanceof Map)) {
            return null;
        }
        return ((Map<String, Object>) coordinateSystem).get("unit");
    }

    private static boolean isAngularConversionParameter(String name) {
        return name.contains("latitude")
            || name.contains("longitude")
            || name.contains("azimuth")
            || name.contains("angle")
            || name.contains("co-latitude");
    }

    private static boolean isLinearConversionParameter(String name) {
        return name.contains("easting")
            || name.contains("northing")
            || name.contains("height");
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
            List<Object> sourceCrsContent =
                findNodeAny(sourceCrsNode, "PROJCRS", "GEOGCRS", "GEODCRS");
            if (sourceCrsContent != null) {
                result.put("source_crs", convert(sourceCrsContent, new HashMap<>()));
            }
        }

        // Process TARGETCRS
        List<Object> targetCrsNode = findNode(node, "TARGETCRS");
        if (targetCrsNode != null) {
            List<Object> targetCrsContent =
                findNodeAny(targetCrsNode, "PROJCRS", "GEOGCRS", "GEODCRS");
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
            if ("Scale difference".equals(scaleDiff.get("name"))
                    || hasEpsgId(scaleDiff, 8611)) {
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

    @SuppressWarnings("unchecked")
    private static boolean hasEpsgId(Map<String, Object> object, int expectedCode) {
        Object idValue = object.get("id");
        if (!(idValue instanceof Map)) {
            return false;
        }
        Map<String, Object> id = (Map<String, Object>) idValue;
        Object authority = id.get("authority");
        Double code = parseDouble(id.get("code"));
        return authority != null && "EPSG".equalsIgnoreCase(authority.toString())
            && code != null && code == expectedCode;
    }

    /**
     * Convert AXIS node.
     */
    @SuppressWarnings("unchecked")
    private static void convertAxis(List<Object> node, Map<String, Object> result) {
        if (!result.containsKey("coordinate_system")) {
            Map<String, Object> coordSystem = new HashMap<>();
            // An AXIS node alone does not identify a coordinate-system subtype.
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
     * The to-radians conversion factor of an ANGLEUNIT/UNIT node, or null when the
     * node is absent or carries no numeric factor.
     */
    private static Double angularUnitFactor(List<Object> unitNode) {
        if (unitNode == null || unitNode.size() < 3) {
            return null;
        }
        double factor = parseDouble(unitNode.get(2));
        return factor > 0 ? factor : null;
    }

    /**
     * Convert an AXIS node to Map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertAxisNode(List<Object> node) {
        Map<String, Object> axis = new HashMap<>();

        String rawName = node.size() > 1 ? node.get(1).toString() : "Unknown";
        String name = rawName;
        String abbreviation = null;
        Matcher nameMatcher = AXIS_NAME_WITH_ABBREVIATION.matcher(rawName);
        if (nameMatcher.matches()) {
            name = nameMatcher.group(1).trim();
            abbreviation = nameMatcher.group(2);
        }
        axis.put("name", name);
        if (abbreviation != null) {
            axis.put("abbreviation", abbreviation);
        }

        // Determine direction
        String direction;
        if (node.size() > 2 && !(node.get(2) instanceof List)) {
            // Preserve the token's case (wkt-parser 1.5.5): the PROJJSON direction
            // enum is camelCase for geocentricX/Y/Z, so lowercasing produced
            // schema-invalid values in the exposed intermediate PROJJSON. It is
            // also authoritative over the abbreviation: a polar "(E)" axis may
            // legitimately point south and be qualified by a meridian.
            direction = node.get(2).toString();
        } else {
            direction = inferAxisDirection(abbreviation);
        }
        axis.put("direction", direction);

        // Find ORDER
        List<Object> orderNode = findNode(node, "ORDER");
        if (orderNode != null && orderNode.size() > 1) {
            axis.put("order", parseAxisOrder(orderNode.get(1)));
        }

        // Find unit
        List<Object> unitNode = findNodeAny(node, "LENGTHUNIT", "ANGLEUNIT", "SCALEUNIT");
        if (unitNode != null) {
            axis.put("unit", convertUnit(unitNode));
        }

        // A polar north/south direction is qualified by a meridian. PROJJSON
        // represents a non-default angular unit inside longitude's value-and-unit
        // object rather than as a sibling of longitude.
        List<Object> meridianNode = findNode(node, "MERIDIAN");
        if (meridianNode != null) {
            if (meridianNode.size() <= 1) {
                throw new IllegalArgumentException(
                    "Axis MERIDIAN requires a longitude");
            }
            Double longitude = parseDouble(meridianNode.get(1));
            if (longitude == null || !Double.isFinite(longitude)) {
                throw new IllegalArgumentException(
                    "Axis MERIDIAN longitude must be a finite number");
            }
            List<Object> meridianUnitNode =
                findNodeAny(meridianNode, "ANGLEUNIT", "UNIT");
            if (meridianUnitNode == null) {
                throw new IllegalArgumentException(
                    "Axis MERIDIAN requires ANGLEUNIT or UNIT");
            }
            Double meridianUnitFactor = angularUnitFactor(meridianUnitNode);
            if (meridianUnitFactor == null || !Double.isFinite(meridianUnitFactor)) {
                throw new IllegalArgumentException(
                    "Axis MERIDIAN unit requires a positive finite conversion factor");
            }
            Map<String, Object> valueAndUnit = new HashMap<>();
            valueAndUnit.put("value", longitude);
            valueAndUnit.put("unit", convertUnit(meridianUnitNode));
            Map<String, Object> meridian = new HashMap<>();
            meridian.put("longitude", valueAndUnit);
            axis.put("meridian", meridian);
        }

        return axis;
    }

    private static String inferAxisDirection(String abbreviation) {
        if (abbreviation == null || abbreviation.length() != 1) {
            return "unknown";
        }
        switch (Character.toUpperCase(abbreviation.charAt(0))) {
            case 'E': return "east";
            case 'N': return "north";
            case 'U': return "up";
            case 'W': return "west";
            case 'S': return "south";
            default: return "unknown";
        }
    }

    private static int parseAxisOrder(Object value) {
        Double numeric = parseDouble(value);
        if (numeric == null || !Double.isFinite(numeric)
                || numeric != Math.rint(numeric)
                || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Axis ORDER must be an integer: " + value);
        }
        return numeric.intValue();
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
        // Array position is coordinate order. Retain ORDER as metadata but do not
        // normalize malformed WKT by sorting it: WKT2 requires ORDER to agree with
        // lexical position, and the serializer can then reject a mismatch.
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
