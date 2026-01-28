package org.datasyslab.proj4sedona.util;

/**
 * Utility methods for working with Coordinate Reference System identifiers.
 */
public final class CRSUtils {

    private CRSUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Normalize an authority:code string to uppercase authority.
     * E.g., "epsg:4326" -> "EPSG:4326", "esri:102001" -> "ESRI:102001"
     * 
     * <p>This method is optimized to quickly reject long strings like
     * PROJ strings, PROJJSON, and WKT without overhead.</p>
     * 
     * @param name The input CRS identifier
     * @return The normalized string, or the original if not an authority:code pattern
     */
    public static String normalizeAuthorityCode(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        // Quick rejection for PROJ strings and PROJJSON
        char first = name.charAt(0);
        if (first == '+' || first == '{') {
            return name;
        }
        
        // Authority codes are short (e.g., "EPSG:4326", "IAU_2015:49900")
        int len = name.length();
        if (len > 25) {
            return name;
        }
        
        // Find colon - authority codes have exactly one
        int colonIdx = name.indexOf(':');
        if (colonIdx <= 0 || colonIdx == len - 1) {
            return name;
        }
        
        // Check for second colon (would indicate not an authority:code)
        if (name.indexOf(':', colonIdx + 1) >= 0) {
            return name;
        }
        
        // Normalize: uppercase authority, keep code as-is
        String authority = name.substring(0, colonIdx).toUpperCase();
        String code = name.substring(colonIdx + 1);
        return authority + ":" + code;
    }
}
