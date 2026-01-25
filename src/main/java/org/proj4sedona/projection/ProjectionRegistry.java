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
 */
public final class ProjectionRegistry {

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[-()\\s]+");
    
    private static final List<Supplier<Projection>> projStore = new ArrayList<>();
    private static final Map<String, Integer> names = new HashMap<>();
    private static boolean started = false;

    private ProjectionRegistry() {
        // Utility class
    }

    /**
     * Register a projection implementation.
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
     * @param name The projection name (e.g., "merc", "longlat", "utm")
     * @return A new instance of the projection, or null if not found
     */
    public static Projection get(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Try direct lookup
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
     */
    public static String getNormalizedProjName(String name) {
        return NORMALIZE_PATTERN.matcher(name).replaceAll(" ").trim().replace(" ", "_");
    }

    /**
     * Initialize the registry with built-in projections.
     * Called once at startup.
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
    }

    /**
     * Check if the registry has been initialized.
     */
    public static boolean isStarted() {
        return started;
    }

    /**
     * Reset the registry (for testing).
     */
    public static synchronized void reset() {
        projStore.clear();
        names.clear();
        started = false;
    }
}
