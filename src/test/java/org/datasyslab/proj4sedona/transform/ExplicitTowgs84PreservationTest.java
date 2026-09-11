package org.datasyslab.proj4sedona.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.DatumParams;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An explicit datum operation in the definition (WKT {@code TOWGS84[...]} or PROJ
 * {@code +towgs84=}) must survive datum-registry defaults. The registry may fill in what the
 * definition left unspecified, but its grid list must never replace a transform the author
 * spelled out, because a grid-shift datum whose grids are not loaded cannot transform at all.
 */
class ExplicitTowgs84PreservationTest {

    private static final String ESRI_NAD27_PREFIX =
        "GEOGCS[\"GCS_North_American_1927\",DATUM[\"D_North_American_1927\","
        + "SPHEROID[\"Clarke_1866\",6378206.4,294.9786982]";
    private static final String ESRI_NAD27_SUFFIX =
        "],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]";

    private static final String ESRI_NAD27_TOWGS84_3 =
        ESRI_NAD27_PREFIX + ",TOWGS84[-8,160,176]" + ESRI_NAD27_SUFFIX;
    private static final String ESRI_NAD27_TOWGS84_7 =
        ESRI_NAD27_PREFIX + ",TOWGS84[-8,160,176,0.5,-0.3,0.2,1.2]" + ESRI_NAD27_SUFFIX;
    private static final String ESRI_NAD27_NO_TOWGS84 = ESRI_NAD27_PREFIX + ESRI_NAD27_SUFFIX;
    private static final String PROJ_NAD27_TOWGS84_3 =
        "+proj=longlat +datum=NAD27 +towgs84=-8,160,176 +no_defs";

    // Reference values from proj4sedona 0.1.6, where the Esri spelling was an unknown datum and
    // the explicit TOWGS84 was therefore the only operation applied.
    private static final double EXPECTED_LON_3 = -100.0004176222;
    private static final double EXPECTED_LAT_3 = 40.0000094828;
    private static final double EXPECTED_LON_7 = -100.0004103613;
    private static final double EXPECTED_LAT_7 = 39.9998578248;
    private static final double TOLERANCE = 1e-9;

    private static Point toWgs84(String crs) {
        return Proj4.transform(new Proj(crs), new Proj("EPSG:4326"), new Point(-100, 40));
    }

    private static DatumParams datumOf(String crs) {
        return new Proj(crs).getParams().datum;
    }

    @Test
    @DisplayName("Esri NAD27 WKT with a three-parameter TOWGS84 keeps the explicit transform")
    void esriNad27ThreeParamTowgs84IsPreserved() {
        DatumParams datum = datumOf(ESRI_NAD27_TOWGS84_3);
        assertTrue(datum.is3Param(), "explicit 3-parameter transform expected, got type " + datum.getDatumType());
        assertNull(datum.getNadgrids(), "registry grids must not override an explicit TOWGS84");

        Point result = toWgs84(ESRI_NAD27_TOWGS84_3);
        assertNotNull(result, "transform must not fail for lack of grid files");
        assertEquals(EXPECTED_LON_3, result.x, TOLERANCE);
        assertEquals(EXPECTED_LAT_3, result.y, TOLERANCE);
    }

    @Test
    @DisplayName("Esri NAD27 WKT with a seven-parameter TOWGS84 keeps the explicit transform")
    void esriNad27SevenParamTowgs84IsPreserved() {
        DatumParams datum = datumOf(ESRI_NAD27_TOWGS84_7);
        assertTrue(datum.is7Param(), "explicit 7-parameter transform expected, got type " + datum.getDatumType());
        assertNull(datum.getNadgrids(), "registry grids must not override an explicit TOWGS84");

        Point result = toWgs84(ESRI_NAD27_TOWGS84_7);
        assertNotNull(result, "transform must not fail for lack of grid files");
        assertEquals(EXPECTED_LON_7, result.x, TOLERANCE);
        assertEquals(EXPECTED_LAT_7, result.y, TOLERANCE);
    }

    @Test
    @DisplayName("PROJ +datum=NAD27 with an explicit +towgs84 keeps the explicit transform")
    void projStringDatumWithTowgs84IsPreserved() {
        DatumParams datum = datumOf(PROJ_NAD27_TOWGS84_3);
        assertTrue(datum.is3Param(), "explicit 3-parameter transform expected, got type " + datum.getDatumType());

        Point result = toWgs84(PROJ_NAD27_TOWGS84_3);
        assertNotNull(result, "transform must not fail for lack of grid files");
        assertEquals(EXPECTED_LON_3, result.x, TOLERANCE);
        assertEquals(EXPECTED_LAT_3, result.y, TOLERANCE);
    }

    @Test
    @DisplayName("Standard exports keep the explicit TOWGS84 and round-trip to the same transform")
    void exportsKeepExplicitTowgs84AndRoundTrip() {
        Proj original = new Proj(ESRI_NAD27_TOWGS84_3);

        String wkt1 = CRSSerializer.toWkt1(original);
        assertTrue(wkt1.contains("TOWGS84[-8,160,176"), wkt1);
        assertNotNull(CRSSerializer.toWkt2(original));
        assertNotNull(CRSSerializer.toProjJson(original));

        Proj reparsed = new Proj(wkt1);
        DatumParams datum = reparsed.getParams().datum;
        assertTrue(datum.is3Param(), "re-parsed WKT must keep the explicit transform, got type " + datum.getDatumType());
        assertNull(datum.getNadgrids());
        Point result = Proj4.transform(reparsed, new Proj("EPSG:4326"), new Point(-100, 40));
        assertNotNull(result);
        assertEquals(EXPECTED_LON_3, result.x, TOLERANCE);
        assertEquals(EXPECTED_LAT_3, result.y, TOLERANCE);
    }

    @Test
    @DisplayName("Esri NAD27 WKT without TOWGS84 still receives the registry's default grid list")
    void esriNad27WithoutTowgs84KeepsRegistryDefault() {
        DatumParams datum = datumOf(ESRI_NAD27_NO_TOWGS84);
        assertTrue(datum.isGridShift(), "registry default expected, got type " + datum.getDatumType());
        assertNotNull(datum.getNadgrids());
        assertTrue(datum.getNadgrids().contains("@conus"), datum.getNadgrids());
    }
}
