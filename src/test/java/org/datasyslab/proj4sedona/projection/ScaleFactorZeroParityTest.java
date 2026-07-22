package org.datasyslab.proj4sedona.projection;

import java.util.stream.Stream;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Current proj4js parity for JavaScript-falsy projection scale factors. */
class ScaleFactorZeroParityTest {

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    static Stream<Arguments> scaleOneCases() {
        return Stream.of(
            Arguments.of("gstmerc",
                "+proj=gstmerc +lat_0=-21.116666667 +lon_0=55.53333333 "
                    + "+x_0=160000 +y_0=50000 +ellps=intl +units=m +no_defs",
                55.5, -21.1),
            Arguments.of("omerc",
                "+proj=omerc +lat_0=37.4769061 +lonc=141.0039618 +alpha=202.22 "
                    + "+x_0=138 +y_0=77.65 +ellps=WGS84 +units=m +no_defs",
                141.003611, 37.476802),
            Arguments.of("somerc",
                "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 "
                    + "+x_0=600000 +y_0=200000 +ellps=bessel +units=m +no_defs",
                8.55, 47.37),
            Arguments.of("lcc",
                "+proj=lcc +lat_0=39 +lat_1=33 +lat_2=45 +lon_0=-96 "
                    + "+ellps=WGS84 +units=m +no_defs",
                -100, 40),
            Arguments.of("merc",
                "+proj=merc +lon_0=0 +ellps=WGS84 +units=m +no_defs",
                10, 50),
            Arguments.of("merc lat_ts=0",
                "+proj=merc +lat_ts=0 +lon_0=0 +ellps=WGS84 +units=m +no_defs",
                10, 50),
            Arguments.of("stere",
                "+proj=stere +lat_0=45 +lon_0=0 +ellps=WGS84 +units=m +no_defs",
                10, 50),
            Arguments.of("sterea",
                "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 "
                    + "+x_0=155000 +y_0=463000 +ellps=bessel +units=m +no_defs",
                5.2, 52.25),
            Arguments.of("gnom",
                "+proj=gnom +lat_0=40 +lon_0=-100 +R=6371000 +units=m +no_defs",
                -99, 41),
            Arguments.of("etmerc approx",
                "+proj=etmerc +approx +lat_0=0 +lon_0=3 +ellps=WGS84 +units=m +no_defs",
                4, 50),
            Arguments.of("tmerc approx",
                "+proj=tmerc +approx +lat_0=0 +lon_0=3 +ellps=WGS84 +units=m +no_defs",
                4, 50));
    }

    static Stream<Arguments> nonStandardTrueScaleCases() {
        return Stream.of("merc", "cea", "eqc")
            .flatMap(projection -> Stream.of("90", "100")
                .map(latitude -> Arguments.of(projection, latitude)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scaleOneCases")
    @DisplayName("Explicit zero uses the projection's scale-one fallback")
    void testExplicitZeroMatchesOmittedAndOne(
            String name, String definition, double longitude, double latitude) {
        Proj omitted = new Proj(definition);
        Proj zero = new Proj(definition + " +k=0");
        Proj one = new Proj(definition + " +k=1");

        assertFalse(omitted.getParams().k0Specified, name);
        assertTrue(zero.getParams().k0Specified, name);
        assertEquals(0.0, zero.getParams().k0, 0.0, name);

        Point expected = project(omitted, longitude, latitude);
        Point explicitZero = project(zero, longitude, latitude);
        Point explicitOne = project(one, longitude, latitude);
        assertFinite(expected, name);
        assertPointEquals(expected, explicitZero, 1e-8, name + " zero");
        assertPointEquals(expected, explicitOne, 1e-8, name + " one");

        String serialized = CRSSerializer.toProjString(zero);
        assertFalse(serialized.contains("+k_0=0.0"), name + ": " + serialized);
        assertTrue(serialized.contains("+k_0=1.0"), name + ": " + serialized);
        if (definition.contains("+approx")) {
            assertTrue(serialized.contains("+approx"), name + ": " + serialized);
        }
        assertPointEquals(explicitZero, project(new Proj(serialized), longitude, latitude),
            1e-8, name + " serialized");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scaleOneCases")
    @DisplayName("Explicit NaN uses the projection's scale-one fallback")
    void testExplicitNaNMatchesOmitted(
            String name, String definition, double longitude, double latitude) {
        Point expected = project(new Proj(definition), longitude, latitude);
        Proj nan = new Proj(definition + " +k=NaN");
        Point actual = project(nan, longitude, latitude);
        assertFinite(actual, name);
        assertPointEquals(expected, actual, 1e-8, name);

        String serialized = CRSSerializer.toProjString(nan);
        assertTrue(serialized.contains("+k_0=1.0"), serialized);
        assertPointEquals(actual, project(new Proj(serialized), longitude, latitude),
            1e-8, serialized);
    }

    @Test
    @DisplayName("Krovak zero still selects its 0.9999 default")
    void testKrovakZeroUsesProjectionDefault() {
        String definition = "+proj=krovak +lat_0=49.5 +lon_0=24.83333333333333 "
            + "+ellps=bessel +units=m +no_defs";
        assertZeroRoundTripMatchesOmitted(definition, 14.42, 50.08);
        String serialized = CRSSerializer.toProjString(new Proj(definition + " +k=0"));
        assertTrue(serialized.contains("+k_0=0.9999"), serialized);
    }

    @Test
    @DisplayName("Polar stereographic derives scale from lat_ts before zero fallback")
    void testStereographicLatTsDerivationPrecedesFallback() {
        String definition = "+proj=stere +lat_0=90 +lat_ts=70 +ellps=WGS84 +units=m +no_defs";
        assertZeroRoundTripMatchesOmitted(definition, 10, 80);
    }

    @Test
    @DisplayName("UTM retains its fixed 0.9996 scale when zero is supplied")
    void testUtmFixedScaleOverridesZero() {
        String definition = "+proj=utm +zone=32 +ellps=WGS84 +units=m +no_defs";
        Proj zero = new Proj(definition + " +k=0");
        assertEquals(0.9996, zero.getParams().k0, 0.0);
        assertZeroRoundTripMatchesOmitted(definition, 10, 50);
        String serialized = CRSSerializer.toProjString(zero);
        assertTrue(serialized.contains("+k_0=0.9996"), serialized);
    }

    @ParameterizedTest(name = "+proj={0}")
    @ValueSource(strings = {"etmerc", "tmerc"})
    @DisplayName("Exact transverse Mercator keeps zero Qn only in PROJ strings")
    void testExactTransverseMercatorZeroKeepsInitializedQn(String projectionName) {
        String definition = "+proj=" + projectionName
            + " +lat_0=0 +lon_0=3 +x_0=500 +y_0=700 "
            + "+ellps=WGS84 +units=m +no_defs";
        Proj projection = new Proj(definition + " +k=0");
        Point zero = project(projection, 4, 50);
        Point omitted = project(new Proj(definition), 4, 50);

        assertEquals(500.0, zero.x, 0.0);
        assertEquals(700.0, zero.y, 0.0);
        assertNotEquals(omitted.x, zero.x, 1.0);
        assertNotEquals(omitted.y, zero.y, 1.0);

        String serialized = CRSSerializer.toProjString(projection);
        assertTrue(serialized.contains("+k_0=0.0"), serialized);
        assertPointEquals(zero, project(new Proj(serialized), 4, 50), 0.0,
            "exact " + projectionName);

        for (Runnable serializer : new Runnable[] {
                () -> CRSSerializer.toWkt1(projection),
                () -> CRSSerializer.toWkt2(projection),
                () -> CRSSerializer.toProjJson(projection)}) {
            UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class, serializer::run);
            assertTrue(exception.getMessage().contains("use toProjString"),
                exception.getMessage());
        }
    }

    @ParameterizedTest(name = "+proj={0}")
    @ValueSource(strings = {"etmerc", "tmerc"})
    @DisplayName("Transverse Mercator requires +approx for a sphere")
    void testTransverseMercatorSphereRequiresApprox(String projectionName) {
        String definition = "+proj=" + projectionName
            + " +lat_0=0 +lon_0=3 +R=6371000 +units=m +no_defs";
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, () -> new Proj(definition));
        assertTrue(exception.getMessage().contains("Try using the +approx option"),
            exception.getMessage());

        Proj approximate = new Proj(definition + " +approx +k=0");
        Point projected = project(approximate, 4, 50);
        assertFinite(projected, definition + " +approx");
        String serialized = CRSSerializer.toProjString(approximate);
        assertTrue(serialized.contains("+approx"), serialized);
        assertTrue(serialized.contains("+k_0=1.0"), serialized);
        assertPointEquals(projected, project(new Proj(serialized), 4, 50),
            1e-8, serialized);
    }

    @ParameterizedTest(name = "+proj={0}")
    @ValueSource(strings = {"etmerc", "tmerc"})
    @DisplayName("Approximate transverse Mercator handles prolate ellipsoids as ellipsoidal")
    void testApproximateTransverseMercatorProlate(String projectionName) {
        Proj projection = new Proj("+proj=" + projectionName
            + " +approx +lat_0=0 +lon_0=3 +k=1 +a=6378137 +b=6400000 +no_defs");
        Point projected = project(projection, 4, 50);
        // Pinned proj4js tmerc.js reference (negative es is JavaScript-truthy).
        assertEquals(71410.40935168377, projected.x, 1e-7);
        assertEquals(5592137.669711486, projected.y, 1e-7);

        Point inverse = projection.inverse(new Point(projected.x, projected.y));
        assertEquals(4 * Values.D2R, inverse.x, 1e-12);
        assertEquals(50 * Values.D2R, inverse.y, 1e-12);

        String serialized = CRSSerializer.toProjString(projection);
        assertTrue(serialized.startsWith("+proj=tmerc "), serialized);
        assertTrue(serialized.contains("+approx"), serialized);
        assertFalse(serialized.contains("+proj=etmerc"), serialized);
        assertPointEquals(projected, project(new Proj(serialized), 4, 50),
            1e-8, serialized);
    }

    @Test
    @DisplayName("Approximate transverse Mercator preserves R_A in PROJ strings")
    void testApproximateTransverseMercatorAuthalicFlagRoundTrip() {
        Proj projection = new Proj(
            "+proj=tmerc +approx +R_A +lat_0=0 +lon_0=3 "
                + "+ellps=WGS84 +units=m +no_defs");
        Point projected = project(projection, 10, 50);
        // Current proj4js 888ce3a takes the spherical traditional-TM path.
        assertEquals(500664.2004314585, projected.x, 1e-8);
        assertEquals(5589456.452970307, projected.y, 1e-8);

        String serialized = CRSSerializer.toProjString(projection);
        assertTrue(serialized.contains("+R_A"), serialized);
        assertPointEquals(projected, project(new Proj(serialized), 10, 50),
            1e-8, serialized);

        for (Runnable serializer : new Runnable[] {
                () -> CRSSerializer.toWkt1(projection),
                () -> CRSSerializer.toWkt2(projection),
                () -> CRSSerializer.toProjJson(projection)}) {
            UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class, serializer::run);
            assertTrue(exception.getMessage().contains("use toProjString"),
                exception.getMessage());
        }
    }

    @Test
    @DisplayName("Pinned proj4js +approx a-only fixture uses spherical tmerc")
    void testApproximateTransverseMercatorUpstreamFixture() {
        Proj projection = new Proj(
            "+proj=tmerc +approx +a=6400000 +lat_1=0.5 +lat_2=2 +n=0.5");
        Point projected = project(projection, 2, 1);
        assertEquals(223413.46640632232, projected.x, 1e-8);
        assertEquals(111769.14504059685, projected.y, 1e-8);
        assertTrue(projection.getParams().sphere);
        assertEquals(6400000.0, projection.getParams().b, 0.0);

        String serialized = CRSSerializer.toProjString(projection);
        assertTrue(serialized.startsWith("+proj=tmerc "), serialized);
        assertTrue(serialized.contains("+a=6400000.0"), serialized);
        assertTrue(serialized.contains("+b=6400000.0"), serialized);
        assertTrue(serialized.contains("+approx"), serialized);
        assertPointEquals(projected, project(new Proj(serialized), 2, 1),
            1e-8, serialized);
    }

    @Test
    @DisplayName("Fast transverse Mercator WKT1 preserves approximate math")
    void testFastTransverseMercatorWkt1RoundTrip() {
        for (String axes : new String[] {"+ellps=WGS84", "+R=6371000"}) {
            Proj approximate = new Proj(
                "+proj=etmerc +approx +lat_0=0 +lon_0=3 +k=1 " + axes + " +no_defs");
            Point expected = project(approximate, 20, 40);
            String wkt1 = CRSSerializer.toWkt1(approximate);
            assertTrue(wkt1.contains("PROJECTION[\"Fast_Transverse_Mercator\"]"), wkt1);
            Proj reimported = new Proj(wkt1);
            assertPointEquals(expected, project(reimported, 20, 40), 1e-8, wkt1);

            String projString = CRSSerializer.toProjString(reimported);
            assertTrue(projString.startsWith("+proj=tmerc "), projString);
            assertTrue(projString.contains("+approx"), projString);

            for (Runnable serializer : new Runnable[] {
                    () -> CRSSerializer.toWkt2(approximate),
                    () -> CRSSerializer.toProjJson(approximate)}) {
                UnsupportedOperationException exception = assertThrows(
                    UnsupportedOperationException.class, serializer::run);
                assertTrue(exception.getMessage().contains("toProjString or toWkt1"),
                    exception.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Hyphenated Fast transverse Mercator alias selects approximate math")
    void testHyphenatedFastTransverseMercatorAlias() {
        Proj projection = new Proj(
            "+proj=Fast-Transverse-Mercator +R=6371000 +lon_0=3 +no_defs");
        Point projected = project(projection, 4, 50);
        // Current proj4js 888ce3a normalizes hyphens before alias lookup.
        assertEquals(71474.09080788007, projected.x, 1e-8);
        assertEquals(5560224.158597246, projected.y, 1e-8);

        String serialized = CRSSerializer.toProjString(projection);
        assertTrue(serialized.startsWith("+proj=tmerc "), serialized);
        assertTrue(serialized.contains("+approx"), serialized);
        assertPointEquals(projected, project(new Proj(serialized), 4, 50),
            1e-8, serialized);
    }

    @Test
    @DisplayName("Approximate UTM serializes as Fast transverse Mercator")
    void testApproximateUtmSerialization() {
        Proj approximate = new Proj(
            "+proj=utm +zone=32 +approx +ellps=WGS84 +units=m +no_defs");
        Point expected = project(approximate, 20, 50);

        String projString = CRSSerializer.toProjString(approximate);
        assertTrue(projString.startsWith("+proj=tmerc "), projString);
        assertTrue(projString.contains("+approx"), projString);
        assertFalse(projString.contains("+zone="), projString);
        assertPointEquals(expected, project(new Proj(projString), 20, 50), 1e-8, projString);

        String wkt1 = CRSSerializer.toWkt1(approximate);
        assertTrue(wkt1.contains("PROJECTION[\"Fast_Transverse_Mercator\"]"), wkt1);
        assertPointEquals(expected, project(new Proj(wkt1), 20, 50), 1e-8, wkt1);
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt2(approximate));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toProjJson(approximate));
    }

    @Test
    @DisplayName("Fast transverse Mercator singularity returns a non-finite point")
    void testFastTransverseMercatorSingularity() {
        Point singular = project(new Proj(
            "+proj=tmerc +approx +lat_0=0 +lon_0=0 +R=6371000 +no_defs"), 90, 0);
        assertFalse(Double.isFinite(singular.x));
        assertFalse(Double.isFinite(singular.y));
    }

    @Test
    @DisplayName("Mercator nonzero lat_ts remains the defining scale parameter")
    void testMercatorLatTsSuppressesConflictingZeroScale() {
        String definition = "+proj=merc +lat_ts=30 +lon_0=0 +ellps=WGS84 +units=m +no_defs";
        Proj zero = new Proj(definition + " +k=0");
        String serialized = CRSSerializer.toProjString(zero);
        assertTrue(serialized.contains("+lat_ts=29.999999999999996")
                || serialized.contains("+lat_ts=30"), serialized);
        assertFalse(serialized.contains("+k_0="), serialized);
        assertPointEquals(project(zero, 10, 50), project(new Proj(serialized), 10, 50),
            1e-8, serialized);
    }

    @Test
    @DisplayName("Mercator NaN lat_ts follows the scale-one fallback")
    void testMercatorNaNLatTsUsesScaleOneFallback() {
        String definition = "+proj=merc +lat_ts=NaN +k=0 +lon_0=0 "
            + "+ellps=WGS84 +units=m +no_defs";
        Proj projection = new Proj(definition);
        Point expected = project(new Proj(
            "+proj=merc +k=1 +lon_0=0 +ellps=WGS84 +units=m +no_defs"), 10, 50);
        Point actual = project(projection, 10, 50);
        assertFinite(actual, definition);
        assertPointEquals(expected, actual, 1e-8, definition);

        String serialized = CRSSerializer.toProjString(projection);
        assertFalse(serialized.contains("+lat_ts="), serialized);
        assertTrue(serialized.contains("+k_0=1.0"), serialized);

        for (String standard : new String[] {
                CRSSerializer.toWkt1(projection),
                CRSSerializer.toWkt2(projection),
                CRSSerializer.toProjJson(projection)}) {
            assertFalse(standard.contains("Mercator (variant B)"), standard);
            assertTrue(standard.toLowerCase().contains("scale_factor")
                    || standard.toLowerCase().contains("scale factor"), standard);
            assertPointEquals(actual, project(new Proj(standard), 10, 50),
                1e-8, standard);
        }
    }

    @Test
    @DisplayName("Mercator infinite lat_ts derives then falls back to scale one")
    void testMercatorInfiniteLatTsUsesScaleOneFallback() {
        Proj projection = new Proj(
            "+proj=merc +lat_ts=Infinity +k=0.5 +lon_0=0 +ellps=WGS84 +no_defs");
        Point actual = project(projection, 10, 50);
        Point expected = project(new Proj(
            "+proj=merc +k=1 +lon_0=0 +ellps=WGS84 +no_defs"), 10, 50);
        assertPointEquals(expected, actual, 1e-8, "infinite lat_ts");

        String serialized = CRSSerializer.toProjString(projection);
        assertFalse(serialized.contains("+lat_ts="), serialized);
        assertTrue(serialized.contains("+k_0=1.0"), serialized);
        for (String standard : new String[] {
                CRSSerializer.toWkt1(projection),
                CRSSerializer.toWkt2(projection),
                CRSSerializer.toProjJson(projection)}) {
            assertFalse(standard.contains("Mercator (variant B)"), standard);
            assertPointEquals(actual, project(new Proj(standard), 10, 50),
                1e-8, standard);
        }
    }

    @Test
    @DisplayName("Mercator derives lat_ts scale from its recomputed axes with R_A")
    void testMercatorAuthalicFlagLatTsScale() {
        Proj projection = new Proj(
            "+proj=merc +R_A +lat_ts=30 +ellps=WGS84 +units=m +no_defs");
        Point projected = project(projection, 10, 50);
        // Current proj4js 888ce3a: merc.init recomputes eccentricity from a/b.
        assertEquals(964862.8025089651, projected.x, 1e-8);
        assertEquals(5558928.87201279, projected.y, 1e-8);

        String serialized = CRSSerializer.toProjString(projection);
        assertPointEquals(projected, project(new Proj(serialized), 10, 50),
            1e-8, serialized);
    }

    @ParameterizedTest(name = "k={0}")
    @ValueSource(strings = {"-0.5", "Infinity"})
    @DisplayName("Nonstandard truthy scales stay in PROJ strings and reject standard formats")
    void testTruthyNonstandardScaleSerialization(String scale) {
        Proj projection = new Proj(
            "+proj=merc +k=" + scale + " +lon_0=0 +ellps=WGS84 +no_defs");
        String projString = CRSSerializer.toProjString(projection);
        assertTrue(projString.contains("+k_0=" + scale), projString);
        if ("Infinity".equals(scale)) {
            Point projected = project(projection, 10, 50);
            assertFalse(Double.isFinite(projected.x));
            assertFalse(Double.isFinite(projected.y));
        }
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt1(projection));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt2(projection));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toProjJson(projection));
    }

    @Test
    @DisplayName("CEA uses explicit lat_ts, not lat_0, and canonicalizes ellipsoidal Infinity")
    void testCeaTrueScaleFallbacks() {
        Proj omitted = new Proj(
            "+proj=cea +lat_0=30 +ellps=WGS84 +units=m +no_defs");
        Point expected = project(new Proj(
            "+proj=cea +lat_ts=0 +lat_0=30 +ellps=WGS84 +units=m +no_defs"), 10, 20);
        Point actual = project(omitted, 10, 20);
        assertEquals(1113194.9079327357, actual.x, 1e-7);
        assertEquals(2167979.8945611375, actual.y, 1e-7);
        assertPointEquals(expected, actual, 1e-8, "omitted CEA lat_ts");

        Proj infinite = new Proj(
            "+proj=cea +lat_ts=Infinity +ellps=WGS84 +units=m +no_defs");
        Point infiniteResult = project(infinite, 10, 20);
        assertPointEquals(expected, infiniteResult, 1e-8, "infinite CEA lat_ts");
        for (String serialized : new String[] {
                CRSSerializer.toProjString(infinite),
                CRSSerializer.toWkt1(infinite),
                CRSSerializer.toWkt2(infinite),
                CRSSerializer.toProjJson(infinite)}) {
            assertPointEquals(infiniteResult, project(new Proj(serialized), 10, 20),
                1e-8, serialized);
        }
    }

    @Test
    @DisplayName("Spherical CEA omission follows PROJ's equatorial default")
    void testSphericalCeaOmittedLatTsUsesProjDefault() {
        Proj projection = new Proj("+proj=cea +R=6371000 +units=m +no_defs");
        Point projected = project(projection, 10, 20);
        // PROJ 9.5.1. Current proj4js leaks undefined into cos() and returns NaN;
        // this is an intentional, documented correctness divergence.
        assertEquals(1111949.2664455874, projected.x, 1e-8);
        assertEquals(2179010.3331278353, projected.y, 1e-8);
    }

    @Test
    @DisplayName("Spherical CEA nonfinite lat_ts is preserved only in PROJ strings")
    void testSphericalCeaNonfiniteLatTsSerialization() {
        Proj projection = new Proj(
            "+proj=cea +lat_ts=Infinity +R=6371000 +units=m +no_defs");
        Point projected = project(projection, 10, 20);
        assertFalse(Double.isFinite(projected.x));
        assertFalse(Double.isFinite(projected.y));

        String projString = CRSSerializer.toProjString(projection);
        assertTrue(projString.contains("+lat_ts=Infinity"), projString);
        Point roundTrip = project(new Proj(projString), 10, 20);
        assertFalse(Double.isFinite(roundTrip.x));
        assertFalse(Double.isFinite(roundTrip.y));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt1(projection));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt2(projection));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toProjJson(projection));
    }

    @Test
    @DisplayName("EQC preserves infinite lat_ts only in PROJ strings")
    void testEqcInfiniteLatTsSerialization() {
        Proj projection = new Proj(
            "+proj=eqc +lat_ts=Infinity +R=6371000 +units=m +no_defs");
        Point projected = project(projection, 10, 20);
        assertFalse(Double.isFinite(projected.x));

        String projString = CRSSerializer.toProjString(projection);
        assertTrue(projString.contains("+lat_ts=Infinity"), projString);
        assertFalse(Double.isFinite(project(new Proj(projString), 10, 20).x));

        for (Runnable serializer : new Runnable[] {
                () -> CRSSerializer.toWkt1(projection),
                () -> CRSSerializer.toWkt2(projection),
                () -> CRSSerializer.toProjJson(projection)}) {
            UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class, serializer::run);
            assertTrue(exception.getMessage().contains("use toProjString"),
                exception.getMessage());
        }
    }

    @ParameterizedTest(name = "+proj={0} +lat_ts={1}")
    @MethodSource("nonStandardTrueScaleCases")
    @DisplayName("Pole and out-of-range true-scale latitudes are PROJ-string-only")
    void testOutOfRangeTrueScaleRejectsStandardFormats(
            String projectionName, String latitudeOfTrueScale) {
        Proj projection = new Proj("+proj=" + projectionName
            + " +lat_ts=" + latitudeOfTrueScale + " +R=6371000 +no_defs");
        String projString = CRSSerializer.toProjString(projection);
        assertTrue(projString.contains("+lat_ts=" + latitudeOfTrueScale), projString);

        for (Runnable serializer : new Runnable[] {
                () -> CRSSerializer.toWkt1(projection),
                () -> CRSSerializer.toWkt2(projection),
                () -> CRSSerializer.toProjJson(projection)}) {
            UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class, serializer::run);
            assertTrue(exception.getMessage().contains("use toProjString"),
                exception.getMessage());
        }
    }

    @ParameterizedTest(name = "+proj={0}")
    @ValueSource(strings = {"cea", "eqc"})
    @DisplayName("CEA and EQC omit ignored input scale factors")
    void testTrueScaleOnlyProjectionsOmitIgnoredScale(String projectionName) {
        String definition = "+proj=" + projectionName
            + " +lat_ts=30 +R=6371000 +units=m +no_defs";
        Proj projection = new Proj(definition + " +k=-0.5");
        Point projected = project(projection, 10, 20);
        assertPointEquals(project(new Proj(definition), 10, 20), projected,
            1e-8, projectionName);

        for (String serialized : new String[] {
                CRSSerializer.toProjString(projection),
                CRSSerializer.toWkt1(projection),
                CRSSerializer.toWkt2(projection),
                CRSSerializer.toProjJson(projection)}) {
            assertFalse(serialized.contains("+k_0="), serialized);
            assertFalse(serialized.toLowerCase().contains("scale_factor"), serialized);
            assertFalse(serialized.toLowerCase().contains("scale factor"), serialized);
            assertPointEquals(projected, project(new Proj(serialized), 10, 20),
                1e-8, serialized);
        }
    }

    @Test
    @DisplayName("A-only Mercator is a serializable sphere matching PROJ")
    void testAOnlyMercatorSphere() {
        Proj projection = new Proj("+proj=merc +a=6400000 +no_defs");
        assertTrue(projection.getParams().sphere);
        Point projected = project(projection, 2, 1);
        assertEquals(223402.14425527418, projected.x, 1e-8);
        assertEquals(111706.74357494432, projected.y, 1e-8);
        String serialized = CRSSerializer.toProjString(projection);
        assertTrue(serialized.contains("+a=6400000.0"), serialized);
        assertTrue(serialized.contains("+b=6400000.0"), serialized);
        assertPointEquals(projected, project(new Proj(serialized), 2, 1),
            1e-8, serialized);
    }

    @ParameterizedTest(name = "+{0}")
    @ValueSource(strings = {"ellps=WGS84", "datum=WGS84"})
    @DisplayName("Explicit a retains named ellipsoid or datum flattening")
    void testAWithNamedEllipsoidUsesProjFlattening(String figureDefinition) {
        Proj projection = new Proj(
            "+proj=merc +a=6400000 +" + figureDefinition + " +no_defs");
        assertFalse(projection.getParams().sphere);
        assertEquals(298.257223563, projection.getParams().rf, 0.0);
        assertEquals(6378542.011745616, projection.getParams().b, 1e-8);

        Point projected = project(projection, 2, 1);
        // PROJ 9.5.1 for +a=6400000 with WGS84 inverse flattening.
        assertEquals(223402.14425527418, projected.x, 1e-8);
        assertEquals(110959.01160795633, projected.y, 1e-8);

        String serialized = CRSSerializer.toProjString(projection);
        assertPointEquals(projected, project(new Proj(serialized), 2, 1),
            1e-8, serialized);
    }

    @ParameterizedTest(name = "lat_ts={0}")
    @ValueSource(strings = {"0", "NaN"})
    @DisplayName("Non-driving Mercator lat_ts serializes an explicit variant-A scale")
    void testMercatorNonDrivingLatTsSerializesScaleOne(String latitudeOfTrueScale) {
        Proj projection = new Proj("+proj=merc +lat_ts=" + latitudeOfTrueScale
            + " +lon_0=0 +ellps=WGS84 +units=m +no_defs");
        String serialized = CRSSerializer.toProjString(projection);
        assertFalse(serialized.contains("+lat_ts="), serialized);
        assertTrue(serialized.contains("+k_0=1.0"), serialized);
        for (String standard : new String[] {
                CRSSerializer.toWkt1(projection),
                CRSSerializer.toWkt2(projection),
                CRSSerializer.toProjJson(projection)}) {
            assertFalse(standard.contains("Mercator (variant B)"), standard);
            assertTrue(standard.toLowerCase().contains("scale_factor")
                    || standard.toLowerCase().contains("scale factor"), standard);
        }
    }

    private static void assertZeroRoundTripMatchesOmitted(
            String definition, double longitude, double latitude) {
        Point expected = project(new Proj(definition), longitude, latitude);
        Proj zero = new Proj(definition + " +k=0");
        Point actual = project(zero, longitude, latitude);
        assertFinite(actual, definition);
        assertPointEquals(expected, actual, 1e-8, definition);

        String serialized = CRSSerializer.toProjString(zero);
        assertPointEquals(actual, project(new Proj(serialized), longitude, latitude),
            1e-8, definition + " serialized");
    }

    private static Point project(Proj projection, double longitude, double latitude) {
        return projection.forward(new Point(longitude * Values.D2R, latitude * Values.D2R));
    }

    private static void assertFinite(Point point, String message) {
        assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y), message);
    }

    private static void assertPointEquals(Point expected, Point actual, double tolerance, String message) {
        assertEquals(expected.x, actual.x, tolerance, message + " x");
        assertEquals(expected.y, actual.y, tolerance, message + " y");
    }
}
