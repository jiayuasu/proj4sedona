package org.proj4sedona.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes parsed WKT1 s-expressions into Map structures.
 * Mirrors: wkt-parser/process.js
 * 
 * Converts the nested List structure from WktTokenizer into a Map
 * representation suitable for further processing.
 */
public final class WktProcessor {

    private WktProcessor() {
        // Utility class
    }

    /**
     * Process a WKT s-expression into a Map structure.
     * 
     * @param v The parsed WKT element (can be String, Number, or List)
     * @param obj The Map to populate
     */
    @SuppressWarnings("unchecked")
    public static void sExpr(Object v, Map<String, Object> obj) {
        if (v == null) {
            return;
        }

        // Handle non-array values
        if (!(v instanceof List)) {
            obj.put(v.toString(), Boolean.TRUE);
            return;
        }

        List<Object> list = (List<Object>) v;
        if (list.isEmpty()) {
            return;
        }

        // Get the key (first element)
        Object keyObj = list.get(0);
        String key;
        
        // Make a mutable copy so we can shift elements
        List<Object> values = new ArrayList<>(list.subList(1, list.size()));

        if (keyObj instanceof List) {
            // If key is a list, prepend it to values and process without key
            values.add(0, keyObj);
            key = null;
        } else {
            key = keyObj.toString();
        }

        // Handle PARAMETER specially - use second element as key
        if ("PARAMETER".equals(key) && !values.isEmpty()) {
            key = values.remove(0).toString();
        }

        // Single value case
        if (values.size() == 1) {
            Object singleValue = values.get(0);
            if (singleValue instanceof List) {
                Map<String, Object> nested = new HashMap<>();
                sExpr(singleValue, nested);
                obj.put(key, nested);
            } else {
                obj.put(key, singleValue);
            }
            return;
        }

        // No values case
        if (values.isEmpty()) {
            obj.put(key, Boolean.TRUE);
            return;
        }

        // Special case: TOWGS84 - store as array
        if ("TOWGS84".equals(key)) {
            obj.put(key, values);
            return;
        }

        // Special case: AXIS - collect as array
        if ("AXIS".equals(key)) {
            @SuppressWarnings("unchecked")
            List<List<Object>> axisList = (List<List<Object>>) obj.computeIfAbsent("AXIS", k -> new ArrayList<>());
            axisList.add(values);
            return;
        }

        // Handle specific keywords
        if (key != null) {
            obj.computeIfAbsent(key, k -> new HashMap<String, Object>());
        }

        switch (key != null ? key : "") {
            case "UNIT":
            case "PRIMEM":
            case "VERT_DATUM":
                handleUnitPrimem(key, values, obj);
                break;

            case "SPHEROID":
            case "ELLIPSOID":
                handleSpheroid(key, values, obj);
                break;

            case "EDATUM":
            case "ENGINEERINGDATUM":
            case "LOCAL_DATUM":
            case "DATUM":
            case "VERT_CS":
            case "VERTCRS":
            case "VERTICALCRS":
                handleDatum(key, values, obj);
                break;

            case "COMPD_CS":
            case "COMPOUNDCRS":
            case "FITTED_CS":
            case "PROJECTEDCRS":
            case "PROJCRS":
            case "GEOGCS":
            case "GEOCCS":
            case "PROJCS":
            case "LOCAL_CS":
            case "GEODCRS":
            case "GEODETICCRS":
            case "GEODETICDATUM":
            case "ENGCRS":
            case "ENGINEERINGCRS":
                handleCrs(key, values, obj);
                break;

            default:
                // For arrays where all items are arrays, call mapit
                boolean allArrays = true;
                for (Object item : values) {
                    if (!(item instanceof List)) {
                        allArrays = false;
                        break;
                    }
                }
                if (allArrays && key != null) {
                    mapit(obj, key, values);
                } else if (key != null) {
                    sExpr(new ArrayList<Object>() {{
                        addAll(values);
                    }}, (Map<String, Object>) obj.get(key));
                }
                break;
        }
    }

    /**
     * Handle UNIT, PRIMEM, VERT_DATUM keywords.
     */
    private static void handleUnitPrimem(String key, List<Object> values, Map<String, Object> obj) {
        Map<String, Object> unitObj = new HashMap<>();
        if (!values.isEmpty()) {
            Object nameVal = values.get(0);
            unitObj.put("name", nameVal instanceof String ? ((String) nameVal).toLowerCase() : nameVal.toString().toLowerCase());
        }
        if (values.size() > 1) {
            unitObj.put("convert", values.get(1));
        }
        if (values.size() == 3 && values.get(2) instanceof List) {
            sExpr(values.get(2), unitObj);
        }
        obj.put(key, unitObj);
    }

    /**
     * Handle SPHEROID, ELLIPSOID keywords.
     */
    private static void handleSpheroid(String key, List<Object> values, Map<String, Object> obj) {
        Map<String, Object> spheroidObj = new HashMap<>();
        if (!values.isEmpty()) {
            spheroidObj.put("name", values.get(0));
        }
        if (values.size() > 1) {
            spheroidObj.put("a", values.get(1));
        }
        if (values.size() > 2) {
            spheroidObj.put("rf", values.get(2));
        }
        if (values.size() == 4 && values.get(3) instanceof List) {
            sExpr(values.get(3), spheroidObj);
        }
        obj.put(key, spheroidObj);
    }

    /**
     * Handle DATUM and related keywords.
     */
    @SuppressWarnings("unchecked")
    private static void handleDatum(String key, List<Object> values, Map<String, Object> obj) {
        // Convert first element to ["name", value]
        if (!values.isEmpty()) {
            Object firstVal = values.get(0);
            List<Object> nameArray = new ArrayList<>();
            nameArray.add("name");
            nameArray.add(firstVal);
            values.set(0, nameArray);
        }
        mapit(obj, key, values);
    }

    /**
     * Handle CRS keywords.
     */
    @SuppressWarnings("unchecked")
    private static void handleCrs(String key, List<Object> values, Map<String, Object> obj) {
        // Convert first element to ["name", value]
        if (!values.isEmpty()) {
            Object firstVal = values.get(0);
            List<Object> nameArray = new ArrayList<>();
            nameArray.add("name");
            nameArray.add(firstVal);
            values.set(0, nameArray);
        }
        mapit(obj, key, values);
        ((Map<String, Object>) obj.get(key)).put("type", key);
    }

    /**
     * Map array of values into an object.
     */
    @SuppressWarnings("unchecked")
    private static void mapit(Map<String, Object> obj, String key, List<Object> values) {
        Map<String, Object> thing;
        
        if (key != null) {
            thing = new HashMap<>();
            obj.put(key, thing);
        } else {
            thing = obj;
        }

        for (Object item : values) {
            sExpr(item, thing);
        }
    }
}
