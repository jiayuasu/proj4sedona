package org.apache.sedona.proj.wkt;

import java.util.*;

/**
 * Processes S-expressions from parsed WKT.
 * Ported from the JavaScript wkt-parser process.js file.
 */
public class SExpressionProcessor {
    
    /**
     * Processes an S-expression and populates the given object.
     * @param v the S-expression to process
     * @param obj the object to populate
     */
    public static void sExpr(Object v, Map<String, Object> obj) {
        if (!(v instanceof List)) {
            obj.put(String.valueOf(v), true);
            return;
        }
        
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) v;
        if (list.isEmpty()) {
            return;
        }
        
        Object key = list.get(0);
        List<Object> remaining = list.subList(1, list.size());
        
        if ("PARAMETER".equals(key)) {
            if (remaining.isEmpty()) {
                return;
            }
            key = remaining.get(0);
            remaining = remaining.subList(1, remaining.size());
        }
        
        if (remaining.size() == 1) {
            Object value = remaining.get(0);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> subList = (List<Object>) value;
                Map<String, Object> subObj = new HashMap<>();
                sExpr(subList, subObj);
                obj.put(String.valueOf(key), subObj);
                return;
            }
            obj.put(String.valueOf(key), value);
            return;
        }
        
        if (remaining.isEmpty()) {
            obj.put(String.valueOf(key), true);
            return;
        }
        
        if ("TOWGS84".equals(key)) {
            obj.put(String.valueOf(key), remaining);
            return;
        }
        
        if ("AXIS".equals(key)) {
            @SuppressWarnings("unchecked")
            List<Object> axisList = (List<Object>) obj.get(key);
            if (axisList == null) {
                axisList = new ArrayList<>();
                obj.put(String.valueOf(key), axisList);
            }
            axisList.add(remaining);
            return;
        }
        
        if ("AUTHORITY".equals(key)) {
            if (remaining.size() >= 2) {
                Map<String, Object> authority = new HashMap<>();
                authority.put(String.valueOf(remaining.get(0)), remaining.get(1));
                obj.put(String.valueOf(key), authority);
                return;
            }
        }
        
        if ("UNIT".equals(key)) {
            if (remaining.size() >= 2) {
                Map<String, Object> unit = new HashMap<>();
                unit.put("name", remaining.get(0));
                unit.put("convert", remaining.get(1));
                obj.put(String.valueOf(key), unit);
                return;
            }
        }
        
        if ("SPHEROID".equals(key)) {
            if (remaining.size() >= 3) {
                Map<String, Object> spheroid = new HashMap<>();
                spheroid.put("name", remaining.get(0));
                spheroid.put("a", remaining.get(1));
                spheroid.put("rf", remaining.get(2));
                obj.put(String.valueOf(key), spheroid);
                return;
            }
        }
        
        if ("PRIMEM".equals(key)) {
            if (remaining.size() >= 2) {
                Map<String, Object> primem = new HashMap<>();
                primem.put("name", remaining.get(0));
                primem.put("convert", remaining.get(1));
                obj.put(String.valueOf(key), primem);
                return;
            }
        }
        
        if ("PROJECTION".equals(key)) {
            if (remaining.size() == 1) {
                obj.put(String.valueOf(key), remaining.get(0));
                return;
            }
        }
        
        if ("DATUM".equals(key)) {
            if (remaining.size() >= 1) {
                Map<String, Object> datum = new HashMap<>();
                datum.put("name", remaining.get(0));
                // Process all remaining sub-lists (SPHEROID, TOWGS84, etc.)
                for (int i = 1; i < remaining.size(); i++) {
                    Object item = remaining.get(i);
                    if (item instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> subList = (List<Object>) item;
                        sExpr(subList, datum);
                    }
                }
                obj.put(String.valueOf(key), datum);
                return;
            }
        }
        
        // Handle nested structures
        Map<String, Object> nested = new HashMap<>();
        for (Object item : remaining) {
            if (item instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> subList = (List<Object>) item;
                sExpr(subList, nested);
            } else {
                nested.put(String.valueOf(item), true);
            }
        }
        obj.put(String.valueOf(key), nested);
    }
}
