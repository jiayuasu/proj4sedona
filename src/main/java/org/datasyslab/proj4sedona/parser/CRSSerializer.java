package org.datasyslab.proj4sedona.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.datasyslab.proj4sedona.constants.Datum;
import org.datasyslab.proj4sedona.constants.Ellipsoid;
import org.datasyslab.proj4sedona.constants.Units;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.defs.Defs;
import org.datasyslab.proj4sedona.projection.ProjectionParams;

import java.util.*;

/**
 * Serializes CRS (Coordinate Reference System) definitions to various formats.
 * 
 * <p>Supported output formats:</p>
 * <ul>
 *   <li><b>PROJ string</b>: Classic PROJ.4 format (e.g., "+proj=longlat +datum=WGS84")</li>
 *   <li><b>WKT1</b>: OGC Well-Known Text 1 format (widely compatible)</li>
 *   <li><b>WKT2</b>: ISO 19162 WKT 2 format (2019 revision, more precise)</li>
 *   <li><b>PROJJSON</b>: JSON representation of CRS (based on PROJ 6+ schema)</li>
 *   <li><b>EPSG code</b>: Attempt to identify matching EPSG code</li>
 * </ul>
 * 
 * <p>Similar to pyproj's CRS export functionality:</p>
 * <pre>
 * Proj proj = new Proj("EPSG:4326");
 * String projStr = CRSSerializer.toProjString(proj);
 * String wkt1 = CRSSerializer.toWkt1(proj);
 * String wkt2 = CRSSerializer.toWkt2(proj);
 * String json = CRSSerializer.toProjJson(proj);
 * String epsg = CRSSerializer.toEpsgCode(proj);  // "EPSG:4326" or null
 * </pre>
 */
public final class CRSSerializer {

    private static final double RAD_TO_DEG = 180.0 / Math.PI;
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final String DEG_TO_RAD_STR = Double.toString(DEG_TO_RAD);

    // Projection name mappings: PROJ -> WKT method names (short/generic names)
    private static final Map<String, String> PROJ_TO_WKT_METHOD = new LinkedHashMap<>();
    
    static {
        PROJ_TO_WKT_METHOD.put("longlat", "Geographic");
        PROJ_TO_WKT_METHOD.put("tmerc", "Transverse Mercator");
        PROJ_TO_WKT_METHOD.put("utm", "Transverse Mercator");  // UTM uses TM
        PROJ_TO_WKT_METHOD.put("merc", "Mercator");
        PROJ_TO_WKT_METHOD.put("lcc", "Lambert Conformal Conic");
        PROJ_TO_WKT_METHOD.put("aea", "Albers Equal Area");
        PROJ_TO_WKT_METHOD.put("stere", "Stereographic");
        PROJ_TO_WKT_METHOD.put("sterea", "Oblique Stereographic");
        PROJ_TO_WKT_METHOD.put("omerc", "Oblique Mercator");
        PROJ_TO_WKT_METHOD.put("somerc", "Swiss Oblique Mercator");
        PROJ_TO_WKT_METHOD.put("krovak", "Krovak");
        PROJ_TO_WKT_METHOD.put("cass", "Cassini-Soldner");
        PROJ_TO_WKT_METHOD.put("laea", "Lambert Azimuthal Equal Area");
        PROJ_TO_WKT_METHOD.put("aeqd", "Azimuthal Equidistant");
        PROJ_TO_WKT_METHOD.put("eqdc", "Equidistant Conic");
        PROJ_TO_WKT_METHOD.put("poly", "Polyconic");
        PROJ_TO_WKT_METHOD.put("nzmg", "New Zealand Map Grid");
        PROJ_TO_WKT_METHOD.put("mill", "Miller Cylindrical");
        PROJ_TO_WKT_METHOD.put("sinu", "Sinusoidal");
        PROJ_TO_WKT_METHOD.put("moll", "Mollweide");
        PROJ_TO_WKT_METHOD.put("eqc", "Equidistant Cylindrical");
        PROJ_TO_WKT_METHOD.put("cea", "Lambert Cylindrical Equal Area");
        PROJ_TO_WKT_METHOD.put("gnom", "Gnomonic");
        PROJ_TO_WKT_METHOD.put("ortho", "Orthographic");
        PROJ_TO_WKT_METHOD.put("vandg", "Van der Grinten");
        PROJ_TO_WKT_METHOD.put("robin", "Robinson");
        PROJ_TO_WKT_METHOD.put("etmerc", "Transverse Mercator");
        PROJ_TO_WKT_METHOD.put("gstmerc", "Gauss Schreiber Transverse Mercator");
        PROJ_TO_WKT_METHOD.put("eck6", "Eckert VI");
        PROJ_TO_WKT_METHOD.put("eqearth", "Equal Earth");
        PROJ_TO_WKT_METHOD.put("bonne", "Bonne");
        // Base geos name; getWktMethodName(proj, params) selects the sweep variant.
        PROJ_TO_WKT_METHOD.put("geos", "Geostationary Satellite (Sweep Y)");
    }

    // Reverse mapping: WKT/PROJJSON method name -> PROJ short name
    // (for projName normalization when parsing PROJJSON)
    // Source: EPSG canonical method names from pyproj's PROJJSON output
    private static final Map<String, String> WKT_TO_PROJ_METHOD = new LinkedHashMap<>();

    static {
        // Build reverse mapping from PROJ_TO_WKT_METHOD
        for (Map.Entry<String, String> entry : PROJ_TO_WKT_METHOD.entrySet()) {
            String wktLower = entry.getValue().toLowerCase(Locale.ROOT);
            if (!WKT_TO_PROJ_METHOD.containsKey(wktLower)) {
                WKT_TO_PROJ_METHOD.put(wktLower, entry.getKey());
            }
        }
        // EPSG canonical PROJJSON method names (from pyproj output) that differ
        // from the short generic names above. These are the names that appear in
        // conversion.method.name in PROJJSON emitted by pyproj/PROJ.
        WKT_TO_PROJ_METHOD.put("mercator (variant a)", "merc");
        WKT_TO_PROJ_METHOD.put("mercator (variant b)", "merc");
        WKT_TO_PROJ_METHOD.put("popular visualisation pseudo mercator", "merc");
        WKT_TO_PROJ_METHOD.put("lambert conic conformal (1sp)", "lcc");
        WKT_TO_PROJ_METHOD.put("lambert conic conformal (2sp)", "lcc");
        WKT_TO_PROJ_METHOD.put("lambert conic conformal (1sp variant b)", "lcc");
        WKT_TO_PROJ_METHOD.put("lambert conic conformal (2sp belgium)", "lcc");
        WKT_TO_PROJ_METHOD.put("lambert conic conformal (2sp michigan)", "lcc");
        WKT_TO_PROJ_METHOD.put("lambert conic conformal (west orientated)", "lcc");
        WKT_TO_PROJ_METHOD.put("lambert conic near-conformal", "lcc");
        WKT_TO_PROJ_METHOD.put("polar stereographic (variant a)", "stere");
        WKT_TO_PROJ_METHOD.put("polar stereographic (variant b)", "stere");
        WKT_TO_PROJ_METHOD.put("polar stereographic (variant c)", "stere");
        WKT_TO_PROJ_METHOD.put("hotine oblique mercator (variant a)", "omerc");
        WKT_TO_PROJ_METHOD.put("hotine oblique mercator (variant b)", "omerc");
        WKT_TO_PROJ_METHOD.put("hotine oblique mercator", "omerc");
        WKT_TO_PROJ_METHOD.put("hotine oblique mercator azimuth center", "omerc");
        WKT_TO_PROJ_METHOD.put("hotine oblique mercator azimuth natural origin", "omerc");
        WKT_TO_PROJ_METHOD.put("hotine oblique mercator two point natural origin", "omerc");
        WKT_TO_PROJ_METHOD.put("laborde oblique mercator", "omerc");
        WKT_TO_PROJ_METHOD.put("krovak (north orientated)", "krovak");
        WKT_TO_PROJ_METHOD.put("krovak modified", "krovak");
        WKT_TO_PROJ_METHOD.put("krovak modified (north orientated)", "krovak");
        WKT_TO_PROJ_METHOD.put("geostationary satellite (sweep x)", "geos");
        WKT_TO_PROJ_METHOD.put("geostationary satellite", "geos");  // ESRI WKT1 name
        WKT_TO_PROJ_METHOD.put("american polyconic", "poly");
        WKT_TO_PROJ_METHOD.put("equidistant cylindrical", "eqc");
        WKT_TO_PROJ_METHOD.put("equirectangular", "eqc");
        WKT_TO_PROJ_METHOD.put("cylindrical equal area", "cea");
        WKT_TO_PROJ_METHOD.put("lambert cylindrical equal area", "cea");
        WKT_TO_PROJ_METHOD.put("lambert cylindrical equal area (spherical)", "cea");
        WKT_TO_PROJ_METHOD.put("lambert azimuthal equal area (spherical)", "laea");
        WKT_TO_PROJ_METHOD.put("modified azimuthal equidistant", "aeqd");
        WKT_TO_PROJ_METHOD.put("transverse mercator (south orientated)", "tmerc");
        WKT_TO_PROJ_METHOD.put("transverse mercator zoned grid system", "tmerc");
        WKT_TO_PROJ_METHOD.put("hyperbolic cassini-soldner", "cass");
        WKT_TO_PROJ_METHOD.put("gauss schreiber transverse mercator", "gstmerc");
        WKT_TO_PROJ_METHOD.put("gauss-schreiber transverse mercator", "gstmerc");
    }

    // Well-known datum names mapped to EPSG geographic 2D CRS codes.
    // Source: PROJ database (EPSG registry)
    // Used for datum-name-based identification when no id field is present (like pyproj).
    // Keys are lowercase. Lookup also normalizes underscores to spaces, so underscore
    // variants are not needed here.
    private static final Map<String, String> DATUM_NAME_TO_EPSG = new LinkedHashMap<>();

    static {
        // WGS 84 (datum name, ensemble name, and aliases)
        DATUM_NAME_TO_EPSG.put("world geodetic system 1984", "EPSG:4326");
        DATUM_NAME_TO_EPSG.put("world geodetic system 1984 ensemble", "EPSG:4326");
        DATUM_NAME_TO_EPSG.put("wgs 84", "EPSG:4326");
        DATUM_NAME_TO_EPSG.put("wgs84", "EPSG:4326");
        // NAD83 family
        DATUM_NAME_TO_EPSG.put("north american datum 1983", "EPSG:4269");
        DATUM_NAME_TO_EPSG.put("nad83", "EPSG:4269");
        DATUM_NAME_TO_EPSG.put("nad83 (national spatial reference system 2011)", "EPSG:6318");
        DATUM_NAME_TO_EPSG.put("nad83 (national spatial reference system 2007)", "EPSG:4759");
        DATUM_NAME_TO_EPSG.put("nad83 (continuously operating reference station 1996)", "EPSG:6783");
        DATUM_NAME_TO_EPSG.put("nad83(cors96)", "EPSG:6783");
        DATUM_NAME_TO_EPSG.put("nad83 (high accuracy reference network)", "EPSG:4152");
        DATUM_NAME_TO_EPSG.put("nad83(harn)", "EPSG:4152");
        // NAD27
        DATUM_NAME_TO_EPSG.put("north american datum 1927", "EPSG:4267");
        DATUM_NAME_TO_EPSG.put("nad27", "EPSG:4267");
        // ETRS89 (datum name, ensemble name, and aliases)
        DATUM_NAME_TO_EPSG.put("european terrestrial reference system 1989", "EPSG:4258");
        DATUM_NAME_TO_EPSG.put("european terrestrial reference system 1989 ensemble", "EPSG:4258");
        DATUM_NAME_TO_EPSG.put("etrs89", "EPSG:4258");
        // Other commonly used datums
        DATUM_NAME_TO_EPSG.put("reseau geodesique francais 1993", "EPSG:4171");
        DATUM_NAME_TO_EPSG.put("reseau geodesique francais 1993 v1", "EPSG:4171");
        DATUM_NAME_TO_EPSG.put("geocentric datum of australia 1994", "EPSG:4283");
        DATUM_NAME_TO_EPSG.put("geocentric datum of australia 2020", "EPSG:7844");
        DATUM_NAME_TO_EPSG.put("japanese geodetic datum 2011", "EPSG:6668");
        DATUM_NAME_TO_EPSG.put("china geodetic coordinate system 2000", "EPSG:4490");
        DATUM_NAME_TO_EPSG.put("indian 1975", "EPSG:4240");
        DATUM_NAME_TO_EPSG.put("ordnance survey of great britain 1936", "EPSG:4277");
        DATUM_NAME_TO_EPSG.put("osgb 1936", "EPSG:4277");
        DATUM_NAME_TO_EPSG.put("osgb36", "EPSG:4277");
    }

    private CRSSerializer() {
        // Utility class
    }

    // ==================== PROJ String Export ====================

    /**
     * Export CRS to PROJ string format.
     * 
     * @param proj The projection to export
     * @return PROJ string (e.g., "+proj=longlat +datum=WGS84 +no_defs")
     */
    public static String toProjString(Proj proj) {
        if (proj == null) {
            return null;
        }
        return toProjString(proj.getParams());
    }

    /**
     * Export projection parameters to PROJ string format.
     */
    public static String toProjString(ProjectionParams params) {
        if (params == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        String normProj = normalizeProjName(params.projName);
        boolean isOmerc = "omerc".equals(normProj);

        // Projection name
        String projName = params.projName;
        if (projName != null) {
            // Normalize WKT method names back to PROJ short codes
            String lookupKey = projName.toLowerCase(Locale.ROOT);
            String shortCode = WKT_TO_PROJ_METHOD.get(lookupKey);
            // Some WKT method names use underscores instead of spaces (e.g., "Mercator_Variant_B")
            if (shortCode == null && projName.indexOf('_') >= 0) {
                shortCode = WKT_TO_PROJ_METHOD.get(lookupKey.replace('_', ' '));
            }
            sb.append("+proj=").append(shortCode != null ? shortCode : projName);
        }

        // UTM zone
        if (params.zone != null) {
            sb.append(" +zone=").append(params.zone);
            if (Boolean.TRUE.equals(params.utmSouth)) {
                sb.append(" +south");
            }
        }

        // Latitude of origin (convert from radians to degrees)
        if (params.lat0 != null && params.lat0 != 0.0) {
            sb.append(" +lat_0=").append(formatAngle(params.lat0 * RAD_TO_DEG));
        }

        // Central meridian
        if (params.long0 != null && params.long0 != 0.0) {
            sb.append(" +lon_0=").append(formatAngle(params.long0 * RAD_TO_DEG));
        }

        // Longitude wrapping (+lon_wrap); PROJ-string-only, no WKT/PROJJSON equivalent
        if (params.longWrap != null) {
            sb.append(" +lon_wrap=").append(formatAngle(params.longWrap * RAD_TO_DEG));
        }

        // Standard parallels: only projections that actually use them (conics). Other
        // projections can carry a lat1 defaulted from lat0, which must not be emitted.
        if (usesStandardParallels(normProj)) {
            if (params.lat1 != null) {
                sb.append(" +lat_1=").append(formatAngle(params.lat1 * RAD_TO_DEG));
            }
            if (params.lat2 != null) {
                sb.append(" +lat_2=").append(formatAngle(params.lat2 * RAD_TO_DEG));
            }
        }

        // Latitude of true scale (for Mercator)
        if (params.latTs != null && params.latTs != 0.0) {
            sb.append(" +lat_ts=").append(formatAngle(params.latTs * RAD_TO_DEG));
        }

        // Scale factor
        if (params.k0 != 1.0) {
            sb.append(" +k_0=").append(params.k0);
        }

        // False easting/northing
        if (params.x0 != 0.0) {
            sb.append(" +x_0=").append(params.x0);
        }
        if (params.y0 != 0.0) {
            sb.append(" +y_0=").append(params.y0);
        }

        // Oblique Mercator (omerc) defining parameters: either the azimuth/gamma
        // parameterization or the two-point form.
        if (isOmerc) {
            if (params.alpha != null || params.rectifiedGridAngle != null) {
                if (params.longc != null) {
                    sb.append(" +lonc=").append(formatAngle(params.longc * RAD_TO_DEG));
                }
                if (params.alpha != null) {
                    sb.append(" +alpha=").append(formatAngle(params.alpha * RAD_TO_DEG));
                }
                if (params.rectifiedGridAngle != null) {
                    sb.append(" +gamma=").append(formatAngle(params.rectifiedGridAngle * RAD_TO_DEG));
                }
                if (omercIsTypeA(params)) {
                    sb.append(" +no_uoff");
                }
            } else {
                // Two-point form requires lon_1/lat_1/lon_2/lat_2
                if (params.long1 != null) {
                    sb.append(" +lon_1=").append(formatAngle(params.long1 * RAD_TO_DEG));
                }
                if (params.lat1 != null) {
                    sb.append(" +lat_1=").append(formatAngle(params.lat1 * RAD_TO_DEG));
                }
                if (params.long2 != null) {
                    sb.append(" +lon_2=").append(formatAngle(params.long2 * RAD_TO_DEG));
                }
                if (params.lat2 != null) {
                    sb.append(" +lat_2=").append(formatAngle(params.lat2 * RAD_TO_DEG));
                }
            }
            if (Boolean.TRUE.equals(params.noRot)) {
                sb.append(" +no_rot");
            }
        }

        // Geostationary (geos) defining parameters
        if (params.h != null) {
            sb.append(" +h=").append(params.h);
        }
        if (params.sweep != null) {
            sb.append(" +sweep=").append(params.sweep);
        }
        // General Oblique Transformation (ob_tran) defining parameters
        if (params.oProj != null) {
            sb.append(" +o_proj=").append(params.oProj);
        }
        if (params.oLatP != null) {
            sb.append(" +o_lat_p=").append(formatAngle(params.oLatP * RAD_TO_DEG));
        }
        if (params.oLonP != null) {
            sb.append(" +o_lon_p=").append(formatAngle(params.oLonP * RAD_TO_DEG));
        }
        if (params.oAlpha != null) {
            sb.append(" +o_alpha=").append(formatAngle(params.oAlpha * RAD_TO_DEG));
        }
        if (params.oLonC != null) {
            sb.append(" +o_lon_c=").append(formatAngle(params.oLonC * RAD_TO_DEG));
        }
        if (params.oLatC != null) {
            sb.append(" +o_lat_c=").append(formatAngle(params.oLatC * RAD_TO_DEG));
        }
        if (params.oLon1 != null) {
            sb.append(" +o_lon_1=").append(formatAngle(params.oLon1 * RAD_TO_DEG));
        }
        if (params.oLat1 != null) {
            sb.append(" +o_lat_1=").append(formatAngle(params.oLat1 * RAD_TO_DEG));
        }
        if (params.oLon2 != null) {
            sb.append(" +o_lon_2=").append(formatAngle(params.oLon2 * RAD_TO_DEG));
        }
        if (params.oLat2 != null) {
            sb.append(" +o_lat_2=").append(formatAngle(params.oLat2 * RAD_TO_DEG));
        }
        // Tilted Perspective (tpers) defining parameters
        if (params.tilt != null) {
            sb.append(" +tilt=").append(formatAngle(params.tilt * RAD_TO_DEG));
        }
        if (params.azi != null) {
            sb.append(" +azi=").append(formatAngle(params.azi * RAD_TO_DEG));
        }

        // Ellipsoid parameters
        appendEllipsoidParams(sb, params);

        // Datum
        if (params.datumCode != null && !params.datumCode.isEmpty()) {
            sb.append(" +datum=").append(params.datumCode.toUpperCase());
        } else if (params.datum != null && params.datum.getDatumParams() != null) {
            double[] dp = params.datum.getDatumParams();
            sb.append(" +towgs84=");
            for (int i = 0; i < dp.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(formatNumber(dp[i]));
            }
        }

        // Units. Mirrors PROJ's CRS export (crs.cpp / UnitOfMeasure::exportToPROJString),
        // which branches on the unit's kind; the CRS kind is the reliable proxy here.
        // A geographic CRS's unit is angular by definition and implicit in
        // +proj=longlat — PROJ emits no unit token for any angular unit (degree,
        // grad, microradian, ...) and drops even an explicit +units= on longlat;
        // only a bare +to_meter= override is preserved verbatim. For projected and
        // geocentric CRSs +units= takes only pj_units short codes, so WKT/PROJJSON
        // authority unit names resolve by conversion factor against the unit table,
        // and an unmatched linear factor falls back to +to_meter=.
        if ("longlat".equals(normProj)) {
            if (params.units == null && params.toMeter != null && params.toMeter != 1.0) {
                sb.append(" +to_meter=").append(params.toMeter);
            }
        } else {
            String unitCode = toProjUnitCode(params.units, params.toMeter);
            if (unitCode != null && !"m".equals(unitCode)) {
                sb.append(" +units=").append(unitCode);
            } else if (unitCode == null && params.toMeter != null && params.toMeter != 1.0) {
                sb.append(" +to_meter=").append(params.toMeter);
            }
        }

        // Prime meridian offset
        if (params.fromGreenwich != null && params.fromGreenwich != 0.0) {
            sb.append(" +pm=").append(formatAngle(params.fromGreenwich * RAD_TO_DEG));
        }

        // Axis order (if not default)
        if (params.axis != null && !"enu".equals(params.axis)) {
            sb.append(" +axis=").append(params.axis);
        }

        // Flags
        if (Boolean.TRUE.equals(params.over)) {
            sb.append(" +over");
        }

        sb.append(" +no_defs");

        return sb.toString();
    }

    private static void appendEllipsoidParams(StringBuilder sb, ProjectionParams params) {
        // Try to find matching ellipsoid by parameters
        String ellpsCode = findEllipsoidCode(params.a, params.b, params.rf);
        
        if (ellpsCode != null) {
            sb.append(" +ellps=").append(ellpsCode);
        } else if (params.a > 0) {
            // Use explicit a/b or a/rf
            sb.append(" +a=").append(params.a);
            // Emit +b whenever it is known (including the sphere case a==b): "+a" alone
            // is not recognized as a sphere on re-import, so sphere-branching projections
            // (sinu, bonne, ortho, ...) would otherwise take the ellipsoidal path.
            if (params.b > 0) {
                sb.append(" +b=").append(params.b);
            } else if (params.rf > 0) {
                sb.append(" +rf=").append(params.rf);
            }
        }
    }

    // ==================== WKT1 Export ====================

    /**
     * Export CRS to OGC WKT 1 format.
     * 
     * @param proj The projection to export
     * @return WKT1 string
     */
    public static String toWkt1(Proj proj) {
        if (proj == null) {
            return null;
        }
        return toWkt1(proj.getParams());
    }

    /**
     * Export projection parameters to WKT1 format.
     */
    public static String toWkt1(ProjectionParams params) {
        if (params == null) {
            return null;
        }
        if ("ob_tran".equals(params.projName)) {
            throw new UnsupportedOperationException(
                "ob_tran has no standard WKT1 representation; use toProjString instead");
        }

        StringBuilder sb = new StringBuilder();

        boolean isGeographic = "longlat".equals(params.projName);

        if (isGeographic) {
            sb.append("GEOGCS[");
            appendWkt1GeogCS(sb, params);
            sb.append("]");
        } else {
            sb.append("PROJCS[");
            appendWkt1ProjCS(sb, params);
            sb.append("]");
        }

        return sb.toString();
    }

    private static void appendWkt1GeogCS(StringBuilder sb, ProjectionParams params) {
        // Name
        String name = getCrsName(params);
        sb.append("\"").append(name).append("\",");

        // Datum
        appendWkt1Datum(sb, params);

        // Prime Meridian
        sb.append(",PRIMEM[\"Greenwich\",");
        sb.append(params.fromGreenwich != null ? formatAngle(params.fromGreenwich * RAD_TO_DEG) : "0");
        sb.append("]");

        // Unit
        sb.append(",UNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]");
    }

    private static void appendWkt1ProjCS(StringBuilder sb, ProjectionParams params) {
        String proj = normalizeProjName(params.projName);

        // Name
        String name = getCrsName(params);
        sb.append("\"").append(name).append("\",");

        // Geographic CRS
        sb.append("GEOGCS[");
        appendWkt1GeogCS(sb, params);
        sb.append("],");

        // Projection
        String methodName = getWktMethodName(proj, params);
        sb.append("PROJECTION[\"").append(methodName).append("\"]");

        // Parameters
        appendWkt1Parameters(sb, proj, params);

        // Unit
        appendWkt1Unit(sb, params);
    }

    private static void appendWkt1Datum(StringBuilder sb, ProjectionParams params) {
        String datumName = getDatumName(params);
        sb.append("DATUM[\"").append(datumName).append("\",");

        // Spheroid
        String ellpsName = getEllipsoidName(params);
        sb.append("SPHEROID[\"").append(ellpsName).append("\",");
        sb.append(params.a).append(",");
        sb.append(params.rf > 0 ? params.rf : 0);
        sb.append("]");

        // TOWGS84 if present
        if (params.datum != null && params.datum.getDatumParams() != null) {
            double[] dp = params.datum.getDatumParams();
            if (dp.length >= 3) {
                sb.append(",TOWGS84[");
                for (int i = 0; i < dp.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(formatNumber(dp[i]));
                }
                sb.append("]");
            }
        }

        sb.append("]");
    }

    private static void appendWkt1Parameters(StringBuilder sb, String proj, ProjectionParams params) {

        // Latitude of origin
        if (params.lat0 != null) {
            sb.append(",PARAMETER[\"latitude_of_origin\",");
            sb.append(formatAngle(params.lat0 * RAD_TO_DEG)).append("]");
        } else if (usesLatTsAsStandardParallel(proj, params)) {
            // Emit explicit latitude_of_origin=0 so that on re-import,
            // standard_parallel_1 doesn't get misinterpreted as lat0
            sb.append(",PARAMETER[\"latitude_of_origin\",0]");
        }

        // Central meridian
        if (params.long0 != null) {
            sb.append(",PARAMETER[\"central_meridian\",");
            sb.append(formatAngle(params.long0 * RAD_TO_DEG)).append("]");
        }

        // Oblique Mercator defining parameters (lonc/alpha/gamma)
        if ("omerc".equals(proj)) {
            appendWkt1OmercParams(sb, params);
        } else if (usesLatTsAsStandardParallel(proj, params)) {
            // Projections using latTs: emit it as standard_parallel_1
            sb.append(",PARAMETER[\"standard_parallel_1\",");
            sb.append(formatAngle(params.latTs * RAD_TO_DEG)).append("]");
        } else if (usesStandardParallels(proj)) {
            if (params.lat1 != null) {
                sb.append(",PARAMETER[\"standard_parallel_1\",");
                sb.append(formatAngle(params.lat1 * RAD_TO_DEG)).append("]");
            }
            if (params.lat2 != null) {
                sb.append(",PARAMETER[\"standard_parallel_2\",");
                sb.append(formatAngle(params.lat2 * RAD_TO_DEG)).append("]");
            }
        }

        // Geostationary satellite height
        if ("geos".equals(proj) && params.h != null) {
            sb.append(",PARAMETER[\"satellite_height\",").append(params.h).append("]");
        }

        // Scale factor
        if (params.k0 != 1.0) {
            sb.append(",PARAMETER[\"scale_factor\",").append(params.k0).append("]");
        }

        // False easting/northing
        if (params.x0 != 0.0) {
            sb.append(",PARAMETER[\"false_easting\",").append(params.x0).append("]");
        }
        if (params.y0 != 0.0) {
            sb.append(",PARAMETER[\"false_northing\",").append(params.y0).append("]");
        }
    }

    private static void appendWkt1OmercParams(StringBuilder sb, ProjectionParams params) {
        if (params.longc != null) {
            sb.append(",PARAMETER[\"longitude_of_center\",")
              .append(formatAngle(params.longc * RAD_TO_DEG)).append("]");
        }
        if (params.alpha != null) {
            sb.append(",PARAMETER[\"azimuth\",")
              .append(formatAngle(params.alpha * RAD_TO_DEG)).append("]");
        }
        if (params.rectifiedGridAngle != null) {
            sb.append(",PARAMETER[\"rectified_grid_angle\",")
              .append(formatAngle(params.rectifiedGridAngle * RAD_TO_DEG)).append("]");
        }
    }

    private static void appendWkt1Unit(StringBuilder sb, ProjectionParams params) {
        String unitName = "metre";
        double toMeter = 1.0;

        if (params.units != null) {
            unitName = getUnitName(params.units);
            Double tm = Units.getToMeter(params.units);
            if (tm != null) {
                toMeter = tm;
            }
        } else if (params.toMeter != null) {
            toMeter = params.toMeter;
        }

        sb.append(",UNIT[\"").append(unitName).append("\",").append(toMeter).append("]");
    }

    // ==================== WKT2 Export ====================

    /**
     * Export CRS to ISO 19162 WKT 2 format (2019 revision).
     * 
     * @param proj The projection to export
     * @return WKT2 string
     */
    public static String toWkt2(Proj proj) {
        if (proj == null) {
            return null;
        }
        return toWkt2(proj.getParams());
    }

    /**
     * Export projection parameters to WKT2 format.
     */
    public static String toWkt2(ProjectionParams params) {
        if (params == null) {
            return null;
        }
        if ("ob_tran".equals(params.projName)) {
            throw new UnsupportedOperationException(
                "ob_tran has no standard WKT2 representation; use toProjString instead");
        }

        StringBuilder sb = new StringBuilder();

        boolean isGeographic = "longlat".equals(params.projName);

        if (isGeographic) {
            sb.append("GEOGCRS[");
            appendWkt2GeogCRS(sb, params);
            sb.append("]");
        } else {
            sb.append("PROJCRS[");
            appendWkt2ProjCRS(sb, params);
            sb.append("]");
        }

        return sb.toString();
    }

    private static void appendWkt2GeogCRS(StringBuilder sb, ProjectionParams params) {
        String name = getCrsName(params);
        sb.append("\"").append(name).append("\",");

        // Datum
        appendWkt2Datum(sb, params);

        // Coordinate System
        sb.append(",CS[ellipsoidal,2],");
        sb.append("AXIS[\"latitude\",north,ORDER[1],ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]],");
        sb.append("AXIS[\"longitude\",east,ORDER[2],ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
    }

    private static void appendWkt2ProjCRS(StringBuilder sb, ProjectionParams params) {
        String proj = normalizeProjName(params.projName);

        String name = getCrsName(params);
        sb.append("\"").append(name).append("\",");

        // Base CRS (geographic)
        String baseCrsName = getDatumName(params);
        sb.append("BASEGEOGCRS[\"").append(baseCrsName).append("\",");
        appendWkt2Datum(sb, params);
        sb.append("],");

        // Conversion
        String methodName = getWktMethodName(proj, params);
        sb.append("CONVERSION[\"unnamed\",");
        sb.append("METHOD[\"").append(methodName).append("\"]");
        appendWkt2Parameters(sb, proj, params);
        sb.append("],");

        // Coordinate System
        sb.append("CS[Cartesian,2],");
        sb.append("AXIS[\"easting\",east,ORDER[1]],");
        sb.append("AXIS[\"northing\",north,ORDER[2]],");
        appendWkt2Unit(sb, params);
    }

    private static void appendWkt2Datum(StringBuilder sb, ProjectionParams params) {
        String datumName = getDatumName(params);
        sb.append("DATUM[\"").append(datumName).append("\",");

        // Ellipsoid
        String ellpsName = getEllipsoidName(params);
        sb.append("ELLIPSOID[\"").append(ellpsName).append("\",");
        sb.append(params.a).append(",");
        sb.append(params.rf > 0 ? params.rf : 0);
        sb.append(",LENGTHUNIT[\"metre\",1]]");

        sb.append("]");

        // Prime Meridian
        sb.append(",PRIMEM[\"Greenwich\",");
        sb.append(params.fromGreenwich != null ? formatAngle(params.fromGreenwich * RAD_TO_DEG) : "0");
        sb.append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
    }

    private static void appendWkt2Parameters(StringBuilder sb, String proj, ProjectionParams params) {

        boolean isOmerc = "omerc".equals(proj);

        // Latitude of natural origin (omerc emits "Latitude of projection centre" below)
        if (params.lat0 != null && !isOmerc) {
            sb.append(",PARAMETER[\"Latitude of natural origin\",");
            sb.append(formatAngle(params.lat0 * RAD_TO_DEG));
            sb.append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
        } else if (usesLatTsAsStandardParallel(proj, params)) {
            // Emit explicit origin=0 so re-import doesn't confuse standard_parallel with lat0
            sb.append(",PARAMETER[\"Latitude of natural origin\",0");
            sb.append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
        }

        // Longitude of natural origin (omerc uses the projection-centre name below)
        if (params.long0 != null && !isOmerc) {
            sb.append(",PARAMETER[\"Longitude of natural origin\",");
            sb.append(formatAngle(params.long0 * RAD_TO_DEG));
            sb.append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
        }

        // Oblique Mercator defining parameters. WKT2 is parsed via WKT2->PROJJSON,
        // so use the EPSG projection-centre parameter names the PROJJSON reader knows.
        if (isOmerc) {
            if (params.lat0 != null) {
                sb.append(",PARAMETER[\"Latitude of projection centre\",")
                  .append(formatAngle(params.lat0 * RAD_TO_DEG))
                  .append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
            }
            if (params.longc != null) {
                sb.append(",PARAMETER[\"Longitude of projection centre\",")
                  .append(formatAngle(params.longc * RAD_TO_DEG))
                  .append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
            }
            if (params.alpha != null) {
                sb.append(",PARAMETER[\"Azimuth at projection centre\",")
                  .append(formatAngle(params.alpha * RAD_TO_DEG))
                  .append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
            }
            if (params.rectifiedGridAngle != null) {
                sb.append(",PARAMETER[\"Angle from Rectified to Skew Grid\",")
                  .append(formatAngle(params.rectifiedGridAngle * RAD_TO_DEG))
                  .append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
            }
        }

        // Standard parallels
        if (usesLatTsAsStandardParallel(proj, params)) {
            // Polar stere uses "Latitude of standard parallel" (singular);
            // merc/cea/eqc use "Latitude of 1st standard parallel" (matching PROJ)
            String paramName = isPolarStereographic(proj, params)
                    ? "Latitude of standard parallel"
                    : "Latitude of 1st standard parallel";
            sb.append(",PARAMETER[\"" + paramName + "\",");
            sb.append(formatAngle(params.latTs * RAD_TO_DEG));
            sb.append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
        } else if (usesStandardParallels(proj)) {
            if (params.lat1 != null) {
                sb.append(",PARAMETER[\"Latitude of 1st standard parallel\",");
                sb.append(formatAngle(params.lat1 * RAD_TO_DEG));
                sb.append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
            }
            if (params.lat2 != null) {
                sb.append(",PARAMETER[\"Latitude of 2nd standard parallel\",");
                sb.append(formatAngle(params.lat2 * RAD_TO_DEG));
                sb.append(",ANGLEUNIT[\"degree\",").append(DEG_TO_RAD_STR).append("]]");
            }
        }

        // Geostationary satellite height (name matches PROJ's WKT2 output)
        if ("geos".equals(proj) && params.h != null) {
            sb.append(",PARAMETER[\"Satellite Height\",");
            sb.append(params.h);
            sb.append(",LENGTHUNIT[\"metre\",1]]");
        }

        // Scale factor
        if (params.k0 != 1.0) {
            sb.append(",PARAMETER[\"Scale factor at natural origin\",");
            sb.append(params.k0).append(",SCALEUNIT[\"unity\",1]]");
        }

        // False easting/northing
        if (params.x0 != 0.0) {
            sb.append(",PARAMETER[\"False easting\",");
            sb.append(params.x0);
            appendWkt2LengthUnit(sb, params);
            sb.append("]");
        }
        if (params.y0 != 0.0) {
            sb.append(",PARAMETER[\"False northing\",");
            sb.append(params.y0);
            appendWkt2LengthUnit(sb, params);
            sb.append("]");
        }
    }

    private static void appendWkt2Unit(StringBuilder sb, ProjectionParams params) {
        String unitName = "metre";
        double toMeter = 1.0;

        if (params.units != null) {
            unitName = getUnitName(params.units);
            Double tm = Units.getToMeter(params.units);
            if (tm != null) {
                toMeter = tm;
            }
        } else if (params.toMeter != null) {
            toMeter = params.toMeter;
        }

        sb.append("LENGTHUNIT[\"").append(unitName).append("\",").append(toMeter).append("]");
    }

    private static void appendWkt2LengthUnit(StringBuilder sb, ProjectionParams params) {
        double toMeter = 1.0;
        String unitName = "metre";
        
        if (params.units != null) {
            unitName = getUnitName(params.units);
            Double tm = Units.getToMeter(params.units);
            if (tm != null) {
                toMeter = tm;
            }
        } else if (params.toMeter != null) {
            toMeter = params.toMeter;
        }
        
        sb.append(",LENGTHUNIT[\"").append(unitName).append("\",").append(toMeter).append("]");
    }

    // ==================== PROJJSON Export ====================

    /**
     * Export CRS to PROJJSON format.
     * 
     * @param proj The projection to export
     * @return PROJJSON string (pretty-printed)
     */
    public static String toProjJson(Proj proj) {
        return toProjJson(proj, true);
    }

    /**
     * Export CRS to PROJJSON format.
     * 
     * @param proj The projection to export
     * @param prettyPrint Whether to pretty-print the JSON
     * @return PROJJSON string
     */
    public static String toProjJson(Proj proj, boolean prettyPrint) {
        if (proj == null) {
            return null;
        }
        return toProjJson(proj.getParams(), prettyPrint);
    }

    /**
     * Export projection parameters to PROJJSON format.
     */
    public static String toProjJson(ProjectionParams params, boolean prettyPrint) {
        if (params == null) {
            return null;
        }
        if ("ob_tran".equals(params.projName)) {
            throw new UnsupportedOperationException(
                "ob_tran has no standard PROJJSON representation; use toProjString instead");
        }

        Map<String, Object> json = toProjJsonMap(params);
        
        Gson gson = prettyPrint 
            ? new GsonBuilder().setPrettyPrinting().create()
            : new Gson();
            
        return gson.toJson(json);
    }

    /**
     * Export projection parameters to a PROJJSON Map.
     */
    public static Map<String, Object> toProjJsonMap(ProjectionParams params) {
        if (params == null) {
            return null;
        }

        Map<String, Object> json = new LinkedHashMap<>();

        boolean isGeographic = "longlat".equals(params.projName);

        if (isGeographic) {
            json.put("type", "GeographicCRS");
            json.put("name", getCrsName(params));
            json.put("datum", buildProjJsonDatum(params));
            json.put("coordinate_system", buildProjJsonGeogCS());
        } else if (isGeocentric(params)) {
            // Geocentric CRSs are a GeodeticCRS with a Cartesian coordinate system in
            // PROJJSON (e.g. EPSG:4978), not a ProjectedCRS with a conversion.
            json.put("type", "GeodeticCRS");
            json.put("name", getCrsName(params));
            json.put("datum", buildProjJsonDatum(params));
            json.put("coordinate_system", buildProjJsonGeocentricCS(params));
        } else {
            json.put("type", "ProjectedCRS");
            json.put("name", getCrsName(params));
            json.put("base_crs", buildProjJsonBaseCRS(params));
            json.put("conversion", buildProjJsonConversion(params));
            json.put("coordinate_system", buildProjJsonProjCS(params));
        }

        // Add "id" field when the authority is known
        String[] authority = toAuthority(params);
        if (authority != null) {
            Map<String, Object> id = new LinkedHashMap<>();
            id.put("authority", authority[0]);
            try {
                id.put("code", Integer.parseInt(authority[1]));
            } catch (NumberFormatException e) {
                id.put("code", authority[1]);
            }
            json.put("id", id);
        }

        return json;
    }

    /**
     * Canonical geocentric identity, as in Transform: matches any registered alias of
     * the Geocentric projection, not just the raw "geocent" spelling.
     */
    private static boolean isGeocentric(ProjectionParams params) {
        if (params.projName == null) {
            return false;
        }
        String n = params.projName.toLowerCase(Locale.ROOT);
        return "geocent".equals(n) || "geocentric".equals(n);
    }

    private static Map<String, Object> buildProjJsonGeocentricCS(ProjectionParams params) {
        // The linear unit lives on each axis. Well-known metre is the bare-string
        // form (as PROJ emits for EPSG:4978); other units carry their to-metre factor.
        Object unit;
        if (params.toMeter == null || params.toMeter == 1.0) {
            unit = "metre";
        } else {
            Map<String, Object> unitMap = new LinkedHashMap<>();
            unitMap.put("type", "LinearUnit");
            unitMap.put("name", params.units != null ? params.units : "unknown");
            unitMap.put("conversion_factor", params.toMeter);
            unit = unitMap;
        }
        Map<String, Object> cs = new LinkedHashMap<>();
        cs.put("subtype", "Cartesian");
        List<Map<String, Object>> axes = new ArrayList<>();
        axes.add(buildGeocentricAxis("Geocentric X", "X", "geocentricX", unit));
        axes.add(buildGeocentricAxis("Geocentric Y", "Y", "geocentricY", unit));
        axes.add(buildGeocentricAxis("Geocentric Z", "Z", "geocentricZ", unit));
        cs.put("axis", axes);
        return cs;
    }

    private static Map<String, Object> buildGeocentricAxis(
            String name, String abbreviation, String direction, Object unit) {
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("name", name);
        axis.put("abbreviation", abbreviation);
        axis.put("direction", direction);
        axis.put("unit", unit);
        return axis;
    }

    private static Map<String, Object> buildProjJsonDatum(ProjectionParams params) {
        Map<String, Object> datum = new LinkedHashMap<>();
        datum.put("type", "GeodeticReferenceFrame");
        datum.put("name", getDatumName(params));
        datum.put("ellipsoid", buildProjJsonEllipsoid(params));
        return datum;
    }

    private static Map<String, Object> buildProjJsonEllipsoid(ProjectionParams params) {
        Map<String, Object> ellipsoid = new LinkedHashMap<>();
        ellipsoid.put("name", getEllipsoidName(params));
        ellipsoid.put("semi_major_axis", params.a);
        if (params.rf > 0) {
            ellipsoid.put("inverse_flattening", params.rf);
        } else if (params.b > 0) {
            ellipsoid.put("semi_minor_axis", params.b);
        }
        return ellipsoid;
    }

    private static Map<String, Object> buildProjJsonBaseCRS(ProjectionParams params) {
        Map<String, Object> baseCrs = new LinkedHashMap<>();
        baseCrs.put("type", "GeographicCRS");
        baseCrs.put("name", getDatumName(params));
        baseCrs.put("datum", buildProjJsonDatum(params));
        baseCrs.put("coordinate_system", buildProjJsonGeogCS());
        return baseCrs;
    }

    private static Map<String, Object> buildProjJsonConversion(ProjectionParams params) {
        String proj = normalizeProjName(params.projName);

        Map<String, Object> conversion = new LinkedHashMap<>();
        conversion.put("name", "unnamed");
        
        Map<String, Object> method = new LinkedHashMap<>();
        method.put("name", getWktMethodName(proj, params));
        conversion.put("method", method);
        
        List<Map<String, Object>> parameters = new ArrayList<>();

        // Oblique Mercator: use projection-centre parameter names
        if ("omerc".equals(proj)) {
            if (params.lat0 != null) {
                parameters.add(buildProjJsonParam("Latitude of projection centre",
                    params.lat0 * RAD_TO_DEG, "degree"));
            }
            if (params.longc != null) {
                parameters.add(buildProjJsonParam("Longitude of projection centre",
                    params.longc * RAD_TO_DEG, "degree"));
            }
            if (params.alpha != null) {
                parameters.add(buildProjJsonParam("Azimuth at projection centre",
                    params.alpha * RAD_TO_DEG, "degree"));
            }
            if (params.rectifiedGridAngle != null) {
                parameters.add(buildProjJsonParam("Angle from Rectified to Skew Grid",
                    params.rectifiedGridAngle * RAD_TO_DEG, "degree"));
            }
            if (params.k0 != 1.0) {
                parameters.add(buildProjJsonParam("Scale factor at projection centre", params.k0, null));
            }
            if (params.x0 != 0.0) {
                parameters.add(buildProjJsonParam("Easting at projection centre", params.x0, "metre"));
            }
            if (params.y0 != 0.0) {
                parameters.add(buildProjJsonParam("Northing at projection centre", params.y0, "metre"));
            }
            conversion.put("parameters", parameters);
            return conversion;
        }

        // Add parameters
        if (params.lat0 != null) {
            parameters.add(buildProjJsonParam("Latitude of natural origin",
                params.lat0 * RAD_TO_DEG, "degree"));
        } else if (usesLatTsAsStandardParallel(proj, params)) {
            // Emit explicit origin=0 so re-import doesn't confuse standard_parallel with lat0
            parameters.add(buildProjJsonParam("Latitude of natural origin", 0.0, "degree"));
        }
        if (params.long0 != null) {
            parameters.add(buildProjJsonParam("Longitude of natural origin", 
                params.long0 * RAD_TO_DEG, "degree"));
        }
        if (usesLatTsAsStandardParallel(proj, params)) {
            // Polar stere: "Latitude of standard parallel"; merc/cea/eqc: "Latitude of 1st standard parallel"
            String paramName = isPolarStereographic(proj, params)
                    ? "Latitude of standard parallel"
                    : "Latitude of 1st standard parallel";
            parameters.add(buildProjJsonParam(paramName, 
                params.latTs * RAD_TO_DEG, "degree"));
        } else if (usesStandardParallels(proj)) {
            if (params.lat1 != null) {
                parameters.add(buildProjJsonParam("Latitude of 1st standard parallel", 
                    params.lat1 * RAD_TO_DEG, "degree"));
            }
            if (params.lat2 != null) {
                parameters.add(buildProjJsonParam("Latitude of 2nd standard parallel",
                    params.lat2 * RAD_TO_DEG, "degree"));
            }
        }
        if ("geos".equals(proj) && params.h != null) {
            parameters.add(buildProjJsonParam("Satellite Height", params.h, "metre"));
        }
        if (params.k0 != 1.0) {
            parameters.add(buildProjJsonParam("Scale factor at natural origin", params.k0, null));
        }
        if (params.x0 != 0.0) {
            parameters.add(buildProjJsonParam("False easting", params.x0, "metre"));
        }
        if (params.y0 != 0.0) {
            parameters.add(buildProjJsonParam("False northing", params.y0, "metre"));
        }
        
        conversion.put("parameters", parameters);
        return conversion;
    }

    private static Map<String, Object> buildProjJsonParam(String name, double value, String unitName) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", name);
        param.put("value", value);
        if (unitName != null) {
            Map<String, Object> unit = new LinkedHashMap<>();
            if ("degree".equals(unitName)) {
                unit.put("type", "AngularUnit");
                unit.put("name", "degree");
                unit.put("conversion_factor", DEG_TO_RAD);
            } else {
                unit.put("type", "LinearUnit");
                unit.put("name", unitName);
                unit.put("conversion_factor", 1.0);
            }
            param.put("unit", unit);
        }
        return param;
    }

    private static Map<String, Object> buildProjJsonGeogCS() {
        Map<String, Object> cs = new LinkedHashMap<>();
        cs.put("subtype", "ellipsoidal");
        cs.put("axis", Arrays.asList(
            createAxis("Latitude", "north", "degree"),
            createAxis("Longitude", "east", "degree")
        ));
        return cs;
    }

    private static Map<String, Object> buildProjJsonProjCS(ProjectionParams params) {
        Map<String, Object> cs = new LinkedHashMap<>();
        cs.put("subtype", "Cartesian");
        
        String unitName = params.units != null ? getUnitName(params.units) : "metre";
        cs.put("axis", Arrays.asList(
            createAxis("Easting", "east", unitName),
            createAxis("Northing", "north", unitName)
        ));
        return cs;
    }

    private static Map<String, Object> createAxis(String name, String direction, String unitName) {
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("name", name);
        axis.put("direction", direction);
        
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("name", unitName);
        if ("degree".equals(unitName)) {
            unit.put("type", "AngularUnit");
            unit.put("conversion_factor", DEG_TO_RAD);
        } else {
            unit.put("type", "LinearUnit");
            Double toMeter = Units.getToMeter(unitName);
            unit.put("conversion_factor", toMeter != null ? toMeter : 1.0);
        }
        axis.put("unit", unit);
        
        return axis;
    }

    // ==================== Authority / EPSG Code Lookup ====================

    /**
     * Attempt to identify the authority and code for a CRS.
     *
     * <p>Identification strategy (in order):</p>
     * <ol>
     *   <li>If srsCode already contains "AUTHORITY:code" (e.g., from PROJJSON id field
     *       or direct "EPSG:4326" input), return it directly.</li>
     *   <li>If a well-known datum name is present, look it up in the datum name table.</li>
     *   <li>Fall back to parameter matching against known EPSG definitions.</li>
     * </ol>
     *
     * <p>Similar to pyproj's {@code CRS.to_authority()} method.</p>
     *
     * @param proj The projection to identify
     * @return String array {@code {"authority", "code"}} (e.g., {"EPSG", "4326"}), or null if not found
     */
    public static String[] toAuthority(Proj proj) {
        if (proj == null) {
            return null;
        }
        return toAuthority(proj.getParams());
    }

    /**
     * Attempt to identify the authority and code for projection parameters.
     *
     * @param params The projection parameters
     * @return String array {@code {"authority", "code"}}, or null if not found
     */
    public static String[] toAuthority(ProjectionParams params) {
        if (params == null) {
            return null;
        }

        // Phase 1: Check if srsCode already contains authority:code
        if (params.srsCode != null) {
            String[] parsed = parseAuthorityCode(params.srsCode);
            if (parsed != null) {
                return parsed;
            }
        }

        // Phase 2: Try datum name lookup (like pyproj)
        String datumEpsg = lookupByDatumName(params);
        if (datumEpsg != null) {
            String[] parsed = parseAuthorityCode(datumEpsg);
            if (parsed != null) {
                return parsed;
            }
        }

        // Phase 3: Fall back to parameter matching (EPSG only)
        String epsg = matchByParameters(params);
        if (epsg != null) {
            String[] parsed = parseAuthorityCode(epsg);
            if (parsed != null) {
                return parsed;
            }
        }

        return null;
    }

    /**
     * Attempt to identify the EPSG code for a CRS.
     *
     * <p>This is a convenience method that delegates to {@link #toAuthority(Proj)}
     * and returns "EPSG:code" only when the authority is EPSG.</p>
     *
     * <p>Similar to pyproj's {@code CRS.to_epsg()} method.</p>
     *
     * @param proj The projection to identify
     * @return EPSG code (e.g., "EPSG:4326") or null if not found or non-EPSG authority
     */
    public static String toEpsgCode(Proj proj) {
        if (proj == null) {
            return null;
        }
        return toEpsgCode(proj.getParams());
    }

    /**
     * Attempt to identify the EPSG code for projection parameters.
     *
     * @param params The projection parameters
     * @return EPSG code (e.g., "EPSG:4326") or null if not found or non-EPSG authority
     */
    public static String toEpsgCode(ProjectionParams params) {
        String[] authority = toAuthority(params);
        if (authority != null && "EPSG".equalsIgnoreCase(authority[0])) {
            return "EPSG:" + authority[1];
        }
        return null;
    }

    /**
     * Parse an "authority:code" string into a String array.
     *
     * @param authorityCode String like "EPSG:4326" or "IAU:49900"
     * @return String array {"authority", "code"} or null if not parseable
     */
    private static String[] parseAuthorityCode(String authorityCode) {
        if (authorityCode == null) {
            return null;
        }
        int colonIdx = authorityCode.indexOf(':');
        if (colonIdx > 0 && colonIdx < authorityCode.length() - 1) {
            String authority = authorityCode.substring(0, colonIdx).trim();
            String code = authorityCode.substring(colonIdx + 1).trim();
            if (!authority.isEmpty() && !code.isEmpty()) {
                return new String[]{authority.toUpperCase(), code};
            }
        }
        return null;
    }

    /**
     * Try to identify a geographic CRS EPSG code from the datum name.
     * Mirrors pyproj's behavior of matching well-known datum names.
     */
    private static String lookupByDatumName(ProjectionParams params) {
        // Only apply datum name lookup for geographic CRS.
        // Projected CRS (e.g., UTM) also have a datum but their EPSG code
        // depends on projection parameters, not just datum.
        String normalizedProj = normalizeProjName(params.projName);
        if (normalizedProj != null && !"longlat".equals(normalizedProj)) {
            return null;
        }

        // Try datumCode (set from PROJJSON datum.name or PROJ +datum flag)
        if (params.datumCode != null) {
            String normalized = params.datumCode.toLowerCase(Locale.ROOT).trim();
            String epsg = DATUM_NAME_TO_EPSG.get(normalized);
            if (epsg != null) {
                return epsg;
            }
            // Also try with underscores replaced by spaces
            epsg = DATUM_NAME_TO_EPSG.get(normalized.replace('_', ' '));
            if (epsg != null) {
                return epsg;
            }
        }
        return null;
    }

    /**
     * Match CRS parameters against known EPSG definitions.
     * This is the existing parameter-matching approach, used as a fallback.
     */
    private static String matchByParameters(ProjectionParams params) {
        // Initialize global definitions
        Defs.globals();

        // Check common codes
        String[] commonCodes = {
            "EPSG:4326", "EPSG:4269", "EPSG:3857"
        };

        for (String code : commonCodes) {
            if (matchesDefinition(params, code)) {
                return code;
            }
        }

        // Check UTM zones
        String normalizedProj = normalizeProjName(params.projName);
        if ("tmerc".equals(normalizedProj) || "utm".equals(normalizedProj)) {
            Integer zone = params.zone;
            if (zone == null && params.long0 != null) {
                // Calculate zone from central meridian
                double lon = params.long0 * RAD_TO_DEG;
                zone = (int) Math.floor((lon + 180) / 6) + 1;
            }

            if (zone != null && zone >= 1 && zone <= 60) {
                boolean isSouth = Boolean.TRUE.equals(params.utmSouth) ||
                    params.y0 > 5000000;

                String epsgCode = isSouth
                    ? "EPSG:" + (32700 + zone)
                    : "EPSG:" + (32600 + zone);

                if (matchesDefinition(params, epsgCode)) {
                    return epsgCode;
                }
            }
        }

        return null;
    }

    /**
     * Normalize a projection name to its canonical PROJ short form.
     * E.g., "Transverse Mercator" -> "tmerc", "longlat" -> "longlat"
     */
    private static String normalizeProjName(String projName) {
        if (projName == null) {
            return null;
        }
        // Fast path: most common case is already a lowercase PROJ short name
        // (e.g. "tmerc", "lcc", "stere") — avoid toLowerCase() allocation.
        if (PROJ_TO_WKT_METHOD.containsKey(projName)) {
            return projName;
        }
        String lower = projName.toLowerCase(Locale.ROOT).trim();
        // Check again after lowercasing (handles "Tmerc", " lcc ", etc.)
        if (PROJ_TO_WKT_METHOD.containsKey(lower)) {
            return lower;
        }
        // Try reverse lookup: WKT name -> PROJ name
        String projShort = WKT_TO_PROJ_METHOD.get(lower);
        if (projShort != null) {
            return projShort;
        }
        // Try with underscores -> spaces
        projShort = WKT_TO_PROJ_METHOD.get(lower.replace('_', ' '));
        if (projShort != null) {
            return projShort;
        }
        return lower;
    }

    private static boolean matchesDefinition(ProjectionParams params, String code) {
        try {
            Proj ref = new Proj(code);
            ProjectionParams refParams = ref.getParams();

            // Compare projection names (normalized to handle aliases)
            String normalizedInput = normalizeProjName(params.projName);
            String normalizedRef = normalizeProjName(refParams.projName);
            if (!Objects.equals(normalizedInput, normalizedRef)) {
                return false;
            }

            // Check datum compatibility.
            // If the reference has a datumCode, the input must have a matching one.
            // This prevents e.g. GRS 1980 + "Unknown datum" from matching NAD83.
            // "none" means no datum was specified (common for sphere-based CRS like EPSG:3857).
            if (refParams.datumCode != null && !refParams.datumCode.isEmpty()
                    && !"none".equalsIgnoreCase(refParams.datumCode)) {
                if (params.datumCode == null || params.datumCode.isEmpty()
                        || "none".equalsIgnoreCase(params.datumCode)) {
                    return false;
                }
                if (!datumCodesMatch(params.datumCode, refParams.datumCode)) {
                    return false;
                }
            }

            // Check semi-major axis
            if (Math.abs(params.a - refParams.a) > 0.1) {
                return false;
            }

            // Check inverse flattening (distinguishes GRS 1980 from WGS 84)
            if (params.rf != 0 && refParams.rf != 0) {
                if (Math.abs(params.rf - refParams.rf) > 1e-6) {
                    return false;
                }
            } else if (Math.abs(params.b - refParams.b) > 0.01) {
                // Fall back to semi-minor axis comparison
                return false;
            }

            // Check lat0/long0
            if (!closeEnough(params.lat0, refParams.lat0, 1e-9)) {
                return false;
            }
            if (!closeEnough(params.long0, refParams.long0, 1e-9)) {
                return false;
            }

            // Check scale factor
            if (Math.abs(params.k0 - refParams.k0) > 1e-9) {
                return false;
            }

            // Check false easting/northing
            if (Math.abs(params.x0 - refParams.x0) > 0.01) {
                return false;
            }
            if (Math.abs(params.y0 - refParams.y0) > 0.01) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean closeEnough(Double a, Double b, double tolerance) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Math.abs(a - b) < tolerance;
    }

    /**
     * Compare two datum codes that may use different representations.
     * Handles PROJ datum codes ("wgs84"), PROJJSON datum names ("World Geodetic System 1984"),
     * base_crs id strings ("EPSG_4326"), and short aliases ("WGS 84", "NAD83").
     *
     * <p>If both datum codes resolve to known EPSG geographic CRS codes, the comparison is
     * authoritative. If one or both cannot be resolved, this returns {@code false} to reject
     * the match, consistent with pyproj's behavior (unknown datum drops confidence below
     * the identification threshold).</p>
     */
    private static boolean datumCodesMatch(String dc1, String dc2) {
        // Fast path: direct string match
        if (dc1.equalsIgnoreCase(dc2)) {
            return true;
        }

        // Normalize both to EPSG geographic CRS codes
        String epsg1 = normalizeDatumToEpsg(dc1);
        String epsg2 = normalizeDatumToEpsg(dc2);

        if (epsg1 != null && epsg2 != null) {
            // Both resolved to known datums — authoritative comparison
            return epsg1.equals(epsg2);
        }

        // At least one datum code is unknown — reject the match.
        // This matches pyproj behavior: unknown datum names drop confidence
        // below the identification threshold (60% < 70%), so to_epsg() returns None.
        return false;
    }

    /**
     * Normalize a datum code/name to an EPSG geographic CRS code for comparison.
     * Tries DATUM_NAME_TO_EPSG directly, then the Datum registry, then the EPSG_ prefix pattern.
     *
     * @return An EPSG code like "EPSG:4326", or null if the datum cannot be resolved
     */
    private static String normalizeDatumToEpsg(String datumCode) {
        if (datumCode == null || datumCode.isEmpty()) {
            return null;
        }
        String lower = datumCode.toLowerCase(Locale.ROOT).trim();

        // Try DATUM_NAME_TO_EPSG directly
        String epsg = DATUM_NAME_TO_EPSG.get(lower);
        if (epsg != null) {
            return epsg;
        }
        // Try with underscores → spaces
        epsg = DATUM_NAME_TO_EPSG.get(lower.replace('_', ' '));
        if (epsg != null) {
            return epsg;
        }

        // Try Datum registry: look up the datum object, then its name/code in DATUM_NAME_TO_EPSG
        Datum d = Datum.get(datumCode);
        if (d != null) {
            if (d.getDatumName() != null) {
                epsg = DATUM_NAME_TO_EPSG.get(d.getDatumName().toLowerCase(Locale.ROOT));
                if (epsg != null) {
                    return epsg;
                }
                epsg = DATUM_NAME_TO_EPSG.get(d.getDatumName().toLowerCase(Locale.ROOT).replace('_', ' '));
                if (epsg != null) {
                    return epsg;
                }
            }
            epsg = DATUM_NAME_TO_EPSG.get(d.getCode().toLowerCase(Locale.ROOT));
            if (epsg != null) {
                return epsg;
            }
        }

        // Handle "EPSG_XXXX" format from base_crs id
        if (lower.startsWith("epsg_")) {
            return "EPSG:" + datumCode.substring(5);
        }

        return null;
    }

    // ==================== Helper Methods ====================

    private static String getCrsName(ProjectionParams params) {
        if (params.srsCode != null && !params.srsCode.isEmpty()) {
            return params.srsCode;
        }
        
        String projName = params.projName != null ? params.projName : "Unknown";
        if ("longlat".equals(projName)) {
            String datumName = params.datumCode != null ? params.datumCode.toUpperCase() : "Unknown";
            return datumName + " (Geographic)";
        }
        
        return getWktMethodName(projName);
    }

    private static String getDatumName(ProjectionParams params) {
        if (params.datumCode != null) {
            Datum datum = Datum.get(params.datumCode);
            if (datum != null && datum.getDatumName() != null) {
                return datum.getDatumName();
            }
            return params.datumCode.toUpperCase();
        }
        return "Unknown";
    }

    private static String getEllipsoidName(ProjectionParams params) {
        String code = findEllipsoidCode(params.a, params.b, params.rf);
        if (code != null) {
            Ellipsoid ellps = Ellipsoid.get(code);
            if (ellps != null) {
                return ellps.getEllipseName();
            }
            return code;
        }
        return "Custom";
    }

    private static String findEllipsoidCode(double a, double b, double rf) {
        // Check all registered ellipsoids (LinkedHashMap ensures deterministic order)
        for (Map.Entry<String, Ellipsoid> entry : Ellipsoid.getAll().entrySet()) {
            Ellipsoid ellps = entry.getValue();
            if (Math.abs(ellps.getA() - a) < 0.1 && 
                (Math.abs(ellps.getB() - b) < 0.1 || Math.abs(ellps.getRf() - rf) < 1e-6)) {
                return ellps.getCode();
            }
        }
        return null;
    }

    private static String getWktMethodName(String projName) {
        String method = PROJ_TO_WKT_METHOD.get(projName);
        return method != null ? method : projName;
    }

    /**
     * Get the WKT method name for a projection, taking into account whether
     * stereographic projections are polar (lat0 at ±90°) and whether Mercator
     * uses lat_ts (variant B) vs k0 (variant A).
     *
     * @param proj pre-normalized projection short name (from normalizeProjName)
     */
    private static String getWktMethodName(String proj, ProjectionParams params) {
        if (isPolarStereographic(proj, params)) {
            // Polar Stereographic: use variant B when latTs is present, variant A otherwise
            if (params.latTs != null && params.latTs != 0.0) {
                return "Polar Stereographic (variant B)";
            }
            return "Polar Stereographic (variant A)";
        }
        if ("merc".equals(proj) && params.latTs != null && params.latTs != 0.0) {
            return "Mercator (variant B)";
        }
        if ("omerc".equals(proj)) {
            return omercIsTypeA(params)
                ? "Hotine Oblique Mercator (variant A)"
                : "Hotine Oblique Mercator (variant B)";
        }
        if ("geos".equals(proj)) {
            // PROJ encodes the sweep axis in the method name.
            return "x".equals(params.sweep)
                ? "Geostationary Satellite (Sweep X)"
                : "Geostationary Satellite (Sweep Y)";
        }
        return getWktMethodName(proj);
    }

    // Oblique Mercator method names that select variant A (no origin offset).
    // Mirrors ObliqueMercator's Type-A detection so serialized output re-imports
    // with the same variant.
    private static final java.util.Set<String> OMERC_TYPE_A_NAMES = new java.util.HashSet<>(Arrays.asList(
        "Hotine_Oblique_Mercator", "Hotine_Oblique_Mercator_variant_A",
        "Hotine_Oblique_Mercator_Azimuth_Natural_Origin"));

    private static boolean omercIsTypeA(ProjectionParams params) {
        if (Boolean.TRUE.equals(params.noUoff)) {
            return true;
        }
        String pn = params.projName;
        if (pn == null) {
            return false;
        }
        String norm = pn.replaceAll("[-()\\s]+", " ").trim().replace(' ', '_');
        return OMERC_TYPE_A_NAMES.contains(pn) || OMERC_TYPE_A_NAMES.contains(norm);
    }

    /**
     * Check if the projection uses latTs as the standard parallel parameter
     * (instead of or in addition to lat1). For these projections, latTs should
     * be emitted as standard_parallel_1 in WKT/PROJJSON, taking priority over
     * the auto-set lat1 value from Proj.java.
     *
     * Projections that use latTs:
     * - Polar Stereographic (stere with |lat0| ≈ π/2)
     * - Mercator (merc)
     * - Cylindrical Equal Area (cea)
     * - Equidistant Cylindrical (eqc)
     *
     * @param proj pre-normalized projection short name (from normalizeProjName)
     */
    private static boolean usesLatTsAsStandardParallel(String proj, ProjectionParams params) {
        if (params.latTs == null) return false;
        if ("merc".equals(proj) || "cea".equals(proj) || "eqc".equals(proj)) {
            return true;
        }
        return isPolarStereographic(proj, params);
    }

    /**
     * Check if the projection is Polar Stereographic (lat0 at ±90°).
     *
     * @param proj pre-normalized projection short name (from normalizeProjName)
     */
    private static boolean isPolarStereographic(String proj, ProjectionParams params) {
        return "stere".equals(proj) && params.lat0 != null
                && Math.abs(Math.abs(params.lat0) - Math.PI / 2) < 1e-10;
    }

    /**
     * Check if the projection uses standard parallels (lat1/lat2).
     * Only conic projections genuinely use these parameters.
     * Other projections (e.g., LAEA, Polar Stereographic variant A, Transverse Mercator)
     * may have lat1 auto-set from lat0 by Proj.java, but should NOT emit it in WKT2/PROJJSON.
     * Projections that use latTs (merc variant B, CEA, EQC, polar stere variant B) are
     * handled separately by usesLatTsAsStandardParallel().
     *
     * @param proj pre-normalized projection short name (from normalizeProjName)
     */
    private static boolean usesStandardParallels(String proj) {
        return "lcc".equals(proj) || "aea".equals(proj)
                || "eqdc".equals(proj) || "krovak".equals(proj)
                || "bonne".equals(proj);
    }

    /**
     * Resolve a stored unit to a PROJ +units= short code, as PROJ's CRS export does:
     * known short codes pass through, metre spellings fold to "m", and other linear
     * units are matched by conversion factor (1e-10 relative) against the unit table.
     * Returns null when no short code applies. Only meaningful for projected and
     * geocentric CRSs — geographic CRS units are angular and never emitted.
     */
    private static String toProjUnitCode(String units, Double toMeter) {
        if (units != null) {
            if (Units.contains(units)) {
                return units;
            }
            if ("meter".equals(units) || "metre".equals(units)) {
                return "m";
            }
        }
        if (toMeter != null) {
            return Units.fromToMeter(toMeter);
        }
        return null;
    }

    private static String getUnitName(String unitCode) {
        if (unitCode == null) return "metre";
        
        switch (unitCode) {
            case "m": return "metre";
            case "meter": return "metre";
            case "ft": return "foot";
            case "us-ft": return "US survey foot";
            case "km": return "kilometre";
            case "mi": return "mile";
            case "yd": return "yard";
            default: return unitCode;
        }
    }

    private static String formatAngle(double degrees) {
        if (degrees == Math.floor(degrees)) {
            return String.valueOf((int) degrees);
        }
        return String.valueOf(degrees);
    }

    private static String formatNumber(double value) {
        if (value == Math.floor(value) && Math.abs(value) < 1e10) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
