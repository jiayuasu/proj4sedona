package org.datasyslab.proj4sedona.grid;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of CRS pairs to grid files for automatic grid-based datum transformations.
 * 
 * <p>This registry enables automatic grid selection when transforming between EPSG codes,
 * similar to pyproj's behavior. When users specify EPSG codes without explicit +nadgrids,
 * this registry is consulted to find the appropriate grid file.</p>
 * 
 * <p>Grid files are sourced from <a href="https://cdn.proj.org">cdn.proj.org</a>.</p>
 * 
 * <p><b>Grid direction handling:</b> Each grid file has a native direction (e.g., NAD27→NAD83).
 * When transforming in the native direction, the grid should be applied to the SOURCE CRS
 * (forward shift). When transforming in the opposite direction, the grid should be applied
 * to the DESTINATION CRS (inverse shift).</p>
 * 
 * @since 1.0.0
 */
public final class TransformationRegistry {

    /**
     * Grid mapping info containing file name and whether to apply to source or destination.
     */
    public static class GridMapping {
        public final String gridFile;
        public final boolean applyToSource;  // true = source (forward), false = dest (inverse)
        
        GridMapping(String gridFile, boolean applyToSource) {
            this.gridFile = gridFile;
            this.applyToSource = applyToSource;
        }
    }

    /**
     * Map from "fromCrs|toCrs" to GridMapping.
     */
    private static final Map<String, GridMapping> GRID_MAPPINGS = new HashMap<>();

    static {
        // ===== Verified from pyproj TransformerGroup =====
        // Grid files define transformations in a specific direction.
        // We track whether the requested transform matches the grid's native direction.
        
        // NAD27 -> NAD83 (Canada) - Grid is NAD27→NAD83, so forward
        registerBidirectional("EPSG:4267", "EPSG:4269", "ca_nrc_ntv2_0.tif");
        
        // NAD83 -> HARN (USA CONUS) - Grid is NAD83→HARN, so forward
        registerBidirectional("EPSG:4269", "EPSG:4152", "us_noaa_nadcon5_nad83_1986_nad83_harn_conus.tif");
        
        // OSGB36 -> ETRS89 (UK) - Grid is OSGB36→ETRS89 (native direction)
        // Note: OSTN15 grid name says "OSGBtoETRS", meaning native direction is OSGB36→ETRS89
        registerBidirectional("EPSG:4277", "EPSG:4258", "uk_os_OSTN15_NTv2_OSGBtoETRS.tif");
        
        // AGD84 -> GDA94 (Australia) - Grid is AGD84→GDA94, so forward
        registerBidirectional("EPSG:4203", "EPSG:4283", "au_icsm_National_84_02_07_01.tif");
        
        // NTF -> RGF93 (France) - Grid is NTF→RGF93, so forward
        registerBidirectional("EPSG:4275", "EPSG:4171", "fr_ign_gr3df97a.tif");
        
        // DHDN -> ETRS89 (Germany) - Grid is DHDN→ETRS89, so forward
        registerBidirectional("EPSG:4314", "EPSG:4258", "de_adv_BETA2007.tif");
        
        // NZGD49 -> NZGD2000 (New Zealand) - Grid is NZGD49→NZGD2000, so forward
        registerBidirectional("EPSG:4272", "EPSG:4167", "nz_linz_nzgd2kgrid0005.tif");
    }

    private TransformationRegistry() {
        // Utility class
    }

    /**
     * Register a bidirectional grid mapping.
     * The first CRS pair is assumed to be the native direction of the grid (forward).
     * The reverse direction is registered as needing inverse application.
     * 
     * @param nativeFrom Source CRS in the grid's native direction
     * @param nativeTo Destination CRS in the grid's native direction
     * @param gridFile Grid file name
     */
    private static void registerBidirectional(String nativeFrom, String nativeTo, String gridFile) {
        // Native direction: grid applied to SOURCE (forward shift)
        GRID_MAPPINGS.put(makeKey(nativeFrom, nativeTo), new GridMapping(gridFile, true));
        // Reverse direction: grid applied to DESTINATION (inverse shift)
        GRID_MAPPINGS.put(makeKey(nativeTo, nativeFrom), new GridMapping(gridFile, false));
    }

    /**
     * Create a lookup key from two CRS codes.
     */
    private static String makeKey(String fromCrs, String toCrs) {
        return fromCrs + "|" + toCrs;
    }

    /**
     * Look up the grid mapping for a CRS pair transformation.
     * 
     * @param fromCrs Source CRS (e.g., "EPSG:4267")
     * @param toCrs Destination CRS (e.g., "EPSG:4269")
     * @return GridMapping with file name and application side, or null if not found
     */
    public static GridMapping getGridMapping(String fromCrs, String toCrs) {
        return GRID_MAPPINGS.get(makeKey(fromCrs, toCrs));
    }

    /**
     * Look up the grid file for a CRS pair transformation.
     * 
     * @param fromCrs Source CRS (e.g., "EPSG:4267")
     * @param toCrs Destination CRS (e.g., "EPSG:4269")
     * @return Grid file name, or null if no grid is registered for this pair
     */
    public static String getGridFile(String fromCrs, String toCrs) {
        GridMapping mapping = GRID_MAPPINGS.get(makeKey(fromCrs, toCrs));
        return mapping != null ? mapping.gridFile : null;
    }

    /**
     * Check if a grid-based transformation is available for the given CRS pair.
     * 
     * @param fromCrs Source CRS
     * @param toCrs Destination CRS
     * @return true if a grid mapping exists
     */
    public static boolean hasGridMapping(String fromCrs, String toCrs) {
        return GRID_MAPPINGS.containsKey(makeKey(fromCrs, toCrs));
    }

    /**
     * Get the number of registered grid mappings.
     * Note: Each bidirectional pair counts as 2 entries.
     * 
     * @return Number of registered mappings
     */
    public static int size() {
        return GRID_MAPPINGS.size();
    }
}
