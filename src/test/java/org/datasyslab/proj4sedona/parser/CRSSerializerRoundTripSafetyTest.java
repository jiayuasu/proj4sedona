package org.datasyslab.proj4sedona.parser;

import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.datasyslab.proj4sedona.transform.Converter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for serializer fields whose omission changes coordinates. */
class CRSSerializerRoundTripSafetyTest {

    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    @Test
    void projJsonProjectedAxesKeepNamedAndCustomLinearFactors() {
        assertProjectedProjJsonUnitRoundTrip("+units=ft", "foot", 0.3048);
        assertProjectedProjJsonUnitRoundTrip(
            "+units=us-ft", "US survey foot", 1200.0 / 3937.0);
        assertProjectedProjJsonUnitRoundTrip("+to_meter=0.31", "unknown", 0.31);
    }

    private static void assertProjectedProjJsonUnitRoundTrip(
            String unitDefinition, String unitName, double factor) {
        Proj original = new Proj("+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 "
            + "+x_0=500000 +y_0=1000 +ellps=WGS84 " + unitDefinition + " +no_defs");
        String json = CRSSerializer.toProjJson(original, false);
        assertTrue(json.contains("\"name\":\"" + unitName + "\""), json);
        assertTrue(json.contains("\"conversion_factor\":" + factor), json);

        Proj reimported = new Proj(json);
        assertEquals(factor, reimported.getParams().toMeter, 0.0, json);

        Point input = new Point(10.0, 48.0);
        Point want = new Converter(new Proj(WGS84), original).forward(input);
        Point got = new Converter(new Proj(WGS84), reimported).forward(input);
        assertEquals(want.x, got.x, 1e-8, json);
        assertEquals(want.y, got.y, 1e-8, json);
    }

    @Test
    void derivedUtmZoneAndSouthSurviveProjExport() {
        Proj derived = new Proj(
            "+proj=utm +lon_0=9 +south +datum=WGS84 +units=m +no_defs");
        assertEquals(32, derived.getParams().zone);
        String serialized = CRSSerializer.toProjString(derived);
        assertTrue(serialized.contains("+zone=32"), serialized);
        assertTrue(serialized.contains("+south"), serialized);
        Proj reimported = new Proj(serialized);
        assertEquals(32, reimported.getParams().zone);
        assertEquals(10000000.0, reimported.getParams().y0, 0.0);

        // The two tokens are independent even for caller-assembled parameters.
        derived.getParams().zone = null;
        String zoneLess = CRSSerializer.toProjString(derived);
        assertFalse(zoneLess.contains("+zone="), zoneLess);
        assertTrue(zoneLess.contains("+south"), zoneLess);
    }

    @Test
    void twoPointOmercKeepsNoUoffInProjAndRejectsLossyStandards() {
        Proj twoPoint = new Proj(
            "+proj=omerc +lat_0=40 +lon_1=-74 +lat_1=40.5 +lon_2=-73 +lat_2=41 "
                + "+k=1 +no_uoff +ellps=WGS84 +units=m +no_defs");
        String projString = CRSSerializer.toProjString(twoPoint);
        assertTrue(projString.contains("+lon_1=-74"), projString);
        assertTrue(projString.contains("+lon_2=-73"), projString);
        assertTrue(projString.contains("+no_uoff"), projString);
        assertDoesNotThrow(() -> new Proj(projString));
        assertAllStandardsReject(twoPoint);
    }

    @Test
    void standardsRejectProjOnlyCoordinateOperations() {
        Proj[] unsupported = {
            new Proj("+proj=longlat +ellps=WGS84 +over +no_defs"),
            new Proj("+proj=longlat +ellps=WGS84 +lon_wrap=180 +no_defs"),
            new Proj("+proj=omerc +lat_0=4 +lonc=102.25 +alpha=323 +gamma=323 "
                + "+no_rot +ellps=WGS84 +no_defs"),
            new Proj("+proj=tpers +lat_0=40 +lon_0=-100 +h=5500000 +tilt=30 +azi=20 "
                + "+a=6378137 +b=6378137 +units=m +no_defs")
        };
        for (Proj proj : unsupported) {
            String lossless = CRSSerializer.toProjString(proj);
            assertDoesNotThrow(() -> new Proj(lossless), lossless);
            assertAllStandardsReject(proj);
        }
    }

    @Test
    void datumOperationsAreNeverSilentlyDropped() {
        Proj helmert = new Proj("+proj=longlat +datum=ch1903 +no_defs");
        String wkt1 = CRSSerializer.toWkt1(helmert);
        assertTrue(wkt1.contains("TOWGS84[674.374,15.056,405.346]"), wkt1);
        assertEquals(helmert.getParams().datum.getDatumParams()[0],
            new Proj(wkt1).getParams().datum.getDatumParams()[0], 0.0);
        assertThrows(UnsupportedOperationException.class, () -> CRSSerializer.toWkt2(helmert));
        assertThrows(UnsupportedOperationException.class, () -> CRSSerializer.toProjJson(helmert));

        Proj grid = new Proj("+proj=longlat +datum=NAD27 +no_defs");
        assertTrue(CRSSerializer.toProjString(grid).contains("+datum=NAD27"));
        assertAllStandardsReject(grid);
    }

    @Test
    void geocentricWktFailsInsteadOfEmittingInvalidProjectedCrs() {
        Proj geocentric = new Proj("+proj=geocent +datum=WGS84 +units=m +no_defs");
        assertThrows(UnsupportedOperationException.class, () -> CRSSerializer.toWkt1(geocentric));
        assertThrows(UnsupportedOperationException.class, () -> CRSSerializer.toWkt2(geocentric));
        Proj reimported = new Proj(CRSSerializer.toProjJson(geocentric));
        assertEquals("geocent", reimported.getParams().projName);
    }

    @Test
    void inferredAuthorityRequiresAllCoordinateSemanticsToMatch() {
        Proj datumOverride = new Proj(
            "+proj=longlat +datum=WGS84 +towgs84=100,0,0 +no_defs");
        Proj latitudeOfTrueScale = new Proj(
            "+proj=merc +a=6378137 +b=6378137 +lat_ts=30 +k=1 +units=m +no_defs");
        Proj footUtm = new Proj(
            "+proj=utm +zone=32 +datum=WGS84 +units=ft +no_defs");
        Proj reversedAxis = new Proj(
            "+proj=longlat +datum=WGS84 +axis=wsu +no_defs");
        Proj strayParallel = new Proj(
            "+proj=longlat +datum=WGS84 +lat_1=20 +no_defs");

        for (Proj candidate : new Proj[]{
                datumOverride, latitudeOfTrueScale, footUtm, reversedAxis, strayParallel}) {
            assertNull(CRSSerializer.toAuthority(candidate.getParams()),
                CRSSerializer.toProjString(candidate));
            if (!candidate.getParams().datum.isGridShift()
                    && !Boolean.TRUE.equals(candidate.getParams().over)
                    && candidate.getParams().longWrap == null) {
                try {
                    assertFalse(CRSSerializer.toProjJson(candidate).contains("\"id\""));
                } catch (UnsupportedOperationException expectedForDatumOperation) {
                    // Non-WKT1 datum operations are intentionally non-exportable.
                }
            }
        }

        assertEquals("EPSG:4326", CRSSerializer.toEpsgCode(
            new Proj("+proj=longlat +datum=WGS84 +no_defs")));
        assertEquals("EPSG:3857", CRSSerializer.toEpsgCode(
            new Proj("+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +k=1 +units=m")));
        assertEquals("EPSG:32632", CRSSerializer.toEpsgCode(
            new Proj("+proj=utm +zone=32 +datum=WGS84 +units=m")));
    }

    @Test
    void crsNameThatLooksLikeBuiltInAuthorityIsValidated() {
        String mislabeled = "GEOGCS[\"EPSG:4326\","
            + "DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],"
            + "PRIMEM[\"Paris\",2.33722917],UNIT[\"degree\",0.0174532925199433]]";
        Proj parsed = new Proj(mislabeled);
        assertEquals("EPSG:4326", parsed.getParams().srsCode,
            "the parser exposes why the display name needs validation");
        assertNull(CRSSerializer.toAuthority(parsed),
            "a Greenwich EPSG identifier cannot describe a Paris-meridian CRS");
        assertFalse(CRSSerializer.toProjJson(parsed).contains("\"id\""));
    }

    @Test
    void nonEnuHorizontalAxesRoundTripAndReproduceEnforcedCoordinates() {
        Proj original = new Proj(
            "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 "
                + "+ellps=WGS84 +units=m +axis=neu +no_defs");
        Point input = new Point(10.0, 48.0);
        Point baseline = new Converter(new Proj(WGS84), new Proj(
            "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 "
                + "+ellps=WGS84 +units=m +axis=enu +no_defs")).forward(input, true);
        Point expected = new Converter(new Proj(WGS84), original).forward(input, true);
        assertEquals(baseline.y, expected.x, 1e-8, "northing is first");
        assertEquals(baseline.x, expected.y, 1e-8, "easting is second");

        for (String serialized : new String[]{
                CRSSerializer.toWkt1(original),
                CRSSerializer.toWkt2(original),
                CRSSerializer.toProjJson(original)}) {
            Proj reimported = new Proj(serialized);
            assertEquals("neu", reimported.getParams().axis, serialized);
            Point got = new Converter(new Proj(WGS84), reimported).forward(input, true);
            assertEquals(expected.x, got.x, 1e-8, serialized);
            assertEquals(expected.y, got.y, 1e-8, serialized);
        }
    }

    @Test
    void geographicAxisOrderRoundTripsInEveryStandardFormat() {
        Proj original = new Proj("+proj=longlat +ellps=WGS84 +axis=neu +no_defs");
        for (String serialized : new String[]{
                CRSSerializer.toWkt1(original),
                CRSSerializer.toWkt2(original),
                CRSSerializer.toProjJson(original)}) {
            Proj reimported = new Proj(serialized);
            assertEquals("neu", reimported.getParams().axis, serialized);
            Point got = new Converter(new Proj(WGS84), reimported)
                .forward(new Point(2.0, 48.0), true);
            assertEquals(48.0, got.x, 1e-12, serialized);
            assertEquals(2.0, got.y, 1e-12, serialized);
        }

        Proj verticalFirst = new Proj(
            "+proj=longlat +ellps=WGS84 +axis=uen +no_defs");
        assertAllStandardsReject(verticalFirst);
    }

    @Test
    void defaultGeographicEnuAxisDoesNotBecomeLatitudeFirst() {
        Proj original = new Proj("+proj=longlat +ellps=WGS84 +axis=enu +no_defs");
        assertEquals("enu", new Proj(CRSSerializer.toWkt1(original)).getParams().axis);
        assertEquals("enu", new Proj(CRSSerializer.toWkt2(original)).getParams().axis);
        assertEquals("enu", new Proj(CRSSerializer.toProjJson(original)).getParams().axis);
    }

    @Test
    void parisPrimeMeridianRoundTripsEveryStandardFormat() {
        Proj paris = new Proj("+proj=longlat +ellps=WGS84 +pm=paris +no_defs");
        double expected = paris.getParams().fromGreenwich;
        String wkt1 = CRSSerializer.toWkt1(paris);
        String wkt2 = CRSSerializer.toWkt2(paris);
        String json = CRSSerializer.toProjJson(paris);
        assertTrue(wkt1.contains("PRIMEM[\"unknown\""), wkt1);
        assertTrue(wkt2.contains("PRIMEM[\"unknown\""), wkt2);
        assertTrue(json.contains("\"prime_meridian\""), json);
        for (String serialized : new String[]{wkt1, wkt2, json}) {
            assertEquals(expected, new Proj(serialized).getParams().fromGreenwich, 1e-11,
                serialized);
        }

        // Numeric longitude wins even when an outside producer mislabels it.
        String mislabeled = wkt2.replace("PRIMEM[\"unknown\"", "PRIMEM[\"Greenwich\"");
        assertEquals(expected, new Proj(mislabeled).getParams().fromGreenwich, 1e-11);
    }

    private static void assertAllStandardsReject(Proj proj) {
        assertThrows(UnsupportedOperationException.class, () -> CRSSerializer.toWkt1(proj));
        assertThrows(UnsupportedOperationException.class, () -> CRSSerializer.toWkt2(proj));
        assertThrows(UnsupportedOperationException.class, () -> CRSSerializer.toProjJson(proj));
    }
}
