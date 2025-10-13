package org.proj4.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Prime meridian definitions for various reference systems.
 * This class contains the same prime meridian definitions as the JavaScript version.
 */
public final class PrimeMeridian {
    
    private static final Map<String, Double> PRIME_MERIDIANS = new HashMap<>();
    
    static {
        PRIME_MERIDIANS.put("greenwich", 0.0);
        PRIME_MERIDIANS.put("lisbon", -9.131906111111);
        PRIME_MERIDIANS.put("paris", 2.337229166667);
        PRIME_MERIDIANS.put("bogota", -74.080916666667);
        PRIME_MERIDIANS.put("madrid", -3.687938888889);
        PRIME_MERIDIANS.put("rome", 12.452333333333);
        PRIME_MERIDIANS.put("bern", 7.439583333333);
        PRIME_MERIDIANS.put("jakarta", 106.807719444444);
        PRIME_MERIDIANS.put("ferro", -17.666666666667);
        PRIME_MERIDIANS.put("brussels", 4.367975);
        PRIME_MERIDIANS.put("stockholm", 18.058277777778);
        PRIME_MERIDIANS.put("athens", 23.7163375);
        PRIME_MERIDIANS.put("oslo", 10.722916666667);
    }
    
    private PrimeMeridian() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Gets the prime meridian offset by name.
     * @param name the prime meridian name
     * @return the offset in degrees, or null if not found
     */
    public static Double get(String name) {
        return PRIME_MERIDIANS.get(name);
    }
    
    /**
     * Gets all available prime meridian names.
     * @return set of prime meridian names
     */
    public static java.util.Set<String> getNames() {
        return PRIME_MERIDIANS.keySet();
    }
    
    /**
     * Checks if a prime meridian with the given name exists.
     * @param name the prime meridian name
     * @return true if the prime meridian exists
     */
    public static boolean exists(String name) {
        return PRIME_MERIDIANS.containsKey(name);
    }
    
    /**
     * Gets the Greenwich prime meridian offset (always 0).
     * @return 0.0
     */
    public static double getGreenwich() {
        return PRIME_MERIDIANS.get("greenwich");
    }
}
