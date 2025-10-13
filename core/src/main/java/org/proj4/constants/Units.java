package org.proj4.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit definitions for various measurement systems.
 * This class contains the same unit definitions as the JavaScript version.
 */
public final class Units {
    
    /**
     * Represents a unit with its conversion factor to meters.
     */
    public static class UnitDef {
        public final double to_meter;
        
        public UnitDef(double to_meter) {
            this.to_meter = to_meter;
        }
    }
    
    private static final Map<String, UnitDef> UNITS = new HashMap<>();
    
    static {
        UNITS.put("mm", new UnitDef(0.001));
        UNITS.put("cm", new UnitDef(0.01));
        UNITS.put("ft", new UnitDef(0.3048));
        UNITS.put("us-ft", new UnitDef(1200.0 / 3937.0));
        UNITS.put("fath", new UnitDef(1.8288));
        UNITS.put("kmi", new UnitDef(1852.0));
        UNITS.put("us-ch", new UnitDef(20.1168402336805));
        UNITS.put("us-mi", new UnitDef(1609.34721869444));
        UNITS.put("km", new UnitDef(1000.0));
        UNITS.put("ind-ft", new UnitDef(0.30479841));
        UNITS.put("ind-yd", new UnitDef(0.91439523));
        UNITS.put("mi", new UnitDef(1609.344));
        UNITS.put("yd", new UnitDef(0.9144));
        UNITS.put("ch", new UnitDef(20.1168));
        UNITS.put("link", new UnitDef(0.201168));
        UNITS.put("dm", new UnitDef(0.1));
        UNITS.put("in", new UnitDef(0.0254));
        UNITS.put("ind-ch", new UnitDef(20.11669506));
        UNITS.put("us-in", new UnitDef(0.025400050800101));
        UNITS.put("us-yd", new UnitDef(0.914401828803658));
        UNITS.put("m", new UnitDef(1.0));
        UNITS.put("degrees", new UnitDef(1.0)); // Special case for angular units
        UNITS.put("radians", new UnitDef(1.0)); // Special case for angular units
    }
    
    private Units() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Gets a unit definition by name.
     * @param name the unit name
     * @return the unit definition, or null if not found
     */
    public static UnitDef get(String name) {
        return UNITS.get(name);
    }
    
    /**
     * Gets all available unit names.
     * @return set of unit names
     */
    public static java.util.Set<String> getNames() {
        return UNITS.keySet();
    }
    
    /**
     * Checks if a unit with the given name exists.
     * @param name the unit name
     * @return true if the unit exists
     */
    public static boolean exists(String name) {
        return UNITS.containsKey(name);
    }
    
    /**
     * Gets the conversion factor to meters for a unit.
     * @param name the unit name
     * @return conversion factor to meters, or 1.0 if not found
     */
    public static double getToMeter(String name) {
        UnitDef unit = UNITS.get(name);
        return unit != null ? unit.to_meter : 1.0;
    }
    
    /**
     * Gets the meter unit definition.
     * @return meter unit definition
     */
    public static UnitDef getMeter() {
        return UNITS.get("m");
    }
    
    /**
     * Gets the degree unit definition.
     * @return degree unit definition
     */
    public static UnitDef getDegrees() {
        return UNITS.get("degrees");
    }
    
    /**
     * Gets the radian unit definition.
     * @return radian unit definition
     */
    public static UnitDef getRadians() {
        return UNITS.get("radians");
    }
    
    /**
     * Converts a value from one unit to another.
     * @param value the value to convert
     * @param fromUnit the source unit name
     * @param toUnit the target unit name
     * @return converted value
     */
    public static double convert(double value, String fromUnit, String toUnit) {
        double fromFactor = getToMeter(fromUnit);
        double toFactor = getToMeter(toUnit);
        return value * fromFactor / toFactor;
    }
    
    /**
     * Converts a value to meters.
     * @param value the value to convert
     * @param fromUnit the source unit name
     * @return value in meters
     */
    public static double toMeters(double value, String fromUnit) {
        return value * getToMeter(fromUnit);
    }
    
    /**
     * Converts a value from meters to the specified unit.
     * @param value the value in meters
     * @param toUnit the target unit name
     * @return converted value
     */
    public static double fromMeters(double value, String toUnit) {
        return value / getToMeter(toUnit);
    }
}
