package org.datasyslab.proj4sedona.parser;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Integration coverage where serialization depends on projection initialization. */
class CRSSerializerProjectionSemanticsTest {

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    @Test
    void exactTransverseMercatorZeroScaleIsProjOnly() {
        for (String code : new String[]{"tmerc", "etmerc"}) {
            Proj projection = new Proj("+proj=" + code
                + " +lat_0=0 +lon_0=3 +k=0 +x_0=500 +y_0=700 "
                + "+ellps=WGS84 +units=m +no_defs");
            Point expected = project(projection, 4.0, 50.0);
            assertEquals(500.0, expected.x, 0.0, code);
            assertEquals(700.0, expected.y, 0.0, code);

            String projString = CRSSerializer.toProjString(projection);
            assertTrue(projString.contains("+k_0=0.0"), projString);
            assertPointEquals(expected, project(new Proj(projString), 4.0, 50.0),
                0.0, projString);
            assertAllStandardsReject(projection);
        }
    }

    @Test
    void approximateUtmNormalizesToExecutableTransverseMercator() {
        Proj approximate = new Proj(
            "+proj=utm +zone=32 +approx +ellps=WGS84 +units=m +no_defs");
        Point expected = project(approximate, 20.0, 50.0);
        String projString = CRSSerializer.toProjString(approximate);
        assertTrue(projString.startsWith("+proj=tmerc "), projString);
        assertTrue(projString.contains("+approx"), projString);
        assertFalse(projString.contains("+zone="), projString);
        assertPointEquals(expected, project(new Proj(projString), 20.0, 50.0),
            1e-8, projString);

        String wkt1 = CRSSerializer.toWkt1(approximate);
        assertTrue(wkt1.contains("PROJECTION[\"Fast_Transverse_Mercator\"]"), wkt1);
        assertPointEquals(expected, project(new Proj(wkt1), 20.0, 50.0), 1e-8, wkt1);
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt2(approximate));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toProjJson(approximate));
    }

    @Test
    void mercatorNonfiniteTrueScaleUsesDefaultScale() {
        for (String value : new String[]{"NaN", "Infinity"}) {
            Proj projection = new Proj("+proj=merc +lat_ts=" + value
                + " +k=0 +lon_0=0 +ellps=WGS84 +units=m +no_defs");
            String projString = CRSSerializer.toProjString(projection);
            assertFalse(projString.contains("+lat_ts="), projString);
            assertTrue(projString.contains("+k_0=1.0"), projString);
            Point expected = project(projection, 10.0, 50.0);
            assertTrue(Double.isFinite(expected.x) && Double.isFinite(expected.y), value);
            for (String standard : standardFormats(projection)) {
                assertFalse(standard.contains("Mercator (variant B)"), standard);
                assertPointEquals(expected, project(new Proj(standard), 10.0, 50.0),
                    1e-8, standard);
            }
        }

        Proj equatorial = new Proj(
            "+proj=merc +lat_ts=0 +lon_0=0 +ellps=WGS84 +units=m +no_defs");
        String projString = CRSSerializer.toProjString(equatorial);
        assertFalse(projString.contains("+lat_ts="), projString);
        assertTrue(projString.contains("+k_0=1.0"), projString);
        for (String standard : standardFormats(equatorial)) {
            assertFalse(standard.contains("Mercator (variant B)"), standard);
        }
    }

    @Test
    void mercatorAuthalicTrueScaleRoundTripsOnlyThroughProj() {
        Proj projection = new Proj(
            "+proj=merc +R_A +lat_ts=30 +ellps=WGS84 +units=m +no_defs");
        Point expected = project(projection, 10.0, 50.0);
        String projString = CRSSerializer.toProjString(projection);
        assertTrue(projString.contains("+R_A"), projString);
        assertTrue(projString.contains("+lat_ts="), projString);
        assertPointEquals(expected, project(new Proj(projString), 10.0, 50.0),
            1e-8, projString);
        assertAllStandardsReject(projection);
    }

    @Test
    void approximateTransverseMercatorAuthalicFlagIsProjOnly() {
        Proj projection = new Proj(
            "+proj=tmerc +approx +R_A +lat_0=0 +lon_0=3 "
                + "+ellps=WGS84 +units=m +no_defs");
        Point expected = project(projection, 10.0, 50.0);
        String projString = CRSSerializer.toProjString(projection);
        assertTrue(projString.startsWith("+proj=tmerc "), projString);
        assertTrue(projString.contains("+approx"), projString);
        assertTrue(projString.contains("+R_A"), projString);
        assertPointEquals(expected, project(new Proj(projString), 10.0, 50.0),
            1e-8, projString);
        assertAllStandardsReject(projection);
    }

    @Test
    void nonzeroMercatorTrueScaleOverridesConflictingScale() {
        Proj projection = new Proj(
            "+proj=merc +lat_ts=30 +k=0 +lon_0=0 +ellps=WGS84 +units=m +no_defs");
        Point expected = project(projection, 10.0, 50.0);
        String projString = CRSSerializer.toProjString(projection);
        assertTrue(projString.contains("+lat_ts="), projString);
        assertFalse(projString.contains("+k_0="), projString);
        assertPointEquals(expected, project(new Proj(projString), 10.0, 50.0),
            1e-8, projString);
        for (String standard : standardFormats(projection)) {
            assertPointEquals(expected, project(new Proj(standard), 10.0, 50.0),
                1e-8, standard);
        }
    }

    @Test
    void truthyInvalidScaleIsPreservedOnlyByProj() {
        for (String scale : new String[]{"-0.5", "Infinity"}) {
            Proj projection = new Proj(
                "+proj=merc +k=" + scale + " +lon_0=0 +ellps=WGS84 +no_defs");
            String projString = CRSSerializer.toProjString(projection);
            assertTrue(projString.contains("+k_0=" + scale), projString);
            assertAllStandardsReject(projection);
        }
    }

    @Test
    void scaleOneProjectionFallbacksDefaultToOne() {
        String[] definitions = {
            "+proj=lcc +lat_0=39 +lat_1=33 +lat_2=45 +lon_0=-96 "
                + "+ellps=WGS84 +units=m +no_defs",
            "+proj=gstmerc +lat_0=-21.116666667 +lon_0=55.53333333 "
                + "+x_0=160000 +y_0=50000 +ellps=intl +units=m +no_defs",
            "+proj=omerc +lat_0=37.4769061 +lonc=141.0039618 +alpha=202.22 "
                + "+x_0=138 +y_0=77.65 +ellps=WGS84 +units=m +no_defs",
            "+proj=somerc +lat_0=46.95240555555556 +lon_0=7.439583333333333 "
                + "+x_0=600000 +y_0=200000 +ellps=bessel +units=m +no_defs",
            "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 "
                + "+x_0=155000 +y_0=463000 +ellps=bessel +units=m +no_defs",
            "+proj=gnom +lat_0=40 +lon_0=-100 +R=6371000 +units=m +no_defs",
            "+proj=tmerc +approx +lat_0=0 +lon_0=3 +ellps=WGS84 +units=m +no_defs"
        };
        for (String definition : definitions) {
            boolean approximateTm = definition.contains("+approx");
            for (String scale : new String[]{"0", "NaN"}) {
                Proj projection = new Proj(definition + " +k=" + scale);
                String projString = CRSSerializer.toProjString(projection);
                assertTrue(projString.contains("+k_0=1.0"), projString);
                if (approximateTm) {
                    assertDoesNotThrow(() -> CRSSerializer.toWkt1(projection));
                    assertThrows(UnsupportedOperationException.class,
                        () -> CRSSerializer.toWkt2(projection));
                    assertThrows(UnsupportedOperationException.class,
                        () -> CRSSerializer.toProjJson(projection));
                } else {
                    for (String standard : standardFormats(projection)) {
                        assertDoesNotThrow(() -> new Proj(standard), standard);
                    }
                }
            }
        }
    }

    @Test
    void cylindricalTrueScaleEdgeCasesAreNeverSilentlyChanged() {
        for (String code : new String[]{"cea", "eqc"}) {
            String base = "+proj=" + code
                + " +lat_ts=30 +R=6371000 +units=m +no_defs";
            Proj ignoredScale = new Proj(base + " +k=-0.5");
            Point expected = project(ignoredScale, 10.0, 20.0);
            for (String serialized : allFormats(ignoredScale)) {
                assertFalse(serialized.contains("+k_0="), serialized);
                assertFalse(serialized.toLowerCase().contains("scale_factor"), serialized);
                assertFalse(serialized.toLowerCase().contains("scale factor"), serialized);
                assertPointEquals(expected, project(new Proj(serialized), 10.0, 20.0),
                    1e-8, serialized);
            }
        }

        Proj ellipsoidalCeaInfinity = new Proj(
            "+proj=cea +lat_ts=Infinity +ellps=WGS84 +units=m +no_defs");
        String ceaProj = CRSSerializer.toProjString(ellipsoidalCeaInfinity);
        assertFalse(ceaProj.contains("+lat_ts="), ceaProj);
        Point expected = project(ellipsoidalCeaInfinity, 10.0, 20.0);
        for (String standard : standardFormats(ellipsoidalCeaInfinity)) {
            assertPointEquals(expected, project(new Proj(standard), 10.0, 20.0),
                1e-8, standard);
        }

        for (Proj projOnly : new Proj[]{
                new Proj("+proj=cea +lat_ts=Infinity +R=6371000 +units=m +no_defs"),
                new Proj("+proj=eqc +lat_ts=Infinity +R=6371000 +units=m +no_defs")}) {
            String serialized = CRSSerializer.toProjString(projOnly);
            assertTrue(serialized.contains("+lat_ts=Infinity"), serialized);
            assertAllStandardsReject(projOnly);
        }
    }

    @Test
    void outOfRangeTrueScaleLatitudesAreProjOnly() {
        for (String code : new String[]{"merc", "cea", "eqc"}) {
            for (String latitude : new String[]{"90", "100"}) {
                Proj projection = new Proj("+proj=" + code + " +lat_ts=" + latitude
                    + " +R=6371000 +no_defs");
                String projString = CRSSerializer.toProjString(projection);
                assertTrue(projString.contains("+lat_ts=" + latitude), projString);
                assertAllStandardsReject(projection);
            }
        }
    }

    @Test
    void polarStereographicSerializesItsInitializedScalePrecedence() {
        String[] variantACases = {
            "+proj=stere +lat_0=90 +lat_ts=90 +k=0.5 +R=6371000 +no_defs",
            "+proj=stere +lat_0=90 +lat_ts=-90 +k=0.5 +ellps=WGS84 +no_defs",
            "+proj=stere +lat_0=90 +lat_ts=0 +k=0.5 +ellps=WGS84 +no_defs",
            "+proj=stere +lat_0=90 +lat_ts=-70 +k=0.5 +ellps=WGS84 +no_defs"
        };
        Point input = new Point(10.0 * Values.D2R, 80.0 * Values.D2R);
        for (String definition : variantACases) {
            Proj original = new Proj(definition);
            Point expected = original.forward(new Point(input.x, input.y));
            String projString = CRSSerializer.toProjString(original);
            assertFalse(projString.contains("+lat_ts="), projString);
            assertTrue(projString.contains("+k_0="), projString);
            for (String serialized : allFormats(original)) {
                Point actual = new Proj(serialized).forward(new Point(input.x, input.y));
                assertPointEquals(expected, actual, 1e-7, serialized);
            }
        }

        for (String scale : new String[]{"0", "NaN"}) {
            Proj projection = new Proj(
                "+proj=stere +lat_0=90 +k=" + scale + " +ellps=WGS84 +no_defs");
            String projString = CRSSerializer.toProjString(projection);
            assertTrue(projString.contains("+k_0=1.0"), projString);
            for (String standard : standardFormats(projection)) {
                assertDoesNotThrow(() -> new Proj(standard), standard);
            }
        }
    }

    @Test
    void bareSemiMajorAxisRoundTripsAsSphere() {
        Proj original = new Proj("+proj=merc +a=6400000 +no_defs");
        String projString = CRSSerializer.toProjString(original);
        assertTrue(projString.contains("+a=6400000.0"), projString);
        assertTrue(original.getParams().sphere);
        assertTrue(projString.contains("+b=6400000.0"), projString);
        Proj projRoundTrip = new Proj(projString);
        assertEquals(original.getParams().a, projRoundTrip.getParams().a, 0.0, projString);
        assertEquals(original.getParams().b, projRoundTrip.getParams().b, 0.0, projString);
        assertEquals(original.getParams().sphere, projRoundTrip.getParams().sphere, projString);
        for (String serialized : standardFormats(original)) {
            Proj reimported = new Proj(serialized);
            assertEquals(original.getParams().a, reimported.getParams().a, 0.0, serialized);
            assertEquals(original.getParams().b, reimported.getParams().b, 0.0, serialized);
            assertEquals(original.getParams().sphere, reimported.getParams().sphere, serialized);
        }
    }

    @Test
    void explicitScaleOnePresenceSurvivesEveryFormat() {
        Proj omitted = new Proj(
            "+proj=tmerc +lat_0=0 +lon_0=3 +ellps=WGS84 +units=m +no_defs");
        Proj explicit = new Proj(
            "+proj=tmerc +lat_0=0 +lon_0=3 +k=1 +ellps=WGS84 +units=m +no_defs");
        assertFalse(omitted.getParams().k0Specified);
        assertTrue(explicit.getParams().k0Specified);
        for (String serialized : allFormats(explicit)) {
            assertTrue(new Proj(serialized).getParams().k0Specified, serialized);
        }
    }

    private static String[] standardFormats(Proj projection) {
        return new String[]{
            CRSSerializer.toWkt1(projection),
            CRSSerializer.toWkt2(projection),
            CRSSerializer.toProjJson(projection)
        };
    }

    private static String[] allFormats(Proj projection) {
        return new String[]{
            CRSSerializer.toProjString(projection),
            CRSSerializer.toWkt1(projection),
            CRSSerializer.toWkt2(projection),
            CRSSerializer.toProjJson(projection)
        };
    }

    private static void assertAllStandardsReject(Proj projection) {
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt1(projection));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt2(projection));
        assertThrows(UnsupportedOperationException.class,
            () -> CRSSerializer.toProjJson(projection));
    }

    private static Point project(Proj projection, double longitude, double latitude) {
        return projection.forward(new Point(longitude * Values.D2R, latitude * Values.D2R));
    }

    private static void assertPointEquals(
            Point expected, Point actual, double tolerance, String message) {
        assertEquals(expected.x, actual.x, tolerance, message + " x");
        assertEquals(expected.y, actual.y, tolerance, message + " y");
    }
}
