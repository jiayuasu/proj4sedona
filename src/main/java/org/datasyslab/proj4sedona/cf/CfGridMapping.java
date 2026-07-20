package org.datasyslab.proj4sedona.cf;

import org.datasyslab.proj4sedona.constants.Datum;
import org.datasyslab.proj4sedona.constants.Ellipsoid;
import org.datasyslab.proj4sedona.constants.PrimeMeridian;
import org.datasyslab.proj4sedona.core.Proj;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates NetCDF CF (Climate and Forecast) convention grid mapping attributes to a CRS.
 *
 * <p>CF metadata defines a coordinate reference system through a <i>grid mapping variable</i>:
 * a {@code grid_mapping_name} attribute naming the projection method plus projection and
 * ellipsoid parameters ({@code standard_parallel}, {@code longitude_of_central_meridian},
 * {@code semi_major_axis}, ...). The optional {@code crs_wkt} attribute carries a full WKT
 * string, but many files define the CRS only through the parameter attributes. This class
 * converts the parameter form to a PROJ string (and from there to a {@link Proj}), following
 * CF conventions Appendix F.</p>
 *
 * <p>This is a proj4sedona extension with no proj4js upstream counterpart. The translation
 * table follows the two reference implementations, which were used as oracles for the unit
 * tests: pyproj's {@code CRS.from_cf} ({@code pyproj/crs/_cf1x8.py}, pyproj 3.7.2 / PROJ
 * 9.5.1) and GDAL's {@code OGRSpatialReference::importFromCF1} ({@code ogr/ogr_srs_cf1.cpp}).
 * Where the two disagree, the choice is documented on the relevant method.</p>
 *
 * <p>Supported {@code grid_mapping_name} values:</p>
 * <ul>
 *   <li>{@code latitude_longitude} (geographic CRS)</li>
 *   <li>{@code albers_conical_equal_area}, {@code azimuthal_equidistant},
 *       {@code geostationary}, {@code lambert_azimuthal_equal_area},
 *       {@code lambert_conformal_conic} (1SP and 2SP),
 *       {@code lambert_cylindrical_equal_area} (and the pre-CF-1.0 alias
 *       {@code cylindrical_equal_area}), {@code mercator} (variants A and B),
 *       {@code orthographic}, {@code polar_stereographic} (variants A and B),
 *       {@code sinusoidal}, {@code stereographic}, {@code transverse_mercator}</li>
 *   <li>{@code universal_transverse_mercator} — not a CF Appendix F mapping but the
 *       netCDF-Java (CDM) convention: {@code utm_zone_number} (alias {@code UTM_zone}),
 *       negative zone numbers meaning the southern hemisphere</li>
 * </ul>
 *
 * <p>Attribute values may be any {@link Number}, numeric {@link String}, primitive numeric
 * array, {@code Number[]}, or {@code List} of either — whatever a netCDF attribute reader
 * produces. Multi-valued attributes ({@code standard_parallel}, {@code towgs84}) additionally
 * accept comma- or whitespace-separated strings.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * Map&lt;String, Object&gt; cf = new HashMap&lt;&gt;();
 * cf.put("grid_mapping_name", "lambert_conformal_conic");
 * cf.put("standard_parallel", new double[]{33.0, 45.0});
 * cf.put("longitude_of_central_meridian", -97.0);
 * cf.put("latitude_of_projection_origin", 40.0);
 *
 * String projString = CfGridMapping.toProjString(cf);
 * // "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=40 +lon_0=-97 +datum=WGS84 +no_defs"
 * Proj proj = CfGridMapping.toProj(cf);
 * </pre>
 */
public final class CfGridMapping {

    private CfGridMapping() {
        // Utility class
    }

    /**
     * Translate CF grid mapping attributes to a {@link Proj}, with projection coordinates
     * in metres.
     *
     * @param cfAttributes The grid mapping variable's attributes, keyed by attribute name
     * @return The corresponding projection
     * @throws IllegalArgumentException if the grid mapping is unsupported or malformed
     */
    public static Proj toProj(Map<String, ?> cfAttributes) {
        return new Proj(toProjString(cfAttributes));
    }

    /**
     * Translate CF grid mapping attributes to a {@link Proj}.
     *
     * @param cfAttributes The grid mapping variable's attributes, keyed by attribute name
     * @param xyUnits The {@code units} attribute of the projection x/y coordinate
     *                variables (e.g. {@code "km"}), or null for metres
     * @return The corresponding projection
     * @throws IllegalArgumentException if the grid mapping is unsupported or malformed
     */
    public static Proj toProj(Map<String, ?> cfAttributes, String xyUnits) {
        return new Proj(toProjString(cfAttributes, xyUnits));
    }

    /**
     * Translate CF grid mapping attributes to a PROJ string, with projection coordinates
     * in metres.
     *
     * @param cfAttributes The grid mapping variable's attributes, keyed by attribute name
     * @return The corresponding PROJ string
     * @throws IllegalArgumentException if the grid mapping is unsupported or malformed
     */
    public static String toProjString(Map<String, ?> cfAttributes) {
        return toProjString(cfAttributes, null);
    }

    /**
     * Translate CF grid mapping attributes to a PROJ string.
     *
     * <p>CF requires {@code false_easting}/{@code false_northing} to be in the same units
     * as the projection coordinates, while PROJ {@code +x_0}/{@code +y_0} are always in
     * metres — so when {@code xyUnits} names a non-metre linear unit, the false origin is
     * converted to metres and the unit is emitted as {@code +units=} for the coordinates
     * (the same split GDAL performs via the PROJCS linear unit). An unrecognized unit
     * throws rather than silently misscaling the false origin.</p>
     *
     * @param cfAttributes The grid mapping variable's attributes, keyed by attribute name
     * @param xyUnits The {@code units} attribute of the projection x/y coordinate
     *                variables (e.g. {@code "km"}), or null for metres; ignored for
     *                {@code latitude_longitude} (angular coordinates)
     * @return The corresponding PROJ string
     * @throws IllegalArgumentException if the grid mapping is unsupported or malformed
     */
    public static String toProjString(Map<String, ?> cfAttributes, String xyUnits) {
        if (cfAttributes == null) {
            throw new IllegalArgumentException("CF grid mapping attributes cannot be null");
        }
        String name = stringAttr(cfAttributes, "grid_mapping_name");
        if (name == null) {
            throw new IllegalArgumentException(
                "CF grid mapping attributes missing 'grid_mapping_name'");
        }
        String gridMappingName = name.trim().toLowerCase(Locale.ROOT);
        boolean geographic = "latitude_longitude".equals(gridMappingName);

        EarthShape earth = resolveEarthShape(cfAttributes);
        // A geographic grid has angular coordinates; its units say nothing about a
        // false origin, so they are not interpreted (or validated) as linear units.
        LinearUnit unit = geographic ? LinearUnit.METRE : LinearUnit.resolve(xyUnits);

        List<String> parts = new ArrayList<>();
        switch (gridMappingName) {
            case "latitude_longitude":
                parts.add("+proj=longlat");
                break;
            case "albers_conical_equal_area":
                albersConicalEqualArea(cfAttributes, unit, parts);
                break;
            case "azimuthal_equidistant":
                azimuthalProjection("aeqd", cfAttributes, unit, parts);
                break;
            case "geostationary":
                geostationary(cfAttributes, unit, parts);
                break;
            case "lambert_azimuthal_equal_area":
                azimuthalProjection("laea", cfAttributes, unit, parts);
                break;
            case "lambert_conformal_conic":
                lambertConformalConic(cfAttributes, unit, parts);
                break;
            case "lambert_cylindrical_equal_area":
            case "cylindrical_equal_area": // pre-CF-1.0 name, still read by GDAL
                lambertCylindricalEqualArea(cfAttributes, unit, earth, parts);
                break;
            case "mercator":
                mercator(cfAttributes, unit, parts);
                break;
            case "orthographic":
                azimuthalProjection("ortho", cfAttributes, unit, parts);
                break;
            case "polar_stereographic":
                polarStereographic(cfAttributes, unit, parts);
                break;
            case "sinusoidal":
                sinusoidal(cfAttributes, unit, parts);
                break;
            case "stereographic":
                stereographic(cfAttributes, unit, parts);
                break;
            case "transverse_mercator":
                transverseMercator(cfAttributes, unit, parts);
                break;
            case "universal_transverse_mercator":
                universalTransverseMercator(cfAttributes, parts);
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported CF grid_mapping_name: " + gridMappingName);
        }

        parts.addAll(earth.tokens);
        primeMeridian(cfAttributes, parts);
        towgs84(cfAttributes, parts);
        if (!geographic && unit.projCode != null) {
            parts.add("+units=" + unit.projCode);
        }
        parts.add("+no_defs");
        return String.join(" ", parts);
    }

    // ==================== Per-projection translations ====================

    /**
     * CF {@code albers_conical_equal_area} — PROJ {@code aea}.
     *
     * <p>With a single standard parallel the second is set equal to the first, as GDAL
     * does; pyproj instead defaults the second parallel to 0, which changes the cone
     * (an apparent bug in {@code _cf1x8.py}: {@code second_parallel or 0.0}).</p>
     */
    private static void albersConicalEqualArea(
            Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        double[] parallels = requireParallels(cf, "albers_conical_equal_area");
        double lat1 = parallels[0];
        double lat2 = parallels.length > 1 ? parallels[1] : parallels[0];
        parts.add("+proj=aea");
        parts.add("+lat_1=" + num(lat1));
        parts.add("+lat_2=" + num(lat2));
        parts.add("+lat_0=" + num(doubleAttr(cf, "latitude_of_projection_origin", 0.0)));
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_central_meridian", 0.0)));
        falseOrigin(cf, unit, parts);
    }

    /**
     * The shared azimuthal shape: CF {@code azimuthal_equidistant} / {@code
     * lambert_azimuthal_equal_area} / {@code orthographic} — PROJ {@code aeqd} /
     * {@code laea} / {@code ortho}: latitude/longitude of projection origin plus
     * false origin.
     */
    private static void azimuthalProjection(
            String projCode, Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        parts.add("+proj=" + projCode);
        parts.add("+lat_0=" + num(doubleAttr(cf, "latitude_of_projection_origin", 0.0)));
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_projection_origin", 0.0)));
        falseOrigin(cf, unit, parts);
    }

    /**
     * CF {@code geostationary} — PROJ {@code geos}.
     *
     * <p>The sweep angle axis comes from {@code sweep_angle_axis}, or the inverse of
     * {@code fixed_angle_axis} (CF defines them as perpendicular), defaulting to
     * {@code y} (the PROJ default, and GDAL's behavior when the attribute is absent;
     * pyproj instead raises). A non-zero {@code latitude_of_projection_origin} is
     * rejected — the projection is defined for equatorial orbits only, which PROJ
     * enforces at setup.</p>
     */
    private static void geostationary(Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        Double height = optionalDouble(cf, "perspective_point_height");
        if (height == null) {
            throw new IllegalArgumentException(
                "CF geostationary mapping missing 'perspective_point_height'");
        }
        double lat0 = doubleAttr(cf, "latitude_of_projection_origin", 0.0);
        if (lat0 != 0.0) {
            throw new IllegalArgumentException(
                "CF geostationary mapping requires latitude_of_projection_origin = 0, got: "
                    + lat0);
        }
        String sweep = stringAttr(cf, "sweep_angle_axis");
        if (sweep == null) {
            String fixed = stringAttr(cf, "fixed_angle_axis");
            if (fixed != null) {
                sweep = invertAxis(fixed, "fixed_angle_axis");
            }
        } else {
            // Validate through the same x/y check
            invertAxis(sweep, "sweep_angle_axis");
        }
        parts.add("+proj=geos");
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_projection_origin", 0.0)));
        parts.add("+h=" + num(height));
        parts.add("+sweep=" + (sweep == null ? "y" : sweep.trim().toLowerCase(Locale.ROOT)));
        falseOrigin(cf, unit, parts);
    }

    private static String invertAxis(String axis, String attrName) {
        String normalized = axis.trim().toLowerCase(Locale.ROOT);
        if ("x".equals(normalized)) {
            return "y";
        }
        if ("y".equals(normalized)) {
            return "x";
        }
        throw new IllegalArgumentException(
            "CF geostationary mapping has invalid " + attrName + ": " + axis);
    }

    /**
     * CF {@code lambert_conformal_conic} — PROJ {@code lcc}.
     *
     * <p>Two standard parallels select the 2SP form. With one, the parallel is the
     * natural origin of the EPSG 1SP method (scale factor 1), as pyproj translates it;
     * a {@code latitude_of_projection_origin} different from the standard parallel is
     * ignored, matching pyproj (GDAL instead derives a scale factor via Snyder eq. 15-4,
     * a conversion it labels experimental). A {@code scale_factor_at_projection_origin}
     * attribute — not CF, but written by some producers for the 1SP form and read by
     * GDAL — selects the 1SP method anchored at {@code latitude_of_projection_origin}.</p>
     */
    private static void lambertConformalConic(
            Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        double[] parallels = optionalParallels(cf);
        parts.add("+proj=lcc");
        if (parallels != null && parallels.length == 2) {
            parts.add("+lat_1=" + num(parallels[0]));
            parts.add("+lat_2=" + num(parallels[1]));
            parts.add("+lat_0=" + num(doubleAttr(cf, "latitude_of_projection_origin", 0.0)));
        } else {
            Double scale = optionalDouble(cf, "scale_factor_at_projection_origin");
            double lat0;
            if (scale == null) {
                if (parallels == null) {
                    throw new IllegalArgumentException(
                        "CF lambert_conformal_conic mapping missing 'standard_parallel'");
                }
                lat0 = parallels[0];
                scale = 1.0;
            } else {
                lat0 = doubleAttr(cf, "latitude_of_projection_origin", 0.0);
            }
            parts.add("+lat_1=" + num(lat0));
            parts.add("+lat_0=" + num(lat0));
            parts.add("+k_0=" + num(scale));
        }
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_central_meridian", 0.0)));
        falseOrigin(cf, unit, parts);
    }

    /**
     * CF {@code lambert_cylindrical_equal_area} — PROJ {@code cea}.
     *
     * <p>The scale-factor form is normalized to a standard parallel with the inverse of
     * the EPSG 9835 scale relation k₀ = cos φ / √(1 − e² sin² φ), i.e.
     * sin² φ = (1 − k₀²)/(1 − k₀² e²), exactly as PROJ normalizes the EPSG method
     * (verified against pyproj: k₀ = 0.9 on WGS 84 → φ = 25.9174996918105°). GDAL does
     * not read the scale-factor form at all.</p>
     */
    private static void lambertCylindricalEqualArea(
            Map<String, ?> cf, LinearUnit unit, EarthShape earth, List<String> parts) {
        Double scale = optionalDouble(cf, "scale_factor_at_projection_origin");
        double latTs;
        if (scale != null) {
            if (scale <= 0 || scale > 1) {
                throw new IllegalArgumentException(
                    "CF lambert_cylindrical_equal_area mapping has invalid "
                        + "scale_factor_at_projection_origin: " + scale);
            }
            double sinSq = (1 - scale * scale) / (1 - scale * scale * earth.es);
            latTs = Math.toDegrees(Math.asin(Math.sqrt(sinSq)));
        } else {
            double[] parallels = optionalParallels(cf);
            latTs = parallels != null ? parallels[0] : 0.0;
        }
        parts.add("+proj=cea");
        parts.add("+lat_ts=" + num(latTs));
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_central_meridian", 0.0)));
        falseOrigin(cf, unit, parts);
    }

    /**
     * CF {@code mercator} — PROJ {@code merc}.
     *
     * <p>CF defines the variants as mutually exclusive: {@code
     * scale_factor_at_projection_origin} selects variant A ({@code +k_0}), otherwise
     * {@code standard_parallel} selects variant B ({@code +lat_ts}). When both appear
     * the scale factor wins, matching pyproj (GDAL checks the standard parallel
     * first).</p>
     */
    private static void mercator(Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        parts.add("+proj=merc");
        Double scale = optionalDouble(cf, "scale_factor_at_projection_origin");
        if (scale != null) {
            parts.add("+k_0=" + num(scale));
        } else {
            double[] parallels = optionalParallels(cf);
            parts.add("+lat_ts=" + num(parallels != null ? parallels[0] : 0.0));
        }
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_projection_origin", 0.0)));
        falseOrigin(cf, unit, parts);
    }

    /**
     * CF {@code polar_stereographic} — PROJ {@code stere} at a pole.
     *
     * <p>A {@code standard_parallel} selects EPSG variant B ({@code +lat_ts}, scale 1);
     * the pole comes from {@code latitude_of_projection_origin} when present (CF requires
     * it to be ±90) and otherwise from the standard parallel's hemisphere, as pyproj
     * derives it. Without a standard parallel, variant A applies:
     * {@code latitude_of_projection_origin} must be ±90 and
     * {@code scale_factor_at_projection_origin} (default 1) becomes {@code +k_0}.
     * {@code straight_vertical_longitude_from_pole} defaults to 0 as in GDAL (pyproj
     * requires it).</p>
     */
    private static void polarStereographic(
            Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        double lon0 = doubleAttr(cf, "straight_vertical_longitude_from_pole", 0.0);
        Double latProjOrigin = optionalDouble(cf, "latitude_of_projection_origin");
        if (latProjOrigin != null && latProjOrigin != 90.0 && latProjOrigin != -90.0) {
            throw new IllegalArgumentException(
                "CF polar_stereographic mapping requires latitude_of_projection_origin"
                    + " = +90 or -90, got: " + latProjOrigin);
        }
        double[] parallels = optionalParallels(cf);
        parts.add("+proj=stere");
        if (parallels != null) {
            // Variant B: standard parallel, k = 1
            double latTs = parallels[0];
            double pole = latProjOrigin != null ? latProjOrigin : (latTs < 0 ? -90.0 : 90.0);
            parts.add("+lat_0=" + num(pole));
            parts.add("+lat_ts=" + num(latTs));
        } else {
            // Variant A: scale factor at the pole
            if (latProjOrigin == null) {
                throw new IllegalArgumentException(
                    "CF polar_stereographic mapping needs 'standard_parallel' or"
                        + " 'latitude_of_projection_origin'");
            }
            parts.add("+lat_0=" + num(latProjOrigin));
            parts.add("+k_0="
                + num(doubleAttr(cf, "scale_factor_at_projection_origin", 1.0)));
        }
        parts.add("+lon_0=" + num(lon0));
        falseOrigin(cf, unit, parts);
    }

    /** CF {@code sinusoidal} — PROJ {@code sinu}. */
    private static void sinusoidal(Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        parts.add("+proj=sinu");
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_projection_origin", 0.0)));
        falseOrigin(cf, unit, parts);
    }

    /** CF {@code stereographic} — PROJ {@code stere} (oblique/equatorial, EPSG 9809). */
    private static void stereographic(Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        parts.add("+proj=stere");
        parts.add("+lat_0=" + num(doubleAttr(cf, "latitude_of_projection_origin", 0.0)));
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_projection_origin", 0.0)));
        parts.add("+k_0=" + num(doubleAttr(cf, "scale_factor_at_projection_origin", 1.0)));
        falseOrigin(cf, unit, parts);
    }

    /** CF {@code transverse_mercator} — PROJ {@code tmerc}. */
    private static void transverseMercator(
            Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        parts.add("+proj=tmerc");
        parts.add("+lat_0=" + num(doubleAttr(cf, "latitude_of_projection_origin", 0.0)));
        parts.add("+lon_0=" + num(doubleAttr(cf, "longitude_of_central_meridian", 0.0)));
        parts.add("+k_0=" + num(doubleAttr(cf, "scale_factor_at_central_meridian", 1.0)));
        falseOrigin(cf, unit, parts);
    }

    /**
     * CDM {@code universal_transverse_mercator} — PROJ {@code utm}.
     *
     * <p>Not a CF Appendix F grid mapping: netCDF-Java's {@code UtmProjection} defines it
     * with a {@code utm_zone_number} attribute (legacy alias {@code UTM_zone}), whose sign
     * carries the hemisphere — negative zones are south.</p>
     */
    private static void universalTransverseMercator(Map<String, ?> cf, List<String> parts) {
        Double zoneValue = optionalDouble(cf, "utm_zone_number");
        if (zoneValue == null) {
            zoneValue = optionalDouble(cf, "UTM_zone");
        }
        if (zoneValue == null) {
            throw new IllegalArgumentException(
                "CF universal_transverse_mercator mapping missing 'utm_zone_number'");
        }
        int zone = (int) (double) zoneValue;
        boolean south = zone < 0;
        zone = Math.abs(zone);
        if (zone < 1 || zone > 60) {
            throw new IllegalArgumentException(
                "CF universal_transverse_mercator mapping has invalid utm_zone_number: "
                    + num(zoneValue));
        }
        parts.add("+proj=utm");
        parts.add("+zone=" + zone);
        if (south) {
            parts.add("+south");
        }
    }

    /**
     * False easting/northing, converted from the coordinate unit to the metres PROJ
     * {@code +x_0}/{@code +y_0} expect. Zero values are omitted.
     */
    private static void falseOrigin(Map<String, ?> cf, LinearUnit unit, List<String> parts) {
        double falseEasting = doubleAttr(cf, "false_easting", 0.0);
        double falseNorthing = doubleAttr(cf, "false_northing", 0.0);
        if (falseEasting != 0.0) {
            parts.add("+x_0=" + num(falseEasting * unit.toMeter));
        }
        if (falseNorthing != 0.0) {
            parts.add("+y_0=" + num(falseNorthing * unit.toMeter));
        }
    }

    // ==================== Datum / ellipsoid / prime meridian ====================

    /** Resolved ellipsoid or datum: the PROJ tokens plus the eccentricity the shape implies. */
    private static final class EarthShape {
        final List<String> tokens;
        /** First eccentricity squared, e² = 1 − (b/a)²; 0 for a sphere. */
        final double es;

        EarthShape(List<String> tokens, double es) {
            this.tokens = tokens;
            this.es = es;
        }

        static EarthShape ofDatum(Datum datum, boolean ellipsoidOnly) {
            Ellipsoid ellipsoid = Ellipsoid.get(datum.getEllipse());
            double es = ellipsoid != null
                ? eccentricitySquared(ellipsoid.getA(), ellipsoid.getB()) : 0;
            if (ellipsoidOnly) {
                // An explicit towgs84 attribute carries the datum shift itself; a
                // +datum= token would bring the registry datum's own (conflicting)
                // shift parameters, so only the datum's ellipsoid is emitted — the
                // shape pyproj exports for a CF mapping with towgs84.
                String ellps = ellipsoid != null ? ellipsoid.getCode() : "WGS84";
                return new EarthShape(List.of("+ellps=" + ellps), es);
            }
            // The registry keys datum codes lowercase; WGS84 keeps its conventional
            // uppercase PROJ spelling.
            String code = "wgs84".equalsIgnoreCase(datum.getCode())
                ? "WGS84" : datum.getCode();
            return new EarthShape(List.of("+datum=" + code), es);
        }

        static double eccentricitySquared(double a, double b) {
            return 1 - (b / a) * (b / a);
        }
    }

    /**
     * Whether the attributes positively identify the Earth figure — a resolvable datum
     * or ellipsoid name, explicit figure parameters, or Helmert parameters.
     *
     * <p>When this is false, {@link #toProjString} still translates the grid mapping but
     * assumes WGS 84, as GDAL and pyproj do. Callers that must not guess a datum (e.g.
     * reporting only CRS identities a file actually declares) can use this to decide
     * whether the assumption would be doing the talking.</p>
     *
     * @param cfAttributes The grid mapping variable's attributes, keyed by attribute name
     * @return true when the attributes identify the Earth figure without the WGS 84 default
     * @throws IllegalArgumentException if a figure attribute is present but malformed
     */
    public static boolean identifiesEarthShape(Map<String, ?> cfAttributes) {
        return resolveIdentifiedEarthShape(cfAttributes) != null;
    }

    /**
     * Resolve the CF ellipsoid/datum attributes to PROJ tokens.
     *
     * <p>With nothing identifying the figure, WGS 84 is assumed, as both GDAL and
     * pyproj do.</p>
     */
    private static EarthShape resolveEarthShape(Map<String, ?> cf) {
        EarthShape identified = resolveIdentifiedEarthShape(cf);
        return identified != null
            ? identified
            : EarthShape.ofDatum(Datum.get("wgs84"), cf.containsKey("towgs84"));
    }

    /**
     * The Earth figure the attributes positively identify, or null when only the WGS 84
     * assumption would remain.
     *
     * <p>Following pyproj's {@code _horizontal_datum_from_params}, a resolvable
     * {@code horizontal_datum_name} wins first — but only when the explicit figure
     * attributes are consistent with that datum's ellipsoid (pyproj lets the name win
     * outright, silently discarding contradicting parameters; a file declaring
     * {@code horizontal_datum_name = "WGS84"} on a GRS 1980 ellipsoid must not be
     * reported as datum-identified WGS 84). Otherwise explicit figure parameters are
     * used with GDAL's precedence: inverse flattening, then a semi-minor axis, then a
     * sphere from {@code earth_radius} (or {@code spherical_earth_radius_meters}, the
     * pre-CF spelling GDAL still reads) or a lone semi-major axis. Failing that, a
     * resolvable {@code reference_ellipsoid_name} is used, then {@code
     * geographic_crs_name} as a datum-name fallback (pyproj resolves it against the full
     * EPSG database; the registry's datum aliases cover the common cases). Helmert
     * parameters alone also identify the figure: {@code towgs84} pins the datum shift
     * even though the ellipsoid stays the WGS 84 default.</p>
     */
    private static EarthShape resolveIdentifiedEarthShape(Map<String, ?> cf) {
        boolean hasTowgs84 = cf.containsKey("towgs84");
        Datum datum = lookupDatum(stringAttr(cf, "horizontal_datum_name"));
        if (datum != null && figureConsistentWithDatum(cf, datum)) {
            return EarthShape.ofDatum(datum, hasTowgs84);
        }

        Double semiMajor = optionalDouble(cf, "semi_major_axis");
        Double semiMinor = optionalDouble(cf, "semi_minor_axis");
        Double inverseFlattening = optionalDouble(cf, "inverse_flattening");
        Double radius = optionalDouble(cf, "earth_radius");
        if (radius == null) {
            radius = optionalDouble(cf, "spherical_earth_radius_meters");
        }
        if (semiMajor != null || semiMinor != null || inverseFlattening != null
                || radius != null) {
            double a = semiMajor != null ? semiMajor : (radius != null ? radius : 0);
            if (a <= 0) {
                throw new IllegalArgumentException(
                    "CF grid mapping has a semi_minor_axis or inverse_flattening but no "
                        + "semi_major_axis or earth_radius");
            }
            if (inverseFlattening != null && inverseFlattening != 0) {
                double b = a * (1 - 1 / inverseFlattening);
                return new EarthShape(
                    List.of("+a=" + num(a), "+rf=" + num(inverseFlattening)),
                    EarthShape.eccentricitySquared(a, b));
            }
            if (semiMinor != null) {
                return new EarthShape(
                    List.of("+a=" + num(a), "+b=" + num(semiMinor)),
                    EarthShape.eccentricitySquared(a, semiMinor));
            }
            return new EarthShape(List.of("+R=" + num(a)), 0);
        }

        String ellipsoidName = stringAttr(cf, "reference_ellipsoid_name");
        Ellipsoid ellipsoid = lookupEllipsoid(ellipsoidName);
        if (ellipsoid != null) {
            return new EarthShape(
                List.of("+ellps=" + ellipsoid.getCode()),
                EarthShape.eccentricitySquared(ellipsoid.getA(), ellipsoid.getB()));
        }

        Datum crsDatum = lookupDatum(stringAttr(cf, "geographic_crs_name"));
        if (crsDatum != null && figureConsistentWithDatum(cf, crsDatum)) {
            return EarthShape.ofDatum(crsDatum, hasTowgs84);
        }

        if (hasTowgs84) {
            // The shift parameters identify the datum relationship; the ellipsoid
            // stays the WGS 84 default, and the +towgs84 token itself is appended
            // by the caller.
            return EarthShape.ofDatum(Datum.get("wgs84"), true);
        }

        return null;
    }

    /**
     * Whether the explicit figure attributes are consistent with a named datum's
     * ellipsoid. Attributes can only veto, never qualify: absent attributes are
     * consistent, but a present one must match the datum's ellipsoid within the axis
     * (1 mm) and inverse-flattening (1e-6) tolerances. An {@code earth_radius} always
     * contradicts (no registry datum is spherical), and a {@code
     * reference_ellipsoid_name} must resolve to exactly the datum's ellipsoid.
     */
    private static boolean figureConsistentWithDatum(Map<String, ?> cf, Datum datum) {
        Ellipsoid ellipsoid = Ellipsoid.get(datum.getEllipse());
        String ellipsoidName = stringAttr(cf, "reference_ellipsoid_name");
        Double semiMajor = optionalDouble(cf, "semi_major_axis");
        Double semiMinor = optionalDouble(cf, "semi_minor_axis");
        Double inverseFlattening = optionalDouble(cf, "inverse_flattening");
        boolean hasRadius = cf.containsKey("earth_radius")
            || cf.containsKey("spherical_earth_radius_meters");
        if (ellipsoid == null) {
            // Datums registered without an ellipsoid reference cannot vouch for any
            // declared figure attribute
            return isUnknown(ellipsoidName) && semiMajor == null && semiMinor == null
                && inverseFlattening == null && !hasRadius;
        }
        if (hasRadius) {
            return false;
        }
        if (!isUnknown(ellipsoidName) && lookupEllipsoid(ellipsoidName) != ellipsoid) {
            return false;
        }
        if (semiMajor != null && Math.abs(semiMajor - ellipsoid.getA()) >= 1e-3) {
            return false;
        }
        if (semiMinor != null && Math.abs(semiMinor - ellipsoid.getB()) >= 1e-3) {
            return false;
        }
        return inverseFlattening == null
            || Math.abs(inverseFlattening - ellipsoid.getRf()) < 1e-6;
    }

    private static boolean isUnknown(String name) {
        if (name == null) {
            return true;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || "unknown".equals(normalized)
            || "undefined".equals(normalized);
    }

    private static Datum lookupDatum(String name) {
        return isUnknown(name) ? null : Datum.get(name.trim());
    }

    /**
     * EPSG ellipsoid names for entries whose registry name (inherited from proj4js's
     * table) is spelled too differently for normalized matching to find, e.g.
     * "GRS 1980" vs the registry's "GRS 1980(IUGG, 1980)".
     */
    private static final Map<String, Ellipsoid> ELLIPSOID_NAME_ALIASES = new HashMap<>();
    static {
        ELLIPSOID_NAME_ALIASES.put("grs1980", Ellipsoid.GRS80);
        ELLIPSOID_NAME_ALIASES.put("international1924", Ellipsoid.INTL);
        ELLIPSOID_NAME_ALIASES.put("krassowsky1940", Ellipsoid.KRASS);
        ELLIPSOID_NAME_ALIASES.put("clarke1880rgs", Ellipsoid.CLRK80);
        ELLIPSOID_NAME_ALIASES.put("clarke1880", Ellipsoid.CLRK80);
    }

    /**
     * Resolve {@code reference_ellipsoid_name} to a registry ellipsoid: by PROJ code
     * (with the registry's own fuzzy matching, so "WGS 84" and "GRS 80" resolve), then
     * by normalized ellipsoid name ("Clarke 1866", "Bessel 1841", "Airy 1830", ...),
     * then through {@link #ELLIPSOID_NAME_ALIASES}.
     */
    private static Ellipsoid lookupEllipsoid(String name) {
        if (isUnknown(name)) {
            return null;
        }
        Ellipsoid byCode = Ellipsoid.get(name.trim());
        if (byCode != null) {
            return byCode;
        }
        String normalized = normalizeName(name);
        for (Ellipsoid candidate : Ellipsoid.getAll().values()) {
            if (candidate.getEllipseName() != null
                    && normalizeName(candidate.getEllipseName()).equals(normalized)) {
                return candidate;
            }
        }
        return ELLIPSOID_NAME_ALIASES.get(normalized);
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Prime meridian: a non-zero {@code longitude_of_prime_meridian} (degrees east of
     * Greenwich) is emitted numerically; otherwise a resolvable, non-Greenwich
     * {@code prime_meridian_name} is emitted by name.
     */
    private static void primeMeridian(Map<String, ?> cf, List<String> parts) {
        Double longitude = optionalDouble(cf, "longitude_of_prime_meridian");
        if (longitude != null && longitude != 0.0) {
            parts.add("+pm=" + num(longitude));
            return;
        }
        String name = stringAttr(cf, "prime_meridian_name");
        if (longitude == null && !isUnknown(name)) {
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            if (!"greenwich".equals(normalized) && PrimeMeridian.get(normalized) != null) {
                parts.add("+pm=" + normalized);
            }
        }
    }

    /**
     * The {@code towgs84} attribute (3 or 7 Helmert parameters) is not CF but is written
     * by some producers and read by pyproj's {@code from_cf}.
     */
    private static void towgs84(Map<String, ?> cf, List<String> parts) {
        if (!cf.containsKey("towgs84")) {
            return;
        }
        double[] values = doubleListAttr(cf, "towgs84");
        if (values.length != 3 && values.length != 7) {
            throw new IllegalArgumentException(
                "CF grid mapping 'towgs84' must have 3 or 7 values, got " + values.length);
        }
        StringBuilder joined = new StringBuilder("+towgs84=");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                joined.append(',');
            }
            joined.append(num(values[i]));
        }
        parts.add(joined.toString());
    }

    // ==================== Linear units ====================

    /**
     * The linear unit of the projection coordinate variables. CF/UDUNITS spellings are
     * normalized to PROJ unit codes; metre variants collapse to the PROJ default (no
     * {@code +units=} emitted).
     */
    private static final class LinearUnit {
        static final LinearUnit METRE = new LinearUnit(null, 1.0);

        /** PROJ {@code +units=} code, or null for metres. */
        final String projCode;
        final double toMeter;

        private LinearUnit(String projCode, double toMeter) {
            this.projCode = projCode;
            this.toMeter = toMeter;
        }

        static LinearUnit resolve(String units) {
            if (units == null || units.trim().isEmpty()) {
                return METRE;
            }
            switch (units.trim().toLowerCase(Locale.ROOT)) {
                case "m":
                case "meter":
                case "meters":
                case "metre":
                case "metres":
                    return METRE;
                case "km":
                case "kilometer":
                case "kilometers":
                case "kilometre":
                case "kilometres":
                    return new LinearUnit("km", 1000.0);
                case "ft":
                case "foot":
                case "feet":
                    return new LinearUnit("ft", 0.3048);
                case "us_survey_foot":
                case "us_survey_feet":
                    return new LinearUnit("us-ft", 1200.0 / 3937.0);
                default:
                    throw new IllegalArgumentException(
                        "Unsupported projection coordinate unit: " + units);
            }
        }
    }

    // ==================== Attribute coercion ====================

    /** A single numeric attribute; null when absent. Rejects multi-valued attributes. */
    private static Double optionalDouble(Map<String, ?> cf, String key) {
        Object value = cf.get(key);
        if (value == null) {
            return null;
        }
        double[] values = coerceDoubles(value, key);
        if (values.length != 1) {
            throw new IllegalArgumentException(
                "CF attribute '" + key + "' must be a single number, got "
                    + values.length + " values");
        }
        return values[0];
    }

    private static double doubleAttr(Map<String, ?> cf, String key, double defaultValue) {
        Double value = optionalDouble(cf, key);
        return value != null ? value : defaultValue;
    }

    private static String stringAttr(Map<String, ?> cf, String key) {
        Object value = cf.get(key);
        return value instanceof String ? (String) value : null;
    }

    /** {@code standard_parallel}: 1 or 2 values; null when absent. */
    private static double[] optionalParallels(Map<String, ?> cf) {
        if (!cf.containsKey("standard_parallel")) {
            return null;
        }
        double[] values = doubleListAttr(cf, "standard_parallel");
        if (values.length < 1 || values.length > 2) {
            throw new IllegalArgumentException(
                "CF attribute 'standard_parallel' must have 1 or 2 values, got "
                    + values.length);
        }
        return values;
    }

    private static double[] requireParallels(Map<String, ?> cf, String gridMappingName) {
        double[] parallels = optionalParallels(cf);
        if (parallels == null) {
            throw new IllegalArgumentException(
                "CF " + gridMappingName + " mapping missing 'standard_parallel'");
        }
        return parallels;
    }

    private static double[] doubleListAttr(Map<String, ?> cf, String key) {
        Object value = cf.get(key);
        if (value == null) {
            throw new IllegalArgumentException("CF attribute '" + key + "' is missing");
        }
        return coerceDoubles(value, key);
    }

    /**
     * Coerce an attribute value as produced by netCDF readers — a {@link Number}, a
     * numeric or comma/whitespace-separated {@link String}, a primitive numeric array,
     * a {@code Number[]}, or a {@link List} of any of these element types.
     */
    private static double[] coerceDoubles(Object value, String key) {
        if (value instanceof Number) {
            return new double[]{((Number) value).doubleValue()};
        }
        if (value instanceof String) {
            String[] tokens = ((String) value).trim().split("[,\\s]+");
            double[] result = new double[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                result[i] = parseDouble(tokens[i], key);
            }
            return result;
        }
        if (value instanceof double[]) {
            return ((double[]) value).clone();
        }
        if (value instanceof float[]) {
            float[] floats = (float[]) value;
            double[] result = new double[floats.length];
            for (int i = 0; i < floats.length; i++) {
                result[i] = floats[i];
            }
            return result;
        }
        if (value instanceof int[]) {
            int[] ints = (int[]) value;
            double[] result = new double[ints.length];
            for (int i = 0; i < ints.length; i++) {
                result[i] = ints[i];
            }
            return result;
        }
        if (value instanceof long[]) {
            long[] longs = (long[]) value;
            double[] result = new double[longs.length];
            for (int i = 0; i < longs.length; i++) {
                result[i] = longs[i];
            }
            return result;
        }
        if (value instanceof Object[]) {
            Object[] objects = (Object[]) value;
            double[] result = new double[objects.length];
            for (int i = 0; i < objects.length; i++) {
                result[i] = coerceScalar(objects[i], key);
            }
            return result;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            double[] result = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = coerceScalar(list.get(i), key);
            }
            return result;
        }
        throw new IllegalArgumentException(
            "CF attribute '" + key + "' has unsupported value type: "
                + value.getClass().getName());
    }

    private static double coerceScalar(Object value, String key) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            return parseDouble((String) value, key);
        }
        throw new IllegalArgumentException(
            "CF attribute '" + key + "' has unsupported element type: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static double parseDouble(String value, String key) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "CF attribute '" + key + "' has non-numeric value: " + value, e);
        }
    }

    /** Plain decimal rendering: integral values without ".0", never scientific notation. */
    private static String num(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
