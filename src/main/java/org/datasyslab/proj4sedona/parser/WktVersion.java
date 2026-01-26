package org.datasyslab.proj4sedona.parser;

import java.util.List;

/**
 * WKT version detection utilities.
 * Mirrors: wkt-parser/detectWKTVersion.js
 * 
 * Detects whether a WKT string is WKT1 or WKT2 based on characteristic keywords.
 */
public enum WktVersion {
    
    /**
     * WKT1 - Original OGC Well-Known Text format.
     * Uses keywords like PROJCS, GEOGCS, UNIT, SPHEROID.
     */
    WKT1,
    
    /**
     * WKT2 - ISO 19162:2015 and ISO 19162:2019 formats.
     * Uses keywords like PROJCRS, GEOGCRS, LENGTHUNIT, ANGLEUNIT.
     */
    WKT2;

    /**
     * WKT2 sub-versions.
     */
    public enum Wkt2Version {
        /** WKT2-2015 (ISO 19162:2015) */
        V2015,
        /** WKT2-2019 (ISO 19162:2019) - has USAGE nodes */
        V2019
    }

    /**
     * Detect whether a WKT string is WKT1 or WKT2.
     * 
     * @param wkt The WKT string to analyze
     * @return The detected WKT version
     */
    public static WktVersion detect(String wkt) {
        if (wkt == null || wkt.isEmpty()) {
            return WKT1; // Default to WKT1
        }

        String normalizedWkt = wkt.toUpperCase();

        // Check for WKT2-specific keywords
        if (normalizedWkt.contains("PROJCRS") ||
            normalizedWkt.contains("GEOGCRS") ||
            normalizedWkt.contains("BOUNDCRS") ||
            normalizedWkt.contains("VERTCRS") ||
            normalizedWkt.contains("LENGTHUNIT") ||
            normalizedWkt.contains("ANGLEUNIT") ||
            normalizedWkt.contains("SCALEUNIT")) {
            return WKT2;
        }

        // Check for WKT1-specific keywords (not strictly necessary, but explicit)
        if (normalizedWkt.contains("PROJCS") ||
            normalizedWkt.contains("GEOGCS") ||
            normalizedWkt.contains("LOCAL_CS") ||
            normalizedWkt.contains("VERT_CS") ||
            normalizedWkt.contains("UNIT")) {
            return WKT1;
        }

        // Default to WKT1 if no specific indicators are found
        return WKT1;
    }

    /**
     * Detect the WKT2 sub-version from a parsed AST.
     * Mirrors: wkt-parser/buildPROJJSON.js detectWKT2Version()
     * 
     * @param root The parsed WKT root node (List structure)
     * @return The detected WKT2 sub-version
     */
    public static Wkt2Version detectWkt2Version(List<Object> root) {
        if (root == null || root.isEmpty()) {
            return Wkt2Version.V2015;
        }

        // Check for WKT2-2019-specific USAGE node
        for (Object child : root) {
            if (child instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> childList = (List<Object>) child;
                if (!childList.isEmpty() && "USAGE".equals(childList.get(0))) {
                    return Wkt2Version.V2019;
                }
            }
        }

        // Check for PROJCRS, GEOGCRS, BOUNDCRS (valid in both, default to 2015)
        if (!root.isEmpty()) {
            Object first = root.get(0);
            if ("BOUNDCRS".equals(first) || "PROJCRS".equals(first) || "GEOGCRS".equals(first)) {
                return Wkt2Version.V2015;
            }
        }

        // Default to WKT2-2015
        return Wkt2Version.V2015;
    }

    /**
     * Check if a string looks like WKT (contains square brackets).
     * 
     * @param str The string to check
     * @return true if the string appears to be WKT format
     */
    public static boolean isWkt(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // WKT strings contain '[' and typically start with a keyword
        return str.contains("[") && !str.startsWith("+");
    }
}
