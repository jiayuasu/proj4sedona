package org.datasyslab.proj4sedona.parser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CRSSerializer - exporting CRS definitions to various formats.
 */
class CRSSerializerTest {

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    // ==================== PROJ String Export Tests ====================

    /**
     * Compact ProjectedCRS PROJJSON whose axis unit is substituted per test.
     * PROJJSON/WKT carry authority unit names ("US survey foot"), not PROJ +units=
     * short codes ("us-ft"); PROJ's own CRS export resolves the emitted code by
     * conversion factor against its unit table (UnitOfMeasure::exportToPROJString)
     * and falls back to +to_meter= for unmatched linear factors.
     */
    private static final String PROJECTED_CRS_TEMPLATE = "{"
        + "\"type\": \"ProjectedCRS\", \"name\": \"unit test\","
        + "\"base_crs\": {\"type\": \"GeographicCRS\", \"name\": \"WGS 84\","
        + "  \"datum\": {\"type\": \"GeodeticReferenceFrame\", \"name\": \"World Geodetic System 1984\","
        + "    \"ellipsoid\": {\"name\": \"WGS 84\", \"semi_major_axis\": 6378137, \"inverse_flattening\": 298.257223563}}},"
        + "\"conversion\": {\"name\": \"unnamed\", \"method\": {\"name\": \"Transverse Mercator\"},"
        + "  \"parameters\": ["
        + "    {\"name\": \"Latitude of natural origin\", \"value\": 0, \"unit\": \"degree\"},"
        + "    {\"name\": \"Longitude of natural origin\", \"value\": 15, \"unit\": \"degree\"},"
        + "    {\"name\": \"Scale factor at natural origin\", \"value\": 0.9996, \"unit\": \"unity\"},"
        + "    {\"name\": \"False easting\", \"value\": 500000, \"unit\": \"metre\"},"
        + "    {\"name\": \"False northing\", \"value\": 0, \"unit\": \"metre\"}]},"
        + "\"coordinate_system\": {\"subtype\": \"Cartesian\", \"axis\": ["
        + "  {\"name\": \"Easting\", \"abbreviation\": \"E\", \"direction\": \"east\", \"unit\": UNIT},"
        + "  {\"name\": \"Northing\", \"abbreviation\": \"N\", \"direction\": \"north\", \"unit\": UNIT}]}"
        + "}";

    private static String projectedCrsWithUnit(String unitJson) {
        return PROJECTED_CRS_TEMPLATE.replace("UNIT", unitJson);
    }

    @Test
    @DisplayName("toProjString: metre unit object folds into the m default")
    void testToProjStringMetreObjectUnit() {
        Proj proj = new Proj(projectedCrsWithUnit(
            "{\"type\": \"LinearUnit\", \"name\": \"metre\", \"conversion_factor\": 1}"));
        String result = CRSSerializer.toProjString(proj);
        assertFalse(result.contains("+units="),
            "\"meter\" must not be emitted (PROJ's unit table keys metres as \"m\"): " + result);
        assertFalse(result.contains("+to_meter="), result);
    }

    @Test
    @DisplayName("toProjString: authority unit name resolves to the +units= short code by factor")
    void testToProjStringAuthorityUnitNameResolvesByFactor() {
        // As in PROJ-emitted PROJJSON for EPSG:2225 (NAD83 / California zone 1, ftUS).
        Proj proj = new Proj(projectedCrsWithUnit(
            "{\"type\": \"LinearUnit\", \"name\": \"US survey foot\", \"conversion_factor\": 0.304800609601219}"));
        String result = CRSSerializer.toProjString(proj);
        assertTrue(result.contains("+units=us-ft"),
            "factor 1200/3937 must resolve to the us-ft short code: " + result);
        assertFalse(result.contains("+units=us survey foot"), result);
        assertFalse(result.contains("+to_meter="), result);
    }

    @Test
    @DisplayName("toProjString: unmatched linear factor falls back to +to_meter=")
    void testToProjStringUnknownLinearUnitFallsBackToToMeter() {
        Proj proj = new Proj(projectedCrsWithUnit(
            "{\"type\": \"LinearUnit\", \"name\": \"local foot\", \"conversion_factor\": 0.31}"));
        String result = CRSSerializer.toProjString(proj);
        assertFalse(result.contains("+units="),
            "no unit-table entry has factor 0.31; the name must not be emitted: " + result);
        assertTrue(result.contains("+to_meter=0.31"), result);
    }

    @Test
    @DisplayName("toProjString: angular unit objects emit neither +units= nor +to_meter=")
    void testToProjStringAngularUnitObjectOmitted() {
        // As in PROJ-emitted PROJJSON for EPSG:4807 (NTF (Paris), grads). +units= has
        // no angular entries, and the angular factor must not leak into +to_meter=.
        String json = "{\"type\": \"GeographicCRS\", \"name\": \"grads test\","
            + "\"datum\": {\"type\": \"GeodeticReferenceFrame\", \"name\": \"World Geodetic System 1984\","
            + "  \"ellipsoid\": {\"name\": \"WGS 84\", \"semi_major_axis\": 6378137, \"inverse_flattening\": 298.257223563}},"
            + "\"coordinate_system\": {\"subtype\": \"ellipsoidal\", \"axis\": ["
            + "  {\"name\": \"Geodetic latitude\", \"abbreviation\": \"Lat\", \"direction\": \"north\","
            + "   \"unit\": {\"type\": \"AngularUnit\", \"name\": \"grad\", \"conversion_factor\": 0.0157079632679489}},"
            + "  {\"name\": \"Geodetic longitude\", \"abbreviation\": \"Lon\", \"direction\": \"east\","
            + "   \"unit\": {\"type\": \"AngularUnit\", \"name\": \"grad\", \"conversion_factor\": 0.0157079632679489}}]}"
            + "}";
        Proj proj = new Proj(json);
        String result = CRSSerializer.toProjString(proj);
        assertFalse(result.contains("+units="), result);
        assertFalse(result.contains("+to_meter="),
            "angular conversion factor must not become a linear +to_meter=: " + result);
    }

    // ---- Cases ported from PROJ's own test suite (test/unit/test_crs.cpp,
    // test_io.cpp at 9.5.1). Inputs are PROJ's verbatim; assertions cover the
    // unit tokens only — full-string equality would pin datum/ellipsoid name
    // resolution that differs from PROJ's EPSG database and is out of scope here.

    @Test
    @DisplayName("PROJ crs.EPSG_2222: WKT2 foot CS unit exports +units=ft")
    void testProjSuiteEpsg2222FootUnit() {
        // PROJ expects "+proj=tmerc ... +x_0=213360 ... +units=ft": the CS unit name
        // "foot" is not a +units= code; factor 0.3048 resolves to ft.
        String wkt2 = "PROJCRS[\"NAD83 / Arizona East (ft)\",BASEGEODCRS[\"NAD83\","
            + "DATUM[\"North American Datum 1983\","
            + "ELLIPSOID[\"GRS 1980\",6378137,298.257222101,LENGTHUNIT[\"metre\",1.0]]]],"
            + "CONVERSION[\"SPCS83 Arizona East zone (International feet)\","
            + "METHOD[\"Transverse Mercator\",ID[\"EPSG\",9807]],"
            + "PARAMETER[\"Latitude of natural origin\",31,ANGLEUNIT[\"degree\",0.01745329252]],"
            + "PARAMETER[\"Longitude of natural origin\",-110.166666666667,ANGLEUNIT[\"degree\",0.01745329252]],"
            + "PARAMETER[\"Scale factor at natural origin\",0.9999,SCALEUNIT[\"unity\",1.0]],"
            + "PARAMETER[\"False easting\",700000,LENGTHUNIT[\"foot\",0.3048]],"
            + "PARAMETER[\"False northing\",0,LENGTHUNIT[\"foot\",0.3048]]],"
            + "CS[cartesian,2],AXIS[\"easting (X)\",east,ORDER[1]],AXIS[\"northing (Y)\",north,ORDER[2]],"
            + "LENGTHUNIT[\"foot\",0.3048],ID[\"EPSG\",2222]]";
        String result = CRSSerializer.toProjString(new Proj(wkt2));
        assertTrue(result.contains("+units=ft"), result);
        assertFalse(result.contains("+units=foot"), result);
        assertFalse(result.contains("+to_meter="), result);
    }

    @Test
    @DisplayName("PROJ crs.EPSG_4807_as_PROJ_string: grads CRS exports no unit token")
    void testProjSuiteEpsg4807GradsNoUnitToken() {
        // PROJ expects "+proj=longlat +ellps=clrk80ign +pm=paris +no_defs" — no
        // +units=, no +to_meter=. Input is PROJ's WKT1_GDAL form of EPSG:4807
        // (crs.EPSG_4807_as_WKT1_GDAL); the WKT2 GEODCRS keyword is not yet parsed.
        String wkt1 = "GEOGCS[\"NTF (Paris)\",DATUM[\"Nouvelle_Triangulation_Francaise_Paris\","
            + "SPHEROID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627,AUTHORITY[\"EPSG\",\"7011\"]],"
            + "AUTHORITY[\"EPSG\",\"6807\"]],"
            + "PRIMEM[\"Paris\",2.33722917,AUTHORITY[\"EPSG\",\"8903\"]],"
            + "UNIT[\"grad\",0.015707963267949,AUTHORITY[\"EPSG\",\"9105\"]],"
            + "AUTHORITY[\"EPSG\",\"4807\"]]";
        String result = CRSSerializer.toProjString(new Proj(wkt1));
        assertFalse(result.contains("+units="), result);
        assertFalse(result.contains("+to_meter="), result);
    }

    @Test
    @DisplayName("PROJ io.projparse_projected_to_meter_known: factor recognized as us-ft")
    void testProjSuiteToMeterKnownFactor() {
        // PROJ resolves to_meter=0.304800609601219 to the US survey foot; on
        // proj-string re-export that is the us-ft code.
        String result = CRSSerializer.toProjString(
            new Proj("+proj=tmerc +to_meter=0.304800609601219"));
        assertTrue(result.contains("+units=us-ft"), result);
        assertFalse(result.contains("+to_meter="), result);
    }

    @Test
    @DisplayName("PROJ io.projparse_projected_to_meter_unknown: factor preserved as +to_meter=")
    void testProjSuiteToMeterUnknownFactor() {
        // PROJ keeps an unrecognized factor as LENGTHUNIT["unknown",0.1234]; on
        // proj-string re-export that is +to_meter=, never a made-up +units= token.
        String result = CRSSerializer.toProjString(new Proj("+proj=tmerc +to_meter=0.1234"));
        assertTrue(result.contains("+to_meter=0.1234"), result);
        assertFalse(result.contains("+units="), result);
    }

    @Test
    @DisplayName("toProjString: geographic CRS with any angular unit emits no unit token")
    void testToProjStringGeographicAngularUnitNoToken() {
        // PROJ branches on the unit's kind, not its name — a geographic CRS's unit is
        // angular by definition, so no name list can be complete (microradian, gon,
        // centesimal minute, ...). PROJ 9.5.1 exports this CRS as
        // "+proj=longlat +datum=WGS84 +no_defs". Also pins that the WKT parser's
        // internally derived to-metre artifact for angular units does not leak out
        // (it produced +to_meter=6.378137 here: 1e-6 x semi-major axis).
        String wkt = "GEOGCS[\"unknown\",DATUM[\"WGS_1984\","
            + "SPHEROID[\"WGS 84\",6378137,298.257223563]],"
            + "PRIMEM[\"Greenwich\",0],UNIT[\"microradian\",0.000001]]";
        String result = CRSSerializer.toProjString(new Proj(wkt));
        assertFalse(result.contains("+units="), result);
        assertFalse(result.contains("+to_meter="), result);
    }

    @Test
    @DisplayName("toProjString: explicit +to_meter= on longlat is preserved verbatim")
    void testToProjStringLonglatExplicitToMeterPreserved() {
        // PROJ parity: CRS.from_proj4("+proj=longlat +to_meter=0.3048").to_proj4()
        // keeps +to_meter=0.3048 — the factor must not be rewritten into a linear
        // +units= code on a geographic CRS.
        String result = CRSSerializer.toProjString(new Proj("+proj=longlat +to_meter=0.3048"));
        assertTrue(result.contains("+to_meter=0.3048"), result);
        assertFalse(result.contains("+units="), result);
    }

    @Test
    @DisplayName("toProjString: explicit +units= on longlat is dropped")
    void testToProjStringLonglatLinearUnitsDropped() {
        // PROJ parity: CRS.from_proj4("+proj=longlat +units=ft").to_proj4() drops the
        // linear unit — it has no meaning on a geographic CRS.
        String result = CRSSerializer.toProjString(new Proj("+proj=longlat +units=ft"));
        assertFalse(result.contains("+units="), result);
        assertFalse(result.contains("+to_meter="), result);
    }

    @Test
    @DisplayName("toWkt1: parsed \"meter\" spelling re-exports as EPSG-canonical \"metre\"")
    void testWkt1MeterSpellingNormalized() {
        Proj proj = new Proj(projectedCrsWithUnit(
            "{\"type\": \"LinearUnit\", \"name\": \"metre\", \"conversion_factor\": 1}"));
        String wkt = CRSSerializer.toWkt1(proj);
        assertTrue(wkt.contains("UNIT[\"metre\""),
            "unit name must round-trip to the canonical spelling: " + wkt);
        assertFalse(wkt.contains("\"meter\""), wkt);
    }

    @Test
    @DisplayName("toProjString: WGS84 Geographic")
    void testToProjStringWgs84() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=longlat"));
        assertTrue(result.contains("+datum=WGS84") || result.contains("+ellps=WGS84"));
        assertTrue(result.contains("+no_defs"));
    }

    @Test
    @DisplayName("toProjString: UTM Zone 10N")
    void testToProjStringUtm() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=utm") || result.contains("+proj=tmerc"));
        assertTrue(result.contains("+zone=10") || result.contains("+lon_0=-123"));
    }

    @Test
    @DisplayName("toProjString: Web Mercator")
    void testToProjStringWebMercator() {
        Proj proj = new Proj("EPSG:3857");
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=merc"));
        assertTrue(result.contains("+a=6378137"));
    }

    @Test
    @DisplayName("toProjString: sphere (a==b) round-trips with sphere preserved (#78)")
    void testToProjStringSphereRoundTrip() {
        // Sinusoidal branches on sphere vs ellipsoid; a sphere must survive the round trip.
        Proj sphere = new Proj("+proj=sinu +lon_0=0 +a=6371007 +b=6371007 +units=m +no_defs");
        String projString = CRSSerializer.toProjString(sphere);
        assertTrue(projString.contains("+b="), "sphere proj string must emit +b: " + projString);

        Proj reimported = new Proj(projString);
        assertTrue(reimported.getParams().sphere, "re-imported CRS must still be a sphere");

        Point want = sphere.forward(new Point(20 * Math.PI / 180, 10 * Math.PI / 180));
        Point got = reimported.forward(new Point(20 * Math.PI / 180, 10 * Math.PI / 180));
        assertEquals(want.x, got.x, 0.01, "easting after round trip");
        assertEquals(want.y, got.y, 0.01, "northing after round trip");
    }

    @Test
    @DisplayName("toProjString: Custom LCC projection")
    void testToProjStringLcc() {
        String lcc = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 " +
                "+x_0=0 +y_0=0 +datum=NAD83 +units=m +no_defs";
        Proj proj = new Proj(lcc);
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+proj=lcc"));
        assertTrue(result.contains("+lat_1="));
        assertTrue(result.contains("+lat_2="));
        assertTrue(result.contains("+lon_0="));
    }

    @Test
    @DisplayName("toProjString: Projection with scale factor")
    void testToProjStringWithScaleFactor() {
        String tmerc = "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84";
        Proj proj = new Proj(tmerc);
        String result = CRSSerializer.toProjString(proj);

        assertNotNull(result);
        assertTrue(result.contains("+k_0=0.9996") || result.contains("+k=0.9996"));
        assertTrue(result.contains("+x_0=500000"));
    }

    @Test
    @DisplayName("toProjString: null input returns null")
    void testToProjStringNull() {
        assertNull(CRSSerializer.toProjString((Proj) null));
    }

    // ==================== WKT1 Export Tests ====================

    @Test
    @DisplayName("toWkt1: Geographic CRS")
    void testToWkt1Geographic() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toWkt1(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("GEOGCS["));
        assertTrue(result.contains("DATUM["));
        assertTrue(result.contains("SPHEROID["));
        assertTrue(result.contains("PRIMEM["));
        assertTrue(result.contains("UNIT[\"degree\""));
    }

    @Test
    @DisplayName("toWkt1: Projected CRS (UTM)")
    void testToWkt1Projected() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toWkt1(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("PROJCS["));
        assertTrue(result.contains("GEOGCS["));
        assertTrue(result.contains("PROJECTION["));
        assertTrue(result.contains("PARAMETER["));
        assertTrue(result.contains("UNIT["));
    }

    @Test
    @DisplayName("toWkt1: Contains proper structure")
    void testToWkt1Structure() {
        Proj proj = new Proj("+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84");
        String result = CRSSerializer.toWkt1(proj);

        // Check for proper nesting
        assertTrue(result.contains("PARAMETER[\"latitude_of_origin\""));
        assertTrue(result.contains("PARAMETER[\"central_meridian\""));
        assertTrue(result.contains("PARAMETER[\"scale_factor\""));
        assertTrue(result.contains("PARAMETER[\"false_easting\""));
    }

    @Test
    @DisplayName("toWkt1: LCC with standard parallels")
    void testToWkt1Lcc() {
        String lcc = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=NAD83";
        Proj proj = new Proj(lcc);
        String result = CRSSerializer.toWkt1(proj);

        assertTrue(result.contains("PARAMETER[\"standard_parallel_1\""));
        assertTrue(result.contains("PARAMETER[\"standard_parallel_2\""));
    }

    @Test
    @DisplayName("toWkt1: null input returns null")
    void testToWkt1Null() {
        assertNull(CRSSerializer.toWkt1((Proj) null));
    }

    // ==================== WKT2 Export Tests ====================

    @Test
    @DisplayName("toWkt2: Geographic CRS")
    void testToWkt2Geographic() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toWkt2(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("GEOGCRS["));
        assertTrue(result.contains("DATUM["));
        assertTrue(result.contains("ELLIPSOID["));
        assertTrue(result.contains("PRIMEM["));
        assertTrue(result.contains("CS[ellipsoidal"));
        assertTrue(result.contains("AXIS["));
        assertTrue(result.contains("ANGLEUNIT["));
    }

    @Test
    @DisplayName("toWkt2: Projected CRS")
    void testToWkt2Projected() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toWkt2(proj);

        assertNotNull(result);
        assertTrue(result.startsWith("PROJCRS["));
        assertTrue(result.contains("BASEGEOGCRS["));
        assertTrue(result.contains("CONVERSION["));
        assertTrue(result.contains("METHOD["));
        assertTrue(result.contains("PARAMETER["));
        assertTrue(result.contains("CS[Cartesian"));
        assertTrue(result.contains("LENGTHUNIT["));
    }

    @Test
    @DisplayName("toWkt2: Contains unit specifications")
    void testToWkt2Units() {
        Proj proj = new Proj("+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84");
        String result = CRSSerializer.toWkt2(proj);

        assertTrue(result.contains("ANGLEUNIT[\"degree\""));
        assertTrue(result.contains("LENGTHUNIT[\"metre\""));
    }

    @Test
    @DisplayName("toWkt2: null input returns null")
    void testToWkt2Null() {
        assertNull(CRSSerializer.toWkt2((Proj) null));
    }

    // ==================== PROJJSON Export Tests ====================

    @Test
    @DisplayName("toProjJson: Geographic CRS")
    void testToProjJsonGeographic() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjJson(proj);

        assertNotNull(result);
        assertTrue(result.contains("\"type\": \"GeographicCRS\""));
        assertTrue(result.contains("\"datum\""));
        assertTrue(result.contains("\"ellipsoid\""));
        assertTrue(result.contains("\"semi_major_axis\""));
        assertTrue(result.contains("\"coordinate_system\""));
        assertTrue(result.contains("\"id\""));
        assertTrue(result.contains("\"authority\": \"EPSG\""));
        assertTrue(result.contains("\"code\": 4326"));
    }

    @Test
    @DisplayName("toProjJson: Projected CRS")
    void testToProjJsonProjected() {
        Proj proj = new Proj("EPSG:32610");
        String result = CRSSerializer.toProjJson(proj);

        assertNotNull(result);
        assertTrue(result.contains("\"type\": \"ProjectedCRS\""));
        assertTrue(result.contains("\"base_crs\""));
        assertTrue(result.contains("\"conversion\""));
        assertTrue(result.contains("\"method\""));
        assertTrue(result.contains("\"parameters\""));
        assertTrue(result.contains("\"id\""));
        assertTrue(result.contains("\"authority\": \"EPSG\""));
        assertTrue(result.contains("\"code\": 32610"));
    }

    @Test
    @DisplayName("toProjJson: Contains proper ellipsoid")
    void testToProjJsonEllipsoid() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjJson(proj);

        assertTrue(result.contains("\"semi_major_axis\": 6378137"));
        assertTrue(result.contains("\"inverse_flattening\""));
    }

    @Test
    @DisplayName("toProjJson: Compact format")
    void testToProjJsonCompact() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toProjJson(proj, false);

        assertNotNull(result);
        assertFalse(result.contains("\n")); // No newlines in compact format
        assertTrue(result.contains("\"type\":\"GeographicCRS\""));
        assertTrue(result.contains("\"authority\":\"EPSG\""));
        assertTrue(result.contains("\"code\":4326"));
    }

    @Test
    @DisplayName("toProjJson: Can be parsed back")
    void testToProjJsonRoundTrip() {
        Proj original = new Proj("EPSG:4326");
        String json = CRSSerializer.toProjJson(original);

        // Parse the JSON back
        Proj parsed = new Proj(json);

        assertNotNull(parsed);
        assertEquals("longlat", parsed.getParams().projName);
        assertEquals(original.getA(), parsed.getA(), 0.1);
    }

    @Test
    @DisplayName("toProjJson: null input returns null")
    void testToProjJsonNull() {
        assertNull(CRSSerializer.toProjJson((Proj) null));
    }

    @Test
    @DisplayName("toProjJson: id field present for EPSG CRS")
    void testToProjJsonIdFieldPresent() {
        // Geographic CRS from authority string
        Proj wgs84 = new Proj("EPSG:4326");
        String json4326 = CRSSerializer.toProjJson(wgs84);
        assertTrue(json4326.contains("\"id\""));
        assertTrue(json4326.contains("\"authority\": \"EPSG\""));
        assertTrue(json4326.contains("\"code\": 4326"));

        // Projected CRS from authority string
        Proj webMercator = new Proj("EPSG:3857");
        String json3857 = CRSSerializer.toProjJson(webMercator);
        assertTrue(json3857.contains("\"id\""));
        assertTrue(json3857.contains("\"authority\": \"EPSG\""));
        assertTrue(json3857.contains("\"code\": 3857"));
    }

    @Test
    @DisplayName("toProjJson: id field absent for custom projection")
    void testToProjJsonIdFieldAbsentForCustom() {
        // Custom projection with no known authority
        Proj custom = new Proj("+proj=lcc +lat_1=20 +lat_2=60 +lat_0=40 +lon_0=-96 +ellps=GRS80 +units=m");
        String json = CRSSerializer.toProjJson(custom);
        assertNotNull(json);
        assertFalse(json.contains("\"id\""), "Custom projection should not have an id field");
    }

    // ==================== EPSG Code Lookup Tests ====================

    @Test
    @DisplayName("toEpsgCode: WGS84")
    void testToEpsgCodeWgs84() {
        Proj proj = new Proj("+proj=longlat +datum=WGS84 +no_defs");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:4326", result);
    }

    @Test
    @DisplayName("toEpsgCode: Web Mercator")
    void testToEpsgCodeWebMercator() {
        Proj proj = new Proj("+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:3857", result);
    }

    @Test
    @DisplayName("toEpsgCode: UTM Zone 10N")
    void testToEpsgCodeUtm10N() {
        Proj proj = new Proj("+proj=utm +zone=10 +datum=WGS84 +units=m");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:32610", result);
    }

    @Test
    @DisplayName("toEpsgCode: UTM Zone 33S")
    void testToEpsgCodeUtm33S() {
        Proj proj = new Proj("+proj=utm +zone=33 +south +datum=WGS84 +units=m");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:32733", result);
    }

    @Test
    @DisplayName("toEpsgCode: Already has EPSG code")
    void testToEpsgCodeAlreadyHasCode() {
        Proj proj = new Proj("EPSG:4326");
        String result = CRSSerializer.toEpsgCode(proj);

        assertEquals("EPSG:4326", result);
    }

    @Test
    @DisplayName("toEpsgCode: Custom projection returns null")
    void testToEpsgCodeCustomProjection() {
        // Custom projection that doesn't match any EPSG
        Proj proj = new Proj("+proj=merc +lon_0=100 +k=0.5 +x_0=12345 +y_0=67890 +datum=WGS84");
        String result = CRSSerializer.toEpsgCode(proj);

        assertNull(result);
    }

    @Test
    @DisplayName("toEpsgCode: null input returns null")
    void testToEpsgCodeNull() {
        assertNull(CRSSerializer.toEpsgCode((Proj) null));
    }

    // ==================== PROJJSON EPSG Code Tests ====================

    @Test
    @DisplayName("toEpsgCode: NAD83(2011) full PROJJSON with id.code=6318")
    void testToEpsgCodeNad83ProjJson() {
        String nad83 = "{\"type\":\"GeographicCRS\",\"name\":\"NAD83(2011)\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"NAD83 (National Spatial Reference System 2011)\","
            + "\"ellipsoid\":{\"name\":\"GRS 1980\",\"semi_major_axis\":6378137,\"inverse_flattening\":298.257222101}},"
            + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\",\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
            + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]},"
            + "\"id\":{\"authority\":\"EPSG\",\"code\":6318}}";
        Proj proj = new Proj(nad83);
        assertEquals("EPSG:6318", proj.toEpsgCode());
    }

    @Test
    @DisplayName("toEpsgCode: UTM 32N full PROJJSON with id.code=32632")
    void testToEpsgCodeUtm32nFullProjJson() {
        String utmFull = "{\"type\":\"ProjectedCRS\",\"name\":\"WGS 84 / UTM zone 32N\","
            + "\"base_crs\":{\"name\":\"WGS 84\",\"datum\":{\"type\":\"GeodeticReferenceFrame\","
            + "\"name\":\"World Geodetic System 1984\","
            + "\"ellipsoid\":{\"name\":\"WGS 84\",\"semi_major_axis\":6378137,\"inverse_flattening\":298.257223563}}},"
            + "\"conversion\":{\"name\":\"UTM zone 32N\",\"method\":{\"name\":\"Transverse Mercator\"},"
            + "\"parameters\":[{\"name\":\"Latitude of natural origin\",\"value\":0},"
            + "{\"name\":\"Longitude of natural origin\",\"value\":9},"
            + "{\"name\":\"Scale factor at natural origin\",\"value\":0.9996},"
            + "{\"name\":\"False easting\",\"value\":500000},"
            + "{\"name\":\"False northing\",\"value\":0}]},"
            + "\"coordinate_system\":{\"subtype\":\"Cartesian\",\"axis\":[{\"name\":\"Easting\",\"direction\":\"east\",\"unit\":\"metre\"},"
            + "{\"name\":\"Northing\",\"direction\":\"north\",\"unit\":\"metre\"}]},"
            + "\"id\":{\"authority\":\"EPSG\",\"code\":32632}}";
        Proj proj = new Proj(utmFull);
        assertEquals("EPSG:32632", proj.toEpsgCode());
    }

    @Test
    @DisplayName("toEpsgCode: No id field with unknown datum returns null (not 4326)")
    void testToEpsgCodeNoIdGrs80() {
        String noId = "{\"type\":\"GeographicCRS\",\"name\":\"Unknown CRS\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"Unknown datum\","
            + "\"ellipsoid\":{\"name\":\"GRS 1980\",\"semi_major_axis\":6378137,\"inverse_flattening\":298.257222101}},"
            + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\",\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
            + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]}}";
        Proj proj = new Proj(noId);
        // "Unknown datum" is not a recognized datum name, so datumCodesMatch() rejects the
        // match. This matches pyproj behavior (confidence 60% < 70% threshold → None).
        assertNull(proj.toEpsgCode());
    }

    @Test
    @DisplayName("toEpsgCode: IAU authority returns null for toEpsgCode")
    void testToEpsgCodeIauAuthority() {
        String iau = "{\"type\":\"GeographicCRS\",\"name\":\"Mars\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"Mars\","
            + "\"ellipsoid\":{\"name\":\"Mars\",\"semi_major_axis\":3396190,\"inverse_flattening\":169.89444722361179}},"
            + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\",\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
            + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]},"
            + "\"id\":{\"authority\":\"IAU\",\"code\":49900}}";
        Proj proj = new Proj(iau);
        // IAU is not EPSG -> toEpsgCode should return null
        assertNull(proj.toEpsgCode());
    }

    @Test
    @DisplayName("toEpsgCode: UTM 32N minimal PROJJSON throws (incomplete)")
    void testToEpsgCodeMinimalProjJsonThrows() {
        String utmMin = "{\"type\":\"ProjectedCRS\",\"name\":\"WGS 84 / UTM zone 32N\","
            + "\"id\":{\"authority\":\"EPSG\",\"code\":32632}}";
        // Minimal PROJJSON without full CRS definition should throw
        assertThrows(IllegalArgumentException.class, () -> new Proj(utmMin));
    }

    // ==================== toAuthority Tests ====================

    @Test
    @DisplayName("toAuthority: EPSG:4326 string")
    void testToAuthorityEpsg4326() {
        Proj proj = new Proj("EPSG:4326");
        String[] auth = proj.toAuthority();
        assertNotNull(auth);
        assertArrayEquals(new String[]{"EPSG", "4326"}, auth);
    }

    @Test
    @DisplayName("toAuthority: EPSG:32632 string")
    void testToAuthorityEpsg32632() {
        Proj proj = new Proj("EPSG:32632");
        String[] auth = proj.toAuthority();
        assertNotNull(auth);
        assertArrayEquals(new String[]{"EPSG", "32632"}, auth);
    }

    @Test
    @DisplayName("toAuthority: NAD83(2011) PROJJSON returns EPSG:6318")
    void testToAuthorityNad83ProjJson() {
        String nad83 = "{\"type\":\"GeographicCRS\",\"name\":\"NAD83(2011)\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"NAD83 (National Spatial Reference System 2011)\","
            + "\"ellipsoid\":{\"name\":\"GRS 1980\",\"semi_major_axis\":6378137,\"inverse_flattening\":298.257222101}},"
            + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\",\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
            + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]},"
            + "\"id\":{\"authority\":\"EPSG\",\"code\":6318}}";
        Proj proj = new Proj(nad83);
        String[] auth = proj.toAuthority();
        assertNotNull(auth);
        assertArrayEquals(new String[]{"EPSG", "6318"}, auth);
    }

    @Test
    @DisplayName("toAuthority: IAU:49900 returns IAU authority")
    void testToAuthorityIau() {
        String iau = "{\"type\":\"GeographicCRS\",\"name\":\"Mars\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"Mars\","
            + "\"ellipsoid\":{\"name\":\"Mars\",\"semi_major_axis\":3396190,\"inverse_flattening\":169.89444722361179}},"
            + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\",\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
            + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]},"
            + "\"id\":{\"authority\":\"IAU\",\"code\":49900}}";
        Proj proj = new Proj(iau);
        String[] auth = proj.toAuthority();
        assertNotNull(auth);
        assertArrayEquals(new String[]{"IAU", "49900"}, auth);
    }

    @Test
    @DisplayName("toAuthority: WGS84 PROJ string matches by parameters")
    void testToAuthorityWgs84ProjString() {
        Proj proj = new Proj("+proj=longlat +datum=WGS84 +no_defs");
        String[] auth = proj.toAuthority();
        assertNotNull(auth);
        assertArrayEquals(new String[]{"EPSG", "4326"}, auth);
    }

    @Test
    @DisplayName("toAuthority: null returns null")
    void testToAuthorityNull() {
        assertNull(CRSSerializer.toAuthority((Proj) null));
    }

    @Test
    @DisplayName("toAuthority: No id, unknown datum returns null")
    void testToAuthorityNoIdUnknownDatum() {
        String noId = "{\"type\":\"GeographicCRS\",\"name\":\"Unknown CRS\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"Unknown datum\","
            + "\"ellipsoid\":{\"name\":\"GRS 1980\",\"semi_major_axis\":6378137,\"inverse_flattening\":298.257222101}},"
            + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\",\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
            + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]}}";
        Proj proj = new Proj(noId);
        // "Unknown datum" is unresolvable → datum mismatch → null (matches pyproj behavior)
        assertNull(proj.toAuthority());
    }

    // ==================== Datum Name Lookup Tests ====================

    @Test
    @DisplayName("toEpsgCode: NAD83(2011) no id, identified by datum name")
    void testToEpsgCodeNad83ByDatumName() {
        // NAD83(2011) with known datum name but no id field
        // pyproj identifies this as EPSG:6318 by datum name
        String nad83NoId = "{\"type\":\"GeographicCRS\",\"name\":\"NAD83(2011)\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"NAD83 (National Spatial Reference System 2011)\","
            + "\"ellipsoid\":{\"name\":\"GRS 1980\",\"semi_major_axis\":6378137,\"inverse_flattening\":298.257222101}},"
            + "\"coordinate_system\":{\"subtype\":\"ellipsoidal\",\"axis\":[{\"name\":\"Lat\",\"direction\":\"north\",\"unit\":\"degree\"},"
            + "{\"name\":\"Lon\",\"direction\":\"east\",\"unit\":\"degree\"}]}}";
        Proj proj = new Proj(nad83NoId);
        String result = proj.toEpsgCode();
        // The datum name "NAD83 (National Spatial Reference System 2011)" should be in our
        // lookup table and should deterministically resolve to EPSG:6318.
        assertEquals("EPSG:6318", result,
            "Expected EPSG:6318, got: " + result);
    }

    // ==================== Round-trip Tests ====================

    @Test
    @DisplayName("Round-trip: PROJ -> WKT1 -> PROJ")
    void testRoundTripProjWkt1() {
        String original = "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84";
        Proj proj1 = new Proj(original);
        
        String wkt1 = CRSSerializer.toWkt1(proj1);
        Proj proj2 = new Proj(wkt1);
        
        // WKT uses full method names, so projName will be different
        // Compare ellipsoid and scale factor instead
        assertEquals(proj1.getA(), proj2.getA(), 0.1);
        assertEquals(proj1.getParams().k0, proj2.getParams().k0, 1e-6);
    }

    @Test
    @DisplayName("Round-trip: PROJ -> PROJJSON -> PROJ")
    void testRoundTripProjJson() {
        String original = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84";
        Proj proj1 = new Proj(original);
        
        String json = CRSSerializer.toProjJson(proj1);
        Proj proj2 = new Proj(json);
        
        // PROJJSON uses full method names, so compare ellipsoid instead
        assertEquals(proj1.getA(), proj2.getA(), 0.1);
    }

    @Test
    @DisplayName("Round-trip: EPSG -> all formats -> verify")
    void testRoundTripAllFormats() {
        Proj original = new Proj("EPSG:32610");
        
        // Export to all formats
        String projStr = CRSSerializer.toProjString(original);
        String wkt1 = CRSSerializer.toWkt1(original);
        String wkt2 = CRSSerializer.toWkt2(original);
        String json = CRSSerializer.toProjJson(original);
        String epsg = CRSSerializer.toEpsgCode(original);
        
        // All should be non-null
        assertNotNull(projStr);
        assertNotNull(wkt1);
        assertNotNull(wkt2);
        assertNotNull(json);
        assertNotNull(epsg);
        
        // Parse back from each format and verify ellipsoid
        Proj fromProj = new Proj(projStr);
        Proj fromWkt1 = new Proj(wkt1);
        Proj fromJson = new Proj(json);
        
        // All should have same ellipsoid
        assertEquals(original.getA(), fromProj.getA(), 0.1);
        assertEquals(original.getA(), fromWkt1.getA(), 0.1);
        assertEquals(original.getA(), fromJson.getA(), 0.1);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Export: Projection with all parameters")
    void testExportFullProjection() {
        // Use a projection that's supported (LCC instead of omerc)
        String full = "+proj=lcc +lat_0=4 +lon_0=115 +lat_1=33 +lat_2=45 " +
                "+k=0.99984 +x_0=590476.87 +y_0=442857.65 +ellps=GRS80 +units=m";
        Proj proj = new Proj(full);
        
        String projStr = CRSSerializer.toProjString(proj);
        String wkt1 = CRSSerializer.toWkt1(proj);
        String json = CRSSerializer.toProjJson(proj);
        
        assertNotNull(projStr);
        assertNotNull(wkt1);
        assertNotNull(json);
    }

    @Test
    @DisplayName("Export: Spherical projection")
    void testExportSpherical() {
        // Use the sphere ellipsoid which is registered
        String sphere = "+proj=merc +ellps=sphere +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m";
        Proj proj = new Proj(sphere);
        
        String projStr = CRSSerializer.toProjString(proj);
        // Should contain ellps=sphere or the a value
        assertTrue(projStr.contains("+ellps=sphere") || projStr.contains("+a=6370997"));
        
        String json = CRSSerializer.toProjJson(proj);
        assertTrue(json.contains("6370997"));
    }

    @Test
    @DisplayName("Export: Non-meter units")
    void testExportNonMeterUnits() {
        String usFeet = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=NAD83 +units=us-ft";
        Proj proj = new Proj(usFeet);
        
        String projStr = CRSSerializer.toProjString(proj);
        assertTrue(projStr.contains("+units=us-ft"));
        
        String wkt1 = CRSSerializer.toWkt1(proj);
        assertTrue(wkt1.contains("US survey foot") || wkt1.contains("us-ft"));
    }

    // ==================== Issue #44: Polar Stereographic lat_ts round-trip ====================

    @Test
    @DisplayName("Issue #44: lat_ts preserved in PROJ round-trip via WKT1")
    void testLatTsPreservedProjWkt1RoundTrip() {
        // Antarctic Polar Stereographic: lat_0=-90, lat_ts=-71
        String input = "+proj=stere +lat_0=-90 +lat_ts=-71 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
        Proj proj = new Proj(input);

        // Export to WKT1
        String wkt1 = CRSSerializer.toWkt1(proj);
        assertNotNull(wkt1);
        // WKT1 should contain standard_parallel_1 with -71
        assertTrue(wkt1.contains("standard_parallel_1") || wkt1.contains("Standard_Parallel_1"),
                "WKT1 should contain standard_parallel_1 parameter");
        assertTrue(wkt1.contains("-71"), "WKT1 should preserve lat_ts value of -71");

        // Re-import and re-export to PROJ
        Proj reimported = new Proj(wkt1);
        String projRoundTrip = CRSSerializer.toProjString(reimported);
        assertTrue(projRoundTrip.contains("+lat_ts=-71"),
                "Round-tripped PROJ string should preserve +lat_ts=-71, got: " + projRoundTrip);
    }

    @Test
    @DisplayName("Issue #44: lat_ts preserved in PROJ round-trip via WKT2")
    void testLatTsPreservedProjWkt2RoundTrip() {
        // Arctic Polar Stereographic: lat_0=90, lat_ts=70
        String input = "+proj=stere +lat_0=90 +lat_ts=70 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
        Proj proj = new Proj(input);

        // Export to WKT2
        String wkt2 = CRSSerializer.toWkt2(proj);
        assertNotNull(wkt2);
        // WKT2 should contain "Latitude of standard parallel" with 70
        assertTrue(wkt2.contains("Latitude of standard parallel") || wkt2.contains("standard parallel"),
                "WKT2 should contain standard parallel parameter");
        assertTrue(wkt2.contains("70"), "WKT2 should preserve lat_ts value of 70");

        // Re-import and re-export to PROJ
        Proj reimported = new Proj(wkt2);
        String projRoundTrip = CRSSerializer.toProjString(reimported);
        assertTrue(projRoundTrip.contains("+lat_ts=70"),
                "Round-tripped PROJ string should preserve +lat_ts=70, got: " + projRoundTrip);
    }

    @Test
    @DisplayName("Issue #44: lat_ts preserved in PROJ round-trip via PROJJSON")
    void testLatTsPreservedProjJsonRoundTrip() {
        // Antarctic Polar Stereographic: lat_0=-90, lat_ts=-71
        String input = "+proj=stere +lat_0=-90 +lat_ts=-71 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
        Proj proj = new Proj(input);

        // Export to PROJJSON
        String projJson = CRSSerializer.toProjJson(proj);
        assertNotNull(projJson);
        assertTrue(projJson.contains("Latitude of standard parallel") || projJson.contains("standard parallel"),
                "PROJJSON should contain standard parallel parameter");

        // Re-import and re-export to PROJ
        Proj reimported = new Proj(projJson);
        String projRoundTrip = CRSSerializer.toProjString(reimported);
        assertTrue(projRoundTrip.contains("+lat_ts=-71"),
                "Round-tripped PROJ string should preserve +lat_ts=-71, got: " + projRoundTrip);
    }

    @Test
    @DisplayName("Issue #44: Mercator lat_ts preserved in WKT1 round-trip")
    void testMercatorLatTsPreservedWkt1RoundTrip() {
        String input = "+proj=merc +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
        Proj proj = new Proj(input);

        String wkt1 = CRSSerializer.toWkt1(proj.getParams());
        assertNotNull(wkt1);
        assertTrue(wkt1.contains("30"), "WKT1 should contain lat_ts value of 30");

        Proj reimported = new Proj(wkt1);
        String projRoundTrip = CRSSerializer.toProjString(reimported);
        // Assert numerically with epsilon to avoid flaky string matching on float representation
        assertNotNull(reimported.getParams().latTs, "Mercator round-trip should preserve latTs");
        assertEquals(Math.toRadians(30), reimported.getParams().latTs, 1e-9,
                "Mercator round-trip latTs should be ~30 degrees, got: " + Math.toDegrees(reimported.getParams().latTs));
        // lat_0 should not be corrupted to 30 (it should be 0 or absent)
        assertTrue(reimported.getParams().lat0 == null || Math.abs(reimported.getParams().lat0) < 1e-9,
                "Mercator lat_0 should be 0 or absent, got: " + (reimported.getParams().lat0 != null ? Math.toDegrees(reimported.getParams().lat0) : "null"));
    }

    @Test
    @DisplayName("Issue #44: CEA lat_ts preserved in WKT2 round-trip")
    void testCeaLatTsPreservedWkt2RoundTrip() {
        String input = "+proj=cea +lat_ts=30 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
        Proj proj = new Proj(input);

        String wkt2 = CRSSerializer.toWkt2(proj);
        assertNotNull(wkt2);

        Proj reimported = new Proj(wkt2);
        // Assert numerically with epsilon to avoid flaky string matching on float representation
        assertNotNull(reimported.getParams().latTs, "CEA round-trip should preserve latTs");
        assertEquals(Math.toRadians(30), reimported.getParams().latTs, 1e-9,
                "CEA round-trip latTs should be ~30 degrees, got: " + Math.toDegrees(reimported.getParams().latTs));
    }

    @Test
    @DisplayName("Issue #44: EQC lat_ts preserved in PROJJSON round-trip")
    void testEqcLatTsPreservedProjJsonRoundTrip() {
        String input = "+proj=eqc +lat_ts=45 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m";
        Proj proj = new Proj(input);

        String projJson = CRSSerializer.toProjJson(proj);
        assertNotNull(projJson);

        Proj reimported = new Proj(projJson);
        String projRoundTrip = CRSSerializer.toProjString(reimported);
        assertTrue(projRoundTrip.contains("+lat_ts=45"),
                "EQC round-trip should preserve +lat_ts=45, got: " + projRoundTrip);
    }

    // ==================== Issue #45: Named ellipsoid round-trip ====================

    @Test
    @DisplayName("Issue #45: Named ellipsoid 'airy' preserved in PROJ round-trip via WKT1")
    void testNamedEllipsoidAiryRoundTrip() {
        // EPSG:27700 - British National Grid uses +ellps=airy
        String input = "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.9996012717 +x_0=400000 +y_0=-100000 +ellps=airy +units=m +no_defs";
        Proj proj = new Proj(input);

        // Round-trip: PROJ -> WKT1 -> PROJ
        String wkt1 = CRSSerializer.toWkt1(proj);
        assertNotNull(wkt1);

        Proj reimported = new Proj(wkt1);
        String projRoundTrip = CRSSerializer.toProjString(reimported);

        assertTrue(projRoundTrip.contains("+ellps=airy"),
                "Round-trip should preserve +ellps=airy, got: " + projRoundTrip);
        assertFalse(projRoundTrip.contains("+a="),
                "Round-trip should not contain raw +a= when named ellipsoid matches, got: " + projRoundTrip);
    }

    @Test
    @DisplayName("Issue #45: Named ellipsoid 'krass' preserved in PROJ round-trip")
    void testNamedEllipsoidKrassRoundTrip() {
        // Krassovsky 1942 - previously not in the hardcoded lookup list
        String input = "+proj=longlat +ellps=krass +no_defs";
        Proj proj = new Proj(input);

        String projRoundTrip = CRSSerializer.toProjString(proj);
        assertTrue(projRoundTrip.contains("+ellps=krass"),
                "Should preserve +ellps=krass, got: " + projRoundTrip);
    }

    @Test
    @DisplayName("Issue #45: Named ellipsoid 'bessel' preserved in PROJ round-trip via WKT1")
    void testNamedEllipsoidBesselRoundTrip() {
        String input = "+proj=tmerc +lat_0=0 +lon_0=9 +k=1 +x_0=3500000 +y_0=0 +ellps=bessel +units=m +no_defs";
        Proj proj = new Proj(input);

        String wkt1 = CRSSerializer.toWkt1(proj);
        Proj reimported = new Proj(wkt1);
        String projRoundTrip = CRSSerializer.toProjString(reimported);

        assertTrue(projRoundTrip.contains("+ellps=bessel"),
                "Round-trip should preserve +ellps=bessel, got: " + projRoundTrip);
    }

    // ==================== Issue #46: WKT2 floating-point drift ====================

    @Test
    @DisplayName("Issue #46: WKT2 round-trip should not drift lat_0 for EPSG:28992 (sterea)")
    void testWkt2RoundTripNoDriftSterea28992() {
        // EPSG:28992 - Amersfoort / RD New (Oblique Stereographic)
        Proj proj = new Proj("EPSG:28992");

        // First conversion: PROJ -> WKT2
        String wkt2First = CRSSerializer.toWkt2(proj);
        assertNotNull(wkt2First);

        // Extract lat_0 from the internal representation
        double lat0Original = proj.getParams().getLat0();

        // Round-trip: WKT2 -> PROJ -> get lat_0
        Proj reimported = new Proj(wkt2First);
        double lat0RoundTrip1 = reimported.getParams().getLat0();

        // The latitude of natural origin should not drift across round-trips
        assertEquals(lat0Original, lat0RoundTrip1,
                "lat_0 should be identical after first WKT2 round-trip (no floating-point drift)");

        // Second round-trip: PROJ -> WKT2 -> PROJ -> get lat_0
        String wkt2Second = CRSSerializer.toWkt2(reimported);
        Proj reimported2 = new Proj(wkt2Second);
        double lat0RoundTrip2 = reimported2.getParams().getLat0();

        assertEquals(lat0Original, lat0RoundTrip2,
                "lat_0 should be identical after second WKT2 round-trip (no cumulative drift)");
    }

    // ==================== Issue #47: Datum name round-trip ====================

    @Test
    @DisplayName("Issue #47: WKT2 round-trip preserves datum name")
    void testWkt2RoundTripPreservesDatumName() {
        // Any projected CRS with a known datum
        Proj proj = new Proj("+proj=utm +zone=10 +datum=WGS84 +units=m +no_defs");

        // First serialization - should contain the proper datum name ("WGS84" from Datum registry)
        String wkt2First = CRSSerializer.toWkt2(proj);
        assertNotNull(wkt2First);
        assertTrue(wkt2First.contains("DATUM[\"WGS84\""),
                "First WKT2 should contain DATUM[\"WGS84\"], got: " + wkt2First);

        // Round-trip: WKT2 -> Proj -> WKT2
        Proj reimported = new Proj(wkt2First);
        String wkt2Second = CRSSerializer.toWkt2(reimported);

        // The datum name should be preserved, not replaced with "BASE"
        assertTrue(wkt2Second.contains("DATUM[\"WGS84\""),
                "Round-tripped WKT2 should preserve datum name, got: " + wkt2Second);
        assertFalse(wkt2Second.toUpperCase().contains("DATUM[\"BASE\""),
                "Round-tripped WKT2 should not contain DATUM[\"BASE\"]");
    }

    @Test
    @DisplayName("Issue #47: PROJJSON round-trip preserves datum name")
    void testProjJsonRoundTripPreservesDatumName() {
        Proj proj = new Proj("+proj=utm +zone=10 +datum=WGS84 +units=m +no_defs");

        // First serialization - datum name should be "WGS84" from registry
        String jsonFirst = CRSSerializer.toProjJson(proj);
        assertNotNull(jsonFirst);
        // Assert datum name field specifically (handles pretty-printed JSON)
        // Pattern: "datum" : { ... "name" : "WGS84" ... } — with DOTALL to span lines
        assertTrue(jsonFirst.matches("(?s).*\"datum\"\\s*:\\s*\\{.*?\"name\"\\s*:\\s*\"WGS84\".*"),
                "First PROJJSON should contain datum name 'WGS84' in datum object, got: " + jsonFirst);

        // Round-trip: PROJJSON -> Proj -> PROJJSON
        Proj reimported = new Proj(jsonFirst);
        String jsonSecond = CRSSerializer.toProjJson(reimported);

        // Datum name should be preserved, not replaced with "Base" or "BASE"
        assertTrue(jsonSecond.matches("(?s).*\"datum\"\\s*:\\s*\\{.*?\"name\"\\s*:\\s*\"WGS84\".*"),
                "Round-tripped PROJJSON should preserve datum name in datum object, got: " + jsonSecond);
        assertFalse(jsonSecond.matches("(?s).*\"datum\"\\s*:\\s*\\{.*?\"name\"\\s*:\\s*\"Base\".*"),
                "Round-tripped PROJJSON should not contain datum name 'Base'");
    }

    @Test
    @DisplayName("Issue #47: WKT2 BASEGEOGCRS uses datum name instead of 'Base'")
    void testWkt2BaseGeogCrsUsesProperName() {
        Proj proj = new Proj("+proj=utm +zone=10 +datum=WGS84 +units=m +no_defs");
        String wkt2 = CRSSerializer.toWkt2(proj);

        // BASEGEOGCRS should use the datum name ("WGS84"), not "Base"
        assertTrue(wkt2.contains("BASEGEOGCRS[\"WGS84\""),
                "BASEGEOGCRS should use datum name, got: " + wkt2);
        assertFalse(wkt2.contains("BASEGEOGCRS[\"Base\""),
                "BASEGEOGCRS should not use hardcoded 'Base' name");
    }

    @Test
    @DisplayName("Issue #47: NAD83 datum name preserved in round-trip")
    void testNad83DatumNameRoundTrip() {
        Proj proj = new Proj("+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=NAD83 +units=m");

        String wkt2 = CRSSerializer.toWkt2(proj);
        assertTrue(wkt2.contains("North_American_Datum_1983"),
                "WKT2 should contain NAD83 datum name");

        Proj reimported = new Proj(wkt2);
        String wkt2_rt = CRSSerializer.toWkt2(reimported);
        assertTrue(wkt2_rt.contains("North_American_Datum_1983"),
                "Round-tripped WKT2 should preserve NAD83 datum name, got: " + wkt2_rt);
    }

    // ==================== Issue #48: WKT2/PROJJSON round-trip tests ====================

    @Test
    @DisplayName("Issue #48: Polar Stereographic variant A (EPSG:32661) WKT2 round-trip")
    void testPolarStereographicVariantAWkt2RoundTrip() {
        // EPSG:32661 = WGS 84 / UPS North — Polar Stereographic variant A (uses k0, not lat_ts)
        Proj proj = new Proj("+proj=stere +lat_0=90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m");

        String wkt2 = CRSSerializer.toWkt2(proj);
        assertNotNull(wkt2);
        assertTrue(wkt2.contains("Polar Stereographic (variant A)"),
                "WKT2 should use variant A method name");
        assertTrue(wkt2.contains("Scale factor at natural origin"),
                "WKT2 variant A should include scale factor");
        assertFalse(wkt2.contains("Latitude of 1st standard parallel"),
                "WKT2 variant A should NOT include spurious standard parallel, got: " + wkt2);

        // Round-trip: re-import the WKT2 and verify parameters are preserved
        Proj reimported = new Proj(wkt2);
        assertEquals(0.994, reimported.getParams().k0, 1e-10,
                "k0 should be preserved through WKT2 round-trip");
        assertEquals(Math.PI / 2, reimported.getParams().lat0, 1e-10,
                "lat0 should be 90° (π/2) through WKT2 round-trip");
        assertEquals(2000000.0, reimported.getParams().x0, 1e-3,
                "x0 should be preserved through WKT2 round-trip");
        assertEquals(2000000.0, reimported.getParams().y0, 1e-3,
                "y0 should be preserved through WKT2 round-trip");
    }

    @Test
    @DisplayName("Issue #48: Polar Stereographic variant A PROJJSON round-trip")
    void testPolarStereographicVariantAProjJsonRoundTrip() {
        Proj proj = new Proj("+proj=stere +lat_0=90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m");

        String projjson = CRSSerializer.toProjJson(proj);
        assertNotNull(projjson);
        assertFalse(projjson.contains("Latitude of 1st standard parallel"),
                "PROJJSON variant A should NOT include spurious standard parallel");

        // Round-trip
        Proj reimported = new Proj(projjson);
        assertEquals(0.994, reimported.getParams().k0, 1e-10,
                "k0 should be preserved through PROJJSON round-trip");
        assertEquals(Math.PI / 2, reimported.getParams().lat0, 1e-10,
                "lat0 should be 90° (π/2) through PROJJSON round-trip");
    }

    @Test
    @DisplayName("Issue #48: Lambert Azimuthal Equal Area WKT2 round-trip")
    void testLaeaWkt2RoundTrip() {
        // LAEA does NOT use standard parallels
        Proj proj = new Proj("+proj=laea +lat_0=90 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m");

        String wkt2 = CRSSerializer.toWkt2(proj);
        assertNotNull(wkt2);
        assertTrue(wkt2.contains("Lambert Azimuthal Equal Area"),
                "WKT2 should use LAEA method name");
        assertFalse(wkt2.contains("Latitude of 1st standard parallel"),
                "WKT2 LAEA should NOT include spurious standard parallel, got: " + wkt2);

        // Round-trip
        Proj reimported = new Proj(wkt2);
        assertEquals(Math.PI / 2, reimported.getParams().lat0, 1e-10,
                "lat0 should be 90° (π/2) through WKT2 round-trip");
    }

    @Test
    @DisplayName("Issue #48: Lambert Azimuthal Equal Area PROJJSON round-trip")
    void testLaeaProjJsonRoundTrip() {
        Proj proj = new Proj("+proj=laea +lat_0=90 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m");

        String projjson = CRSSerializer.toProjJson(proj);
        assertNotNull(projjson);
        assertFalse(projjson.contains("Latitude of 1st standard parallel"),
                "PROJJSON LAEA should NOT include spurious standard parallel");

        // Round-trip
        Proj reimported = new Proj(projjson);
        assertEquals(Math.PI / 2, reimported.getParams().lat0, 1e-10,
                "lat0 should be 90° (π/2) through PROJJSON round-trip");
    }

    @Test
    @DisplayName("Issue #48: LCC still emits standard parallels correctly")
    void testLccStandardParallelsPreserved() {
        // LCC SHOULD still emit standard parallels — regression guard
        Proj proj = new Proj("+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84 +units=m");

        String wkt2 = CRSSerializer.toWkt2(proj);
        assertTrue(wkt2.contains("Latitude of 1st standard parallel"),
                "WKT2 LCC should include 1st standard parallel");
        assertTrue(wkt2.contains("Latitude of 2nd standard parallel"),
                "WKT2 LCC should include 2nd standard parallel");

        // Round-trip
        Proj reimported = new Proj(wkt2);
        assertEquals(33 * Math.PI / 180, reimported.getParams().lat1, 1e-10,
                "lat1 should be preserved through WKT2 round-trip");
        assertEquals(45 * Math.PI / 180, reimported.getParams().lat2, 1e-10,
                "lat2 should be preserved through WKT2 round-trip");
    }

    @Test
    @DisplayName("Issue #48: Polar Stereographic variant B still works correctly")
    void testPolarStereographicVariantBPreserved() {
        // Variant B uses lat_ts — regression guard
        Proj proj = new Proj("+proj=stere +lat_0=-90 +lat_ts=-71 +lon_0=0 +x_0=0 +y_0=0 +datum=WGS84 +units=m");

        String wkt2 = CRSSerializer.toWkt2(proj);
        assertTrue(wkt2.contains("Polar Stereographic (variant B)"),
                "WKT2 should use variant B method name");
        assertTrue(wkt2.contains("Latitude of standard parallel"),
                "WKT2 variant B should include standard parallel");

        // Round-trip
        Proj reimported = new Proj(wkt2);
        assertEquals(-71 * Math.PI / 180, reimported.getParams().latTs, 1e-10,
                "latTs should be preserved through WKT2 round-trip");
    }

    // ==================== Issue #83: standard WKT method names ====================

    @Test
    @DisplayName("Issue #83: eck6/eqearth/bonne/geos serialize with standard method names")
    void testStandardWktMethodNames() {
        assertTrue(CRSSerializer.toWkt2(new Proj(
            "+proj=eck6 +lon_0=0 +a=6371007 +b=6371007 +units=m +no_defs"))
            .contains("METHOD[\"Eckert VI\"]"), "eck6 method name");
        assertTrue(CRSSerializer.toWkt2(new Proj(
            "+proj=eqearth +lon_0=0 +datum=WGS84 +units=m +no_defs"))
            .contains("METHOD[\"Equal Earth\"]"), "eqearth method name");
        assertTrue(CRSSerializer.toWkt2(new Proj(
            "+proj=bonne +lat_1=40 +lon_0=0 +datum=WGS84 +units=m +no_defs"))
            .contains("METHOD[\"Bonne\"]"), "bonne method name");
        assertTrue(CRSSerializer.toWkt2(new Proj(
            "+proj=geos +h=35785831 +sweep=y +lon_0=0 +datum=WGS84 +units=m +no_defs"))
            .contains("METHOD[\"Geostationary Satellite (Sweep Y)\"]"), "geos sweep-y method name");
        assertTrue(CRSSerializer.toWkt2(new Proj(
            "+proj=geos +h=35785831 +sweep=x +lon_0=0 +datum=WGS84 +units=m +no_defs"))
            .contains("METHOD[\"Geostationary Satellite (Sweep X)\"]"), "geos sweep-x method name");
    }

    @Test
    @DisplayName("Issue #83: geos round-trips through WKT2/PROJJSON (Satellite Height + sweep)")
    void testGeosWktRoundTrip() {
        for (String sweep : new String[] {"x", "y"}) {
            Proj original = new Proj("+proj=geos +h=35785831 +sweep=" + sweep
                + " +lon_0=0 +x_0=0 +y_0=0 +ellps=WGS84 +units=m +no_defs");
            double lon = 10 * Math.PI / 180, lat = -5 * Math.PI / 180;
            org.datasyslab.proj4sedona.core.Point want =
                original.forward(new org.datasyslab.proj4sedona.core.Point(lon, lat));
            for (String serialized : new String[] {
                    CRSSerializer.toWkt1(original),
                    CRSSerializer.toWkt2(original),
                    CRSSerializer.toProjJson(original)}) {
                Proj reimported = new Proj(serialized);
                org.datasyslab.proj4sedona.core.Point got =
                    reimported.forward(new org.datasyslab.proj4sedona.core.Point(lon, lat));
                assertEquals(want.x, got.x, 0.01, "easting (sweep " + sweep + ") after re-import");
                assertEquals(want.y, got.y, 0.01, "northing (sweep " + sweep + ") after re-import");
            }
        }
    }

    @Test
    @DisplayName("Issue #83: geos Sweep X survives a serialize -> parse -> re-serialize chain")
    void testGeosSweepXSurvivesReserialization() {
        // The sweep axis lives only in the method name in WKT2/PROJJSON; the resolved
        // sweep must be persisted on parse so the second serialization keeps Sweep X.
        Proj original = new Proj("+proj=geos +h=35785831 +sweep=x +lon_0=0 +ellps=WGS84 +units=m +no_defs");
        Proj hop1 = new Proj(CRSSerializer.toWkt2(original));
        assertTrue(CRSSerializer.toWkt2(hop1).contains("(Sweep X)"),
            "second WKT2 serialization keeps Sweep X");
        assertTrue(CRSSerializer.toProjString(hop1).contains("+sweep=x"),
            "proj string re-export keeps +sweep=x");

        double lon = 10 * Math.PI / 180, lat = -5 * Math.PI / 180;
        org.datasyslab.proj4sedona.core.Point want =
            original.forward(new org.datasyslab.proj4sedona.core.Point(lon, lat));
        Proj hop2 = new Proj(CRSSerializer.toWkt2(hop1));
        org.datasyslab.proj4sedona.core.Point got =
            hop2.forward(new org.datasyslab.proj4sedona.core.Point(lon, lat));
        assertEquals(want.x, got.x, 0.01, "easting after two hops");
        assertEquals(want.y, got.y, 0.01, "northing after two hops");
    }

    @Test
    @DisplayName("Issue #83: external WKT2 method name re-exports to a valid proj string")
    void testExternalWkt2MethodNameReExport() {
        // A CRS whose projName is the WKT2 method name must re-export with the PROJ
        // short code, not the method name (e.g. not "+proj=Eckert VI").
        Proj ext = new Proj(CRSSerializer.toWkt2(
            new Proj("+proj=eck6 +lon_0=0 +a=6371007 +b=6371007 +no_defs")));
        String projString = CRSSerializer.toProjString(ext);
        assertTrue(projString.contains("+proj=eck6"), "re-export uses short code: " + projString);
    }
}
