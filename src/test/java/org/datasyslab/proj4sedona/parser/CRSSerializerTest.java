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
    @DisplayName("toProjString: PROJJSON string-form units do not leak PROJ-invalid +units=")
    void testToProjStringNormalizesStringFormUnits() {
        // PROJ >= 6 emits bare-string axis units in PROJJSON ("degree", "metre").
        // Parsing stores them on params.units, but PROJ's +units= table has no
        // angular entries and keys metre as "m" — emitting the stored value verbatim
        // (+units=degree / +units=meter) produces strings external PROJ rejects.
        String json = "{\"type\": \"GeographicCRS\", \"name\": \"WGS 84\","
            + "\"datum\": {\"type\": \"GeodeticReferenceFrame\", \"name\": \"World Geodetic System 1984\","
            + "  \"ellipsoid\": {\"name\": \"WGS 84\", \"semi_major_axis\": 6378137, \"inverse_flattening\": 298.257223563}},"
            + "\"coordinate_system\": {\"subtype\": \"ellipsoidal\", \"axis\": ["
            + "  {\"name\": \"Geodetic latitude\", \"abbreviation\": \"Lat\", \"direction\": \"north\", \"unit\": \"degree\"},"
            + "  {\"name\": \"Geodetic longitude\", \"abbreviation\": \"Lon\", \"direction\": \"east\", \"unit\": \"degree\"}]},"
            + "\"id\": {\"authority\": \"EPSG\", \"code\": 4326}}";
        Proj proj = new Proj(json);
        assertEquals("degree", proj.getParams().units, "string-form axis unit parsed");
        String projStr = CRSSerializer.toProjString(proj);
        assertFalse(projStr.contains("+units="),
            "angular unit must not become +units=: " + projStr);
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

    // ========== Ellipsoid resolution (issues #101, #105) ==========

    @Test
    @DisplayName("Issue #105: every registered +ellps code parses with its own axes")
    void testAllEllipsoidCodesParse() {
        // A hardcoded 10-entry switch shadowed the 45-entry registry, silently
        // defaulting the rest to WGS84 (+ellps=clrk80ign parsed 112 m off in a).
        for (org.datasyslab.proj4sedona.constants.Ellipsoid e :
                org.datasyslab.proj4sedona.constants.Ellipsoid.getAll().values()) {
            Proj p = new Proj("+proj=longlat +ellps=" + e.getCode() + " +no_defs");
            assertEquals(e.getA(), p.getParams().a, 1e-6, e.getCode() + " semi-major");
            assertEquals(e.getB(), p.getParams().b, 1e-6, e.getCode() + " semi-minor");
        }
    }

    @Test
    @DisplayName("Issue #105: parsed clrk80ign transforms match pyproj")
    void testClrk80ignTransform() {
        // Reference from pyproj 3.7.2/PROJ 9.5.1; with the WGS84 fallback this was
        // ~100 m off.
        Point p = org.datasyslab.proj4sedona.Proj4
            .proj4("+proj=longlat +ellps=clrk80ign +no_defs",
                   "+proj=utm +zone=31 +ellps=clrk80ign +no_defs")
            .forward(new Point(2.5, 46.0));
        assertEquals(461282.081634, p.x, 0.01);
        assertEquals(5093857.024237, p.y, 0.01);
    }

    @Test
    @DisplayName("Ellipsoid codes resolve with match.js normalization (separators ignored)")
    void testEllipsoidCodeNormalization() {
        // proj4js resolves registry keys through match.js, which ignores whitespace,
        // underscores, hyphens, slashes and parentheses — +ellps=bess-nam is
        // Bessel Namibia upstream, and previously parsed here as WGS84 silently.
        Proj p = new Proj("+proj=longlat +ellps=bess-nam +no_defs");
        assertEquals(6377483.865, p.getParams().a, 1e-6, "bess-nam resolves to bess_nam");

        // The legacy clark80 alias spelling resolves to clrk80 (carthage's ellipse).
        assertEquals(6378249.145,
            new Proj("+proj=longlat +ellps=clark80 +no_defs").getParams().a, 1e-6);

        // Datum codes get the same normalization (+datum=s-jtsk -> s_jtsk).
        assertEquals("bessel",
            org.datasyslab.proj4sedona.constants.Datum.get("s-jtsk").getEllipse());
    }

    @Test
    @DisplayName("A matching rf must not override a conflicting explicit b")
    void testRfDoesNotOverrideConflictingB() {
        // b wins over rf when both are given (here and in proj4js); the resolver
        // previously accepted the rf equality and emitted +ellps=WGS84, silently
        // moving the effective semi-minor axis by 56.8 km on re-parse.
        Proj p = new Proj("+proj=longlat +a=6378137 +b=6300000 +rf=298.257223563 +no_defs");
        assertEquals(6300000.0, p.getParams().b, 0, "explicit b is the effective b");
        String out = CRSSerializer.toProjString(p);
        assertFalse(out.contains("+ellps="), out);
        assertTrue(out.contains("+a=6378137") && out.contains("+b=6300000"), out);
        assertEquals(6300000.0, new Proj(out).getParams().b, 0,
            "effective semi-minor axis survives the round-trip");
    }

    @Test
    @DisplayName("A stale conflicting rf does not survive WKT or PROJJSON either")
    void testStaleRfAllFormats() {
        // b is authoritative when both b and rf are present; the WKT writers and the
        // PROJJSON exporter emitted the stale rf literal, so re-import changed the
        // effective semi-minor axis by 56.8 km even though the proj-string
        // round-trip was already fixed.
        Proj p = new Proj("+proj=longlat +a=6378137 +b=6300000 +rf=298.257223563 +no_defs");
        assertEquals(6300000.0, new Proj(CRSSerializer.toWkt1(p)).getParams().b, 1e-6, "WKT1");
        assertEquals(6300000.0, new Proj(CRSSerializer.toWkt2(p)).getParams().b, 1e-6, "WKT2");
        assertEquals(6300000.0, new Proj(CRSSerializer.toProjJson(p)).getParams().b, 1e-6, "PROJJSON");
        assertEquals(6300000.0, new Proj(CRSSerializer.toProjString(p)).getParams().b, 1e-6, "proj string");

        // A consistent rf keeps its clean literal in WKT.
        assertTrue(CRSSerializer.toWkt2(new Proj("+proj=longlat +ellps=WGS84 +no_defs"))
            .contains("298.257223563"));
    }

    @Test
    @DisplayName("Authority matching validates the effective semi-minor axis")
    void testAuthorityRejectsConflictingEllipsoid() {
        // The datum name and rf both said WGS84, but the explicit b overrides the
        // ellipsoid at parse — toAuthority returned EPSG:4326 and the PROJJSON
        // export carried the 4326 id for a non-WGS84 ellipsoid.
        Proj q = new Proj("+proj=longlat +datum=WGS84 +a=6378137 +b=6300000 +rf=298.257223563 +no_defs");
        assertNull(CRSSerializer.toAuthority(q.getParams()), "conflicting b must not identify");
        assertFalse(CRSSerializer.toProjJson(q).contains("4326"), "no EPSG:4326 id");

        // Genuine WGS84 still identifies, and the rf discrimination still separates
        // GRS 1980 (b only 0.1 mm away) from WGS 84.
        String[] wgs = CRSSerializer.toAuthority(
            new Proj("+proj=longlat +datum=WGS84 +no_defs").getParams());
        assertNotNull(wgs);
        assertEquals("4326", wgs[1]);
        String[] grs80 = CRSSerializer.toAuthority(
            new Proj("+proj=longlat +ellps=GRS80 +no_defs").getParams());
        assertFalse(grs80 != null && "4326".equals(grs80[1]),
            "GRS80 must not identify as EPSG:4326");
    }

    @Test
    @DisplayName("Datum-name authority candidates are validated against their reference")
    void testDatumNameCandidateValidated() {
        // The datum-name shortcut previously validated the ellipsoid only when the
        // datum resolved in the registry — 15 of the 27 mapped names (ETRS89,
        // GDA2020, JGD2011, CGCS2000, ...) do not, so a conflicting definition was
        // stamped with the mapped EPSG id unchecked. Candidates now validate through
        // matchesDefinition (effective axes, rf, datum, projection parameters).
        Proj conflict = new Proj("+proj=longlat +datum=ETRS89 +a=6378137 +b=6300000 +no_defs");
        assertNull(CRSSerializer.toAuthority(conflict.getParams()),
            "conflicting axes must not identify as EPSG:4258");

        // rf discrimination now applies to the shortcut too: GRS80's semi-minor axis
        // is only 0.1 mm from WGS84's, so the coarse axis check alone cannot see it.
        Proj mixed = new Proj("+proj=longlat +datum=WGS84 +ellps=GRS80 +no_defs");
        String[] auth = CRSSerializer.toAuthority(mixed.getParams());
        assertFalse(auth != null && "4326".equals(auth[1]),
            "a GRS80 ellipsoid must not identify as EPSG:4326");

        // Positive control: the realistic carrier of these names — a document with
        // the datum name and its proper ellipsoid — still identifies.
        String etrs89 = "{\"type\": \"GeographicCRS\", \"name\": \"ETRS89\","
            + "\"datum\": {\"type\": \"GeodeticReferenceFrame\","
            + " \"name\": \"European Terrestrial Reference System 1989\","
            + " \"ellipsoid\": {\"name\": \"GRS 1980\", \"semi_major_axis\": 6378137,"
            + "  \"inverse_flattening\": 298.257222101}},"
            + "\"coordinate_system\": {\"subtype\": \"ellipsoidal\", \"axis\": ["
            + " {\"name\": \"Geodetic latitude\", \"abbreviation\": \"Lat\", \"direction\": \"north\", \"unit\": \"degree\"},"
            + " {\"name\": \"Geodetic longitude\", \"abbreviation\": \"Lon\", \"direction\": \"east\", \"unit\": \"degree\"}]}}";
        String[] pos = CRSSerializer.toAuthority(new Proj(etrs89).getParams());
        assertNotNull(pos, "genuine ETRS89 with GRS80 still identifies");
        assertEquals("4258", pos[1]);
    }

    private static String geographicDoc(String datumName, double a, double rf) {
        return "{\"type\": \"GeographicCRS\", \"name\": \"" + datumName + "\","
            + "\"datum\": {\"type\": \"GeodeticReferenceFrame\", \"name\": \"" + datumName + "\","
            + " \"ellipsoid\": {\"name\": \"e\", \"semi_major_axis\": " + a + ","
            + "  \"inverse_flattening\": " + rf + "}},"
            + "\"coordinate_system\": {\"subtype\": \"ellipsoidal\", \"axis\": ["
            + " {\"name\": \"Geodetic latitude\", \"abbreviation\": \"Lat\", \"direction\": \"north\", \"unit\": \"degree\"},"
            + " {\"name\": \"Geodetic longitude\", \"abbreviation\": \"Lon\", \"direction\": \"east\", \"unit\": \"degree\"}]}}";
    }

    @Test
    @DisplayName("Datum-name authority identification is fully offline")
    void testDatumNameAuthorityOffline() {
        // Phase-2 validation previously constructed the mapped EPSG reference via
        // new Proj(code); only EPSG:4326 and EPSG:4269 are built-in definitions, so
        // 13 of the 15 mapped codes triggered blocking HTTP through the remote CRS
        // provider inside toAuthority/toProjJson — and failed silently offline.
        // Validation now uses bundled ellipsoid metadata; this test runs with the
        // remote provider removed to guard against reintroducing the construction.
        org.datasyslab.proj4sedona.defs.Defs.globals();
        org.datasyslab.proj4sedona.defs.Defs.removeProvider("spatialreference.org");
        try {
            // One name per mapped ellipsoid family, plus the previously failing
            // spellings (CGCS2000 rejected via the reference's "China 2000" datum
            // name; ETRS89/GDA2020/JGD2011 unvalidatable in the datum registry).
            Object[][] sweep = {
                {"World Geodetic System 1984", 6378137.0, 298.257223563, "4326"},
                {"NAD83 (National Spatial Reference System 2011)", 6378137.0, 298.257222101, "6318"},
                {"European Terrestrial Reference System 1989", 6378137.0, 298.257222101, "4258"},
                {"Geocentric Datum of Australia 2020", 6378137.0, 298.257222101, "7844"},
                {"Japanese Geodetic Datum 2011", 6378137.0, 298.257222101, "6668"},
                {"China Geodetic Coordinate System 2000", 6378137.0, 298.257222101, "4490"},
                {"North American Datum 1927", 6378206.4, 294.9786982139006, "4267"},
                {"Indian 1975", 6377276.345, 300.8017, "4240"},
                {"Ordnance Survey of Great Britain 1936", 6377563.396, 299.3249646, "4277"},
            };
            for (Object[] c : sweep) {
                String doc = geographicDoc((String) c[0], (Double) c[1], (Double) c[2]);
                String[] auth = CRSSerializer.toAuthority(new Proj(doc).getParams());
                assertNotNull(auth, c[0] + " identifies offline");
                assertEquals(c[3], auth[1], (String) c[0]);
            }

            // Registry-sourced definitions identify too: +datum=OSGB36 inherits the
            // proj4js-faithful rounded airy semi-minor axis (b=6356256.91), whose
            // derived rf sits ~1e-5 from the EPSG-canonical literal — the per-row
            // tolerance must accommodate both sources.
            String[] osgb = CRSSerializer.toAuthority(new Proj(
                "+proj=longlat +datum=OSGB36 +no_defs").getParams());
            assertNotNull(osgb, "registry-sourced OSGB36 identifies");
            assertEquals("4277", osgb[1]);
            String[] nad27 = CRSSerializer.toAuthority(new Proj(
                "+proj=longlat +datum=NAD27 +no_defs").getParams());
            assertNotNull(nad27, "registry-sourced NAD27 identifies");
            assertEquals("4267", nad27[1]);

            // The conflict rejections hold offline too.
            assertNull(CRSSerializer.toAuthority(new Proj(
                "+proj=longlat +datum=ETRS89 +a=6378137 +b=6300000 +no_defs").getParams()));
        } finally {
            org.datasyslab.proj4sedona.defs.Defs.registerProvider(
                org.datasyslab.proj4sedona.defs.UrlCRSProvider.spatialReference(), 101);
        }
    }

    @Test
    @DisplayName("A stale matching rf cannot vouch for the wrong authority")
    void testStaleRfCannotVouchAuthority() {
        // Effective b is GRS80's, the stale raw rf is WGS84's: the rf discrimination
        // previously trusted the raw value and stamped EPSG:4326 onto an effectively
        // GRS80 ellipsoid. The effective rf (derived from the authoritative axes)
        // is compared instead.
        Proj p = new Proj("+proj=longlat +datum=WGS84 +a=6378137 "
            + "+b=6356752.314140356 +rf=298.257223563 +no_defs");
        assertNull(CRSSerializer.toAuthority(p.getParams()),
            "effectively-GRS80 ellipsoid must not identify as EPSG:4326");
        assertFalse(CRSSerializer.toProjJson(p).contains("4326"));
    }

    @Test
    @DisplayName("The stated ellipsoid identity does not swallow near-twins")
    void testStatedIdentityDoesNotSwallowNearTwins() {
        // +datum=WGS84 states the WGS84 identity, but the explicit semi-minor axis
        // is GRS80's (0.105 mm away). The identity validation previously used a 1 mm
        // tolerance, serializing +ellps=WGS84 and changing b on re-parse; the
        // exact-parameter pass now wins and keeps the axes bit-identical.
        Proj p = new Proj("+proj=longlat +datum=WGS84 +a=6378137 +b=6356752.314140356 +no_defs");
        String out = CRSSerializer.toProjString(p);
        assertTrue(out.contains("+ellps=GRS80"), out);
        assertEquals(6356752.314140356, new Proj(out).getParams().b, 0,
            "semi-minor axis is preserved exactly");
    }

    @Test
    @DisplayName("plessis matches PROJ, not proj4js's rf typo")
    void testPlessisMatchesProj() {
        // Documented divergence: proj4js stores plessis's 6355863 as the inverse
        // flattening (deriving the near-sphere b=6376521.997, 20.7 km off); PROJ
        // defines a=6376523, b=6355863, which this registry matches.
        Proj p = new Proj("+proj=longlat +ellps=plessis +no_defs");
        assertEquals(6376523.0, p.getParams().a, 1e-6);
        assertEquals(6355863.0, p.getParams().b, 1e-6, "b per PROJ's table");
    }

    @Test
    @DisplayName("Issue #101: every registered +ellps code round-trips through toProjString")
    void testAllEllipsoidCodesRoundTrip() {
        // First-tolerance-match resolution shadowed WGS84 behind MERIT (semi-minor
        // axes 1.6 cm apart) and could never round-trip parameter-identical twins
        // (NWL9D/WGS66); the definition's own code now wins when its parameters
        // match, then exact parameter match, then closest-in-tolerance.
        for (org.datasyslab.proj4sedona.constants.Ellipsoid e :
                org.datasyslab.proj4sedona.constants.Ellipsoid.getAll().values()) {
            String out = CRSSerializer.toProjString(
                new Proj("+proj=longlat +ellps=" + e.getCode() + " +no_defs"));
            assertTrue(out.contains("+ellps=" + e.getCode()), e.getCode() + " -> " + out);
        }
    }

    @Test
    @DisplayName("Issue #101: WGS84 is no longer shadowed by MERIT")
    void testWgs84NotShadowedByMerit() {
        assertTrue(CRSSerializer.toProjString(new Proj("+proj=longlat +datum=WGS84 +no_defs"))
            .contains("+ellps=WGS84"));
        assertTrue(CRSSerializer.toProjString(new Proj("EPSG:4326"))
            .contains("+ellps=WGS84"));
        assertTrue(CRSSerializer.toProjString(new Proj("+proj=utm +zone=10 +datum=NAD83 +no_defs"))
            .contains("+ellps=GRS80"));

        // The WKT ellipsoid *name* resolves through the registry too.
        String wkt = "GEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563,LENGTHUNIT[\"metre\",1]]],"
            + "CS[ellipsoidal,2],AXIS[\"lat\",north],AXIS[\"lon\",east],"
            + "ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",4326]]";
        assertTrue(CRSSerializer.toProjString(new Proj(wkt)).contains("+ellps=WGS84"));
    }

    @Test
    @DisplayName("Issue #101: custom parameters are not snapped to a registry ellipsoid")
    void testCustomParametersStayExplicit() {
        // Proj assigns the "wgs84" ellps placeholder when none is given; the resolver
        // must reject it when the actual parameters differ.
        String custom = CRSSerializer.toProjString(
            new Proj("+proj=longlat +a=6378137 +b=6356000 +no_defs"));
        assertFalse(custom.contains("+ellps="), custom);
        assertTrue(custom.contains("+a=6378137") && custom.contains("+b=6356000"), custom);

        String sphere = CRSSerializer.toProjString(
            new Proj("+proj=longlat +R=6371000 +no_defs"));
        assertFalse(sphere.contains("+ellps="), sphere);
        assertTrue(sphere.contains("+a=6371000") && sphere.contains("+b=6371000"), sphere);
    }

    // ========== WKT datum + method-name interop (apache/sedona#3103) ==========

    @Test
    @DisplayName("toWkt1 emits TOWGS84 in human units (arc-seconds/ppm), not internal")
    void testWkt1Towgs84HumanUnits() {
        // DatumParams stores the 7-parameter tail internally as radians + a scale
        // multiplier; WKT's TOWGS84 (like PROJ's +towgs84=) uses arc-seconds and ppm.
        // Emitting the internal values made a consumer that ingests this WKT1 (GeoTools,
        // in Sedona's raster CRS bridge) see near-zero rotations and a bogus scale.
        String wkt1 = CRSSerializer.toWkt1(new Proj(
            "+proj=tmerc +lat_0=49 +lon_0=-2 +k_0=0.9996012717 +x_0=400000 +y_0=-100000 "
                + "+ellps=airy +datum=OSGB36 +no_defs"));
        assertTrue(wkt1.contains("TOWGS84[446.448,-125.157,542.06,0.1502,0.247,0.8421,-20.4894]"),
            "OSGB36 TOWGS84 in arc-seconds/ppm: " + wkt1);
        assertFalse(wkt1.contains("7.28"), "no radian-form rotations: " + wkt1);

        // Idempotent through a WKT1 re-parse (what the GeoTools bridge does): the
        // datum survives unchanged.
        String wkt1b = CRSSerializer.toWkt1(new Proj(wkt1));
        assertEquals(wkt1, wkt1b, "WKT1 is stable across a re-parse");
    }

    @Test
    @DisplayName("A GeoTools WKT1 method name re-exports to the PROJ short code with all parameters")
    void testWktMethodNameNormalizesToShortCode() {
        // A CRS that round-tripped through GeoTools carries the WKT/GeoTools method
        // name (PROJECTION["Albers_Conic_Equal_Area"]) rather than the PROJ short
        // code. toProjString previously emitted the method name verbatim (unparseable)
        // and dropped the standard parallels, because parallel emission is gated on the
        // short code. normalizeProjName now resolves the alias through the registry.
        String geotoolsWkt = "PROJCS[\"NAD83 / Conus Albers\","
            + "GEOGCS[\"NAD83\",DATUM[\"North_American_Datum_1983\","
            + "SPHEROID[\"GRS 1980\",6378137.0,298.257222101],TOWGS84[0,0,0,0,0,0,0]],"
            + "PRIMEM[\"Greenwich\",0.0],UNIT[\"degree\",0.017453292519943295]],"
            + "PROJECTION[\"Albers_Conic_Equal_Area\"],"
            + "PARAMETER[\"central_meridian\",-96.0],PARAMETER[\"latitude_of_origin\",23.0],"
            + "PARAMETER[\"standard_parallel_1\",29.5],PARAMETER[\"standard_parallel_2\",45.5],"
            + "PARAMETER[\"false_easting\",0.0],PARAMETER[\"false_northing\",0.0],"
            + "UNIT[\"m\",1.0]]";
        String projStr = CRSSerializer.toProjString(new Proj(geotoolsWkt));
        assertTrue(projStr.contains("+proj=aea"), "short code, not method name: " + projStr);
        assertFalse(projStr.contains("Albers_Conic_Equal_Area"), projStr);
        assertTrue(projStr.contains("+lat_1=29.5"), "first standard parallel kept: " + projStr);
        assertTrue(projStr.contains("+lat_2=45.5"), "second standard parallel kept: " + projStr);
        // Re-parseable (the round-trip is now closed).
        assertNotNull(new Proj(projStr));
    }

    @Test
    @DisplayName("A projected CRS parsed from a GeoTools WKT1 round-trips its PROJ string")
    void testGeoToolsWkt1ProjStringRoundTrip() {
        // export2 == export3 in Sedona's CrsRoundTripComplianceTest: parsing a
        // GeoTools-shaped WKT1 and re-exporting to PROJ must be idempotent.
        String geotoolsWkt = "PROJCS[\"OSGB\",GEOGCS[\"OSGB\","
            + "DATUM[\"Ordnance Survey of Great Britain 1936\","
            + "SPHEROID[\"Airy 1830\",6377563.396,299.3249646],"
            + "TOWGS84[446.448,-125.157,542.06,0.1502,0.247,0.8421,-20.4894]],"
            + "PRIMEM[\"Greenwich\",0.0],UNIT[\"degree\",0.017453292519943295]],"
            + "PROJECTION[\"Transverse_Mercator\"],"
            + "PARAMETER[\"central_meridian\",-2.0],PARAMETER[\"latitude_of_origin\",49.0],"
            + "PARAMETER[\"scale_factor\",0.9996012717],"
            + "PARAMETER[\"false_easting\",400000.0],PARAMETER[\"false_northing\",-100000.0],"
            + "UNIT[\"m\",1.0]]";
        String proj2 = CRSSerializer.toProjString(new Proj(geotoolsWkt));
        String proj3 = CRSSerializer.toProjString(new Proj(proj2));
        assertEquals(proj2, proj3, "PROJ string is idempotent");
        assertDoesNotThrow(() -> new Proj(proj2), "re-exported PROJ string is parseable");
        assertTrue(proj2.startsWith("+proj=tmerc"), proj2);
        // The human-unit TOWGS84 matches OSGB36's canonical values, so it collapses to
        // the compact +datum=OSGB36 token (issue #102 behavior) rather than +towgs84=.
        // The point of this test: no internal-unit (radian/multiplier) leak survives.
        assertTrue(proj2.contains("+datum=OSGB36"), "datum recognized: " + proj2);
        assertFalse(proj2.matches(".*towgs84=[^ ]*[0-9]E-[0-9].*"),
            "no radian-form rotations leak: " + proj2);
    }

    @Test
    @DisplayName("Registry fallback maps GeoTools method names to short codes across projections")
    void testMethodNameNormalizationAcrossProjections() {
        // The registry fallback is not aea-specific: any registered alias resolves to
        // its PROJ short code. One WKT1 per family, all GeoTools underscore names.
        String[][] cases = {
            {"Lambert_Conformal_Conic_2SP", "lcc"},
            {"Lambert_Azimuthal_Equal_Area", "laea"},
            {"Cassini_Soldner", "cass"},
            {"Transverse_Mercator", "tmerc"},
        };
        for (String[] c : cases) {
            String wkt = "PROJCS[\"x\",GEOGCS[\"x\",DATUM[\"World Geodetic System 1984\","
                + "SPHEROID[\"WGS 84\",6378137.0,298.257223563],TOWGS84[0,0,0,0,0,0,0]],"
                + "PRIMEM[\"Greenwich\",0.0],UNIT[\"degree\",0.017453292519943295]],"
                + "PROJECTION[\"" + c[0] + "\"],"
                + "PARAMETER[\"central_meridian\",0.0],PARAMETER[\"latitude_of_origin\",0.0],"
                + "PARAMETER[\"standard_parallel_1\",30.0],PARAMETER[\"standard_parallel_2\",50.0],"
                + "PARAMETER[\"false_easting\",0.0],PARAMETER[\"false_northing\",0.0],UNIT[\"m\",1.0]]";
            String projStr = CRSSerializer.toProjString(new Proj(wkt));
            assertTrue(projStr.contains("+proj=" + c[1]), c[0] + " -> " + projStr);
            assertFalse(projStr.contains(c[0]), "no raw method name: " + projStr);
        }
    }

    @Test
    @DisplayName("LCC standard parallels survive re-export through a GeoTools method name")
    void testLccParallelsSurviveMethodName() {
        // lcc is the projection where dropped standard parallels matter most; assert
        // the short-code path keeps both.
        String wkt = "PROJCS[\"x\",GEOGCS[\"x\",DATUM[\"North_American_Datum_1983\","
            + "SPHEROID[\"GRS 1980\",6378137.0,298.257222101],TOWGS84[0,0,0,0,0,0,0]],"
            + "PRIMEM[\"Greenwich\",0.0],UNIT[\"degree\",0.017453292519943295]],"
            + "PROJECTION[\"Lambert_Conformal_Conic_2SP\"],"
            + "PARAMETER[\"central_meridian\",-96.0],PARAMETER[\"latitude_of_origin\",39.0],"
            + "PARAMETER[\"standard_parallel_1\",33.0],PARAMETER[\"standard_parallel_2\",45.0],"
            + "PARAMETER[\"false_easting\",0.0],PARAMETER[\"false_northing\",0.0],UNIT[\"m\",1.0]]";
        String projStr = CRSSerializer.toProjString(new Proj(wkt));
        assertTrue(projStr.contains("+proj=lcc"), projStr);
        assertTrue(projStr.contains("+lat_1=33"), "first parallel: " + projStr);
        assertTrue(projStr.contains("+lat_2=45"), "second parallel: " + projStr);
    }

    // ========== Datum token normalization (issue #98) ==========

    @Test
    @DisplayName("toProjString: WKT datum name resolves to the PROJ +datum= short code")
    void testDatumNameResolvesToShortCode() {
        // A WKT/PROJJSON-parsed CRS stores the full datum name; PROJ's +datum= accepts
        // only its short codes, so the name was emitted verbatim as an unparseable
        // token. The name now resolves to the canonical code.
        String wkt4326 = "GEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563,LENGTHUNIT[\"metre\",1]]],"
            + "CS[ellipsoidal,2],AXIS[\"lat\",north],AXIS[\"lon\",east],"
            + "ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",4326]]";
        String out = CRSSerializer.toProjString(new Proj(wkt4326));
        assertTrue(out.contains("+datum=WGS84"), out);
        assertFalse(out.contains("WORLD GEODETIC SYSTEM"), out);

        String wkt4269 = "GEOGCRS[\"NAD83\",DATUM[\"North American Datum 1983\","
            + "ELLIPSOID[\"GRS 1980\",6378137,298.257222101,LENGTHUNIT[\"metre\",1]]],"
            + "CS[ellipsoidal,2],AXIS[\"lat\",north],AXIS[\"lon\",east],"
            + "ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",4269]]";
        assertTrue(CRSSerializer.toProjString(new Proj(wkt4269)).contains("+datum=NAD83"));
    }

    @Test
    @DisplayName("toProjString: unknown datum name is dropped, not emitted as a broken token")
    void testUnknownDatumNameDropped() {
        // PROJ's WKT2 for EPSG:4807 (issue #98 repro): the datum "Nouvelle
        // Triangulation Francaise (Paris)" is not one of PROJ's datums, so PROJ emits
        // only +ellps=. We previously emitted +datum=NOUVELLE TRIANGULATION FRANCAISE
        // (PARIS), which splits into garbage tokens.
        String wkt4807 = "GEODCRS[\"NTF (Paris)\","
            + "DATUM[\"Nouvelle Triangulation Francaise (Paris)\","
            + "ELLIPSOID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627,LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Paris\",2.5969213,ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "CS[ellipsoidal,2],AXIS[\"lat\",north],AXIS[\"lon\",east],"
            + "ANGLEUNIT[\"grad\",0.015707963267949],ID[\"EPSG\",4807]]";
        String out = CRSSerializer.toProjString(new Proj(wkt4807));
        assertFalse(out.contains("+datum="), "unknown datum must not be emitted: " + out);
        assertTrue(out.contains("+ellps="), "relies on the ellipsoid instead: " + out);
    }

    @Test
    @DisplayName("toProjString: PROJ datum short code keeps PROJ's canonical case")
    void testDatumCanonicalCase() {
        // PROJ's +datum= lookup is case-sensitive: nzgd49/hermannskogel/carthage are
        // lower-case, so the old blanket toUpperCase() emitted tokens PROJ rejects.
        assertTrue(CRSSerializer.toProjString(new Proj("+proj=longlat +datum=nzgd49 +no_defs"))
            .contains("+datum=nzgd49"));
        assertTrue(CRSSerializer.toProjString(new Proj("+proj=longlat +datum=hermannskogel +no_defs"))
            .contains("+datum=hermannskogel"));
        assertTrue(CRSSerializer.toProjString(new Proj("+proj=longlat +datum=WGS84 +no_defs"))
            .contains("+datum=WGS84"));
    }

    @Test
    @DisplayName("toProjString: potsdam is not tokenized — PROJ's potsdam means a grid shift")
    void testPotsdamSemanticMismatch() {
        // Our registry (like proj4js) defines potsdam as the legacy 7-parameter
        // transform, but PROJ's pj_datums defines +datum=potsdam as
        // nadgrids=@BETA2007.gsb (the towgs84 form is commented out in datums.cpp).
        // Emitting the token would silently change the transform for external PROJ,
        // so the actual parameters are serialized instead, in human units.
        String out = CRSSerializer.toProjString(new Proj("+proj=longlat +datum=potsdam +no_defs"));
        assertFalse(out.contains("+datum="), out);
        assertTrue(out.contains("+towgs84=598.1,73.7,418.2,0.202,0.045,-2.455,6.7"), out);

        // Even a definition that carries PROJ's exact @BETA2007.gsb grid must not be
        // tokenized: our own parser (like proj4js) re-reads +datum=potsdam as the
        // legacy Helmert, so the token cannot round-trip on both sides.
        String grid = CRSSerializer.toProjString(
            new Proj("+proj=longlat +datum=potsdam +nadgrids=@BETA2007.gsb +no_defs"));
        assertFalse(grid.contains("+datum="), grid);
        assertTrue(grid.contains("+nadgrids=@BETA2007.gsb"), grid);
    }

    @Test
    @DisplayName("toProjString: 7-parameter datums re-encode to arc-seconds/ppm")
    void testSevenParamReEncoded() {
        // DatumParams stores rotations in radians and scale as a multiplier;
        // emitting those raw made a re-parse convert them a second time (a
        // +datum=mgi round-trip moved WGS84 results by ~12.4 m at (16,48)).
        Proj mgi = new Proj("+proj=longlat +datum=mgi +no_defs");
        String out = CRSSerializer.toProjString(mgi);
        assertTrue(out.contains("+towgs84=577.326,90.129,463.919,5.137,1.474,5.297,2.4232"), out);

        Proj reimported = new Proj(out);
        assertArrayEquals(
            mgi.getParams().datum.getDatumParams(),
            reimported.getParams().datum.getDatumParams(), 1e-15,
            "7-parameter transform survives the round-trip");

        org.datasyslab.proj4sedona.transform.Converter a =
            org.datasyslab.proj4sedona.Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs",
                "+proj=longlat +datum=mgi +no_defs");
        org.datasyslab.proj4sedona.transform.Converter b =
            org.datasyslab.proj4sedona.Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", out);
        Point pa = a.inverse(new Point(16, 48));
        Point pb = b.inverse(new Point(16, 48));
        assertEquals(pa.x, pb.x, 1e-12, "lon identical after round-trip");
        assertEquals(pa.y, pb.y, 1e-12, "lat identical after round-trip");
    }

    @Test
    @DisplayName("toProjString: all-zero 7-value towgs84 is not scale-corrupted")
    void testAllZeroTailTowgs84() {
        // A 7-value +towgs84 whose rotation/scale entries are all zero stays on the
        // 3-parameter path (no unit conversion at parse), so re-encoding must not
        // treat the zero scale slot as a multiplier ((0-1)*1e6 = -1000000 ppm).
        String out = CRSSerializer.toProjString(
            new Proj("+proj=longlat +ellps=bessel +towgs84=1,2,3,0,0,0,0 +no_defs"));
        assertTrue(out.contains("+towgs84=1,2,3,0,0,0,0"), out);
    }

    @Test
    @DisplayName("toProjString: explicit TOWGS84 override blocks the +datum= token")
    void testExplicitTowgs84OverrideBlocksToken() {
        // A WKT datum named NAD83 with TOWGS84[1,2,3] is not PROJ's NAD83 (whose
        // canonical transform is 0,0,0): emitting the token would replace the
        // explicit override with the registry values on re-parse.
        String wkt = "GEOGCS[\"NAD83-ish\",DATUM[\"North_American_Datum_1983\","
            + "SPHEROID[\"GRS 1980\",6378137,298.257222101],TOWGS84[1,2,3]],"
            + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        String out = CRSSerializer.toProjString(new Proj(wkt));
        assertFalse(out.contains("+datum="), out);
        assertTrue(out.contains("+towgs84=1,2,3"), out);
        assertArrayEquals(new double[]{1, 2, 3},
            new Proj(out).getParams().datum.getDatumParams(), 0,
            "the override survives the round-trip");
    }

    @Test
    @DisplayName("toProjString: grid shift is preserved and stays authoritative")
    void testGridShiftPreserved() {
        // Grids are authoritative over towgs84 (DatumParams sets PJD_GRIDSHIFT last,
        // as proj4js does); both are emitted so the re-parsed datum state is
        // identical. Previously the grid was silently dropped.
        Proj p = new Proj("+proj=longlat +datum=ch1903 +nadgrids=@foo.gsb +no_defs");
        String out = CRSSerializer.toProjString(p);
        assertTrue(out.contains("+nadgrids=@foo.gsb"), out);
        assertTrue(out.contains("+towgs84=674.374,15.056,405.346"),
            "the dormant towgs84 half of the state is preserved too: " + out);
        Proj reimported = new Proj(out);
        assertTrue(reimported.getParams().datum.isGridShift(),
            "grid shift stays authoritative after the round-trip");
    }

    @Test
    @DisplayName("toProjString: NAD27 keeps its token — the grid lists match PROJ's")
    void testNad27GridTokenKept() {
        // NAD27 is grid-shift on both sides with the identical grid list, so the
        // semantic gate passes and the compact token is kept.
        assertTrue(CRSSerializer.toProjString(new Proj("+proj=longlat +datum=NAD27 +no_defs"))
            .contains("+datum=NAD27"));

        // A different grid list must reject the token and keep the explicit grid.
        String out = CRSSerializer.toProjString(
            new Proj("+proj=longlat +datum=NAD27 +nadgrids=@other.gsb +no_defs"));
        assertFalse(out.contains("+datum="), out);
        assertTrue(out.contains("+nadgrids=@other.gsb"), out);
    }

    @Test
    @DisplayName("toProjString: grid-shift datum with a converted 7-param tail re-encodes it")
    void testGridShiftWithConverted7ParamTail() {
        // The nadgrids override flips the datum type to PJD_GRIDSHIFT after the
        // 7-parameter tail was already converted to radians/multiplier, so the
        // re-encode must key on the converted-at-parse flag, not the datum type —
        // otherwise the dormant tail is emitted in internal units and every
        // round-trip converts it again.
        String def = "+proj=longlat +ellps=bessel "
            + "+towgs84=597.1,71.4,412.1,0.894,0.068,-1.563,7.58 +nadgrids=@foo.gsb +no_defs";
        String s1 = CRSSerializer.toProjString(new Proj(def));
        assertTrue(s1.contains("+towgs84=597.1,71.4,412.1,0.894,0.068,-1.563,7.58"), s1);
        assertTrue(s1.contains("+nadgrids=@foo.gsb"), s1);
        assertEquals(s1, CRSSerializer.toProjString(new Proj(s1)), "serialization is idempotent");
    }

    @Test
    @DisplayName("toProjString: rotation/scale re-encode is float-noise free")
    void testTowgs84ReEncodeRounding() {
        // The radian round-trip (x * SEC_TO_RAD / SEC_TO_RAD) is not exact in
        // binary floating point; the 1e-9 human-unit rounding keeps the emitted
        // values byte-identical to the input.
        String out = CRSSerializer.toProjString(
            new Proj("+proj=longlat +ellps=bessel +towgs84=1,2,3,0.1,0.2,0.3,0.4 +no_defs"));
        assertTrue(out.contains("+towgs84=1,2,3,0.1,0.2,0.3,0.4"), out);
    }

    @Test
    @DisplayName("toProjString: token gate compares 3- and 7-value transforms padded")
    void testTokenGatePaddedComparison() {
        // A 7-value all-zero-tail TOWGS84 equals PROJ's 3-value canonical WGS84.
        String wgs = "GEOGCS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "SPHEROID[\"WGS 84\",6378137,298.257223563],TOWGS84[0,0,0,0,0,0,0]],"
            + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        assertTrue(CRSSerializer.toProjString(new Proj(wgs)).contains("+datum=WGS84"));

        // A 3-value transform does not equal a canonical 7-value one (hermannskogel).
        String herm = "GEOGCS[\"MGI-ish\",DATUM[\"Hermannskogel\","
            + "SPHEROID[\"Bessel 1841\",6377397.155,299.1528128],"
            + "TOWGS84[577.326,90.129,463.919]],"
            + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        String out = CRSSerializer.toProjString(new Proj(herm));
        assertFalse(out.contains("+datum="), out);
        assertTrue(out.contains("+towgs84=577.326,90.129,463.919"), out);
    }

    @Test
    @DisplayName("toProjString: token gate rejects a datum name on the wrong ellipsoid")
    void testEllipsoidGateRejectsToken() {
        // PROJ's +datum=NAD83 implies GRS80; a document claiming the NAD83 datum on
        // a Bessel spheroid must not be tokenized (the token would replace the
        // ellipsoid on re-parse).
        String wkt = "GEOGCS[\"odd\",DATUM[\"North_American_Datum_1983\","
            + "SPHEROID[\"Bessel 1841\",6377397.155,299.1528128]],"
            + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        String out = CRSSerializer.toProjString(new Proj(wkt));
        assertFalse(out.contains("+datum="), out);
        assertTrue(out.contains("+ellps=bessel"), out);
    }

    @Test
    @DisplayName("toProjString: every PROJ datum token row is exercised")
    void testAllProjDatumTokens() {
        // One assertion per pj_datums row we tokenize (potsdam is deliberately
        // absent — see testPotsdamSemanticMismatch).
        String[][] cases = {
            {"WGS84", "+datum=WGS84"}, {"GGRS87", "+datum=GGRS87"},
            {"NAD83", "+datum=NAD83"}, {"NAD27", "+datum=NAD27"},
            {"carthage", "+datum=carthage"}, {"hermannskogel", "+datum=hermannskogel"},
            {"ire65", "+datum=ire65"}, {"nzgd49", "+datum=nzgd49"},
            {"OSGB36", "+datum=OSGB36"},
        };
        for (String[] c : cases) {
            String out = CRSSerializer.toProjString(
                new Proj("+proj=longlat +datum=" + c[0] + " +no_defs"));
            assertTrue(out.contains(c[1]), c[0] + " -> " + out);
        }
    }

    @Test
    @DisplayName("toProjString: PROJJSON-parsed datum name goes through the same gate")
    void testProjJsonDatumNameTokenized() {
        // EPSG:4277-style PROJJSON: the datum name resolves through the registry and
        // the gate (transform + airy ellipsoid match), yielding the compact token.
        String json = "{\"type\": \"GeographicCRS\", \"name\": \"OSGB36\","
            + "\"datum\": {\"type\": \"GeodeticReferenceFrame\","
            + "  \"name\": \"Ordnance Survey of Great Britain 1936\","
            + "  \"ellipsoid\": {\"name\": \"Airy 1830\", \"semi_major_axis\": 6377563.396,"
            + "   \"inverse_flattening\": 299.3249646}},"
            + "\"coordinate_system\": {\"subtype\": \"ellipsoidal\", \"axis\": ["
            + " {\"name\": \"Geodetic latitude\", \"abbreviation\": \"Lat\", \"direction\": \"north\", \"unit\": \"degree\"},"
            + " {\"name\": \"Geodetic longitude\", \"abbreviation\": \"Lon\", \"direction\": \"east\", \"unit\": \"degree\"}]}}";
        String out = CRSSerializer.toProjString(new Proj(json));
        assertTrue(out.contains("+datum=OSGB36"), out);
    }

    @Test
    @DisplayName("toProjString: datum outside PROJ's set falls back to +towgs84= and round-trips")
    void testNonProjDatumFallsBackToTowgs84() {
        // ch1903 is in our registry but not PROJ's pj_datums; PROJ serializes it as
        // +ellps=bessel +towgs84=... We do the same, which round-trips exactly.
        Proj original = new Proj("+proj=longlat +datum=ch1903 +no_defs");
        String out = CRSSerializer.toProjString(original);
        assertFalse(out.contains("+datum="), "not a PROJ datum: " + out);
        assertTrue(out.contains("+towgs84=674.374,15.056,405.346"), out);

        Proj reimported = new Proj(out);
        assertArrayEquals(
            original.getParams().datum.getDatumParams(),
            reimported.getParams().datum.getDatumParams(), 0.0,
            "the datum transform survives the +towgs84= round-trip");
    }
}
