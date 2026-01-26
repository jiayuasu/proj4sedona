package org.proj4sedona.parser;

import org.proj4sedona.constants.Values;
import org.proj4sedona.core.ProjectionDef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * WKT parsing utilities for parameter renaming and property processing.
 * Mirrors: wkt-parser/index.js (cleanWKT, setPropertiesFromWkt, rename functions)
 * and wkt-parser/util.js (d2r, applyProjectionDefaults)
 */
public final class WktUtils {

    /** Known CRS type keywords */
    private static final Set<String> KNOWN_TYPES = new HashSet<>(Arrays.asList(
        "PROJECTEDCRS", "PROJCRS", "GEOGCS", "GEOCCS", "PROJCS", "LOCAL_CS",
        "GEODCRS", "GEODETICCRS", "GEODETICDATUM", "ENGCRS", "ENGINEERINGCRS"
    ));

    private WktUtils() {
        // Utility class
    }

    /**
     * Convert degrees to radians.
     * Mirrors: wkt-parser/util.js d2r()
     * 
     * @param degrees Value in degrees
     * @return Value in radians
     */
    public static double d2r(double degrees) {
        return degrees * Values.D2R;
    }

    /**
     * Apply projection-specific defaults to a ProjectionDef.
     * Mirrors: wkt-parser/util.js applyProjectionDefaults()
     * 
     * @param def The ProjectionDef to modify
     */
    public static void applyProjectionDefaults(ProjectionDef def) {
        String projName = def.getProjName();
        if (projName == null) {
            return;
        }

        String normalizedProjName = projName.toLowerCase().replace("_", " ");

        // For Albers and Lambert Azimuthal, long0 from longc
        if (def.getLong0() == null && def.getLongc() != null) {
            if (normalizedProjName.equals("albers conic equal area") ||
                normalizedProjName.equals("lambert azimuthal equal area")) {
                def.setLong0(def.getLongc());
            }
        }

        // Handle stereographic projections
        if (def.getLatTs() == null && def.getLat1() != null) {
            if (normalizedProjName.equals("stereographic south pole") ||
                normalizedProjName.equals("polar stereographic (variant b)")) {
                double lat1 = def.getLat1();
                def.setLat0(d2r(lat1 > 0 ? 90 : -90));
                def.setLatTs(lat1);
                def.setLat1(null);
            }
        } else if (def.getLatTs() == null && def.getLat0() != null) {
            if (normalizedProjName.equals("polar stereographic") ||
                normalizedProjName.equals("polar stereographic (variant a)")) {
                double lat0 = def.getLat0();
                def.setLatTs(lat0);
                def.setLat0(d2r(lat0 > 0 ? 90 : -90));
                def.setLat1(null);
            }
        }
    }

    /**
     * Clean a WKT Map structure and extract properties.
     * Mirrors: wkt-parser/index.js cleanWKT()
     * 
     * @param wkt The WKT Map to clean
     */
    @SuppressWarnings("unchecked")
    public static void cleanWkt(Map<String, Object> wkt) {
        for (String key : wkt.keySet()) {
            if (KNOWN_TYPES.contains(key)) {
                Object value = wkt.get(key);
                if (value instanceof Map) {
                    setPropertiesFromWkt((Map<String, Object>) value);
                }
            }
            Object value = wkt.get(key);
            if (value instanceof Map) {
                cleanWkt((Map<String, Object>) value);
            }
        }
    }

    /**
     * Extract and normalize properties from a WKT Map.
     * Mirrors: wkt-parser/index.js setPropertiesFromWkt()
     * 
     * @param wkt The WKT Map to process
     */
    @SuppressWarnings("unchecked")
    public static void setPropertiesFromWkt(Map<String, Object> wkt) {
        // Handle AUTHORITY
        Object authority = wkt.get("AUTHORITY");
        if (authority instanceof Map) {
            Map<String, Object> authMap = (Map<String, Object>) authority;
            for (String authKey : authMap.keySet()) {
                Object authValue = authMap.get(authKey);
                if (authValue != null) {
                    wkt.put("title", authKey + ":" + authValue);
                    break;
                }
            }
        }

        // Determine projName from type
        Object type = wkt.get("type");
        if ("GEOGCS".equals(type)) {
            wkt.put("projName", "longlat");
        } else if ("LOCAL_CS".equals(type)) {
            wkt.put("projName", "identity");
            wkt.put("local", true);
        } else {
            Object projection = wkt.get("PROJECTION");
            if (projection instanceof Map) {
                Map<String, Object> projMap = (Map<String, Object>) projection;
                if (!projMap.isEmpty()) {
                    wkt.put("projName", projMap.keySet().iterator().next());
                }
            } else if (projection != null) {
                wkt.put("projName", projection.toString());
            }
        }

        // Handle AXIS
        Object axisObj = wkt.get("AXIS");
        if (axisObj instanceof List) {
            List<List<Object>> axisList = (List<List<Object>>) axisObj;
            StringBuilder axisOrder = new StringBuilder();
            for (List<Object> axis : axisList) {
                if (axis.size() >= 2) {
                    String axisName = axis.get(0).toString().toLowerCase();
                    String axisDir = axis.get(1).toString().toLowerCase();
                    if (axisName.contains("north") || ((axisName.equals("y") || axisName.equals("lat")) && axisDir.equals("north"))) {
                        axisOrder.append('n');
                    } else if (axisName.contains("south") || ((axisName.equals("y") || axisName.equals("lat")) && axisDir.equals("south"))) {
                        axisOrder.append('s');
                    } else if (axisName.contains("east") || ((axisName.equals("x") || axisName.equals("lon")) && axisDir.equals("east"))) {
                        axisOrder.append('e');
                    } else if (axisName.contains("west") || ((axisName.equals("x") || axisName.equals("lon")) && axisDir.equals("west"))) {
                        axisOrder.append('w');
                    }
                }
            }
            if (axisOrder.length() == 2) {
                axisOrder.append('u');
            }
            if (axisOrder.length() == 3) {
                wkt.put("axis", axisOrder.toString());
            }
        }

        // Handle UNIT
        Object unitObj = wkt.get("UNIT");
        if (unitObj instanceof Map) {
            Map<String, Object> unit = (Map<String, Object>) unitObj;
            Object unitName = unit.get("name");
            if (unitName != null) {
                String units = unitName.toString().toLowerCase();
                if ("metre".equals(units)) {
                    units = "meter";
                }
                wkt.put("units", units);
            }
            Object convert = unit.get("convert");
            if (convert != null) {
                if ("GEOGCS".equals(type)) {
                    Object datum = wkt.get("DATUM");
                    if (datum instanceof Map) {
                        Object spheroid = ((Map<String, Object>) datum).get("SPHEROID");
                        if (spheroid instanceof Map) {
                            Object a = ((Map<String, Object>) spheroid).get("a");
                            if (a != null) {
                                wkt.put("to_meter", toDouble(convert) * toDouble(a));
                            }
                        }
                    }
                } else {
                    wkt.put("to_meter", convert);
                }
            }
        }

        // Handle GEOGCS (for datum and ellipsoid info)
        Map<String, Object> geogcs = null;
        if ("GEOGCS".equals(type)) {
            geogcs = wkt;
        } else {
            Object geogcsObj = wkt.get("GEOGCS");
            if (geogcsObj instanceof Map) {
                geogcs = (Map<String, Object>) geogcsObj;
            }
        }

        if (geogcs != null) {
            // Extract datumCode
            Object datum = geogcs.get("DATUM");
            if (datum instanceof Map) {
                Object datumName = ((Map<String, Object>) datum).get("name");
                if (datumName != null) {
                    wkt.put("datumCode", datumName.toString().toLowerCase());
                }
            } else {
                Object geogcsName = geogcs.get("name");
                if (geogcsName != null) {
                    wkt.put("datumCode", geogcsName.toString().toLowerCase());
                }
            }

            // Normalize datumCode
            Object datumCodeObj = wkt.get("datumCode");
            if (datumCodeObj != null) {
                String datumCode = datumCodeObj.toString();
                
                // Remove "d_" prefix
                if (datumCode.startsWith("d_")) {
                    datumCode = datumCode.substring(2);
                }
                
                // Normalize known datum codes
                datumCode = normalizeDatumCode(datumCode, wkt);
                wkt.put("datumCode", datumCode);
            }

            // Extract ellipsoid info
            if (datum instanceof Map) {
                Object spheroid = ((Map<String, Object>) datum).get("SPHEROID");
                if (spheroid instanceof Map) {
                    Map<String, Object> spheroidMap = (Map<String, Object>) spheroid;
                    Object ellpsName = spheroidMap.get("name");
                    if (ellpsName != null) {
                        String ellps = ellpsName.toString()
                            .replace("_19", "")
                            .replaceAll("[Cc]larke_18", "clrk");
                        if (ellps.toLowerCase().startsWith("international")) {
                            ellps = "intl";
                        }
                        wkt.put("ellps", ellps);
                    }
                    wkt.put("a", spheroidMap.get("a"));
                    Object rf = spheroidMap.get("rf");
                    if (rf != null) {
                        wkt.put("rf", toDouble(rf));
                    }
                }

                // Extract TOWGS84
                Object towgs84 = ((Map<String, Object>) datum).get("TOWGS84");
                if (towgs84 instanceof List) {
                    wkt.put("datum_params", towgs84);
                }
            }
            
            // Extract PRIMEM (prime meridian offset from Greenwich)
            Object primem = geogcs.get("PRIMEM");
            if (primem instanceof Map) {
                Map<String, Object> primemMap = (Map<String, Object>) primem;
                Object convert = primemMap.get("convert");
                if (convert != null) {
                    // Convert from degrees to radians
                    wkt.put("from_greenwich", toDouble(convert) * Values.D2R);
                }
            }
        }

        // Handle b (semi-minor axis) - if infinite, set to a
        Object b = wkt.get("b");
        if (b != null && !Double.isFinite(toDouble(b))) {
            wkt.put("b", wkt.get("a"));
        }

        // Handle rectified_grid_angle - convert to radians
        Object rectAngle = wkt.get("rectified_grid_angle");
        if (rectAngle != null) {
            wkt.put("rectified_grid_angle", d2r(toDouble(rectAngle)));
        }

        // Apply parameter renaming
        applyRenaming(wkt);
    }

    /**
     * Normalize datum code strings.
     */
    private static String normalizeDatumCode(String datumCode, Map<String, Object> wkt) {
        if ("new_zealand_1949".equals(datumCode)) {
            return "nzgd49";
        }
        if ("wgs_1984".equals(datumCode) || "world_geodetic_system_1984".equals(datumCode)) {
            Object projection = wkt.get("PROJECTION");
            if ("Mercator_Auxiliary_Sphere".equals(projection)) {
                wkt.put("sphere", true);
            }
            return "wgs84";
        }
        if ("belge_1972".equals(datumCode)) {
            return "rnb72";
        }
        if (datumCode.contains("osgb_1936")) {
            return "osgb36";
        }
        if (datumCode.contains("osni_1952")) {
            return "osni52";
        }
        if (datumCode.contains("tm65") || datumCode.contains("geodetic_datum_of_1965")) {
            return "ire65";
        }
        if ("ch1903+".equals(datumCode)) {
            return "ch1903";
        }
        if (datumCode.contains("israel")) {
            return "isr93";
        }
        return datumCode;
    }

    /**
     * Apply parameter renaming transformations.
     * Mirrors: wkt-parser/index.js rename() and list
     */
    @SuppressWarnings("unchecked")
    private static void applyRenaming(Map<String, Object> wkt) {
        // Get to_meter for false easting/northing conversion
        final double toMeter = wkt.containsKey("to_meter") ? toDouble(wkt.get("to_meter")) : 1.0;
        
        // Define renaming rules: [outName, inName, transformer?]
        Object[][] renamingRules = {
            {"standard_parallel_1", "Standard_Parallel_1", null},
            {"standard_parallel_1", "Latitude of 1st standard parallel", null},
            {"standard_parallel_2", "Standard_Parallel_2", null},
            {"standard_parallel_2", "Latitude of 2nd standard parallel", null},
            {"false_easting", "False_Easting", null},
            {"false_easting", "False easting", null},
            {"false_easting", "Easting at false origin", null},
            {"false_northing", "False_Northing", null},
            {"false_northing", "False northing", null},
            {"false_northing", "Northing at false origin", null},
            {"central_meridian", "Central_Meridian", null},
            {"central_meridian", "Longitude of natural origin", null},
            {"central_meridian", "Longitude of false origin", null},
            {"latitude_of_origin", "Latitude_Of_Origin", null},
            {"latitude_of_origin", "Central_Parallel", null},
            {"latitude_of_origin", "Latitude of natural origin", null},
            {"latitude_of_origin", "Latitude of false origin", null},
            {"scale_factor", "Scale_Factor", null},
            {"k0", "scale_factor", null},
            {"latitude_of_center", "Latitude_Of_Center", null},
            {"latitude_of_center", "Latitude_of_center", null},
            {"lat0", "latitude_of_center", (Function<Double, Double>) WktUtils::d2r},
            {"longitude_of_center", "Longitude_Of_Center", null},
            {"longitude_of_center", "Longitude_of_center", null},
            {"longc", "longitude_of_center", (Function<Double, Double>) WktUtils::d2r},
            {"x0", "false_easting", (Function<Double, Double>) v -> v * toMeter},
            {"y0", "false_northing", (Function<Double, Double>) v -> v * toMeter},
            {"long0", "central_meridian", (Function<Double, Double>) WktUtils::d2r},
            {"lat0", "latitude_of_origin", (Function<Double, Double>) WktUtils::d2r},
            {"lat0", "standard_parallel_1", (Function<Double, Double>) WktUtils::d2r},
            {"lat1", "standard_parallel_1", (Function<Double, Double>) WktUtils::d2r},
            {"lat2", "standard_parallel_2", (Function<Double, Double>) WktUtils::d2r},
            {"azimuth", "Azimuth", null},
            {"alpha", "azimuth", (Function<Double, Double>) WktUtils::d2r},
            {"srsCode", "name", null}
        };

        for (Object[] rule : renamingRules) {
            String outName = (String) rule[0];
            String inName = (String) rule[1];
            Function<Double, Double> transformer = (Function<Double, Double>) rule[2];
            rename(wkt, outName, inName, transformer);
        }
    }

    /**
     * Rename a parameter if source exists and target doesn't.
     */
    @SuppressWarnings("unchecked")
    private static void rename(Map<String, Object> wkt, String outName, String inName, 
                               Function<Double, Double> transformer) {
        if (!wkt.containsKey(outName) && wkt.containsKey(inName)) {
            Object value = wkt.get(inName);
            if (transformer != null && value != null) {
                wkt.put(outName, transformer.apply(toDouble(value)));
            } else {
                wkt.put(outName, value);
            }
        }
    }

    /**
     * Convert a WKT Map to ProjectionDef.
     * This extracts the relevant properties from the processed WKT Map.
     * 
     * @param wkt The processed WKT Map
     * @return The populated ProjectionDef
     */
    @SuppressWarnings("unchecked")
    public static ProjectionDef mapToProjectionDef(Map<String, Object> wkt) {
        ProjectionDef def = new ProjectionDef();

        // Basic identifiers
        setIfPresent(wkt, "title", def::setTitle);
        setIfPresent(wkt, "projName", def::setProjName);
        setIfPresent(wkt, "srsCode", def::setSrsCode);

        // Ellipsoid
        setIfPresent(wkt, "ellps", def::setEllps);
        setDoubleIfPresent(wkt, "a", def::setA);
        setDoubleIfPresent(wkt, "b", def::setB);
        setDoubleIfPresent(wkt, "rf", def::setRf);
        setBooleanIfPresent(wkt, "sphere", def::setSphere);

        // Datum
        setIfPresent(wkt, "datumCode", def::setDatumCode);
        if (wkt.containsKey("datum_params")) {
            Object params = wkt.get("datum_params");
            if (params instanceof List) {
                List<Object> paramList = (List<Object>) params;
                double[] datumParams = new double[paramList.size()];
                for (int i = 0; i < paramList.size(); i++) {
                    datumParams[i] = toDouble(paramList.get(i));
                }
                def.setDatumParams(datumParams);
            }
        }
        setIfPresent(wkt, "nadgrids", def::setNadgrids);

        // Projection parameters
        setDoubleIfPresent(wkt, "lat0", def::setLat0);
        setDoubleIfPresent(wkt, "lat1", def::setLat1);
        setDoubleIfPresent(wkt, "lat2", def::setLat2);
        setDoubleIfPresent(wkt, "lat_ts", def::setLatTs);
        setDoubleIfPresent(wkt, "long0", def::setLong0);
        setDoubleIfPresent(wkt, "long1", def::setLong1);
        setDoubleIfPresent(wkt, "long2", def::setLong2);
        setDoubleIfPresent(wkt, "alpha", def::setAlpha);
        setDoubleIfPresent(wkt, "longc", def::setLongc);
        setDoubleIfPresent(wkt, "rectified_grid_angle", def::setRectifiedGridAngle);

        // Scale and offsets
        setDoubleIfPresent(wkt, "k0", def::setK0);
        setDoubleIfPresent(wkt, "x0", def::setX0);
        setDoubleIfPresent(wkt, "y0", def::setY0);

        // Units
        setDoubleIfPresent(wkt, "to_meter", def::setToMeter);
        setIfPresent(wkt, "units", def::setUnits);
        setDoubleIfPresent(wkt, "from_greenwich", def::setFromGreenwich);
        setIfPresent(wkt, "axis", def::setAxis);

        // UTM
        setIntegerIfPresent(wkt, "zone", def::setZone);
        setBooleanIfPresent(wkt, "utmSouth", def::setUtmSouth);

        // Flags
        setBooleanIfPresent(wkt, "R_A", def::setRA);
        setBooleanIfPresent(wkt, "approx", def::setApprox);
        setBooleanIfPresent(wkt, "over", def::setOver);

        // Apply projection defaults
        applyProjectionDefaults(def);

        return def;
    }

    // Helper methods for setting properties

    private static void setIfPresent(Map<String, Object> map, String key, java.util.function.Consumer<String> setter) {
        Object value = map.get(key);
        if (value != null) {
            setter.accept(value.toString());
        }
    }

    private static void setDoubleIfPresent(Map<String, Object> map, String key, java.util.function.Consumer<Double> setter) {
        Object value = map.get(key);
        if (value != null) {
            setter.accept(toDouble(value));
        }
    }

    private static void setIntegerIfPresent(Map<String, Object> map, String key, java.util.function.Consumer<Integer> setter) {
        Object value = map.get(key);
        if (value != null) {
            if (value instanceof Number) {
                setter.accept(((Number) value).intValue());
            } else {
                try {
                    setter.accept(Integer.parseInt(value.toString()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
    }

    private static void setBooleanIfPresent(Map<String, Object> map, String key, java.util.function.Consumer<Boolean> setter) {
        Object value = map.get(key);
        if (value != null) {
            if (value instanceof Boolean) {
                setter.accept((Boolean) value);
            } else {
                setter.accept(Boolean.parseBoolean(value.toString()));
            }
        }
    }

    private static double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
