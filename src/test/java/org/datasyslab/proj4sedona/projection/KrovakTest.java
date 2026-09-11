package org.datasyslab.proj4sedona.projection;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.parser.WktParser;
import org.datasyslab.proj4sedona.transform.Converter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Krovak projection (krovak).
 *
 * <p>Krovak is always defined on the Bessel 1841 ellipsoid; the reference eastings/
 * northings are from proj4js 2.20.9 for the S-JTSK / Krovak definition, transformed
 * from Bessel longitude/latitude (same ellipsoid on both sides, no datum shift, so the
 * numbers exercise the projection math). The default negative easting/northing output is
 * the north-orientated form used by EPSG:5514. The traditional EPSG:5513 form uses
 * {@code +axis=swu} to expose positive southing/westing coordinates. Tolerance 0.01 m /
 * 1e-7&deg;.</p>
 */
class KrovakTest {

    private static final double XY_EPSLN = 0.01;
    private static final double LL_EPSLN = 1e-7;

    // EPSG:5514 / S-JTSK / Krovak, expressed on Greenwich.
    private static final String KROVAK_NO_SCALE =
        "+proj=krovak +lat_0=49.5 +lon_0=24.83333333333333 +alpha=30.28813972222222 "
            + "+x_0=0 +y_0=0 +ellps=bessel +pm=greenwich +units=m +no_defs";
    private static final String KROVAK = KROVAK_NO_SCALE + " +k=0.9999";
    private static final String BESSEL_LL = "+proj=longlat +ellps=bessel +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void testRegistry() {
        assertNotNull(ProjectionRegistry.get("krovak"));
        assertNotNull(ProjectionRegistry.get("Krovak"));
        assertNotNull(ProjectionRegistry.get("Krovak (North Orientated)"));
    }

    @Test
    void testKnownValues() {
        // ll (Bessel deg) -> easting, northing (m), per proj4js 2.20.9
        double[][] cases = {
            {14.42, 50.08, -743101.013895, -1043898.660356},
            {16.6, 49.2, -598786.141107, -1160206.215911},
            {17.1, 48.15, -574341.827532, -1280152.023098},
        };
        Converter conv = Proj4.proj4(BESSEL_LL, KROVAK);
        for (double[] c : cases) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            assertEquals(c[2], xy.x, XY_EPSLN, "easting for " + c[0] + "," + c[1]);
            assertEquals(c[3], xy.y, XY_EPSLN, "northing for " + c[0] + "," + c[1]);
        }
    }

    @Test
    void testRoundTrip() {
        Converter conv = Proj4.proj4(BESSEL_LL, KROVAK);
        double[][] coords = {{14.42, 50.08}, {16.6, 49.2}, {17.1, 48.15}, {18.9, 49.0}};
        for (double[] c : coords) {
            Point xy = conv.forward(new Point(c[0], c[1]));
            Point ll = conv.inverse(new Point(xy.x, xy.y));
            assertEquals(c[0], ll.x, LL_EPSLN, "lng for " + c[0]);
            assertEquals(c[1], ll.y, LL_EPSLN, "lat for " + c[1]);
        }
    }

    @Test
    void testFalseEastingNorthingApplied() {
        // proj4js's krovak ignores +x_0/+y_0; this port applies them (matching PROJ and
        // the codebase convention). A 500000 offset must shift the zero-offset result.
        Converter conv = Proj4.proj4(BESSEL_LL, KROVAK
            .replace("+x_0=0 +y_0=0", "+x_0=500000 +y_0=500000"));
        Point xy = conv.forward(new Point(14.42, 50.08));
        assertEquals(-743101.013895 + 500000, xy.x, XY_EPSLN, "easting with offset");
        assertEquals(-1043898.660356 + 500000, xy.y, XY_EPSLN, "northing with offset");
        Point ll = conv.inverse(new Point(xy.x, xy.y));
        assertEquals(14.42, ll.x, LL_EPSLN);
        assertEquals(50.08, ll.y, LL_EPSLN);
    }

    @Test
    void testDefaultScaleFactor() {
        Converter conv = Proj4.proj4(BESSEL_LL, KROVAK_NO_SCALE);
        ProjectionParams params = conv.getTo().getParams();
        assertFalse(params.k0Specified, "omission survives parsing/building");
        assertEquals(Krovak.DEFAULT_SCALE_FACTOR, params.k0, 0.0);
        assertEquals(49.5 * Math.PI / 180.0, params.lat0, 1e-15);
        assertEquals(24.83333333333333 * Math.PI / 180.0, params.long0, 1e-15);
        assertEquals(Krovak.CO_LATITUDE_OF_CONE_AXIS, params.alpha, 0.0);
        assertEquals(Krovak.LATITUDE_OF_PSEUDO_STANDARD_PARALLEL, params.lat1, 0.0);
        Point xy = conv.forward(new Point(14.42, 50.08));
        assertEquals(-743101.013895, xy.x, XY_EPSLN, "easting with default k0");
        assertEquals(-1043898.660356, xy.y, XY_EPSLN, "northing with default k0");
    }

    @Test
    void testScaleDefaultParityForZeroNanAndManualParams() {
        for (String token : new String[] {" +k=0", " +k_0=0"}) {
            Proj proj = new Proj(KROVAK_NO_SCALE + token);
            assertTrue(proj.getParams().k0Specified, token);
            assertEquals(Krovak.DEFAULT_SCALE_FACTOR, proj.getParams().k0, 0.0, token);
            assertTrue(CRSSerializer.toProjString(proj).contains("+k_0=0.9999"), token);
        }

        // ProjectionParams is public: a non-one value is supplied even when the
        // parser's explicit-input flag is unavailable. JavaScript treats Infinity as
        // truthy, while zero and NaN select Krovak's projection-specific default.
        for (double supplied : new double[] {0.5, Double.POSITIVE_INFINITY}) {
            ProjectionParams params = new ProjectionParams();
            params.k0 = supplied;
            new Krovak().init(params);
            assertEquals(supplied, params.k0, 0.0, "manual scale " + supplied);
        }
        ProjectionParams nan = new ProjectionParams();
        nan.k0 = Double.NaN;
        new Krovak().init(nan);
        assertEquals(Krovak.DEFAULT_SCALE_FACTOR, nan.k0, 0.0);

        ProjectionParams raw = new ProjectionParams();
        raw.projName = "krovak";
        raw.k0 = 0.5;
        assertTrue(CRSSerializer.toProjString(raw).contains("+k_0=0.5"));
    }

    @Test
    void testDirectParamsNonDefaultScaleIsPreserved() {
        ProjectionParams params = new ProjectionParams();
        params.lat0 = 49.5 * Math.PI / 180;
        params.long0 = 24.83333333333333 * Math.PI / 180;
        params.k0 = 0.5;
        assertFalse(params.k0Specified);

        Krovak direct = new Krovak();
        direct.init(params);
        Point actual = direct.forward(new Point(14.42 * Math.PI / 180, 50.08 * Math.PI / 180));
        Point expected = new Proj(KROVAK_NO_SCALE + " +k=0.5")
            .forward(new Point(14.42 * Math.PI / 180, 50.08 * Math.PI / 180));

        assertEquals(expected.x, actual.x, 1e-8);
        assertEquals(expected.y, actual.y, 1e-8);
    }

    @Test
    void testExplicitScaleOneSerializationRoundTrip() {
        Proj original = new Proj(KROVAK_NO_SCALE + " +k=1");
        assertTrue(original.getParams().k0Specified);
        assertEquals(1.0, original.getParams().k0, 0.0);

        Point input = new Point(14.42 * Math.PI / 180, 50.08 * Math.PI / 180);
        Point want = original.forward(input);
        assertEquals(-743175.331428, want.x, XY_EPSLN);
        assertEquals(-1044003.060662, want.y, XY_EPSLN);

        for (String serialized : new String[] {
                CRSSerializer.toProjString(original),
                CRSSerializer.toWkt1(original),
                CRSSerializer.toWkt2(original),
                CRSSerializer.toProjJson(original)}) {
            Proj reimported = new Proj(serialized);
            assertTrue(reimported.getParams().k0Specified, serialized);
            assertEquals(1.0, reimported.getParams().k0, 0.0, serialized);
            Point got = reimported.forward(input);
            assertEquals(want.x, got.x, XY_EPSLN, serialized);
            assertEquals(want.y, got.y, XY_EPSLN, serialized);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSerializationUsesCanonicalKrovakParameters() {
        Proj original = new Proj("+proj=krovak +ellps=bessel +units=m +no_defs");
        assertEquals(Krovak.DEFAULT_LATITUDE_OF_PROJECTION_CENTRE,
            original.getParams().lat0, 0.0);
        assertEquals(Krovak.DEFAULT_LONGITUDE_OF_ORIGIN,
            original.getParams().long0, 0.0);

        String projString = CRSSerializer.toProjString(original);
        assertTrue(projString.contains("+lat_0=49.49999999999999"), projString);
        assertTrue(projString.contains("+lon_0=24.83333333333333"), projString);
        assertTrue(projString.contains("+alpha=30.288139752777"), projString);
        assertTrue(projString.contains("+k_0=0.9999"), projString);
        assertFalse(projString.contains("+lat_1="), projString);

        String wkt1 = CRSSerializer.toWkt1(original);
        assertTrue(wkt1.contains("PROJECTION[\"Krovak\"]"), wkt1);
        assertTrue(wkt1.contains("PARAMETER[\"latitude_of_center\""), wkt1);
        assertTrue(wkt1.contains("PARAMETER[\"longitude_of_center\""), wkt1);
        assertTrue(wkt1.contains("PARAMETER[\"azimuth\""), wkt1);
        assertTrue(wkt1.contains("PARAMETER[\"pseudo_standard_parallel_1\",78.5"), wkt1);
        assertTrue(wkt1.contains("PARAMETER[\"scale_factor\",0.9999]"), wkt1);
        assertTrue(wkt1.contains("AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]"), wkt1);

        String wkt2 = CRSSerializer.toWkt2(original);
        assertTrue(wkt2.contains("METHOD[\"Krovak (North Orientated)\"]"), wkt2);
        assertTrue(wkt2.contains("PARAMETER[\"Co-latitude of cone axis\""), wkt2);
        assertTrue(wkt2.contains("PARAMETER[\"Latitude of pseudo standard parallel\",78.5"), wkt2);
        assertTrue(wkt2.contains("PARAMETER[\"Scale factor on pseudo standard parallel\",0.9999"), wkt2);

        String projJson = CRSSerializer.toProjJson(original);
        assertTrue(projJson.contains("\"name\": \"Krovak (North Orientated)\""), projJson);
        assertTrue(projJson.contains("\"name\": \"Co-latitude of cone axis\""), projJson);
        assertTrue(projJson.contains("\"name\": \"Latitude of pseudo standard parallel\""), projJson);
        assertTrue(projJson.contains("\"unit\": \"unity\""), projJson);
        assertTrue(projJson.contains("\"abbreviation\": \"E\""), projJson);

        assertCanonicalParameters(WktParser.parse(wkt1));
        assertCanonicalParameters(WktParser.parse(wkt2));
        Map<String, Object> jsonMap = new Gson().fromJson(projJson, Map.class);
        assertCanonicalParameters(WktParser.parse(jsonMap));
    }

    @Test
    void testOrientationSerializationAndEnforcedAxisRoundTrip() {
        Proj north = new Proj(KROVAK);
        Proj southWest = new Proj(KROVAK + " +axis=swu");
        Proj geographic = new Proj(BESSEL_LL);
        Point ll = new Point(14.42, 50.08);

        Point northXy = new Converter(geographic, north).forward(ll, true);
        Point southWestXy = new Converter(geographic, southWest).forward(ll, true);
        assertEquals(-743101.013895, northXy.x, XY_EPSLN);
        assertEquals(-1043898.660356, northXy.y, XY_EPSLN);
        assertEquals(1043898.660356, southWestXy.x, XY_EPSLN);
        assertEquals(743101.013895, southWestXy.y, XY_EPSLN);

        String southWkt1 = CRSSerializer.toWkt1(southWest);
        String southWkt2 = CRSSerializer.toWkt2(southWest);
        String southJson = CRSSerializer.toProjJson(southWest);
        assertTrue(southWkt1.contains("AXIS[\"Southing\",SOUTH],AXIS[\"Westing\",WEST]"), southWkt1);
        assertTrue(southWkt2.contains("METHOD[\"Krovak\"]"), southWkt2);
        assertTrue(southWkt2.contains("AXIS[\"southing\",south"), southWkt2);
        assertTrue(southJson.contains("\"direction\": \"south\""), southJson);
        assertTrue(southJson.contains("\"direction\": \"west\""), southJson);

        assertEnforcedAxisRoundTrip(northXy, new String[] {
            CRSSerializer.toProjString(north), CRSSerializer.toWkt1(north),
            CRSSerializer.toWkt2(north), CRSSerializer.toProjJson(north)
        });
        assertEnforcedAxisRoundTrip(southWestXy, new String[] {
            CRSSerializer.toProjString(southWest), southWkt1, southWkt2, southJson
        });
    }

    @Test
    void testSerializationRoundTrip() {
        Proj original = new Proj(KROVAK);
        double[][] coords = {{14.42, 50.08}, {16.6, 49.2}};
        for (String serialized : new String[] {
                CRSSerializer.toProjString(original),
                CRSSerializer.toWkt1(original),
                CRSSerializer.toWkt2(original),
                CRSSerializer.toProjJson(original)}) {
            Proj reimported = new Proj(serialized);
            for (double[] c : coords) {
                Point want = original.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
                Point got = reimported.forward(new Point(c[0] * Math.PI / 180, c[1] * Math.PI / 180));
                assertEquals(want.x, got.x, XY_EPSLN, "easting after re-import of " + serialized);
                assertEquals(want.y, got.y, XY_EPSLN, "northing after re-import of " + serialized);
            }
        }
    }

    private static void assertCanonicalParameters(ProjectionDef def) {
        assertEquals(Krovak.DEFAULT_LATITUDE_OF_PROJECTION_CENTRE, def.getLat0(), 1e-14);
        assertEquals(Krovak.DEFAULT_LONGITUDE_OF_ORIGIN, def.getLong0(), 1e-14);
        assertEquals(Krovak.CO_LATITUDE_OF_CONE_AXIS, def.getAlpha(), 1e-14);
        assertEquals(Krovak.LATITUDE_OF_PSEUDO_STANDARD_PARALLEL, def.getLat1(), 1e-14);
        assertEquals(Krovak.DEFAULT_SCALE_FACTOR, def.getK0(), 0.0);
        assertEquals(0.0, def.getX0(), 0.0);
        assertEquals(0.0, def.getY0(), 0.0);
    }

    private static void assertEnforcedAxisRoundTrip(Point expected, String[] definitions) {
        for (String definition : definitions) {
            Proj reimported = new Proj(definition);
            Point got = Proj4.proj4(BESSEL_LL, definition)
                .forward(new Point(14.42, 50.08), true);
            assertEquals(expected.x, got.x, XY_EPSLN, definition);
            assertEquals(expected.y, got.y, XY_EPSLN, definition);
            assertEquals(expected.x < 0 ? "enu" : "swu",
                reimported.getParams().axis, definition);
        }
    }
}
