package org.datasyslab.proj4sedona.projection;

import java.util.stream.Stream;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("scaleOneCases")
    @DisplayName("Explicit zero uses the projection's scale-one fallback")
    void testExplicitZeroMatchesOmittedAndOne(
            String name, String definition, double longitude, double latitude) {
        Proj omitted = new Proj(definition);
        Proj zero = new Proj(definition + " +k=0");
        Proj one = new Proj(definition + " +k=1");

        assertEquals(0.0, zero.getParams().k0, 0.0, name);

        Point expected = project(omitted, longitude, latitude);
        Point explicitZero = project(zero, longitude, latitude);
        Point explicitOne = project(one, longitude, latitude);
        assertFinite(expected, name);
        assertPointEquals(expected, explicitZero, 1e-8, name + " zero");
        assertPointEquals(expected, explicitOne, 1e-8, name + " one");
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
    }

    @Test
    @DisplayName("Polar stereographic derives scale from lat_ts before zero fallback")
    void testStereographicLatTsDerivationPrecedesFallback() {
        String definition = "+proj=stere +lat_0=90 +lat_ts=70 +ellps=WGS84 +units=m +no_defs";
        Point expected = project(new Proj(definition), 10, 80);
        assertPointEquals(expected, project(new Proj(definition + " +k=0"), 10, 80),
            1e-8, "polar zero");
        assertPointEquals(expected, project(new Proj(definition + " +k=0.5"), 10, 80),
            1e-8, "polar conflicting scale");
    }

    @Test
    @DisplayName("UTM retains its fixed 0.9996 scale when zero is supplied")
    void testUtmFixedScaleOverridesZero() {
        String definition = "+proj=utm +zone=32 +ellps=WGS84 +units=m +no_defs";
        Proj zero = new Proj(definition + " +k=0");
        assertEquals(0.9996, zero.getParams().k0, 0.0);
        assertPointEquals(project(new Proj(definition), 10, 50), project(zero, 10, 50),
            1e-8, "UTM fixed scale");
    }

    @ParameterizedTest(name = "+proj={0}")
    @ValueSource(strings = {"etmerc", "tmerc"})
    @DisplayName("Exact transverse Mercator preserves a zero scale")
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
    }

    @Test
    @DisplayName("Approximate transverse Mercator follows the authalic-radius path")
    void testApproximateTransverseMercatorAuthalicFlag() {
        Proj projection = new Proj(
            "+proj=tmerc +approx +R_A +lat_0=0 +lon_0=3 "
                + "+ellps=WGS84 +units=m +no_defs");
        Point projected = project(projection, 10, 50);
        // Current proj4js 955bfd6 takes the spherical traditional-TM path.
        assertEquals(500664.2004314585, projected.x, 1e-8);
        assertEquals(5589456.452970307, projected.y, 1e-8);
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
    }

    @Test
    @DisplayName("Hyphenated Fast transverse Mercator alias selects approximate math")
    void testHyphenatedFastTransverseMercatorAlias() {
        Proj projection = new Proj(
            "+proj=Fast-Transverse-Mercator +R=6371000 +lon_0=3 +no_defs");
        Point projected = project(projection, 4, 50);
        // Current proj4js 955bfd6 normalizes hyphens before alias lookup.
        assertEquals(71474.09080788007, projected.x, 1e-8);
        assertEquals(5560224.158597246, projected.y, 1e-8);
    }

    @Test
    @DisplayName("Approximate UTM intentionally honors +approx and expands to traditional TM")
    void testApproximateUtmNormalization() {
        // Current proj4js 955bfd6 overwrites UTM's approximate functions after init,
        // ignoring +approx. This intentional divergence preserves the flag and matches
        // proj4js's own tmerc +approx expansion (and PROJ) below.
        Proj approximate = new Proj(
            "+proj=utm +zone=32 +approx +ellps=WGS84 +units=m +no_defs");
        Point expected = project(approximate, 20, 50);
        Proj expanded = new Proj(
            "+proj=tmerc +approx +lat_0=0 +lon_0=9 +k=0.9996 "
                + "+x_0=500000 +y_0=0 +ellps=WGS84 +units=m +no_defs");
        assertPointEquals(expected, project(expanded, 20, 50), 1e-8, "approximate UTM");
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
        assertPointEquals(project(new Proj(definition), 10, 50),
            project(new Proj(definition + " +k=0"), 10, 50), 1e-8, "lat_ts precedence");
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
    }

    @Test
    @DisplayName("Mercator derives lat_ts scale from its recomputed axes with R_A")
    void testMercatorAuthalicFlagLatTsScale() {
        Proj projection = new Proj(
            "+proj=merc +R_A +lat_ts=30 +ellps=WGS84 +units=m +no_defs");
        Point projected = project(projection, 10, 50);
        // Current proj4js 955bfd6: merc.init recomputes eccentricity from a/b.
        assertEquals(964862.8025089651, projected.x, 1e-8);
        assertEquals(5558928.87201279, projected.y, 1e-8);
    }

    @ParameterizedTest(name = "k={0}")
    @ValueSource(strings = {"-0.5", "Infinity"})
    @DisplayName("Nonstandard truthy scales remain numerically effective")
    void testTruthyNonstandardScale(String scale) {
        Proj projection = new Proj(
            "+proj=merc +k=" + scale + " +lon_0=0 +ellps=WGS84 +no_defs");
        if ("Infinity".equals(scale)) {
            Point projected = project(projection, 10, 50);
            assertFalse(Double.isFinite(projected.x));
            assertFalse(Double.isFinite(projected.y));
        } else {
            Point projected = project(projection, 10, 50);
            assertTrue(Double.isFinite(projected.x));
            assertTrue(projected.x < 0);
        }
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
    @DisplayName("Spherical CEA preserves a nonfinite lat_ts numerically")
    void testSphericalCeaNonfiniteLatTs() {
        Proj projection = new Proj(
            "+proj=cea +lat_ts=Infinity +R=6371000 +units=m +no_defs");
        Point projected = project(projection, 10, 20);
        assertFalse(Double.isFinite(projected.x));
        assertFalse(Double.isFinite(projected.y));
    }

    @Test
    @DisplayName("EQC preserves an infinite lat_ts numerically")
    void testEqcInfiniteLatTs() {
        Proj projection = new Proj(
            "+proj=eqc +lat_ts=Infinity +R=6371000 +units=m +no_defs");
        Point projected = project(projection, 10, 20);
        assertFalse(Double.isFinite(projected.x));
    }

    @ParameterizedTest(name = "+proj={0}")
    @ValueSource(strings = {"cea", "eqc"})
    @DisplayName("CEA and EQC ignore input scale factors")
    void testTrueScaleOnlyProjectionsIgnoreScale(String projectionName) {
        String definition = "+proj=" + projectionName
            + " +lat_ts=30 +R=6371000 +units=m +no_defs";
        Proj projection = new Proj(definition + " +k=-0.5");
        Point projected = project(projection, 10, 20);
        assertPointEquals(project(new Proj(definition), 10, 20), projected,
            1e-8, projectionName);
    }

    @Test
    @DisplayName("A-only Mercator is a sphere matching PROJ")
    void testAOnlyMercatorSphere() {
        Proj projection = new Proj("+proj=merc +a=6400000 +no_defs");
        assertTrue(projection.getParams().sphere);
        Point projected = project(projection, 2, 1);
        assertEquals(223402.14425527418, projected.x, 1e-8);
        assertEquals(111706.74357494432, projected.y, 1e-8);
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
    }

    @ParameterizedTest(name = "lat_ts={0}")
    @ValueSource(strings = {"0", "NaN"})
    @DisplayName("Non-driving Mercator lat_ts uses the variant-A scale")
    void testMercatorNonDrivingLatTsUsesScaleOne(String latitudeOfTrueScale) {
        Proj projection = new Proj("+proj=merc +lat_ts=" + latitudeOfTrueScale
            + " +lon_0=0 +ellps=WGS84 +units=m +no_defs");
        Point expected = project(new Proj(
            "+proj=merc +k=1 +lon_0=0 +ellps=WGS84 +units=m +no_defs"), 10, 50);
        assertPointEquals(expected, project(projection, 10, 50), 1e-8, latitudeOfTrueScale);
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
