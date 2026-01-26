package org.datasyslab.proj4sedona.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit definitions with conversion factors to meters.
 * Mirrors: lib/constants/units.js
 */
public final class Units {

    private static final Map<String, Double> UNITS = new HashMap<>();

    static {
        // All units from proj4js units.js
        UNITS.put("mm", 0.001);
        UNITS.put("cm", 0.01);
        UNITS.put("ft", 0.3048);
        UNITS.put("us-ft", 1200.0 / 3937.0);
        UNITS.put("fath", 1.8288);
        UNITS.put("kmi", 1852.0);
        UNITS.put("us-ch", 20.1168402336805);
        UNITS.put("us-mi", 1609.34721869444);
        UNITS.put("km", 1000.0);
        UNITS.put("ind-ft", 0.30479841);
        UNITS.put("ind-yd", 0.91439523);
        UNITS.put("mi", 1609.344);
        UNITS.put("yd", 0.9144);
        UNITS.put("ch", 20.1168);
        UNITS.put("link", 0.201168);
        UNITS.put("dm", 0.1);
        UNITS.put("in", 0.0254);
        UNITS.put("ind-ch", 20.11669506);
        UNITS.put("us-in", 0.025400050800101);
        UNITS.put("us-yd", 0.914401828803658);
        UNITS.put("m", 1.0);  // meter is the base unit
    }

    private Units() {
        // Utility class
    }

    /**
     * Get the conversion factor to meters for a unit (case-sensitive).
     * Returns null if unit not found.
     */
    public static Double getToMeter(String unit) {
        if (unit == null) {
            return null;
        }
        return UNITS.get(unit);
    }

    /**
     * Check if a unit exists.
     */
    public static boolean contains(String unit) {
        if (unit == null) {
            return false;
        }
        return UNITS.containsKey(unit);
    }
}
