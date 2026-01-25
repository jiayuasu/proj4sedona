package org.proj4sedona.projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Registry of available projection implementations.
 * Mirrors: lib/projections.js
 * 
 * This class maintains a global registry of projection implementations that can be
 * looked up by name. Projections register themselves with multiple aliases (e.g.,
 * "merc", "Mercator", "mercator") to support various naming conventions.
 * 
 * <p>Usage:</p>
 * <pre>
 * // Initialize the registry (called once at startup)
 * ProjectionRegistry.start();
 * 
 * // Get a projection by name
 * Projection merc = ProjectionRegistry.get("merc");
 * </pre>
 * 
 * <p>The registry uses a supplier pattern to create fresh projection instances,
 * ensuring thread safety and avoiding state sharing between projections.</p>
 */
public final class ProjectionRegistry {

    /** Pattern for normalizing projection names (removes dashes, parentheses, whitespace) */
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[-()\\s]+");
    
    /** Store of projection suppliers, indexed by registration order */
    private static final List<Supplier<Projection>> projStore = new ArrayList<>();
    
    /** Map from projection name (lowercase) to index in projStore */
    private static final Map<String, Integer> names = new HashMap<>();
    
    /** Flag indicating whether the registry has been initialized */
    private static boolean started = false;

    private ProjectionRegistry() {
        // Utility class - prevent instantiation
    }

    /**
     * Register a projection implementation.
     * 
     * <p>The projection's {@link Projection#getNames()} method is called to get
     * all aliases under which the projection should be registered.</p>
     * 
     * @param projSupplier Supplier that creates new instances of the projection
     */
    public static void add(Supplier<Projection> projSupplier) {
        Projection sample = projSupplier.get();
        String[] projNames = sample.getNames();
        if (projNames == null || projNames.length == 0) {
            return;
        }

        int index = projStore.size();
        projStore.add(projSupplier);

        for (String name : projNames) {
            names.put(name.toLowerCase(), index);
        }
    }

    /**
     * Get a projection by name.
     * 
     * <p>Lookup is case-insensitive. If direct lookup fails, the name is normalized
     * (removing dashes, parentheses, and whitespace) and tried again.</p>
     * 
     * @param name The projection name (e.g., "merc", "longlat", "utm", "Transverse_Mercator")
     * @return A new instance of the projection, or null if not found
     */
    public static Projection get(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Try direct lookup (case-insensitive)
        String n = name.toLowerCase();
        Integer index = names.get(n);
        if (index != null) {
            return projStore.get(index).get();
        }

        // Try normalized name
        String normalized = getNormalizedProjName(n);
        index = names.get(normalized);
        if (index != null) {
            return projStore.get(index).get();
        }

        return null;
    }

    /**
     * Normalize a projection name by replacing special characters with underscores.
     * Mirrors: lib/projections.js getNormalizedProjName()
     * 
     * <p>Example: "Lambert Conformal Conic" becomes "Lambert_Conformal_Conic"</p>
     * 
     * @param name The projection name to normalize
     * @return Normalized name with underscores
     */
    public static String getNormalizedProjName(String name) {
        return NORMALIZE_PATTERN.matcher(name).replaceAll(" ").trim().replace(" ", "_");
    }

    /**
     * Initialize the registry with built-in projections.
     * 
     * <p>This method is thread-safe and idempotent - calling it multiple times
     * has no effect after the first call.</p>
     * 
     * <p>Registered projections (in order):</p>
     * <ul>
     *   <li>LongLat - Geographic (lat/lon) identity projection</li>
     *   <li>Mercator - Standard Mercator projection</li>
     *   <li>ExtendedTransverseMercator - Accurate Transverse Mercator (etmerc/tmerc)</li>
     *   <li>UTM - Universal Transverse Mercator</li>
     * </ul>
     */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        
        // Register built-in projections
        // Mirrors: lib/projections.js projs array
        add(LongLat::new);
        add(Mercator::new);
        add(ExtendedTransverseMercator::new);
        add(UTM::new);
    }

    /**
     * Check if the registry has been initialized.
     * 
     * @return true if {@link #start()} has been called
     */
    public static boolean isStarted() {
        return started;
    }

    /**
     * Reset the registry to its initial empty state.
     * 
     * <p><strong>Warning:</strong> This is primarily intended for testing.
     * Do not call in production code.</p>
     */
    public static synchronized void reset() {
        projStore.clear();
        names.clear();
        started = false;
    }
}
