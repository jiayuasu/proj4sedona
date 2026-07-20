package org.datasyslab.proj4sedona.cf;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the CF grid mapping translator.
 *
 * <p>Expected coordinates come from pyproj 3.7.2 / PROJ 9.5.1 as the oracle: each CF
 * attribute map was fed to {@code pyproj.CRS.from_cf} and a sample geographic point
 * transformed from EPSG:4326 with {@code always_xy=True}. Cases where the translation
 * deliberately diverges from pyproj (single-parallel Albers, GDAL-style leniency) note
 * the oracle used instead.</p>
 */
class CfGridMappingTest {

    private static final String WGS84 = "+proj=longlat +datum=WGS84";

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    private static Map<String, Object> cf(Object... keyValues) {
        Map<String, Object> attrs = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            attrs.put((String) keyValues[i], keyValues[i + 1]);
        }
        return attrs;
    }

    /** Transform a WGS 84 lon/lat with the translated CRS and compare to the oracle. */
    private static void assertProjects(
            Map<String, Object> cfAttrs, String xyUnits,
            double lon, double lat, double expectedX, double expectedY, double tolerance) {
        String projString = CfGridMapping.toProjString(cfAttrs, xyUnits);
        double[] result = Proj4.proj4(WGS84, projString, new double[]{lon, lat});
        assertEquals(expectedX, result[0], tolerance, "x for " + projString);
        assertEquals(expectedY, result[1], tolerance, "y for " + projString);
    }

    // ==================== latitude_longitude ====================

    @Test
    @DisplayName("latitude_longitude: bare mapping assumes WGS 84 like GDAL and pyproj")
    void testLatitudeLongitudeBare() {
        Map<String, Object> attrs = cf("grid_mapping_name", "latitude_longitude");
        assertEquals("+proj=longlat +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        assertEquals("EPSG:4326", CfGridMapping.toProj(attrs).toEpsgCode());
    }

    @Test
    @DisplayName("latitude_longitude: verbose WGS 84 datum name resolves to +datum")
    void testLatitudeLongitudeDatumName() {
        assertEquals("+proj=longlat +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "horizontal_datum_name", "World Geodetic System 1984")));
    }

    @Test
    @DisplayName("latitude_longitude: NAD83 datum name identifies EPSG:4269")
    void testLatitudeLongitudeNad83() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "latitude_longitude",
            "horizontal_datum_name", "North American Datum 1983");
        assertEquals("+proj=longlat +datum=nad83 +no_defs",
            CfGridMapping.toProjString(attrs));
        assertEquals("EPSG:4269", CfGridMapping.toProj(attrs).toEpsgCode());
    }

    @Test
    @DisplayName("latitude_longitude: earth_radius builds a sphere")
    void testLatitudeLongitudeSphere() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "latitude_longitude",
            "earth_radius", 6371000.0);
        assertEquals("+proj=longlat +R=6371000 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: identity for the geographic coordinates themselves
        assertProjects(attrs, null, -100.0, 35.0, -100.0, 35.0, 1e-9);
    }

    @Test
    @DisplayName("latitude_longitude: semi-major axis with inverse flattening")
    void testLatitudeLongitudeAxisAndFlattening() {
        assertEquals("+proj=longlat +a=6378137 +rf=298.257222101 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "semi_major_axis", 6378137.0,
                "inverse_flattening", 298.257222101)));
    }

    @Test
    @DisplayName("latitude_longitude: semi-major with semi-minor axis")
    void testLatitudeLongitudeAxes() {
        assertEquals("+proj=longlat +a=6378137 +b=6356752.314245 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "semi_major_axis", 6378137.0,
                "semi_minor_axis", 6356752.314245)));
    }

    @Test
    @DisplayName("latitude_longitude: EPSG ellipsoid names resolve to +ellps codes")
    void testLatitudeLongitudeEllipsoidNames() {
        assertEquals("+proj=longlat +ellps=GRS80 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "reference_ellipsoid_name", "GRS 1980")));
        assertEquals("+proj=longlat +ellps=clrk66 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "reference_ellipsoid_name", "Clarke 1866")));
        assertEquals("+proj=longlat +ellps=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "reference_ellipsoid_name", "WGS 84")));
    }

    @Test
    @DisplayName("latitude_longitude: geographic_crs_name is a datum-name fallback")
    void testLatitudeLongitudeGeographicCrsName() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "latitude_longitude",
            "geographic_crs_name", "WGS 84");
        assertEquals("+proj=longlat +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
    }

    @Test
    @DisplayName("latitude_longitude: explicit figure parameters beat the datum-name fallback order")
    void testUnknownDatumNameFallsToParameters() {
        assertEquals("+proj=longlat +a=6378137 +rf=298.257223563 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "horizontal_datum_name", "unknown",
                "semi_major_axis", 6378137.0,
                "inverse_flattening", 298.257223563)));
    }

    @Test
    @DisplayName("latitude_longitude: non-Greenwich prime meridian shifts longitudes")
    void testLatitudeLongitudePrimeMeridian() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "latitude_longitude",
            "semi_major_axis", 6378137.0,
            "inverse_flattening", 298.257223563,
            "longitude_of_prime_meridian", 2.5969213,
            "prime_meridian_name", "Paris");
        String projString = CfGridMapping.toProjString(attrs);
        assertTrue(projString.contains("+pm=2.5969213"), projString);
        // pyproj: (-100, 35) -> (-102.5969213, 35)
        assertProjects(attrs, null, -100.0, 35.0, -102.5969213, 35.0, 1e-9);
    }

    @Test
    @DisplayName("latitude_longitude: named prime meridian without a longitude value")
    void testLatitudeLongitudeNamedPrimeMeridian() {
        String projString = CfGridMapping.toProjString(cf(
            "grid_mapping_name", "latitude_longitude",
            "prime_meridian_name", "Paris"));
        assertTrue(projString.contains("+pm=paris"), projString);
    }

    @Test
    @DisplayName("latitude_longitude: angular coordinate units are not linear-validated")
    void testLatitudeLongitudeIgnoresAngularUnits() {
        assertEquals("+proj=longlat +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(
                cf("grid_mapping_name", "latitude_longitude"), "degrees_east"));
    }

    // ==================== mercator ====================

    @Test
    @DisplayName("mercator: standard parallel selects variant B (+lat_ts)")
    void testMercatorVariantB() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "mercator",
            "standard_parallel", 20.0,
            "longitude_of_projection_origin", -80.5,
            "false_easting", 1000.0,
            "false_northing", 2000.0);
        assertEquals(
            "+proj=merc +lat_ts=20 +lon_0=-80.5 +x_0=1000 +y_0=2000 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-75, 25) -> (576558.9747042408, 2688404.7184919477)
        assertProjects(attrs, null, -75.0, 25.0, 576558.9747042408, 2688404.7184919477, 1e-3);
    }

    @Test
    @DisplayName("mercator: scale factor selects variant A (+k_0)")
    void testMercatorVariantA() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "mercator",
            "scale_factor_at_projection_origin", 0.9996,
            "longitude_of_projection_origin", -80.5);
        assertEquals("+proj=merc +k_0=0.9996 +lon_0=-80.5 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-75, 25) -> (612012.2964832595, 2856549.534116069)
        assertProjects(attrs, null, -75.0, 25.0, 612012.2964832595, 2856549.534116069, 1e-3);
    }

    // ==================== transverse_mercator / UTM ====================

    @Test
    @DisplayName("transverse_mercator: full parameter set")
    void testTransverseMercator() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "transverse_mercator",
            "latitude_of_projection_origin", 0.0,
            "longitude_of_central_meridian", -123.0,
            "scale_factor_at_central_meridian", 0.9996,
            "false_easting", 500000.0,
            "false_northing", 0.0);
        assertEquals(
            "+proj=tmerc +lat_0=0 +lon_0=-123 +k_0=0.9996 +x_0=500000 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-122, 47.6) -> (575169.9645693353, 5272327.878876387)
        assertProjects(attrs, null, -122.0, 47.6, 575169.9645693353, 5272327.878876387, 1e-3);
    }

    @Test
    @DisplayName("transverse_mercator: omitted origins and scale use oracle defaults")
    void testTransverseMercatorDefaults() {
        assertEquals("+proj=tmerc +lat_0=0 +lon_0=0 +k_0=1 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf("grid_mapping_name", "transverse_mercator")));
    }

    @Test
    @DisplayName("universal_transverse_mercator: positive zone is north")
    void testUtmNorth() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "universal_transverse_mercator",
            "utm_zone_number", 33);
        assertEquals("+proj=utm +zone=33 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: EPSG:32633, (15.5, 52) -> (534325.167454962, 5761156.235698992)
        assertProjects(attrs, null, 15.5, 52.0, 534325.167454962, 5761156.235698992, 1e-3);
    }

    @Test
    @DisplayName("universal_transverse_mercator: negative zone is south (CDM convention)")
    void testUtmSouth() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "universal_transverse_mercator",
            "utm_zone_number", -33);
        assertEquals("+proj=utm +zone=33 +south +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: EPSG:32733, (15.5, -30) -> (548224.1512265288, 6681109.436954567)
        assertProjects(attrs, null, 15.5, -30.0, 548224.1512265288, 6681109.436954567, 1e-3);
    }

    @Test
    @DisplayName("universal_transverse_mercator: legacy UTM_zone alias")
    void testUtmZoneAlias() {
        assertEquals("+proj=utm +zone=17 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "universal_transverse_mercator",
                "UTM_zone", 17)));
    }

    // ==================== lambert_conformal_conic ====================

    @Test
    @DisplayName("lambert_conformal_conic: two standard parallels select the 2SP form")
    void testLambertConformalConic2SP() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "lambert_conformal_conic",
            "standard_parallel", new double[]{33.0, 45.0},
            "longitude_of_central_meridian", -97.0,
            "latitude_of_projection_origin", 40.0);
        assertEquals("+proj=lcc +lat_1=33 +lat_2=45 +lat_0=40 +lon_0=-97 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-100, 35) -> (-272997.97662925394, -547779.5422659346)
        assertProjects(attrs, null, -100.0, 35.0,
            -272997.97662925394, -547779.5422659346, 1e-3);
    }

    @Test
    @DisplayName("lambert_conformal_conic: one standard parallel is the 1SP natural origin")
    void testLambertConformalConic1SP() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "lambert_conformal_conic",
            "standard_parallel", 25.0,
            "longitude_of_central_meridian", -95.0,
            "latitude_of_projection_origin", 25.0);
        assertEquals("+proj=lcc +lat_1=25 +lat_0=25 +k_0=1 +lon_0=-95 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-100, 35) -> (-463549.29671087995, 1122854.9752173377)
        assertProjects(attrs, null, -100.0, 35.0,
            -463549.29671087995, 1122854.9752173377, 1e-3);
    }

    @Test
    @DisplayName("lambert_conformal_conic 1SP: a differing projection origin is ignored, as pyproj")
    void testLambertConformalConic1SPDifferingOrigin() {
        // pyproj anchors the 1SP natural origin at the standard parallel and ignores
        // latitude_of_projection_origin; GDAL instead derives a scale factor via
        // Snyder eq. 15-4 (flagged experimental there, bug #3324).
        assertEquals("+proj=lcc +lat_1=25 +lat_0=25 +k_0=1 +lon_0=-95 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "lambert_conformal_conic",
                "standard_parallel", 25.0,
                "longitude_of_central_meridian", -95.0,
                "latitude_of_projection_origin", 26.0)));
    }

    @Test
    @DisplayName("lambert_conformal_conic 1SP: non-CF scale factor form, as GDAL reads it")
    void testLambertConformalConic1SPScaleFactor() {
        assertEquals(
            "+proj=lcc +lat_1=36 +lat_0=36 +k_0=0.9986 +lon_0=-95 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "lambert_conformal_conic",
                "scale_factor_at_projection_origin", 0.9986,
                "longitude_of_central_meridian", -95.0,
                "latitude_of_projection_origin", 36.0)));
    }

    // ==================== polar_stereographic ====================

    @Test
    @DisplayName("polar_stereographic: standard parallel selects variant B")
    void testPolarStereographicVariantB() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "polar_stereographic",
            "standard_parallel", 70.0,
            "straight_vertical_longitude_from_pole", -45.0,
            "latitude_of_projection_origin", 90.0);
        assertEquals("+proj=stere +lat_0=90 +lat_ts=70 +lon_0=-45 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-30, 75) -> (422879.13134797517, -1578206.403651236)
        assertProjects(attrs, null, -30.0, 75.0, 422879.13134797517, -1578206.403651236, 1e-3);
    }

    @Test
    @DisplayName("polar_stereographic: current longitude attribute without legacy alias")
    void testPolarStereographicCurrentLongitudeOnly() {
        assertEquals("+proj=stere +lat_0=90 +lat_ts=70 +lon_0=-45 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "polar_stereographic",
                "standard_parallel", 70.0,
                "longitude_of_projection_origin", -45.0,
                "latitude_of_projection_origin", 90.0)));
    }

    @Test
    @DisplayName("polar_stereographic: current longitude attribute takes precedence over legacy")
    void testPolarStereographicCurrentLongitudeAttribute() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "polar_stereographic",
            "standard_parallel", 70.0,
            "longitude_of_projection_origin", -45.0,
            "straight_vertical_longitude_from_pole", 10.0,
            "latitude_of_projection_origin", 90.0);
        assertEquals("+proj=stere +lat_0=90 +lat_ts=70 +lon_0=-45 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
    }

    @Test
    @DisplayName("polar_stereographic variant B: pole derived from the parallel's hemisphere")
    void testPolarStereographicVariantBSouth() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "polar_stereographic",
            "standard_parallel", -71.0,
            "straight_vertical_longitude_from_pole", 0.0);
        assertEquals("+proj=stere +lat_0=-90 +lat_ts=-71 +lon_0=0 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (45, -75) -> (1158794.7407726075, 1158794.7407726077)
        assertProjects(attrs, null, 45.0, -75.0, 1158794.7407726075, 1158794.7407726077, 1e-3);
    }

    @Test
    @DisplayName("polar_stereographic: scale factor selects variant A")
    void testPolarStereographicVariantA() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "polar_stereographic",
            "latitude_of_projection_origin", -90.0,
            "straight_vertical_longitude_from_pole", 0.0,
            "scale_factor_at_projection_origin", 0.994);
        assertEquals("+proj=stere +lat_0=-90 +k_0=0.994 +lon_0=0 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (45, -75) -> (1184085.7974123128, 1184085.7974123128)
        assertProjects(attrs, null, 45.0, -75.0, 1184085.7974123128, 1184085.7974123128, 1e-3);
    }

    @Test
    @DisplayName("polar_stereographic: the projection origin must be a pole")
    void testPolarStereographicRequiresPole() {
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "polar_stereographic",
                "latitude_of_projection_origin", 45.0,
                "straight_vertical_longitude_from_pole", 0.0)));
        // Variant A without any latitude_of_projection_origin
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "polar_stereographic",
                "scale_factor_at_projection_origin", 0.994)));
    }

    // ==================== stereographic ====================

    @Test
    @DisplayName("stereographic: oblique case on Bessel 1841 (RD-style parameters)")
    void testStereographicOblique() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "stereographic",
            "latitude_of_projection_origin", 52.15616055555555,
            "longitude_of_projection_origin", 5.38763888888889,
            "scale_factor_at_projection_origin", 0.9999079,
            "false_easting", 155000.0,
            "false_northing", 463000.0,
            "semi_major_axis", 6377397.155,
            "inverse_flattening", 299.1528128);
        // pyproj: (5, 52) -> (128383.70882521641, 445698.7003830712)
        assertProjects(attrs, null, 5.0, 52.0, 128383.70882521641, 445698.7003830712, 1e-3);
    }

    @Test
    @DisplayName("stereographic: omitted origins and scale use oracle defaults")
    void testStereographicDefaults() {
        assertEquals("+proj=stere +lat_0=0 +lon_0=0 +k_0=1 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf("grid_mapping_name", "stereographic")));
    }

    // ==================== albers_conical_equal_area ====================

    @Test
    @DisplayName("albers_conical_equal_area: two standard parallels")
    void testAlbers2SP() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "albers_conical_equal_area",
            "standard_parallel", List.of(29.5, 45.5),
            "longitude_of_central_meridian", -96.0,
            "latitude_of_projection_origin", 23.0);
        assertEquals(
            "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-100, 35) -> (-361961.77654349455, 1334419.5069948842)
        assertProjects(attrs, null, -100.0, 35.0, -361961.77654349455, 1334419.5069948842, 1e-3);
    }

    @Test
    @DisplayName("albers_conical_equal_area: a single parallel is duplicated, as GDAL")
    void testAlbers1SPDuplicatesParallel() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "albers_conical_equal_area",
            "standard_parallel", 29.5,
            "longitude_of_central_meridian", -96.0,
            "latitude_of_projection_origin", 23.0);
        // GDAL duplicates the first parallel; pyproj defaults the second parallel to 0,
        // which silently changes the cone. Expected value from PROJ 9.5.1 for
        // +proj=aea +lat_1=29.5 +lat_2=29.5 +lat_0=23 +lon_0=-96 +datum=WGS84:
        assertEquals(
            "+proj=aea +lat_1=29.5 +lat_2=29.5 +lat_0=23 +lon_0=-96 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        assertProjects(attrs, null, -100.0, 35.0,
            -366859.23139493144, 1333923.9009652464, 1e-3);
    }

    // ==================== other projections ====================

    @Test
    @DisplayName("lambert_azimuthal_equal_area on GRS 80 (ETRS89-LAEA-style parameters)")
    void testLambertAzimuthalEqualArea() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "lambert_azimuthal_equal_area",
            "latitude_of_projection_origin", 52.0,
            "longitude_of_projection_origin", 10.0,
            "false_easting", 4321000.0,
            "false_northing", 3210000.0,
            "semi_major_axis", 6378137.0,
            "inverse_flattening", 298.257222101);
        // pyproj: (5, 52) -> (3977921.1759082996, 3221773.634434365)
        assertProjects(attrs, null, 5.0, 52.0, 3977921.1759082996, 3221773.634434365, 1e-3);
    }

    @Test
    @DisplayName("azimuthal_equidistant")
    void testAzimuthalEquidistant() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "azimuthal_equidistant",
            "latitude_of_projection_origin", 40.0,
            "longitude_of_projection_origin", -100.0);
        // pyproj: (-95, 35) -> (456802.6959157313, -542560.7336703506)
        assertProjects(attrs, null, -95.0, 35.0, 456802.6959157313, -542560.7336703506, 1e-3);
    }

    @Test
    @DisplayName("orthographic")
    void testOrthographic() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "orthographic",
            "latitude_of_projection_origin", 40.0,
            "longitude_of_projection_origin", -100.0);
        // The translation matches pyproj; the expected values are PROJ 9.5.1 for
        // +proj=ortho +lat_0=40 +lon_0=-100 +R=6378137 because proj4js (and this
        // port) implements the spherical orthographic evaluated on the semi-major
        // axis, while PROJ computes the ellipsoidal form on an ellipsoidal CRS
        // (which puts (-95, 35) at x=455861.736, ~500 m away).
        assertProjects(attrs, null, -95.0, 35.0, 455359.46824163693, -543111.7347346254, 1e-3);
    }

    @Test
    @DisplayName("sinusoidal on the MODIS sphere")
    void testSinusoidal() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "sinusoidal",
            "longitude_of_projection_origin", 0.0,
            "earth_radius", 6371007.181);
        assertEquals("+proj=sinu +lon_0=0 +R=6371007.181 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-100, 35) -> (-9108565.414149545, 3891826.819182831)
        assertProjects(attrs, null, -100.0, 35.0, -9108565.414149545, 3891826.819182831, 1e-3);
    }

    @Test
    @DisplayName("lambert_cylindrical_equal_area: standard parallel form")
    void testCylindricalEqualArea() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "lambert_cylindrical_equal_area",
            "standard_parallel", 30.0,
            "longitude_of_central_meridian", -75.0);
        assertEquals("+proj=cea +lat_ts=30 +lon_0=-75 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(attrs));
        // pyproj: (-70, 35) -> (482431.4012544832, 4198673.820940024)
        assertProjects(attrs, null, -70.0, 35.0, 482431.4012544832, 4198673.820940024, 1e-3);
    }

    @Test
    @DisplayName("lambert_cylindrical_equal_area: scale factor normalized to a parallel")
    void testCylindricalEqualAreaScaleFactor() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "lambert_cylindrical_equal_area",
            "scale_factor_at_projection_origin", 0.9,
            "longitude_of_central_meridian", -75.0);
        // pyproj normalizes k0=0.9 on WGS 84 to lat_ts=25.9174996918105 and gives
        // (-70, 35) -> (500937.70856973197, 4043560.8264148985)
        assertProjects(attrs, null, -70.0, 35.0, 500937.70856973197, 4043560.8264148985, 1e-3);
    }

    @Test
    @DisplayName("cylindrical_equal_area: the pre-CF-1.0 name is accepted")
    void testCylindricalEqualAreaLegacyName() {
        assertEquals("+proj=cea +lat_ts=30 +lon_0=-75 +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "cylindrical_equal_area",
                "standard_parallel", 30.0,
                "longitude_of_central_meridian", -75.0)));
    }

    @Test
    @DisplayName("geostationary: sweep_angle_axis (GOES-R ABI-style parameters)")
    void testGeostationarySweepX() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "geostationary",
            "perspective_point_height", 35786023.0,
            "longitude_of_projection_origin", -75.0,
            "latitude_of_projection_origin", 0.0,
            "sweep_angle_axis", "x",
            "semi_major_axis", 6378137.0,
            "inverse_flattening", 298.257222096);
        String projString = CfGridMapping.toProjString(attrs);
        assertTrue(projString.contains("+sweep=x"), projString);
        // pyproj: (-80, 30) -> (-468595.77315508184, 3087367.476236351)
        assertProjects(attrs, null, -80.0, 30.0, -468595.77315508184, 3087367.476236351, 1e-2);
    }

    @Test
    @DisplayName("geostationary: fixed_angle_axis is the perpendicular of the sweep axis")
    void testGeostationaryFixedAngleAxis() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "geostationary",
            "perspective_point_height", 35786023.0,
            "longitude_of_projection_origin", -75.0,
            "fixed_angle_axis", "y");
        String projString = CfGridMapping.toProjString(attrs);
        assertTrue(projString.contains("+sweep=x"), projString);
        // pyproj: (-80, 30) -> (-468595.7731527729, 3087367.476323175)
        assertProjects(attrs, null, -80.0, 30.0, -468595.7731527729, 3087367.476323175, 1e-2);
    }

    @Test
    @DisplayName("geostationary: sweep defaults to y and the orbit must be equatorial")
    void testGeostationaryDefaultsAndValidation() {
        String projString = CfGridMapping.toProjString(cf(
            "grid_mapping_name", "geostationary",
            "perspective_point_height", 35786023.0));
        assertTrue(projString.contains("+sweep=y"), projString);
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "geostationary",
                "perspective_point_height", 35786023.0,
                "latitude_of_projection_origin", 10.0)));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf("grid_mapping_name", "geostationary")));
    }

    // ==================== towgs84 / units ====================

    @Test
    @DisplayName("towgs84: Helmert parameters carried into the datum shift")
    void testTowgs84() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "transverse_mercator",
            "latitude_of_projection_origin", 0.0,
            "longitude_of_central_meridian", 15.0,
            "scale_factor_at_central_meridian", 0.9996,
            "false_easting", 500000.0,
            "towgs84", new double[]{674.374, 15.056, 405.346},
            "semi_major_axis", 6377397.155,
            "inverse_flattening", 299.1528128);
        String projString = CfGridMapping.toProjString(attrs);
        assertTrue(projString.contains("+towgs84=674.374,15.056,405.346"), projString);
        // pyproj: (16, 48) -> (574755.4738060532, 5316391.412676798)
        assertProjects(attrs, null, 16.0, 48.0, 574755.4738060532, 5316391.412676798, 1e-3);
    }

    @Test
    @DisplayName("towgs84: a resolvable datum name degrades to its ellipsoid, keeping the shift")
    void testTowgs84WithDatumName() {
        // +datum= would bring the registry datum's own shift parameters alongside the
        // explicit +towgs84; the datum's ellipsoid alone must be emitted instead.
        assertEquals("+proj=longlat +ellps=WGS84 +towgs84=1,2,3 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "horizontal_datum_name", "WGS84",
                "towgs84", new double[]{1, 2, 3})));
    }

    @Test
    @DisplayName("towgs84 alone identifies the shift on the default WGS 84 ellipsoid")
    void testTowgs84WithoutFigureParameters() {
        assertEquals("+proj=longlat +ellps=WGS84 +towgs84=1,2,3 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "towgs84", new double[]{1, 2, 3})));
    }

    @Test
    @DisplayName("datum name vetoed by a contradicting ellipsoid name or figure parameters")
    void testDatumNameVeto() {
        // horizontal_datum_name = WGS84 on an explicitly GRS 1980 ellipsoid: the name
        // must not win (pyproj would let it; the declared figure would be discarded).
        // The resolvable ellipsoid name takes over.
        assertEquals("+proj=longlat +ellps=GRS80 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "horizontal_datum_name", "WGS84",
                "reference_ellipsoid_name", "GRS_1980")));
        // Contradicting numeric parameters win over the name (GDAL's precedence)
        assertEquals("+proj=longlat +a=6378137 +rf=298.257222101 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "horizontal_datum_name", "WGS84",
                "semi_major_axis", 6378137.0,
                "inverse_flattening", 298.257222101)));
        // Consistent parameters keep the datum identification
        assertEquals("+proj=longlat +datum=WGS84 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "horizontal_datum_name", "WGS84",
                "semi_major_axis", 6378137.0,
                "inverse_flattening", 298.257223563,
                "reference_ellipsoid_name", "WGS 84")));
        // An earth_radius contradicts any registry datum's ellipsoid
        assertEquals("+proj=longlat +R=6371000 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "horizontal_datum_name", "WGS84",
                "earth_radius", 6371000.0)));
    }

    @Test
    @DisplayName("a WGS 84 CRS on a non-Greenwich prime meridian is not EPSG:4326")
    void testPrimeMeridianBlocksEpsgIdentification() {
        Proj paris = CfGridMapping.toProj(cf(
            "grid_mapping_name", "latitude_longitude",
            "geographic_crs_name", "WGS 84",
            "prime_meridian_name", "Paris"));
        assertEquals(null, paris.toEpsgCode());
    }

    @Test
    @DisplayName("identifiesEarthShape: positive identification vs the WGS 84 assumption")
    void testIdentifiesEarthShape() {
        assertFalse(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude")));
        assertFalse(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "horizontal_datum_name", "unknown")));
        // An unresolvable datum name is not identification — the translation would
        // fall through to the WGS 84 default
        assertFalse(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "horizontal_datum_name", "Costa Rica 2005")));
        assertTrue(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "horizontal_datum_name", "World Geodetic System 1984")));
        assertTrue(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "semi_major_axis", 6378137.0,
            "inverse_flattening", 298.257223563)));
        assertTrue(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "earth_radius", 6371000.0)));
        assertTrue(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "reference_ellipsoid_name", "GRS 1980")));
        assertTrue(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "geographic_crs_name", "WGS 84")));
        assertTrue(CfGridMapping.identifiesEarthShape(cf(
            "grid_mapping_name", "latitude_longitude",
            "towgs84", new double[]{1, 2, 3})));
        assertThrows(IllegalArgumentException.class,
            () -> CfGridMapping.identifiesEarthShape(null));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.identifiesEarthShape(cf(
                "grid_mapping_name", "latitude_longitude",
                "towgs84", new double[]{1, 2, 3, 4, 5})));
    }

    @Test
    @DisplayName("towgs84: only 3, 6, or 7 parameters are meaningful")
    void testTowgs84Validation() {
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "towgs84", new double[]{1, 2, 3, 4, 5})));
    }

    @Test
    @DisplayName("towgs84: CF six-parameter form is padded to PROJ's seven values")
    void testTowgs84SixParameters() {
        assertEquals("+proj=longlat +ellps=WGS84 +towgs84=1,2,3,4,5,6,0 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "towgs84", new double[]{1, 2, 3, 4, 5, 6})));
    }

    @Test
    @DisplayName("km coordinates: false origin converted to metres, +units=km emitted")
    void testKilometreUnits() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "polar_stereographic",
            "standard_parallel", 70.0,
            "straight_vertical_longitude_from_pole", -45.0,
            "false_easting", 2000.0,
            "false_northing", 2000.0);
        assertEquals(
            "+proj=stere +lat_0=90 +lat_ts=70 +lon_0=-45 +x_0=2000000 +y_0=2000000"
                + " +datum=WGS84 +units=km +no_defs",
            CfGridMapping.toProjString(attrs, "km"));
        // PROJ 9.5.1 for the equivalent proj string: (-30, 75) -> km coordinates
        assertProjects(attrs, "km", -30.0, 75.0, 2422.879131347975, 421.7935963487639, 1e-6);
    }

    @Test
    @DisplayName("km false origin survives the WKT round trip in the declared unit")
    void testKilometreUnitsWktRoundTrip() {
        // Regression: WKT emission used to write the internal metre value under a
        // kilometre LENGTHUNIT tag, so parsing the emitted WKT scaled the false
        // origin by the unit factor a second time (x_0 = 2e9).
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "polar_stereographic",
            "standard_parallel", 70.0,
            "straight_vertical_longitude_from_pole", -45.0,
            "false_easting", 2000.0,
            "false_northing", 2000.0);
        Proj original = CfGridMapping.toProj(attrs, "km");
        for (String wkt : new String[]{original.toWkt1(), original.toWkt2()}) {
            double[] viaWkt = Proj4.proj4(WGS84, wkt, new double[]{-30.0, 75.0});
            assertEquals(2422.879131347975, viaWkt[0], 1e-6, "x via " + wkt);
            assertEquals(421.7935963487639, viaWkt[1], 1e-6, "y via " + wkt);
        }
    }

    @Test
    @DisplayName("unit spellings: metre variants collapse, feet map to PROJ codes")
    void testUnitSpellings() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "transverse_mercator",
            "longitude_of_central_meridian", -123.0,
            "scale_factor_at_central_meridian", 1.0);
        assertFalse(CfGridMapping.toProjString(attrs, "meters").contains("+units="));
        assertTrue(CfGridMapping.toProjString(attrs, "US_survey_foot").contains("+units=us-ft"));
        assertTrue(CfGridMapping.toProjString(attrs, "foot").contains("+units=ft"));
        assertThrows(IllegalArgumentException.class,
            () -> CfGridMapping.toProjString(attrs, "furlongs"));
    }

    @Test
    @DisplayName("projection coordinate axes must use compatible linear units")
    void testCoordinateUnitCompatibility() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "transverse_mercator",
            "longitude_of_central_meridian", -123.0,
            "scale_factor_at_central_meridian", 1.0);
        assertTrue(CfGridMapping.toProjString(attrs, "km", "kilometres").contains("+units=km"));
        assertTrue(CfGridMapping.toProjString(attrs, "km", null).contains("+units=km"));
        assertTrue(CfGridMapping.toProjString(attrs, null, "kilometres").contains("+units=km"));
        assertThrows(IllegalArgumentException.class,
            () -> CfGridMapping.toProjString(attrs, "km", "m"));
    }

    @Test
    @DisplayName("single-parallel mappings reject two standard parallels")
    void testSingleParallelMappingsRejectTwoParallels() {
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "mercator",
                "standard_parallel", new double[]{10, 20})));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "lambert_cylindrical_equal_area",
                "standard_parallel", new double[]{10, 20})));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "polar_stereographic",
                "standard_parallel", new double[]{70, 71},
                "latitude_of_projection_origin", 90.0)));
    }

    @Test
    @DisplayName("non-physical ellipsoid parameters are rejected")
    void testInvalidEarthShapeParameters() {
        for (double inverseFlattening : new double[]{-298.0, 0.5, 1.0}) {
            assertThrows(IllegalArgumentException.class, () ->
                CfGridMapping.toProjString(cf(
                    "grid_mapping_name", "latitude_longitude",
                    "semi_major_axis", 6378137.0,
                    "inverse_flattening", inverseFlattening)));
        }
        for (double semiMinor : new double[]{0.0, -1.0, 6378138.0}) {
            assertThrows(IllegalArgumentException.class, () ->
                CfGridMapping.toProjString(cf(
                    "grid_mapping_name", "latitude_longitude",
                    "semi_major_axis", 6378137.0,
                    "semi_minor_axis", semiMinor)));
        }
        assertEquals("+proj=longlat +R=6378137 +no_defs",
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "latitude_longitude",
                "semi_major_axis", 6378137.0,
                "inverse_flattening", 0.0)));
    }

    // ==================== attribute value coercion ====================

    @Test
    @DisplayName("standard_parallel accepts arrays, lists, and separated strings")
    void testStandardParallelForms() {
        String expected =
            "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +datum=WGS84 +no_defs";
        for (Object form : new Object[]{
                new double[]{29.5, 45.5},
                new float[]{29.5f, 45.5f},
                List.of(29.5, 45.5),
                "29.5,45.5",
                "29.5 45.5"}) {
            assertEquals(expected, CfGridMapping.toProjString(cf(
                "grid_mapping_name", "albers_conical_equal_area",
                "standard_parallel", form,
                "longitude_of_central_meridian", -96.0,
                "latitude_of_projection_origin", 23.0)), "form: " + form.getClass());
        }
    }

    @Test
    @DisplayName("numeric attributes accept Number and numeric String values")
    void testScalarForms() {
        String expected = "+proj=merc +lat_ts=20 +lon_0=-80.5 +datum=WGS84 +no_defs";
        assertEquals(expected, CfGridMapping.toProjString(cf(
            "grid_mapping_name", "mercator",
            "standard_parallel", "20",
            "longitude_of_projection_origin", "-80.5")));
        assertEquals(expected, CfGridMapping.toProjString(cf(
            "grid_mapping_name", "mercator",
            "standard_parallel", 20,
            "longitude_of_projection_origin", -80.5f)));
    }

    // ==================== errors ====================

    @Test
    @DisplayName("missing or unsupported grid mappings are rejected with clear messages")
    void testUnsupportedMappings() {
        assertThrows(IllegalArgumentException.class,
            () -> CfGridMapping.toProjString(cf("standard_parallel", 30.0)));
        assertThrows(IllegalArgumentException.class, () -> CfGridMapping.toProjString(null));
        for (String unsupported : new String[]{
                "rotated_latitude_longitude", "oblique_mercator", "vertical_perspective",
                "made_up_projection"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CfGridMapping.toProjString(cf("grid_mapping_name", unsupported)));
            assertTrue(e.getMessage().contains(unsupported), e.getMessage());
        }
    }

    @Test
    @DisplayName("malformed attributes are rejected")
    void testMalformedAttributes() {
        // Albers without its required standard_parallel
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "albers_conical_equal_area",
                "longitude_of_central_meridian", -96.0)));
        // LCC with neither standard_parallel nor scale factor
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "lambert_conformal_conic",
                "longitude_of_central_meridian", -95.0)));
        // Three standard parallels
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "albers_conical_equal_area",
                "standard_parallel", new double[]{1, 2, 3})));
        // Non-numeric value
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "mercator",
                "standard_parallel", "twenty")));
        // Multi-valued scalar
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "mercator",
                "longitude_of_projection_origin", new double[]{1, 2})));
        // UTM zone missing or out of range
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "universal_transverse_mercator")));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "universal_transverse_mercator",
                "utm_zone_number", 61)));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "universal_transverse_mercator",
                "utm_zone_number", 33.7)));
        // Mercator's variant-defining scale/parallel parameter is required
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "mercator",
                "longitude_of_projection_origin", 0.0)));
        // Explicit scale factors must remain positive even when omission defaults to one
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "transverse_mercator",
                "scale_factor_at_central_meridian", 0.0)));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "stereographic",
                "scale_factor_at_projection_origin", -1.0)));
        // Non-finite numeric metadata is rejected at coercion
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "mercator",
                "standard_parallel", Double.NaN,
                "longitude_of_projection_origin", 0.0)));
        assertThrows(IllegalArgumentException.class, () ->
            CfGridMapping.toProjString(cf(
                "grid_mapping_name", "mercator",
                "standard_parallel", "Infinity",
                "longitude_of_projection_origin", 0.0)));
    }

    // ==================== downstream serialization ====================

    @Test
    @DisplayName("translated CRS serializes to WKT2 that parses back to the same projection")
    void testWktRoundTrip() {
        Map<String, Object> attrs = cf(
            "grid_mapping_name", "lambert_conformal_conic",
            "standard_parallel", new double[]{33.0, 45.0},
            "longitude_of_central_meridian", -97.0,
            "latitude_of_projection_origin", 40.0);
        Proj original = CfGridMapping.toProj(attrs);
        String wkt2 = original.toWkt2();
        assertNotNull(wkt2);
        double[] viaWkt = Proj4.proj4(WGS84, wkt2, new double[]{-100.0, 35.0});
        assertEquals(-272997.97662925394, viaWkt[0], 1e-3);
        assertEquals(-547779.5422659346, viaWkt[1], 1e-3);
    }

    @Test
    @DisplayName("translated UTM identifies its EPSG code")
    void testUtmEpsgIdentification() {
        assertEquals("EPSG:32633", CfGridMapping.toProj(cf(
            "grid_mapping_name", "universal_transverse_mercator",
            "utm_zone_number", 33)).toEpsgCode());
        assertEquals("EPSG:32733", CfGridMapping.toProj(cf(
            "grid_mapping_name", "universal_transverse_mercator",
            "utm_zone_number", -33)).toEpsgCode());
    }
}
