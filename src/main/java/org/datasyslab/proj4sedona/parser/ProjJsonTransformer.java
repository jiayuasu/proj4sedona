package org.datasyslab.proj4sedona.parser;

import org.datasyslab.proj4sedona.constants.Datum;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.CoordinateAxis;
import org.datasyslab.proj4sedona.core.ProjectionDef;

import java.util.ArrayList;
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

    private static final String[] TOWGS84_PARAMETER_NAMES = {
        "X-axis translation",
        "Y-axis translation",
        "Z-axis translation",
        "X-axis rotation",
        "Y-axis rotation",
        "Z-axis rotation",
        "Scale difference"
    };

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

        if ("ProjectedCRS".equals(projjson.get("type"))
                && (projjson.containsKey("id") || projjson.containsKey("ids"))) {
            Object coordinateSystem = projjson.get("coordinate_system");
            if (!(coordinateSystem instanceof Map)) {
                throw new IllegalArgumentException(
                    "Identified ProjectedCRS requires a coordinate_system");
            }
            validateIdentifiedProjectedCoordinateSystem(
                (Map<String, Object>) coordinateSystem);
        }

        ProjectionDef def = new ProjectionDef();
        
        // Handle BoundCRS specially - recurse into source_crs
        if ("BoundCRS".equals(projjson.get("type"))) {
            Object sourceCrs = projjson.get("source_crs");
            if (!(sourceCrs instanceof Map)) {
                throw new IllegalArgumentException("BoundCRS requires a source_crs");
            }
            Object transformation = projjson.get("transformation");
            if (!(transformation instanceof Map)) {
                throw new IllegalArgumentException("BoundCRS requires a transformation");
            }

            Map<String, Object> source = (Map<String, Object>) sourceCrs;
            Map<String, Object> operation = (Map<String, Object>) transformation;
            if (!isNtv2Transformation(operation)) {
                validateSupportedTowgs84BoundCrs(projjson, source, operation);
            }

            def = transform(source);
            processTransformation(operation, def);
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

    private static void validateIdentifiedProjectedCoordinateSystem(
            Map<String, Object> coordinateSystem) {
        Object subtype = coordinateSystem.get("subtype");
        if (subtype == null || subtype.toString().trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Identified ProjectedCRS coordinate_system requires a subtype");
        }
        Object axisValue = coordinateSystem.get("axis");
        if (!(axisValue instanceof List)) {
            throw new IllegalArgumentException(
                "Identified ProjectedCRS coordinate_system requires an axis array");
        }
        List<?> axes = (List<?>) axisValue;
        if (axes.size() < 2 || axes.size() > 3) {
            throw new IllegalArgumentException(
                "Identified ProjectedCRS requires two or three coordinate axes");
        }
        for (Object axis : axes) {
            if (!(axis instanceof Map)) {
                throw new IllegalArgumentException(
                    "Identified ProjectedCRS coordinate axes must be objects");
            }
        }
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
                } else if ("GeodeticCRS".equals(value)) {
                    // As in wkt-parser's transformPROJJSON: a GeodeticCRS with a
                    // Cartesian coordinate system is a geocentric CRS (e.g. EPSG:4978)
                    // and maps to geocent; any other subtype (ellipsoidal) is a
                    // geographic CRS and maps to longlat.
                    Object coordSys = projjson.get("coordinate_system");
                    boolean cartesian = coordSys instanceof Map
                        && "Cartesian".equals(((Map<String, Object>) coordSys).get("subtype"));
                    def.setProjName(cartesian ? "geocent" : "longlat");
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
                    // Store datum name in datumCode for geographic CRS identification
                    // (e.g., "NAD83 (National Spatial Reference System 2011)" -> EPSG:6318)
                    if (def.getDatumCode() == null) {
                        Object datumName = ((Map<String, Object>) value).get("name");
                        if (datumName != null) {
                            def.setDatumCode(datumName.toString());
                        }
                    }
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
                        def.setLong0(primeMeridianToRadians(longitude));
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
                    processAuthorityId((Map<String, Object>) value, def);
                }
                break;

            case "ids":
                if (value instanceof List) {
                    for (Object id : (List<?>) value) {
                        if (id instanceof Map
                                && processAuthorityId(
                                    (Map<String, Object>) id, def)) {
                            break;
                        }
                    }
                }
                break;

            case "conversion":
                if (value instanceof Map) {
                    processConversion((Map<String, Object>) value, def);
                }
                break;

            case "unit":
                processUnit(value, def);
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
                    // Set datumCode from datum name (preferred) or base CRS id/name
                    String resolvedDatumCode = null;
                    // First, try to get datum name from the datum/datum_ensemble object
                    Map<String, Object> datumSource = baseDatum instanceof Map
                            ? (Map<String, Object>) baseDatum
                            : (baseDatumEnsemble instanceof Map ? (Map<String, Object>) baseDatumEnsemble : null);
                    if (datumSource != null) {
                        Object datumName = datumSource.get("name");
                        if (datumName != null) {
                            // Resolve via Datum registry to get the short code
                            // (e.g., "World Geodetic System 1984" -> "wgs84")
                            // This avoids datum names with spaces producing invalid +datum= values.
                            // If the name is not in the registry, skip it and fall through to
                            // the base CRS id fallback — raw names may contain whitespace or
                            // be legacy placeholders (e.g., "Base").
                            Datum datum = Datum.get(datumName.toString());
                            if (datum != null) {
                                resolvedDatumCode = datum.getCode();
                            }
                        }
                    }
                    // Fall back to base CRS id if datum name not found
                    if (resolvedDatumCode == null) {
                        Object baseId = baseCrs.get("id");
                        if (baseId instanceof Map) {
                            Map<String, Object> id = (Map<String, Object>) baseId;
                            Object authority = id.get("authority");
                            Object code = id.get("code");
                            if (authority != null && code != null) {
                                resolvedDatumCode = authority.toString() + "_" + toIntString(code);
                            }
                        }
                    }
                    // Last resort: base CRS name, normalized to a PROJ-safe
                    // token (strip whitespace, lowercase).  e.g. "WGS 84" -> "wgs84".
                    if (resolvedDatumCode == null) {
                        Object baseName = baseCrs.get("name");
                        if (baseName != null) {
                            String normalized = baseName.toString()
                                    .replaceAll("\\s+", "").toLowerCase();
                            if (!normalized.isEmpty()
                                    && !"base".equals(normalized)) {
                                resolvedDatumCode = normalized;
                            }
                        }
                    }
                    if (resolvedDatumCode != null) {
                        def.setDatumCode(resolvedDatumCode);
                    }
                }
                break;

            default:
                // Ignore unrecognized keys
                break;
        }
    }

    private static boolean processAuthorityId(
            Map<String, Object> id, ProjectionDef def) {
        Object authority = id.get("authority");
        Object code = id.get("code");
        if (authority == null || code == null) {
            return false;
        }
        String authorityCode =
            authority.toString() + ":" + toIntString(code);
        def.setTitle(authorityCode);
        // Store authority:code for toEpsgCode()/toAuthority() lookup.
        def.setSrsCode(authorityCode);
        return true;
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
                def.setFromGreenwich(primeMeridianToRadians(longitude));
            }
        }
    }

    /**
     * Resolve a PROJJSON prime-meridian longitude to radians. The field is either a
     * plain number in degrees, or a value-with-unit object as PROJ emits for
     * non-degree meridians (EPSG:4807's Paris meridian is
     * {"value": 2.5969213, "unit": {..."grad", "conversion_factor": 0.0157...}});
     * the previous degree assumption fed the object through toDouble, which lost
     * the meridian entirely (0.0). Divergence from wkt-parser 1.5.5, which assumes
     * degrees unconditionally.
     */
    @SuppressWarnings("unchecked")
    private static double primeMeridianToRadians(Object longitude) {
        if (longitude instanceof Map) {
            Map<String, Object> lon = (Map<String, Object>) longitude;
            double value = toDouble(lon.get("value"));
            Object unit = lon.get("unit");
            if (unit instanceof Map) {
                Object factor = ((Map<String, Object>) unit).get("conversion_factor");
                if (factor != null && toDouble(factor) > 0) {
                    // Rounded at 1e-9 in degrees (as the towgs84 re-encode does) so
                    // the unit conversion's float noise does not leak into +pm=.
                    double degrees = Math.round(
                        value * toDouble(factor) * Values.R2D * 1e9) / 1e9;
                    return degrees * Values.D2R;
                }
            }
            return value * Values.D2R;
        }
        return toDouble(longitude) * Values.D2R;
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
    private static void calculateEllipsoid(Map<String, Object> ellipsoid, ProjectionDef def) {
        Object radius = ellipsoid.get("radius");
        if (radius != null) {
            double r = lengthInMetres(radius);
            def.setA(r);
            def.setRf(0.0);
            return;
        }

        Object sma = ellipsoid.get("semi_major_axis");
        if (sma != null) {
            double a = lengthInMetres(sma);
            def.setA(a);

            Object invFlat = ellipsoid.get("inverse_flattening");
            if (invFlat != null) {
                def.setRf(toDouble(invFlat));
            } else {
                Object smb = ellipsoid.get("semi_minor_axis");
                if (smb != null) {
                    double b = lengthInMetres(smb);
                    def.setRf(a == b ? 0.0 : a / (a - b));
                }
            }
        }
    }

    /**
     * Decode a PROJJSON value_in_metre_or_value_and_unit value.
     */
    @SuppressWarnings("unchecked")
    private static double lengthInMetres(Object length) {
        if (!(length instanceof Map)) {
            return toDouble(length);
        }
        Map<String, Object> valueAndUnit = (Map<String, Object>) length;
        double value = toDouble(valueAndUnit.get("value"));
        Object unit = valueAndUnit.get("unit");
        if (unit instanceof Map) {
            Object conversionFactor =
                ((Map<String, Object>) unit).get("conversion_factor");
            if (conversionFactor != null) {
                value *= toDouble(conversionFactor);
            }
        }
        return value;
    }

    /**
     * Process coordinate_system node.
     */
    @SuppressWarnings("unchecked")
    private static void processCoordinateSystem(Map<String, Object> coordSys, ProjectionDef def) {
        Object subtypeValue = coordSys.get("subtype");
        String subtype = subtypeValue == null ? null : subtypeValue.toString();
        def.setCoordinateSystemType(subtype);

        Object axisList = coordSys.get("axis");
        if (axisList instanceof List) {
            List<?> axisValues = (List<?>) axisList;
            List<Map<String, Object>> axes = new ArrayList<>();
            List<CoordinateAxis> coordinateAxes = new ArrayList<>();
            Object sharedUnit = coordSys.get("unit");
            for (Object axisValue : axisValues) {
                if (axisValue instanceof Map) {
                    Map<String, Object> axis = (Map<String, Object>) axisValue;
                    axes.add(axis);
                    coordinateAxes.add(parseCoordinateAxis(axis, sharedUnit, subtype));
                }
            }
            def.setCoordinateAxes(coordinateAxes);

            // Mirrors wkt-parser's transformPROJJSON direction map: the axis string is
            // set only when every direction maps (all-or-nothing), preserving the
            // document's axis order — including geocentric X/Y/Z permutations — and a
            // 2-axis system gets the implicit up axis appended.
            StringBuilder axisOrder = new StringBuilder();
            boolean allMapped = !axes.isEmpty();
            for (Map<String, Object> axis : axes) {
                Object direction = axis.get("direction");
                String mapped = direction == null ? null
                    : mapAxisDirection(direction.toString().toLowerCase());
                if (mapped == null) {
                    allMapped = false;
                    break;
                }
                axisOrder.append(mapped);
            }
            if (allMapped) {
                if (axisOrder.length() == 2) {
                    axisOrder.append('u');
                }
                def.setAxis(axisOrder.toString());
            }

            // Process units from coordinate system
            if (sharedUnit != null) {
                processUnit(sharedUnit, def);
            } else if (!axes.isEmpty()) {
                // Try to get unit from first axis
                Object axisUnit = axes.get(0).get("unit");
                if (axisUnit != null) {
                    processUnit(axisUnit, def);
                }
            }
        }
    }

    private static CoordinateAxis parseCoordinateAxis(
            Map<String, Object> axis, Object sharedUnit, String subtype) {
        Object nameValue = axis.get("name");
        String name = nameValue == null ? "Unknown" : nameValue.toString();
        Object abbreviationValue = axis.get("abbreviation");
        String abbreviation =
            abbreviationValue == null ? null : abbreviationValue.toString();
        Object directionValue = axis.get("direction");
        String direction =
            directionValue == null ? "unknown" : directionValue.toString();
        Integer order = axis.containsKey("order")
            ? parseRequiredAxisOrder(axis.get("order")) : null;
        Object unitValue = axis.containsKey("unit") ? axis.get("unit") : sharedUnit;
        CoordinateAxis.Unit unit = parseAxisUnit(unitValue, subtype);
        CoordinateAxis.Meridian meridian = axis.containsKey("meridian")
            ? parseAxisMeridian(axis.get("meridian")) : null;
        return new CoordinateAxis(
            name, abbreviation, direction, order, unit, meridian);
    }

    @SuppressWarnings("unchecked")
    private static CoordinateAxis.Unit parseAxisUnit(Object unitValue, String subtype) {
        if (unitValue == null) {
            return null;
        }

        String type = null;
        String name;
        Double conversionFactor = null;
        if (unitValue instanceof Map) {
            Map<String, Object> unit = (Map<String, Object>) unitValue;
            Object nameValue = unit.get("name");
            if (nameValue == null) {
                return null;
            }
            name = nameValue.toString();
            Object typeValue = unit.get("type");
            if (typeValue != null) {
                type = canonicalUnitType(typeValue.toString());
            }
            if (unit.containsKey("conversion_factor")) {
                conversionFactor =
                    parseOptionalDouble(unit.get("conversion_factor"));
                if (conversionFactor == null) {
                    throw new IllegalArgumentException(
                        "Axis unit conversion factor must be numeric");
                }
            }
        } else {
            name = unitValue.toString();
        }

        if (type == null || "Unit".equals(type)) {
            type = inferAxisUnitType(subtype, name);
        }
        if (conversionFactor == null) {
            conversionFactor = knownUnitFactor(name);
        }
        return new CoordinateAxis.Unit(type, name, conversionFactor);
    }

    @SuppressWarnings("unchecked")
    private static CoordinateAxis.Meridian parseAxisMeridian(Object meridianValue) {
        if (!(meridianValue instanceof Map)) {
            throw new IllegalArgumentException(
                "Axis meridian must be an object");
        }
        Map<String, Object> meridian = (Map<String, Object>) meridianValue;
        if (!meridian.containsKey("longitude")) {
            throw new IllegalArgumentException(
                "Axis meridian requires longitude");
        }
        Object longitudeValue = meridian.get("longitude");
        Object unitValue = meridian.get("unit");
        boolean unitSpecified = meridian.containsKey("unit");
        if (longitudeValue instanceof Map) {
            Map<String, Object> valueAndUnit = (Map<String, Object>) longitudeValue;
            if (!valueAndUnit.containsKey("value")
                    || !valueAndUnit.containsKey("unit")) {
                throw new IllegalArgumentException(
                    "Axis meridian longitude object requires value and unit");
            }
            longitudeValue = valueAndUnit.get("value");
            unitValue = valueAndUnit.get("unit");
            unitSpecified = true;
        }

        Double longitude = parseOptionalDouble(longitudeValue);
        if (longitude == null || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException(
                "Axis meridian longitude must be a finite number");
        }
        CoordinateAxis.Unit unit;
        if (!unitSpecified) {
            unit = new CoordinateAxis.Unit("AngularUnit", "degree", Values.D2R);
        } else {
            unit = parseAxisUnit(unitValue, "ellipsoidal");
        }
        if (unit == null
                || !"AngularUnit".equals(unit.getType())
                || unit.getConversionFactor() == null
                || !Double.isFinite(unit.getConversionFactor())
                || unit.getConversionFactor() <= 0) {
            throw new IllegalArgumentException(
                "Axis meridian requires a positive finite angular unit");
        }
        return new CoordinateAxis.Meridian(longitude, unit);
    }

    private static String canonicalUnitType(String type) {
        if ("linearunit".equalsIgnoreCase(type)) {
            return "LinearUnit";
        }
        if ("angularunit".equalsIgnoreCase(type)) {
            return "AngularUnit";
        }
        if ("scaleunit".equalsIgnoreCase(type)) {
            return "ScaleUnit";
        }
        if ("unit".equalsIgnoreCase(type)) {
            return "Unit";
        }
        return type;
    }

    private static String inferAxisUnitType(String subtype, String name) {
        if (isNamedUnit(name, "degree", "grad", "gon", "radian")) {
            return "AngularUnit";
        }
        if (isNamedUnit(name, "metre", "meter", "m")) {
            return "LinearUnit";
        }
        if (isNamedUnit(name, "unity")) {
            return "ScaleUnit";
        }
        if (subtype != null
                && ("ellipsoidal".equalsIgnoreCase(subtype)
                    || "spherical".equalsIgnoreCase(subtype))) {
            return "AngularUnit";
        }
        if (subtype != null
                && ("cartesian".equalsIgnoreCase(subtype)
                    || "vertical".equalsIgnoreCase(subtype))) {
            return "LinearUnit";
        }
        return "Unit";
    }

    private static Double knownUnitFactor(String name) {
        if (isNamedUnit(name, "degree")) {
            return Values.D2R;
        }
        if (isNamedUnit(name, "grad", "gon")) {
            return Math.PI / 200.0;
        }
        if (isNamedUnit(name, "radian")) {
            return 1.0;
        }
        if (isNamedUnit(name, "metre", "meter", "m", "unity")) {
            return 1.0;
        }
        return null;
    }

    private static boolean isNamedUnit(String actual, String... names) {
        for (String name : names) {
            if (name.equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    private static Integer parseRequiredAxisOrder(Object value) {
        Double numeric = parseOptionalDouble(value);
        if (numeric == null || !Double.isFinite(numeric)
                || numeric != Math.rint(numeric)
                || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Axis order must be an integer: " + value);
        }
        return numeric.intValue();
    }

    private static Double parseOptionalDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Process unit info. PROJJSON units are either an object with name and
     * conversion_factor, or a bare string for well-known units (e.g. the "metre"
     * on EPSG:4978's geocentric axes).
     */
    @SuppressWarnings("unchecked")
    private static void processUnit(Object unit, ProjectionDef def) {
        if (unit instanceof String) {
            setUnitsFromName(unit.toString(), def);
            // wkt-parser's processUnit records the identity factor for the
            // well-known metre; the proj-string parser does the same for +units=m.
            if ("meter".equals(def.getUnits())) {
                def.setToMeter(1.0);
            }
            return;
        }
        if (!(unit instanceof Map)) {
            return;
        }
        Map<String, Object> unitMap = (Map<String, Object>) unit;
        Object unitName = unitMap.get("name");
        if (unitName != null) {
            setUnitsFromName(unitName.toString(), def);
        }
        Object convFactor = unitMap.get("conversion_factor");
        if (convFactor != null) {
            def.setToMeter(toDouble(convFactor));
        }
    }

    private static void setUnitsFromName(String unitName, ProjectionDef def) {
        String units = unitName.toLowerCase();
        if ("metre".equals(units)) {
            units = "meter";
        }
        def.setUnits(units);
    }

    private static String mapAxisDirection(String direction) {
        switch (direction) {
            case "east": return "e";
            case "north": return "n";
            case "west": return "w";
            case "south": return "s";
            case "up": return "u";
            case "down": return "d";
            case "geocentricx": return "e";
            case "geocentricy": return "n";
            case "geocentricz": return "u";
            default: return null;
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
            case "latitude_of_pseudo_standard_parallel":
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
            case "co-latitude_of_cone_axis":
            case "co_latitude_of_cone_axis":
                def.setAlpha(value);
                break;
            case "angle_from_rectified_to_skew_grid":
                def.setRectifiedGridAngle(value);
                break;
            case "satellite_height":
                def.setH(value);
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
        if (isNtv2Transformation(transformation)) {
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

    @SuppressWarnings("unchecked")
    private static boolean isNtv2Transformation(Map<String, Object> transformation) {
        Object method = transformation.get("method");
        if (!(method instanceof Map)) {
            return false;
        }
        Object methodName = ((Map<String, Object>) method).get("name");
        return methodName != null && "NTv2".equals(methodName.toString());
    }

    /**
     * ProjectionDef can represent only PROJ's legacy three/seven-parameter
     * transformation to WGS 84. Reject other BoundCRS operations before flattening
     * them into datumParams, otherwise a later export would silently replace the
     * original target or rotation convention.
     */
    @SuppressWarnings("unchecked")
    private static void validateSupportedTowgs84BoundCrs(
            Map<String, Object> boundCrs,
            Map<String, Object> sourceCrs,
            Map<String, Object> transformation) {
        boolean geocentric = isGeocentricCrs(sourceCrs);

        Object parametersValue = transformation.get("parameters");
        if (!(parametersValue instanceof List)) {
            throw unsupportedBoundCrs(
                "transformation parameters must be a three- or seven-element array");
        }
        List<?> parameters = (List<?>) parametersValue;
        if (parameters.size() != 3 && parameters.size() != 7) {
            throw unsupportedBoundCrs(
                "transformation must contain exactly three or seven parameters");
        }

        int methodCode;
        String methodName;
        if (geocentric) {
            methodCode = parameters.size() == 7 ? 1033 : 1031;
            methodName = parameters.size() == 7
                ? "Position Vector transformation (geocentric domain)"
                : "Geocentric translations (geocentric domain)";
        } else {
            methodCode = parameters.size() == 7 ? 9606 : 9603;
            methodName = parameters.size() == 7
                ? "Position Vector transformation (geog2D domain)"
                : "Geocentric translations (geog2D domain)";
        }

        validateBoundCrsTarget(boundCrs.get("target_crs"), geocentric);
        validateBoundCrsMethod(transformation.get("method"), methodCode, methodName);
        validateTowgs84Parameters(parameters);
    }

    @SuppressWarnings("unchecked")
    private static boolean isGeocentricCrs(Map<String, Object> crs) {
        Object type = crs.get("type");
        if ("GeographicCRS".equals(type) || "ProjectedCRS".equals(type)) {
            return false;
        }
        if (!"GeodeticCRS".equals(type)) {
            throw unsupportedBoundCrs("source CRS type is not supported");
        }
        Object coordinateSystem = crs.get("coordinate_system");
        return coordinateSystem instanceof Map
            && "Cartesian".equals(((Map<String, Object>) coordinateSystem).get("subtype"));
    }

    @SuppressWarnings("unchecked")
    private static void validateBoundCrsTarget(Object targetValue, boolean geocentric) {
        if (!(targetValue instanceof Map)) {
            throw unsupportedBoundCrs("target_crs must be WGS 84");
        }
        Map<String, Object> target = (Map<String, Object>) targetValue;

        Object type = target.get("type");
        Object coordinateSystem = target.get("coordinate_system");
        boolean cartesian = coordinateSystem instanceof Map
            && "Cartesian".equals(((Map<String, Object>) coordinateSystem).get("subtype"));
        if (geocentric) {
            if (!"GeodeticCRS".equals(type) || !cartesian) {
                throw unsupportedBoundCrs(
                    "geocentric sources require a geocentric WGS 84 target");
            }
        } else if (!(("GeographicCRS".equals(type) || "GeodeticCRS".equals(type))
                && !cartesian)) {
            throw unsupportedBoundCrs(
                "geographic and projected sources require a geographic WGS 84 target");
        }

        Object idValue = target.get("id");
        if (idValue != null) {
            if (!(idValue instanceof Map)) {
                throw unsupportedBoundCrs("target CRS identifier is malformed");
            }
            Map<String, Object> id = (Map<String, Object>) idValue;
            Object authority = id.get("authority");
            Object code = id.get("code");
            int expectedCode = geocentric ? 4978 : 4326;
            if (authority == null || code == null
                    || !"EPSG".equalsIgnoreCase(authority.toString())
                    || parseInteger(code, "target CRS identifier") != expectedCode) {
                throw unsupportedBoundCrs(
                    "target CRS must be EPSG:" + expectedCode);
            }
            return;
        }

        if (!hasWgs84Identity(target)) {
            throw unsupportedBoundCrs(
                "ID-less target CRS must identify the WGS 84 datum and ellipsoid");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasWgs84Identity(Map<String, Object> target) {
        Object datumValue = target.get("datum");
        if (!(datumValue instanceof Map)) {
            datumValue = target.get("datum_ensemble");
        }
        if (!(datumValue instanceof Map)) {
            return false;
        }
        Map<String, Object> datum = (Map<String, Object>) datumValue;
        Object ellipsoidValue = datum.get("ellipsoid");
        return isWgs84Name(datum.get("name")) && ellipsoidValue instanceof Map
            && isWgs84Ellipsoid((Map<String, Object>) ellipsoidValue);
    }

    private static boolean isWgs84Name(Object value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toString().replaceAll("[^A-Za-z0-9]", "");
        return "WGS84".equalsIgnoreCase(normalized)
            || "WorldGeodeticSystem1984".equalsIgnoreCase(normalized)
            || "WorldGeodeticSystem1984ensemble".equalsIgnoreCase(normalized);
    }

    private static boolean isWgs84Ellipsoid(Map<String, Object> ellipsoid) {
        Object semiMajor = ellipsoid.get("semi_major_axis");
        Object inverseFlattening = ellipsoid.get("inverse_flattening");
        if (semiMajor == null || inverseFlattening == null) {
            return false;
        }
        return Math.abs(parseFiniteDouble(semiMajor, "target semi-major axis")
                - 6378137.0) < 1e-6
            && Math.abs(parseFiniteDouble(
                    inverseFlattening, "target inverse flattening")
                - 298.257223563) < 1e-9;
    }

    @SuppressWarnings("unchecked")
    private static void validateBoundCrsMethod(
            Object methodValue, int expectedCode, String expectedName) {
        if (!(methodValue instanceof Map)) {
            throw unsupportedBoundCrs("transformation method is required");
        }
        Map<String, Object> method = (Map<String, Object>) methodValue;
        Object name = method.get("name");
        Object idValue = method.get("id");
        if (name == null && idValue == null) {
            throw unsupportedBoundCrs("transformation method is required");
        }
        if (idValue != null) {
            requireEpsgCode(idValue, expectedCode, "transformation method");
        } else if (!expectedName.equalsIgnoreCase(name.toString())) {
            throw unsupportedBoundCrs(
                "transformation method must be " + expectedName);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateTowgs84Parameters(List<?> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            Object parameterValue = parameters.get(i);
            if (!(parameterValue instanceof Map)) {
                throw unsupportedBoundCrs("transformation parameter is malformed");
            }
            Map<String, Object> parameter = (Map<String, Object>) parameterValue;
            String expectedName = TOWGS84_PARAMETER_NAMES[i];
            int expectedCode = 8605 + i;
            Object name = parameter.get("name");
            Object id = parameter.get("id");
            if (name == null && id == null) {
                throw unsupportedBoundCrs(
                    "transformation parameter " + (i + 1) + " has no identity");
            }
            if (id != null) {
                requireEpsgCode(id, expectedCode, "transformation parameter");
            } else if (!expectedName.equalsIgnoreCase(name.toString())) {
                throw unsupportedBoundCrs(
                    "transformation parameters must use canonical TOWGS84 order");
            }
            parseFiniteDouble(parameter.get("value"), expectedName);
            validateTowgs84Unit(parameter.get("unit"), i);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateTowgs84Unit(Object unitValue, int parameterIndex) {
        if (unitValue == null) {
            // WKT2 ABRIDGEDTRANSFORMATION may omit explicit parameter units.
            return;
        }

        String expectedType;
        double expectedFactor;
        String[] acceptedNames;
        if (parameterIndex < 3) {
            expectedType = "LinearUnit";
            expectedFactor = 1.0;
            acceptedNames = new String[]{"metre", "meter", "m"};
        } else if (parameterIndex < 6) {
            expectedType = "AngularUnit";
            expectedFactor = Values.SEC_TO_RAD;
            acceptedNames = new String[]{"arc-second", "arcsecond", "arc second"};
        } else {
            expectedType = "ScaleUnit";
            expectedFactor = 1e-6;
            acceptedNames = new String[]{"parts per million", "ppm"};
        }

        if (unitValue instanceof String) {
            if (!equalsAnyIgnoreCase(unitValue.toString(), acceptedNames)) {
                throw unsupportedBoundCrs("transformation parameter unit is not supported");
            }
            return;
        }
        if (!(unitValue instanceof Map)) {
            throw unsupportedBoundCrs("transformation parameter unit is malformed");
        }

        Map<String, Object> unit = (Map<String, Object>) unitValue;
        Object type = unit.get("type");
        if (type != null && !expectedType.equalsIgnoreCase(type.toString())) {
            throw unsupportedBoundCrs("transformation parameter unit type is not supported");
        }
        Object factor = unit.get("conversion_factor");
        Object name = unit.get("name");
        if (factor == null && (name == null
                || !equalsAnyIgnoreCase(name.toString(), acceptedNames))) {
            throw unsupportedBoundCrs("transformation parameter unit is not supported");
        }
        if (factor != null) {
            double actualFactor =
                parseFiniteDouble(factor, "transformation unit conversion factor");
            double tolerance = Math.max(1e-15, Math.abs(expectedFactor) * 1e-12);
            if (Math.abs(actualFactor - expectedFactor) > tolerance) {
                throw unsupportedBoundCrs("transformation parameter unit is not supported");
            }
        }
    }

    private static boolean equalsAnyIgnoreCase(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void requireEpsgCode(
            Object idValue, int expectedCode, String description) {
        if (!(idValue instanceof Map)) {
            throw unsupportedBoundCrs(description + " identifier is malformed");
        }
        Map<String, Object> id = (Map<String, Object>) idValue;
        Object authority = id.get("authority");
        Object code = id.get("code");
        if (authority == null || code == null
                || !"EPSG".equalsIgnoreCase(authority.toString())
                || parseInteger(code, description + " identifier") != expectedCode) {
            throw unsupportedBoundCrs(
                description + " must use EPSG:" + expectedCode);
        }
    }

    private static int parseInteger(Object value, String description) {
        double numeric = parseFiniteDouble(value, description);
        if (numeric != Math.rint(numeric)
                || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            throw unsupportedBoundCrs(description + " is not an integer");
        }
        return (int) numeric;
    }

    private static double parseFiniteDouble(Object value, String description) {
        if (value == null) {
            throw unsupportedBoundCrs(description + " is missing");
        }
        final double numeric;
        if (value instanceof Number) {
            numeric = ((Number) value).doubleValue();
        } else {
            try {
                numeric = Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                throw unsupportedBoundCrs(description + " is not numeric");
            }
        }
        if (!Double.isFinite(numeric)) {
            throw unsupportedBoundCrs(description + " is not finite");
        }
        return numeric;
    }

    private static IllegalArgumentException unsupportedBoundCrs(String detail) {
        return new IllegalArgumentException(
            "Unsupported BoundCRS: " + detail);
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
     * Convert a value to an integer string representation.
     * Handles the case where GSON parses JSON integer codes (e.g., 6318) as Double (6318.0).
     */
    private static String toIntString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return value.toString();
        }
        return value.toString();
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
