package org.proj4sedona.parser;

import org.proj4sedona.constants.PrimeMeridian;
import org.proj4sedona.constants.Units;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.ProjectionDef;

import java.util.HashMap;
import java.util.Map;

/**
 * PROJ string parser.
 * Mirrors: lib/projString.js
 * 
 * Parses PROJ.4 format strings like:
 *   +proj=utm +zone=32 +datum=WGS84 +units=m +no_defs
 *   +proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs
 */
public final class ProjString {

    private static final String LEGAL_AXIS_CHARS = "ewnsud";

    private ProjString() {
        // Utility class
    }

    /**
     * Parse a PROJ string into a ProjectionDef.
     * 
     * @param defData The PROJ string (e.g., "+proj=longlat +datum=WGS84")
     * @return Parsed projection definition
     */
    public static ProjectionDef parse(String defData) {
        if (defData == null || defData.isEmpty()) {
            throw new IllegalArgumentException("PROJ string cannot be null or empty");
        }

        ProjectionDef def = new ProjectionDef();
        def.setSrsCode(defData);

        // Split by '+', trim, filter empty, create key-value pairs
        // Mirrors: lib/projString.js lines 13-23
        Map<String, String> paramObj = new HashMap<>();
        String[] parts = defData.split("\\+(?=[a-zA-Z])");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] split = trimmed.split("=", 2);
            String key = split[0].toLowerCase();
            String value = split.length > 1 ? split[1] : "true";
            paramObj.put(key, value);
        }

        // Process each parameter
        for (Map.Entry<String, String> entry : paramObj.entrySet()) {
            String paramName = entry.getKey();
            String paramVal = entry.getValue();
            processParam(def, paramName, paramVal);
        }

        // Lowercase datumCode except WGS84
        // Mirrors: lib/projString.js lines 146-148
        if (def.getDatumCode() != null && !"WGS84".equals(def.getDatumCode())) {
            def.setDatumCode(def.getDatumCode().toLowerCase());
        }

        return def;
    }

    /**
     * Process a single PROJ parameter.
     * Mirrors: lib/projString.js params object (lines 25-132)
     */
    private static void processParam(ProjectionDef def, String paramName, String paramVal) {
        switch (paramName) {
            // Simple mappings
            case "proj":
                def.setProjName(paramVal);
                break;
            case "datum":
                def.setDatumCode(paramVal);
                break;
            case "ellps":
                def.setEllps(paramVal);
                break;
            case "title":
                def.setTitle(paramVal);
                break;

            // Angle parameters (convert degrees to radians)
            case "lat_0":
                def.setLat0(parseDouble(paramVal) * Values.D2R);
                break;
            case "lat_1":
                def.setLat1(parseDouble(paramVal) * Values.D2R);
                break;
            case "lat_2":
                def.setLat2(parseDouble(paramVal) * Values.D2R);
                break;
            case "lat_ts":
                def.setLatTs(parseDouble(paramVal) * Values.D2R);
                break;
            case "lon_0":
                def.setLong0(parseDouble(paramVal) * Values.D2R);
                break;
            case "lon_1":
                def.setLong1(parseDouble(paramVal) * Values.D2R);
                break;
            case "lon_2":
                def.setLong2(parseDouble(paramVal) * Values.D2R);
                break;
            case "alpha":
                def.setAlpha(parseDouble(paramVal) * Values.D2R);
                break;
            case "gamma":
                def.setRectifiedGridAngle(parseDouble(paramVal) * Values.D2R);
                break;
            case "lonc":
                def.setLongc(parseDouble(paramVal) * Values.D2R);
                break;

            // Numeric parameters
            case "x_0":
                def.setX0(parseDouble(paramVal));
                break;
            case "y_0":
                def.setY0(parseDouble(paramVal));
                break;
            case "k_0":
            case "k":
                def.setK0(parseDouble(paramVal));
                break;
            case "a":
                def.setA(parseDouble(paramVal));
                break;
            case "b":
                def.setB(parseDouble(paramVal));
                break;
            case "r":
                // Sphere radius sets both a and b
                double radius = parseDouble(paramVal);
                def.setA(radius);
                def.setB(radius);
                break;
            case "rf":
                def.setRf(parseDouble(paramVal));
                break;

            // UTM zone
            case "zone":
                def.setZone(Integer.parseInt(paramVal));
                break;

            // Boolean flags
            case "south":
                def.setUtmSouth(true);
                break;
            case "r_a":
                def.setRA(true);
                break;
            case "approx":
                def.setApprox(true);
                break;
            case "over":
                def.setOver(true);
                break;

            // towgs84 parameters
            case "towgs84":
                def.setDatumParams(parseTowgs84(paramVal));
                break;

            // Units
            case "to_meter":
                def.setToMeter(parseDouble(paramVal));
                break;
            case "units":
                def.setUnits(paramVal);
                Double toMeter = Units.getToMeter(paramVal);
                if (toMeter != null) {
                    def.setToMeter(toMeter);
                }
                break;

            // Prime meridian
            case "from_greenwich":
                def.setFromGreenwich(parseDouble(paramVal) * Values.D2R);
                break;
            case "pm":
                Double pmOffset = PrimeMeridian.get(paramVal);
                if (pmOffset != null) {
                    def.setFromGreenwich(pmOffset * Values.D2R);
                } else {
                    // Try parsing as a number (degrees)
                    def.setFromGreenwich(parseDouble(paramVal) * Values.D2R);
                }
                break;

            // NAD grids
            case "nadgrids":
                if ("@null".equals(paramVal)) {
                    def.setDatumCode("none");
                } else {
                    def.setNadgrids(paramVal);
                }
                break;

            // Axis order
            case "axis":
                if (isValidAxis(paramVal)) {
                    def.setAxis(paramVal);
                }
                break;

            // Ignored parameters (no_defs, wktext, etc.)
            case "no_defs":
            case "wktext":
            case "type":
                // Silently ignore
                break;

            default:
                // Unknown parameters are silently ignored
                // (proj4js stores them, but we don't need that)
                break;
        }
    }

    /**
     * Parse towgs84 parameter string to double array.
     * Mirrors: lib/projString.js lines 91-95
     */
    private static double[] parseTowgs84(String value) {
        String[] parts = value.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = parseDouble(parts[i].trim());
        }
        return result;
    }

    /**
     * Validate axis parameter.
     * Mirrors: lib/projString.js lines 120-125
     */
    private static boolean isValidAxis(String axis) {
        if (axis == null || axis.length() != 3) {
            return false;
        }
        return LEGAL_AXIS_CHARS.indexOf(axis.charAt(0)) != -1 &&
               LEGAL_AXIS_CHARS.indexOf(axis.charAt(1)) != -1 &&
               LEGAL_AXIS_CHARS.indexOf(axis.charAt(2)) != -1;
    }

    /**
     * Parse a string to double, with error handling.
     */
    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + value, e);
        }
    }
}
