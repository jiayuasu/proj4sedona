package org.proj4.wkt;

import java.util.Map;

/**
 * Utility functions for WKT processing.
 * Ported from the JavaScript wkt-parser util.js file.
 */
public class WKTUtils {
    
    private static final double D2R = 0.01745329251994329577; // Math.PI / 180.0
    
    /**
     * Converts degrees to radians.
     * @param degrees the value in degrees
     * @return the value in radians
     */
    public static double d2r(double degrees) {
        return degrees * D2R;
    }
    
    /**
     * Applies projection defaults based on the projection name.
     * @param wkt the WKT object to apply defaults to
     */
    public static void applyProjectionDefaults(Map<String, Object> wkt) {
        // Normalize projName for WKT2 compatibility
        String projName = (String) wkt.get("projName");
        if (projName == null) {
            projName = "";
        }
        String normalizedProjName = projName.toLowerCase().replace("_", " ");
        
        // Handle longitude of center for certain projections
        if (wkt.get("long0") == null && wkt.get("longc") != null) {
            if ("albers conic equal area".equals(normalizedProjName) || 
                "lambert azimuthal equal area".equals(normalizedProjName)) {
                wkt.put("long0", wkt.get("longc"));
            }
        }
        
        // Handle latitude of true scale for stereographic projections
        if (wkt.get("lat_ts") == null && wkt.get("lat1") != null) {
            if ("stereographic south pole".equals(normalizedProjName) || 
                "polar stereographic (variant b)".equals(normalizedProjName)) {
                double lat1 = ((Number) wkt.get("lat1")).doubleValue();
                wkt.put("lat0", d2r(lat1 > 0 ? 90 : -90));
                wkt.put("lat_ts", lat1);
                wkt.remove("lat1");
            }
        } else if (wkt.get("lat_ts") == null && wkt.get("lat0") != null) {
            if ("polar stereographic".equals(normalizedProjName) || 
                "polar stereographic (variant a)".equals(normalizedProjName)) {
                double lat0 = ((Number) wkt.get("lat0")).doubleValue();
                wkt.put("lat_ts", lat0);
                wkt.put("lat0", d2r(lat0 > 0 ? 90 : -90));
                wkt.remove("lat1");
            }
        }
    }
}
