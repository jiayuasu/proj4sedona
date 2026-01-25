package org.proj4sedona.parser;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fuzzy key matcher for looking up values in maps.
 * Mirrors: lib/match.js
 * 
 * Ignores spaces, underscores, hyphens, slashes, and parentheses when matching.
 * This allows "us-ft" to match "US_FT", "us ft", etc.
 */
public final class Match {

    private static final Pattern IGNORED_CHARS = Pattern.compile("[\\s_\\-/()]");

    private Match() {
        // Utility class
    }

    /**
     * Look up a value in a map with fuzzy key matching.
     * 
     * @param map The map to search
     * @param key The key to look for
     * @param <T> The value type
     * @return The matched value, or null if not found
     */
    public static <T> T match(Map<String, T> map, String key) {
        if (key == null) {
            return null;
        }

        // Direct match first
        T direct = map.get(key);
        if (direct != null) {
            return direct;
        }

        // Normalize the search key
        String normalizedKey = normalize(key);

        // Search through all keys
        for (Map.Entry<String, T> entry : map.entrySet()) {
            String normalizedEntryKey = normalize(entry.getKey());
            if (normalizedEntryKey.equals(normalizedKey)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Normalize a key by lowercasing and removing ignored characters.
     */
    private static String normalize(String key) {
        return IGNORED_CHARS.matcher(key.toLowerCase()).replaceAll("");
    }
}
