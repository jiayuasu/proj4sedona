package org.datasyslab.proj4sedona.defs;

import com.google.gson.Gson;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.parser.ProjString;
import org.datasyslab.proj4sedona.parser.WktParser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Global registry of named projection definitions.
 * Mirrors: lib/defs.js
 * 
 * <p>This class maintains a global registry of projection definitions that can be
 * looked up by name. Definitions are typically registered using EPSG/ESRI/IAU codes
 * (e.g., "EPSG:4326", "EPSG:3857") or common aliases (e.g., "WGS84", "GOOGLE").</p>
 * 
 * <p>Usage:</p>
 * <pre>
 * // Initialize the registry with default definitions
 * Defs.globals();
 * 
 * // Get a projection definition by code
 * ProjectionDef wgs84 = Defs.get("EPSG:4326");
 * 
 * // Register a custom definition
 * Defs.set("MyProj", "+proj=merc +lon_0=0 +k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m");
 * 
 * // Or register a ProjectionDef object directly
 * Defs.set("MyProj2", myProjectionDef);
 * </pre>
 * 
 * <p>The registry automatically parses PROJ strings (starting with "+") into
 * ProjectionDef objects when registering.</p>
 */
public final class Defs {

    /** Storage for projection definitions, keyed by name (case-sensitive) */
    private static final Map<String, ProjectionDef> definitions = new HashMap<>();
    
    /** Flag indicating whether global definitions have been initialized */
    private static boolean globalsInitialized = false;

    /** Whether remote EPSG lookup is enabled (default: true) */
    private static boolean remoteFetchEnabled = true;

    /** Pattern to match EPSG codes (e.g., "EPSG:4326", "epsg:2154") */
    private static final Pattern EPSG_PATTERN = Pattern.compile("^EPSG:(\\d+)$", Pattern.CASE_INSENSITIVE);

    private Defs() {
        // Utility class - prevent instantiation
    }

    /**
     * Set (register) a projection definition by name.
     * 
     * <p>If the definition is a PROJ string (starting with "+"), it will be
     * automatically parsed into a ProjectionDef object.</p>
     * 
     * @param name The name/code to register the definition under (e.g., "EPSG:4326")
     * @param projString The PROJ string definition (must start with "+")
     */
    public static void set(String name, String projString) {
        if (projString == null || projString.isEmpty()) {
            definitions.remove(name);
            return;
        }
        
        if (projString.charAt(0) == '+') {
            ProjectionDef def = ProjString.parse(projString);
            def.setSrsCode(name);
            definitions.put(name, def);
        } else {
            // For now, we only support PROJ strings
            // WKT support can be added in Phase 13
            throw new IllegalArgumentException(
                "Unsupported definition format. Only PROJ strings (starting with '+') are supported.");
        }
    }

    /**
     * Set (register) a projection definition by name.
     * 
     * @param name The name/code to register the definition under
     * @param def The ProjectionDef object
     */
    public static void set(String name, ProjectionDef def) {
        if (def == null) {
            definitions.remove(name);
        } else {
            if (def.getSrsCode() == null) {
                def.setSrsCode(name);
            }
            definitions.put(name, def);
        }
    }

    /**
     * Get a projection definition by name.
     * 
     * <p>This method first checks the local registry. If not found and remote
     * fetching is enabled, it will attempt to fetch the definition from
     * spatialreference.org for EPSG codes.</p>
     * 
     * @param name The name/code to look up (e.g., "EPSG:4326", "WGS84")
     * @return The ProjectionDef, or null if not found
     */
    public static ProjectionDef get(String name) {
        // Auto-initialize globals if not yet done
        if (!globalsInitialized) {
            globals();
        }

        // Check local registry first
        ProjectionDef def = definitions.get(name);
        if (def != null) {
            return def;
        }

        // Try remote fetch if enabled and code matches EPSG pattern
        if (remoteFetchEnabled) {
            Matcher matcher = EPSG_PATTERN.matcher(name);
            if (matcher.matches()) {
                String code = matcher.group(1);
                def = fetchFromRemote("epsg", code, name);
                if (def != null) {
                    definitions.put(name, def);
                    return def;
                }
            }
        }

        return null;
    }

    /**
     * Fetch a projection definition from spatialreference.org.
     * 
     * @param authName The authority name (e.g., "epsg")
     * @param code The CRS code (e.g., "2154")
     * @param fullCode The full code string (e.g., "EPSG:2154")
     * @return The ProjectionDef, or null if fetch failed
     */
    @SuppressWarnings("unchecked")
    private static ProjectionDef fetchFromRemote(String authName, String code, String fullCode) {
        try {
            String projJson = SpatialReferenceFetcher.fetchProjJson(authName, code);
            if (projJson != null) {
                Gson gson = new Gson();
                Map<String, Object> json = gson.fromJson(projJson, Map.class);
                ProjectionDef def = WktParser.parse(json);
                def.setSrsCode(fullCode);
                return def;
            }
        } catch (Exception e) {
            // Silently fail - return null to indicate "not found"
            // Could add logging here if needed
        }
        return null;
    }

    /**
     * Check if a definition exists.
     * 
     * @param name The name/code to check
     * @return true if the definition exists
     */
    public static boolean has(String name) {
        return definitions.containsKey(name);
    }

    /**
     * Remove a definition.
     * 
     * @param name The name/code to remove
     * @return The removed definition, or null if it didn't exist
     */
    public static ProjectionDef remove(String name) {
        return definitions.remove(name);
    }

    /**
     * Create an alias for an existing definition.
     * 
     * <p>The alias will point to the same ProjectionDef object as the original.</p>
     * 
     * @param alias The new alias name
     * @param existingName The existing definition name
     * @throws IllegalArgumentException if the existing definition doesn't exist
     */
    public static void alias(String alias, String existingName) {
        ProjectionDef def = definitions.get(existingName);
        if (def == null) {
            throw new IllegalArgumentException("Definition not found: " + existingName);
        }
        definitions.put(alias, def);
    }

    /**
     * Initialize the registry with common global definitions.
     * Mirrors: lib/global.js
     * 
     * <p>This method is idempotent - calling it multiple times has no effect
     * after the first call.</p>
     * 
     * <p>Registered definitions include:</p>
     * <ul>
     *   <li>EPSG:4326 - WGS 84 (long/lat)</li>
     *   <li>EPSG:4269 - NAD83 (long/lat)</li>
     *   <li>EPSG:3857 - WGS 84 / Pseudo-Mercator (Web Mercator)</li>
     *   <li>EPSG:326xx - UTM zones 1-60 North (WGS84)</li>
     *   <li>EPSG:327xx - UTM zones 1-60 South (WGS84)</li>
     *   <li>EPSG:5041 - WGS 84 / UPS North</li>
     *   <li>EPSG:5042 - WGS 84 / UPS South</li>
     *   <li>Common aliases: WGS84, GOOGLE, EPSG:3785, EPSG:900913, EPSG:102113</li>
     * </ul>
     */
    public static synchronized void globals() {
        if (globalsInitialized) {
            return;
        }
        globalsInitialized = true;

        // WGS84 - Geographic CRS
        set("EPSG:4326", "+title=WGS 84 (long/lat) +proj=longlat +ellps=WGS84 +datum=WGS84 +units=degrees");
        
        // NAD83 - North American Datum 1983
        set("EPSG:4269", "+title=NAD83 (long/lat) +proj=longlat +a=6378137.0 +b=6356752.31414036 +ellps=GRS80 +datum=NAD83 +units=degrees");
        
        // Web Mercator (Pseudo-Mercator)
        set("EPSG:3857", "+title=WGS 84 / Pseudo-Mercator +proj=merc +a=6378137 +b=6378137 +lat_ts=0.0 +lon_0=0.0 +x_0=0.0 +y_0=0 +k=1.0 +units=m +nadgrids=@null +no_defs");
        
        // UTM zones WGS84 (1-60, North and South)
        for (int zone = 1; zone <= 60; zone++) {
            // Northern hemisphere
            set("EPSG:" + (32600 + zone), "+proj=utm +zone=" + zone + " +datum=WGS84 +units=m");
            // Southern hemisphere
            set("EPSG:" + (32700 + zone), "+proj=utm +zone=" + zone + " +south +datum=WGS84 +units=m");
        }
        
        // UPS North
        set("EPSG:5041", "+title=WGS 84 / UPS North (E,N) +proj=stere +lat_0=90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m");
        
        // UPS South
        set("EPSG:5042", "+title=WGS 84 / UPS South (E,N) +proj=stere +lat_0=-90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m");
        
        // Common aliases
        alias("WGS84", "EPSG:4326");
        alias("EPSG:3785", "EPSG:3857");   // backward compat
        alias("GOOGLE", "EPSG:3857");
        alias("EPSG:900913", "EPSG:3857");
        alias("EPSG:102113", "EPSG:3857");
    }

    /**
     * Get the number of registered definitions.
     * 
     * @return The count of definitions
     */
    public static int size() {
        return definitions.size();
    }

    /**
     * Clear all definitions and reset all flags.
     * 
     * <p><strong>Warning:</strong> This is primarily intended for testing.
     * Do not call in production code.</p>
     */
    public static synchronized void reset() {
        definitions.clear();
        globalsInitialized = false;
        remoteFetchEnabled = true;
        SpatialReferenceFetcher.reset();
    }

    /**
     * Check if globals have been initialized.
     * 
     * @return true if {@link #globals()} has been called
     */
    public static boolean isGlobalsInitialized() {
        return globalsInitialized;
    }

    /**
     * Enable or disable remote fetching of EPSG definitions.
     * 
     * <p>When enabled (default), the {@link #get(String)} method will attempt
     * to fetch unknown EPSG codes from spatialreference.org.</p>
     * 
     * @param enabled true to enable remote fetching, false to disable
     */
    public static void setRemoteFetchEnabled(boolean enabled) {
        remoteFetchEnabled = enabled;
    }

    /**
     * Check if remote fetching is enabled.
     * 
     * @return true if remote fetching is enabled
     */
    public static boolean isRemoteFetchEnabled() {
        return remoteFetchEnabled;
    }
}
