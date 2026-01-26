package org.proj4sedona.parser;

import org.proj4sedona.constants.Values;
import org.proj4sedona.core.ProjectionDef;

import java.util.List;
import java.util.Map;

/**
 * Transforms PROJJSON-like Map structures into ProjectionDef objects.
 * Mirrors: wkt-parser/transformPROJJSON.js
 * 
 * This class handles the conversion of PROJJSON structures (from WKT2 parsing
 * or direct PROJJSON input) into the ProjectionDef format used by proj4sedona.
 */
public final class ProjJsonTransformer {

    private ProjJsonTransformer() {
        // Utility class
    }

    /**
     * Transform a PROJJSON-like Map into a ProjectionDef.
     * 
     * @param projjson The PROJJSON Map structure
     * @return The populated ProjectionDef
     */
    @SuppressWarnings("unchecked")
    public static ProjectionDef transform(Map<String, Object> projjson) {
        if (projjson == null) {
            return new ProjectionDef();
        }

        ProjectionDef def = new ProjectionDef();
        
        // Handle BoundCRS specially - recurse into source_crs
        if ("BoundCRS".equals(projjson.get("type"))) {
            Object sourceCrs = projjson.get("source_crs");
            if (sourceCrs instanceof Map) {
                def = transform((Map<String, Object>) sourceCrs);
            }
            
            // Process transformation for datum params
            Object transformation = projjson.get("transformation");
            if (transformation instanceof Map) {
                processTransformation((Map<String, Object>) transformation, def);
            }
            
            return def;
        }

        // Process each key in the PROJJSON
        for (Map.Entry<String, Object> entry : projjson.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value == null) {
                continue;
            }

            processKey(key, value, projjson, def);
        }

        // Apply calculated properties (parameter normalization)
        applyCalculatedProperties(def);

        // Apply projection defaults
        WktUtils.applyProjectionDefaults(def);

        return def;
    }

    /**
     * Process a single key-value pair from the PROJJSON.
     */
    @SuppressWarnings("unchecked")
    private static void processKey(String key, Object value, Map<String, Object> projjson, ProjectionDef def) {
        switch (key) {
            case "name":
                if (def.getSrsCode() == null) {
                    def.setTitle(value.toString());
                    def.setSrsCode(value.toString());
                }
                break;

            case "type":
                if ("GeographicCRS".equals(value)) {
                    def.setProjName("longlat");
                } else if ("ProjectedCRS".equals(value)) {
                    // projName will be set from conversion.method.name
                    Object conversion = projjson.get("conversion");
                    if (conversion instanceof Map) {
                        Object method = ((Map<String, Object>) conversion).get("method");
                        if (method instanceof Map) {
                            Object methodName = ((Map<String, Object>) method).get("name");
                            if (methodName != null) {
                                def.setProjName(methodName.toString());
                            }
                        }
                    }
                }
                break;

            case "datum":
            case "datum_ensemble":
                if (value instanceof Map) {
                    processDatum((Map<String, Object>) value, def);
                }
                break;

            case "ellipsoid":
                if (value instanceof Map) {
                    processEllipsoid((Map<String, Object>) value, def);
                }
                break;

            case "prime_meridian":
                if (value instanceof Map) {
                    Object longitude = ((Map<String, Object>) value).get("longitude");
                    if (longitude != null) {
                        def.setLong0(toDouble(longitude) * Values.D2R);
                    }
                }
                break;

            case "coordinate_system":
                if (value instanceof Map) {
                    processCoordinateSystem((Map<String, Object>) value, def);
                }
                break;

            case "id":
                if (value instanceof Map) {
                    Map<String, Object> id = (Map<String, Object>) value;
                    Object authority = id.get("authority");
                    Object code = id.get("code");
                    if (authority != null && code != null) {
                        def.setTitle(authority.toString() + ":" + code.toString());
                    }
                }
                break;

            case "conversion":
                if (value instanceof Map) {
                    processConversion((Map<String, Object>) value, def);
                }
                break;

            case "unit":
                if (value instanceof Map) {
                    Map<String, Object> unit = (Map<String, Object>) value;
                    Object unitName = unit.get("name");
                    if (unitName != null) {
                        String units = unitName.toString().toLowerCase();
                        if ("metre".equals(units)) {
                            units = "meter";
                        }
                        def.setUnits(units);
                    }
                    Object convFactor = unit.get("conversion_factor");
                    if (convFactor != null) {
                        def.setToMeter(toDouble(convFactor));
                    }
                }
                break;

            case "base_crs":
                if (value instanceof Map) {
                    Map<String, Object> baseCrs = (Map<String, Object>) value;
                    // Recurse into base_crs to get datum info
                    transform(baseCrs); // Process but merge into current def
                    Object baseDatum = baseCrs.get("datum");
                    Object baseDatumEnsemble = baseCrs.get("datum_ensemble");
                    if (baseDatum instanceof Map) {
                        processDatum((Map<String, Object>) baseDatum, def);
                    } else if (baseDatumEnsemble instanceof Map) {
                        processDatum((Map<String, Object>) baseDatumEnsemble, def);
                    }
                    // Set datumCode
                    Object baseId = baseCrs.get("id");
                    if (baseId instanceof Map) {
                        Map<String, Object> id = (Map<String, Object>) baseId;
                        def.setDatumCode(id.get("authority") + "_" + id.get("code"));
                    } else {
                        Object baseName = baseCrs.get("name");
                        if (baseName != null) {
                            def.setDatumCode(baseName.toString());
                        }
                    }
                }
                break;

            default:
                // Ignore unrecognized keys
                break;
        }
    }

    /**
     * Process datum/datum_ensemble node.
     */
    @SuppressWarnings("unchecked")
    private static void processDatum(Map<String, Object> datum, ProjectionDef def) {
        Object ellipsoid = datum.get("ellipsoid");
        if (ellipsoid instanceof Map) {
            Map<String, Object> ellipsoidMap = (Map<String, Object>) ellipsoid;
            def.setEllps(ellipsoidMap.get("name") != null ? ellipsoidMap.get("name").toString() : null);
            calculateEllipsoid(ellipsoidMap, def);
        }

        Object primeMeridian = datum.get("prime_meridian");
        if (primeMeridian instanceof Map) {
            Object longitude = ((Map<String, Object>) primeMeridian).get("longitude");
            if (longitude != null) {
                def.setFromGreenwich(toDouble(longitude) * Values.D2R);
            }
        }
    }

    /**
     * Process ellipsoid node.
     */
    private static void processEllipsoid(Map<String, Object> ellipsoid, ProjectionDef def) {
        def.setEllps(ellipsoid.get("name") != null ? ellipsoid.get("name").toString() : null);
        calculateEllipsoid(ellipsoid, def);
    }

    /**
     * Calculate ellipsoid parameters.
     */
    @SuppressWarnings("unchecked")
    private static void calculateEllipsoid(Map<String, Object> ellipsoid, ProjectionDef def) {
        Object radius = ellipsoid.get("radius");
        if (radius != null) {
            double r = toDouble(radius);
            def.setA(r);
            def.setRf(0.0);
            return;
        }

        Object sma = ellipsoid.get("semi_major_axis");
        if (sma != null) {
            double a;
            if (sma instanceof Map) {
                // Handle { value: x, unit: { conversion_factor: y } }
                Map<String, Object> smaMap = (Map<String, Object>) sma;
                double value = toDouble(smaMap.get("value"));
                Object unit = smaMap.get("unit");
                if (unit instanceof Map) {
                    Object cf = ((Map<String, Object>) unit).get("conversion_factor");
                    if (cf != null) {
                        value *= toDouble(cf);
                    }
                }
                a = value;
            } else {
                a = toDouble(sma);
            }
            def.setA(a);

            Object invFlat = ellipsoid.get("inverse_flattening");
            if (invFlat != null) {
                def.setRf(toDouble(invFlat));
            } else {
                Object smb = ellipsoid.get("semi_minor_axis");
                if (smb != null) {
                    double b = toDouble(smb);
                    def.setRf(a / (a - b));
                }
            }
        }
    }

    /**
     * Process coordinate_system node.
     */
    @SuppressWarnings("unchecked")
    private static void processCoordinateSystem(Map<String, Object> coordSys, ProjectionDef def) {
        Object axisList = coordSys.get("axis");
        if (axisList instanceof List) {
            List<Map<String, Object>> axes = (List<Map<String, Object>>) axisList;
            StringBuilder axisOrder = new StringBuilder();
            
            for (Map<String, Object> axis : axes) {
                Object direction = axis.get("direction");
                if (direction != null) {
                    String dir = direction.toString().toLowerCase();
                    switch (dir) {
                        case "east": axisOrder.append('e'); break;
                        case "north": axisOrder.append('n'); break;
                        case "west": axisOrder.append('w'); break;
                        case "south": axisOrder.append('s'); break;
                        default: break;
                    }
                }
            }
            
            if (axisOrder.length() > 0) {
                axisOrder.append('u'); // Add up direction
                def.setAxis(axisOrder.toString());
            }

            // Process units from coordinate system
            Object unit = coordSys.get("unit");
            if (unit instanceof Map) {
                processUnit((Map<String, Object>) unit, def);
            } else if (!axes.isEmpty()) {
                // Try to get unit from first axis
                Object axisUnit = axes.get(0).get("unit");
                if (axisUnit instanceof Map) {
                    processUnit((Map<String, Object>) axisUnit, def);
                }
            }
        }
    }

    /**
     * Process unit info.
     */
    private static void processUnit(Map<String, Object> unit, ProjectionDef def) {
        Object unitName = unit.get("name");
        if (unitName != null) {
            String units = unitName.toString().toLowerCase();
            if ("metre".equals(units)) {
                units = "meter";
            }
            def.setUnits(units);
        }
        Object convFactor = unit.get("conversion_factor");
        if (convFactor != null) {
            def.setToMeter(toDouble(convFactor));
        }
    }

    /**
     * Process conversion node (method and parameters).
     */
    @SuppressWarnings("unchecked")
    private static void processConversion(Map<String, Object> conversion, ProjectionDef def) {
        // Get method name as projName
        Object method = conversion.get("method");
        if (method instanceof Map) {
            Object methodName = ((Map<String, Object>) method).get("name");
            if (methodName != null) {
                def.setProjName(methodName.toString());
            }
        }

        // Process parameters
        Object params = conversion.get("parameters");
        if (params instanceof List) {
            List<Map<String, Object>> paramList = (List<Map<String, Object>>) params;
            for (Map<String, Object> param : paramList) {
                processParameter(param, def);
            }
        }
    }

    /**
     * Process a single parameter.
     */
    @SuppressWarnings("unchecked")
    private static void processParameter(Map<String, Object> param, ProjectionDef def) {
        Object nameObj = param.get("name");
        if (nameObj == null) {
            return;
        }

        String paramName = nameObj.toString().toLowerCase().replace(" ", "_");
        Object valueObj = param.get("value");
        if (valueObj == null) {
            return;
        }

        double value = toDouble(valueObj);

        // Apply unit conversion if present
        Object unit = param.get("unit");
        if (unit instanceof Map) {
            Object convFactor = ((Map<String, Object>) unit).get("conversion_factor");
            if (convFactor != null) {
                value *= toDouble(convFactor);
            }
        } else if ("degree".equals(unit)) {
            value *= Values.D2R;
        }

        // Store the parameter using normalized name
        setParameterByName(def, paramName, value);
    }

    /**
     * Set a parameter on the ProjectionDef by normalized name.
     */
    private static void setParameterByName(ProjectionDef def, String name, double value) {
        // Store raw value for later normalization
        switch (name) {
            case "latitude_of_false_origin":
            case "latitude_of_natural_origin":
            case "latitude_of_projection_centre":
            case "latitude_of_standard_parallel":
                // Will be normalized to lat0 later
                def.setLat0(value);
                break;
            case "longitude_of_false_origin":
            case "longitude_of_natural_origin":
            case "longitude_of_origin":
                // Will be normalized to long0 later
                def.setLong0(value);
                break;
            case "longitude_of_projection_centre":
                def.setLongc(value);
                break;
            case "latitude_of_1st_standard_parallel":
                def.setLat1(value);
                break;
            case "latitude_of_2nd_standard_parallel":
                def.setLat2(value);
                break;
            case "easting_at_false_origin":
            case "false_easting":
            case "easting_at_projection_centre":
                def.setX0(value);
                break;
            case "northing_at_false_origin":
            case "false_northing":
            case "northing_at_projection_centre":
                def.setY0(value);
                break;
            case "scale_factor_at_natural_origin":
            case "scale_factor_at_projection_centre":
            case "scale_factor_on_pseudo_standard_parallel":
                def.setK0(value);
                break;
            case "azimuth":
            case "azimuth_at_projection_centre":
                def.setAlpha(value);
                break;
            case "angle_from_rectified_to_skew_grid":
                def.setRectifiedGridAngle(value);
                break;
            default:
                // Unknown parameters are ignored
                break;
        }
    }

    /**
     * Process transformation node (for BoundCRS).
     */
    @SuppressWarnings("unchecked")
    private static void processTransformation(Map<String, Object> transformation, ProjectionDef def) {
        Object method = transformation.get("method");
        if (method instanceof Map) {
            Object methodName = ((Map<String, Object>) method).get("name");
            if (methodName != null && "NTv2".equals(methodName.toString())) {
                // Set nadgrids from parameter file
                Object params = transformation.get("parameters");
                if (params instanceof List) {
                    List<Map<String, Object>> paramList = (List<Map<String, Object>>) params;
                    if (!paramList.isEmpty()) {
                        Object value = paramList.get(0).get("value");
                        if (value != null) {
                            def.setNadgrids(value.toString());
                        }
                    }
                }
                return;
            }
        }

        // For non-NTv2 transformations, extract datum_params
        Object params = transformation.get("parameters");
        if (params instanceof List) {
            List<Map<String, Object>> paramList = (List<Map<String, Object>>) params;
            double[] datumParams = new double[paramList.size()];
            for (int i = 0; i < paramList.size(); i++) {
                Object value = paramList.get(i).get("value");
                datumParams[i] = value != null ? toDouble(value) : 0.0;
            }
            def.setDatumParams(datumParams);
        }
    }

    /**
     * Apply calculated properties for parameter normalization.
     * This mirrors the post-processing in transformPROJJSON.js
     */
    private static void applyCalculatedProperties(ProjectionDef def) {
        // lat0 from lat1 if only lat1 is set (for some projections)
        if (def.getLat0() == null && def.getLat1() != null) {
            def.setLat0(def.getLat1());
        }
        
        // If latitude_of_standard_parallel was set (stored in lat0), also set lat1
        // This handles projections that use a single standard parallel
        if (def.getLat1() == null && def.getLat0() != null) {
            def.setLat1(def.getLat0());
        }
    }

    /**
     * Safely convert value to double.
     */
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
