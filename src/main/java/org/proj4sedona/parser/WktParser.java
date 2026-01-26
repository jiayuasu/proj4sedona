package org.proj4sedona.parser;

import org.proj4sedona.core.ProjectionDef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for WKT (Well-Known Text) parsing.
 * Mirrors: wkt-parser/index.js
 * 
 * This class supports parsing of:
 * - WKT1 (OGC Well-Known Text) strings
 * - WKT2 (ISO 19162:2015 and 2019) strings
 * - PROJJSON (as Map objects)
 * 
 * Example usage:
 * <pre>
 * // Parse WKT1
 * ProjectionDef def = WktParser.parse("PROJCS[\"WGS 84 / UTM zone 32N\", ...]");
 * 
 * // Parse WKT2
 * ProjectionDef def = WktParser.parse("PROJCRS[\"WGS 84 / UTM zone 32N\", ...]");
 * 
 * // Parse PROJJSON
 * Map&lt;String, Object&gt; projjson = ...; // PROJJSON as Map
 * ProjectionDef def = WktParser.parse(projjson);
 * </pre>
 */
public final class WktParser {

    private WktParser() {
        // Utility class
    }

    /**
     * Parse a WKT string (WKT1 or WKT2) into a ProjectionDef.
     * 
     * The method automatically detects whether the input is WKT1 or WKT2
     * and uses the appropriate parsing strategy.
     * 
     * @param wkt The WKT string to parse
     * @return The parsed ProjectionDef
     * @throws IllegalArgumentException if the WKT string cannot be parsed
     */
    public static ProjectionDef parse(String wkt) {
        if (wkt == null || wkt.isEmpty()) {
            throw new IllegalArgumentException("WKT string cannot be null or empty");
        }

        // Detect WKT version
        WktVersion version = WktVersion.detect(wkt);

        // Parse the WKT string into AST
        WktTokenizer tokenizer = new WktTokenizer();
        List<Object> ast = tokenizer.parse(wkt);

        if (version == WktVersion.WKT2) {
            // WKT2 path: AST -> PROJJSON -> ProjectionDef
            Map<String, Object> projjson = ProjJsonBuilder.build(ast);
            return ProjJsonTransformer.transform(projjson);
        } else {
            // WKT1 path: AST -> sExpr processing -> ProjectionDef
            return parseWkt1(ast);
        }
    }

    /**
     * Parse a PROJJSON Map directly into a ProjectionDef.
     * 
     * @param projjson The PROJJSON Map structure
     * @return The parsed ProjectionDef
     */
    public static ProjectionDef parse(Map<String, Object> projjson) {
        if (projjson == null) {
            throw new IllegalArgumentException("PROJJSON cannot be null");
        }
        return ProjJsonTransformer.transform(projjson);
    }

    /**
     * Parse WKT1 AST into a ProjectionDef.
     */
    @SuppressWarnings("unchecked")
    private static ProjectionDef parseWkt1(List<Object> ast) {
        if (ast == null || ast.isEmpty()) {
            throw new IllegalArgumentException("Empty WKT AST");
        }

        // Get the root type (PROJCS, GEOGCS, etc.)
        String type = ast.get(0).toString();

        // Process AST into Map structure
        Map<String, Object> obj = new HashMap<>();
        WktProcessor.sExpr(ast, obj);

        // Clean and normalize the WKT Map
        WktUtils.cleanWkt(obj);

        // Get the processed CRS object
        Object crsObj = obj.get(type);
        if (crsObj instanceof Map) {
            return WktUtils.mapToProjectionDef((Map<String, Object>) crsObj);
        }

        // Fallback: convert the whole object
        return WktUtils.mapToProjectionDef(obj);
    }

    /**
     * Check if a string looks like WKT format.
     * 
     * @param str The string to check
     * @return true if the string appears to be WKT
     */
    public static boolean isWkt(String str) {
        return WktVersion.isWkt(str);
    }

    /**
     * Check if a string is WKT2 format.
     * 
     * @param str The string to check
     * @return true if the string appears to be WKT2
     */
    public static boolean isWkt2(String str) {
        return WktVersion.detect(str) == WktVersion.WKT2;
    }

    /**
     * Parse WKT and return the raw AST (for debugging/testing).
     * 
     * @param wkt The WKT string
     * @return The parsed AST as a nested List structure
     */
    public static List<Object> parseToAst(String wkt) {
        WktTokenizer tokenizer = new WktTokenizer();
        return tokenizer.parse(wkt);
    }

    /**
     * Parse WKT2 and return the intermediate PROJJSON Map (for debugging/testing).
     * 
     * @param wkt The WKT2 string
     * @return The PROJJSON Map structure
     */
    public static Map<String, Object> parseWkt2ToProjJson(String wkt) {
        WktTokenizer tokenizer = new WktTokenizer();
        List<Object> ast = tokenizer.parse(wkt);
        return ProjJsonBuilder.build(ast);
    }
}
