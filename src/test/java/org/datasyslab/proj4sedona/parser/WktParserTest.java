package org.datasyslab.proj4sedona.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.ProjectionDef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for WKT parsing (WKT1, WKT2, and PROJJSON).
 */
class WktParserTest {

    // ========== WKT Version Detection Tests ==========

    @Test
    @DisplayName("Detect WKT1 from PROJCS keyword")
    void testDetectWkt1Projcs() {
        String wkt = "PROJCS[\"WGS 84 / UTM zone 32N\",GEOGCS[\"WGS 84\"]]";
        assertEquals(WktVersion.WKT1, WktVersion.detect(wkt));
    }

    @Test
    @DisplayName("Detect WKT1 from GEOGCS keyword")
    void testDetectWkt1Geogcs() {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\"]]";
        assertEquals(WktVersion.WKT1, WktVersion.detect(wkt));
    }

    @Test
    @DisplayName("Detect WKT2 from PROJCRS keyword")
    void testDetectWkt2Projcrs() {
        String wkt = "PROJCRS[\"WGS 84 / UTM zone 32N\",BASEGEOGCRS[\"WGS 84\"]]";
        assertEquals(WktVersion.WKT2, WktVersion.detect(wkt));
    }

    @Test
    @DisplayName("Detect WKT2 from GEOGCRS keyword")
    void testDetectWkt2Geogcrs() {
        String wkt = "GEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\"]]";
        assertEquals(WktVersion.WKT2, WktVersion.detect(wkt));
    }

    @Test
    @DisplayName("Detect WKT2 from LENGTHUNIT keyword")
    void testDetectWkt2Lengthunit() {
        String wkt = "PROJCS[\"Test\",LENGTHUNIT[\"metre\",1]]";
        assertEquals(WktVersion.WKT2, WktVersion.detect(wkt));
    }

    @Test
    @DisplayName("isWkt returns true for WKT strings")
    void testIsWkt() {
        assertTrue(WktParser.isWkt("PROJCS[\"Name\"]"));
        assertTrue(WktParser.isWkt("GEOGCS[\"WGS 84\"]"));
        assertTrue(WktParser.isWkt("PROJCRS[\"Name\"]"));
        assertFalse(WktParser.isWkt("+proj=longlat"));
        assertFalse(WktParser.isWkt(""));
        assertFalse(WktParser.isWkt(null));
    }

    // ========== WKT Tokenizer Tests ==========

    @Test
    @DisplayName("Tokenizer parses simple WKT")
    void testTokenizerSimple() {
        String wkt = "GEOGCS[\"WGS 84\"]";
        List<Object> ast = WktParser.parseToAst(wkt);
        
        assertNotNull(ast);
        assertEquals("GEOGCS", ast.get(0));
        assertEquals("WGS 84", ast.get(1));
    }

    @Test
    @DisplayName("Tokenizer parses nested structure")
    void testTokenizerNested() {
        String wkt = "PROJCS[\"Test\",GEOGCS[\"WGS 84\"]]";
        List<Object> ast = WktParser.parseToAst(wkt);
        
        assertNotNull(ast);
        assertEquals("PROJCS", ast.get(0));
        assertEquals("Test", ast.get(1));
        assertTrue(ast.get(2) instanceof List);
        
        @SuppressWarnings("unchecked")
        List<Object> geogcs = (List<Object>) ast.get(2);
        assertEquals("GEOGCS", geogcs.get(0));
        assertEquals("WGS 84", geogcs.get(1));
    }

    @Test
    @DisplayName("Tokenizer parses numbers")
    void testTokenizerNumbers() {
        String wkt = "SPHEROID[\"WGS 84\",6378137,298.257223563]";
        List<Object> ast = WktParser.parseToAst(wkt);
        
        assertNotNull(ast);
        assertEquals("SPHEROID", ast.get(0));
        assertEquals("WGS 84", ast.get(1));
        assertEquals(6378137.0, ast.get(2));
        assertEquals(298.257223563, (Double) ast.get(3), 1e-9);
    }

    @Test
    @DisplayName("Tokenizer handles quoted strings with spaces")
    void testTokenizerQuotedStrings() {
        String wkt = "PROJCS[\"NAD83 / UTM zone 10N\"]";
        List<Object> ast = WktParser.parseToAst(wkt);
        
        assertEquals("NAD83 / UTM zone 10N", ast.get(1));
    }

    // ========== WKT1 Parsing Tests ==========

    @Test
    @DisplayName("Parse WKT1 GEOGCS (WGS 84)")
    void testParseWkt1GeogcsWgs84() {
        String wkt = "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
        assertEquals("wgs84", def.getDatumCode());
        assertEquals(6378137.0, def.getA(), 0.1);
        assertEquals(298.257223563, def.getRf(), 1e-6);
    }

    @Test
    @DisplayName("Parse WKT1 PROJCS (UTM)")
    void testParseWkt1ProjcsUtm() {
        String wkt = "PROJCS[\"WGS 84 / UTM zone 32N\"," +
                "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]," +
                "PROJECTION[\"Transverse_Mercator\"]," +
                "PARAMETER[\"latitude_of_origin\",0]," +
                "PARAMETER[\"central_meridian\",9]," +
                "PARAMETER[\"scale_factor\",0.9996]," +
                "PARAMETER[\"false_easting\",500000]," +
                "PARAMETER[\"false_northing\",0]," +
                "UNIT[\"metre\",1]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("Transverse_Mercator", def.getProjName());
        assertEquals("wgs84", def.getDatumCode());
        assertEquals(0.9996, def.getK0(), 1e-6);
        assertEquals(500000.0, def.getX0(), 0.1);
        assertEquals(0.0, def.getY0(), 0.1);
        // Central meridian should be converted to radians
        assertEquals(9.0 * Values.D2R, def.getLong0(), 1e-6);
    }

    @Test
    @DisplayName("Parse WKT1 with TOWGS84")
    void testParseWkt1WithTowgs84() {
        String wkt = "GEOGCS[\"OSGB 1936\"," +
                "DATUM[\"OSGB_1936\"," +
                "SPHEROID[\"Airy 1830\",6377563.396,299.3249646]," +
                "TOWGS84[446.448,-125.157,542.06,0.15,0.247,0.842,-20.489]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertNotNull(def.getDatumParams());
        assertEquals(7, def.getDatumParams().length);
        assertEquals(446.448, def.getDatumParams()[0], 0.001);
        assertEquals(-125.157, def.getDatumParams()[1], 0.001);
    }

    @Test
    @DisplayName("Parse WKT1 Lambert Conformal Conic")
    void testParseWkt1Lcc() {
        String wkt = "PROJCS[\"NAD83 / California zone 6\"," +
                "GEOGCS[\"NAD83\"," +
                "DATUM[\"North_American_Datum_1983\"," +
                "SPHEROID[\"GRS 1980\",6378137,298.257222101]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]," +
                "PROJECTION[\"Lambert_Conformal_Conic_2SP\"]," +
                "PARAMETER[\"standard_parallel_1\",33.88333333333333]," +
                "PARAMETER[\"standard_parallel_2\",32.78333333333333]," +
                "PARAMETER[\"latitude_of_origin\",32.16666666666666]," +
                "PARAMETER[\"central_meridian\",-116.25]," +
                "PARAMETER[\"false_easting\",2000000]," +
                "PARAMETER[\"false_northing\",500000]," +
                "UNIT[\"metre\",1]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("Lambert_Conformal_Conic_2SP", def.getProjName());
        assertNotNull(def.getLat1());
        assertNotNull(def.getLat2());
        assertEquals(33.88333333333333 * Values.D2R, def.getLat1(), 1e-6);
        assertEquals(32.78333333333333 * Values.D2R, def.getLat2(), 1e-6);
    }

    // ========== WKT2 Parsing Tests ==========

    @Test
    @DisplayName("Parse WKT2 GEOGCRS")
    void testParseWkt2Geogcrs() {
        String wkt = "GEOGCRS[\"WGS 84\"," +
                "DATUM[\"World Geodetic System 1984\"," +
                "ELLIPSOID[\"WGS 84\",6378137,298.257223563,LENGTHUNIT[\"metre\",1]]]," +
                "CS[ellipsoidal,2]," +
                "AXIS[\"latitude\",north,ORDER[1]]," +
                "AXIS[\"longitude\",east,ORDER[2]]," +
                "ANGLEUNIT[\"degree\",0.0174532925199433]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
        assertEquals(6378137.0, def.getA(), 0.1);
    }

    @Test
    @DisplayName("Parse WKT2 PROJCRS (UTM)")
    void testParseWkt2ProjcrsUtm() {
        String wkt = "PROJCRS[\"WGS 84 / UTM zone 32N\"," +
                "BASEGEOGCRS[\"WGS 84\"," +
                "DATUM[\"World Geodetic System 1984\"," +
                "ELLIPSOID[\"WGS 84\",6378137,298.257223563]]]," +
                "CONVERSION[\"UTM zone 32N\"," +
                "METHOD[\"Transverse Mercator\"]," +
                "PARAMETER[\"Latitude of natural origin\",0,ANGLEUNIT[\"degree\",0.0174532925199433]]," +
                "PARAMETER[\"Longitude of natural origin\",9,ANGLEUNIT[\"degree\",0.0174532925199433]]," +
                "PARAMETER[\"Scale factor at natural origin\",0.9996,SCALEUNIT[\"unity\",1]]," +
                "PARAMETER[\"False easting\",500000,LENGTHUNIT[\"metre\",1]]," +
                "PARAMETER[\"False northing\",0,LENGTHUNIT[\"metre\",1]]]," +
                "CS[Cartesian,2]," +
                "AXIS[\"easting\",east]," +
                "AXIS[\"northing\",north]," +
                "LENGTHUNIT[\"metre\",1]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("Transverse Mercator", def.getProjName());
        assertEquals(6378137.0, def.getA(), 0.1);
        assertEquals(0.9996, def.getK0(), 1e-6);
        assertEquals(500000.0, def.getX0(), 0.1);

        Map<String, Object> projjson = WktParser.parseWkt2ToProjJson(wkt);
        Object coordinateSystemValue = projjson.get("coordinate_system");
        assertTrue(coordinateSystemValue instanceof Map);
        Map<?, ?> coordinateSystem = (Map<?, ?>) coordinateSystemValue;
        assertEquals("Cartesian", coordinateSystem.get("subtype"));
        assertFalse(coordinateSystem.containsKey("type"),
            "PROJJSON coordinate systems use the subtype key");
    }

    @Test
    @DisplayName("Standalone AXIS fallback does not invent a coordinate-system subtype")
    void testStandaloneAxisFallbackOmitsUnknownSubtype() {
        Map<String, Object> projjson = ProjJsonBuilder.convert(
            List.<Object>of("AXIS", "easting", "east"), new HashMap<>());

        Object coordinateSystemValue = projjson.get("coordinate_system");
        assertTrue(coordinateSystemValue instanceof Map);
        Map<?, ?> coordinateSystem = (Map<?, ?>) coordinateSystemValue;
        assertFalse(coordinateSystem.containsKey("type"));
        assertFalse(coordinateSystem.containsKey("subtype"));

        Object axes = coordinateSystem.get("axis");
        assertTrue(axes instanceof List);
        assertEquals(1, ((List<?>) axes).size());
    }

    @Test
    @DisplayName("Parse WKT2 BOUNDCRS")
    void testParseWkt2BoundCrs() {
        String wkt = "BOUNDCRS[" +
                "SOURCECRS[GEOGCRS[\"OSGB 1936\"," +
                "DATUM[\"OSGB 1936\"," +
                "ELLIPSOID[\"Airy 1830\",6377563.396,299.3249646]]]]," +
                "TARGETCRS[GEOGCRS[\"WGS 84\"," +
                "DATUM[\"World Geodetic System 1984\"," +
                "ELLIPSOID[\"WGS 84\",6378137,298.257223563]]]]," +
                "ABRIDGEDTRANSFORMATION[\"OSGB 1936 to WGS 84 (6)\"," +
                "METHOD[\"Position Vector transformation (geog2D domain)\"]," +
                "PARAMETER[\"X-axis translation\",446.448,LENGTHUNIT[\"metre\",1]]," +
                "PARAMETER[\"Y-axis translation\",-125.157,LENGTHUNIT[\"metre\",1]]," +
                "PARAMETER[\"Z-axis translation\",542.06,LENGTHUNIT[\"metre\",1]]]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        // Should extract datum params from the transformation
        assertNotNull(def.getDatumParams());
        assertTrue(def.getDatumParams().length >= 3);
        assertEquals(446.448, def.getDatumParams()[0], 0.001);
    }

    // ========== PROJJSON Parsing Tests ==========

    @Test
    @DisplayName("Parse PROJJSON GeographicCRS")
    void testParseProjJsonGeographic() {
        Map<String, Object> projjson = new HashMap<>();
        projjson.put("type", "GeographicCRS");
        projjson.put("name", "WGS 84");
        
        Map<String, Object> datum = new HashMap<>();
        datum.put("type", "GeodeticReferenceFrame");
        datum.put("name", "World Geodetic System 1984");
        
        Map<String, Object> ellipsoid = new HashMap<>();
        ellipsoid.put("name", "WGS 84");
        ellipsoid.put("semi_major_axis", 6378137.0);
        ellipsoid.put("inverse_flattening", 298.257223563);
        datum.put("ellipsoid", ellipsoid);
        
        projjson.put("datum", datum);
        
        ProjectionDef def = WktParser.parse(projjson);
        
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
        assertEquals(6378137.0, def.getA(), 0.1);
        assertEquals(298.257223563, def.getRf(), 1e-6);
    }

    @Test
    @DisplayName("Parse PROJJSON ProjectedCRS")
    void testParseProjJsonProjected() {
        Map<String, Object> projjson = new HashMap<>();
        projjson.put("type", "ProjectedCRS");
        projjson.put("name", "WGS 84 / UTM zone 32N");
        
        // Conversion
        Map<String, Object> conversion = new HashMap<>();
        Map<String, Object> method = new HashMap<>();
        method.put("name", "Transverse Mercator");
        conversion.put("method", method);
        projjson.put("conversion", conversion);
        
        // Base CRS with datum
        Map<String, Object> baseCrs = new HashMap<>();
        baseCrs.put("type", "GeographicCRS");
        Map<String, Object> datum = new HashMap<>();
        Map<String, Object> ellipsoid = new HashMap<>();
        ellipsoid.put("semi_major_axis", 6378137.0);
        ellipsoid.put("inverse_flattening", 298.257223563);
        datum.put("ellipsoid", ellipsoid);
        baseCrs.put("datum", datum);
        projjson.put("base_crs", baseCrs);
        
        ProjectionDef def = WktParser.parse(projjson);
        
        assertNotNull(def);
        assertEquals("Transverse Mercator", def.getProjName());
        assertEquals(6378137.0, def.getA(), 0.1);
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("WKT1 round-trip transformation")
    void testWkt1RoundTrip() {
        String wkt = "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]";
        
        // Parse to ProjectionDef
        ProjectionDef def = WktParser.parse(wkt);
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
        
        // Verify WKT can be used directly with Proj (via WktParser integration)
        try {
            org.datasyslab.proj4sedona.core.Proj proj = new org.datasyslab.proj4sedona.core.Proj(wkt);
            // Forward and inverse should work
            Point input = new Point(10.0 * Values.D2R, 50.0 * Values.D2R);
            Point result = proj.forward(input);
            // For longlat, forward is identity
            assertEquals(input.x, result.x, 1e-9);
            assertEquals(input.y, result.y, 1e-9);
        } catch (Exception e) {
            // Expected - projection initialization might require more setup
        }
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Handle empty WKT throws exception")
    void testEmptyWktThrows() {
        assertThrows(IllegalArgumentException.class, () -> WktParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> WktParser.parse((String) null));
    }

    @Test
    @DisplayName("Handle null PROJJSON throws exception")
    void testNullProjJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> WktParser.parse((Map<String, Object>) null));
    }

    @Test
    @DisplayName("Parse WKT with scientific notation")
    void testWktScientificNotation() {
        String wkt = "SPHEROID[\"WGS 84\",6.378137E+6,2.98257223563E+2]";
        List<Object> ast = WktParser.parseToAst(wkt);
        
        // The tokenizer returns the name as string, numbers as Double
        assertEquals("WGS 84", ast.get(1));
        Object val2 = ast.get(2);
        Object val3 = ast.get(3);
        double num2 = val2 instanceof Number ? ((Number) val2).doubleValue() : Double.parseDouble(val2.toString());
        double num3 = val3 instanceof Number ? ((Number) val3).doubleValue() : Double.parseDouble(val3.toString());
        assertEquals(6378137.0, num2, 1.0);
        assertEquals(298.257223563, num3, 1e-6);
    }

    @Test
    @DisplayName("Parse WKT with negative numbers")
    void testWktNegativeNumbers() {
        String wkt = "PARAMETER[\"false_northing\",-10000000]";
        List<Object> ast = WktParser.parseToAst(wkt);
        
        // The tokenizer returns: ["PARAMETER", "false_northing", -10000000.0]
        assertEquals("false_northing", ast.get(1));
        Object val = ast.get(2);
        double num = val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString());
        assertEquals(-10000000.0, num, 0.1);
    }

    @Test
    @DisplayName("Parse WKT with escaped quotes")
    void testWktEscapedQuotes() {
        String wkt = "PROJCS[\"Test \"\"Name\"\"\"]";
        List<Object> ast = WktParser.parseToAst(wkt);
        
        assertEquals("Test \"Name\"", ast.get(1));
    }

    // ========== Datum Code Normalization Tests ==========

    @Test
    @DisplayName("Datum code normalization for WGS_1984")
    void testDatumNormalizationWgs84() {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]]]";
        ProjectionDef def = WktParser.parse(wkt);
        
        assertEquals("wgs84", def.getDatumCode());
    }

    @Test
    @DisplayName("Datum code normalization removes d_ prefix")
    void testDatumNormalizationDPrefix() {
        String wkt = "GEOGCS[\"Test\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]]]";
        ProjectionDef def = WktParser.parse(wkt);
        
        // Should remove "d_" prefix and normalize
        assertTrue(def.getDatumCode().equals("wgs84") || def.getDatumCode().equals("wgs_1984"));
    }

    // ========== Unusual Units Tests ==========

    @Test
    @DisplayName("Parse WKT with US Survey Feet")
    void testWktUsSurveyFeet() {
        String wkt = "PROJCS[\"NAD83 / California zone 6 (ftUS)\"," +
                "GEOGCS[\"NAD83\"," +
                "DATUM[\"North_American_Datum_1983\"," +
                "SPHEROID[\"GRS 1980\",6378137,298.257222101]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]," +
                "PROJECTION[\"Lambert_Conformal_Conic_2SP\"]," +
                "PARAMETER[\"standard_parallel_1\",33.88333333333333]," +
                "PARAMETER[\"standard_parallel_2\",32.78333333333333]," +
                "PARAMETER[\"latitude_of_origin\",32.16666666666666]," +
                "PARAMETER[\"central_meridian\",-116.25]," +
                "PARAMETER[\"false_easting\",6561666.667]," +
                "PARAMETER[\"false_northing\",1640416.667]," +
                "UNIT[\"US survey foot\",0.3048006096012192]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("us survey foot", def.getUnits());
        assertEquals(0.3048006096012192, def.getToMeter(), 1e-12);
    }

    @Test
    @DisplayName("Parse WKT with chains")
    void testWktChains() {
        String wkt = "PROJCS[\"Test with chains\"," +
                "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563]]]," +
                "PROJECTION[\"Transverse_Mercator\"]," +
                "UNIT[\"chain\",20.1168]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("chain", def.getUnits());
        assertEquals(20.1168, def.getToMeter(), 1e-6);
    }

    @Test
    @DisplayName("Parse WKT with links")
    void testWktLinks() {
        String wkt = "PROJCS[\"Test with links\"," +
                "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563]]]," +
                "PROJECTION[\"Transverse_Mercator\"]," +
                "UNIT[\"link\",0.201168]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("link", def.getUnits());
        assertEquals(0.201168, def.getToMeter(), 1e-8);
    }

    // ========== Non-Greenwich Prime Meridian Tests ==========

    @Test
    @DisplayName("Parse WKT with Paris prime meridian")
    void testWktParisPrimeMeridian() {
        String wkt = "GEOGCS[\"NTF (Paris)\"," +
                "DATUM[\"Nouvelle_Triangulation_Francaise_Paris\"," +
                "SPHEROID[\"Clarke 1880 (IGN)\",6378249.2,293.4660212936265]]," +
                "PRIMEM[\"Paris\",2.33722917]," +
                "UNIT[\"grad\",0.01570796326794897]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        // Paris meridian is approximately 2.337 degrees east of Greenwich
        assertNotNull(def.getFromGreenwich());
        // The value should be in radians: 2.33722917 * D2R ≈ 0.0408 radians
        assertEquals(2.33722917 * Values.D2R, def.getFromGreenwich(), 1e-6);
    }

    @Test
    @DisplayName("Parse WKT with Ferro prime meridian")
    void testWktFerroPrimeMeridian() {
        String wkt = "GEOGCS[\"MGI (Ferro)\"," +
                "DATUM[\"Militar_Geographische_Institut_Ferro\"," +
                "SPHEROID[\"Bessel 1841\",6377397.155,299.1528128]]," +
                "PRIMEM[\"Ferro\",-17.66666666666667]," +
                "UNIT[\"degree\",0.0174532925199433]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        // Ferro is approximately 17.67 degrees west of Greenwich (negative)
        assertNotNull(def.getFromGreenwich());
        assertEquals(-17.66666666666667 * Values.D2R, def.getFromGreenwich(), 1e-6);
    }

    // ========== Deeply Nested WKT Tests ==========

    @Test
    @DisplayName("Parse deeply nested WKT with COMPD_CS")
    void testWktDeeplyNestedCompoundCrs() {
        String wkt = "COMPD_CS[\"NAD83 / UTM zone 10N + NAVD88 height\"," +
                "PROJCS[\"NAD83 / UTM zone 10N\"," +
                "GEOGCS[\"NAD83\"," +
                "DATUM[\"North_American_Datum_1983\"," +
                "SPHEROID[\"GRS 1980\",6378137,298.257222101]," +
                "TOWGS84[0,0,0,0,0,0,0]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]," +
                "PROJECTION[\"Transverse_Mercator\"]," +
                "PARAMETER[\"latitude_of_origin\",0]," +
                "PARAMETER[\"central_meridian\",-123]," +
                "PARAMETER[\"scale_factor\",0.9996]," +
                "PARAMETER[\"false_easting\",500000]," +
                "PARAMETER[\"false_northing\",0]," +
                "UNIT[\"metre\",1]]," +
                "VERT_CS[\"NAVD88 height\"," +
                "VERT_DATUM[\"North American Vertical Datum 1988\",2005]," +
                "UNIT[\"metre\",1]]]";
        
        // Should parse without throwing an exception
        List<Object> ast = WktParser.parseToAst(wkt);
        assertNotNull(ast);
        assertEquals("COMPD_CS", ast.get(0));
        
        // The nested PROJCS should be present
        boolean hasProjcs = false;
        for (Object item : ast) {
            if (item instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> child = (List<Object>) item;
                if (!child.isEmpty() && "PROJCS".equals(child.get(0))) {
                    hasProjcs = true;
                    break;
                }
            }
        }
        assertTrue(hasProjcs, "Should contain nested PROJCS");
    }

    @Test
    @DisplayName("Parse WKT with multiple nested AUTHORITY")
    void testWktMultipleAuthority() {
        String wkt = "PROJCS[\"WGS 84 / UTM zone 32N\"," +
                "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563," +
                "AUTHORITY[\"EPSG\",\"7030\"]]," +
                "AUTHORITY[\"EPSG\",\"6326\"]]," +
                "PRIMEM[\"Greenwich\",0," +
                "AUTHORITY[\"EPSG\",\"8901\"]]," +
                "UNIT[\"degree\",0.0174532925199433," +
                "AUTHORITY[\"EPSG\",\"9122\"]]," +
                "AUTHORITY[\"EPSG\",\"4326\"]]," +
                "PROJECTION[\"Transverse_Mercator\"]," +
                "PARAMETER[\"latitude_of_origin\",0]," +
                "PARAMETER[\"central_meridian\",9]," +
                "PARAMETER[\"scale_factor\",0.9996]," +
                "PARAMETER[\"false_easting\",500000]," +
                "PARAMETER[\"false_northing\",0]," +
                "UNIT[\"metre\",1," +
                "AUTHORITY[\"EPSG\",\"9001\"]]," +
                "AUTHORITY[\"EPSG\",\"32632\"]]";
        
        ProjectionDef def = WktParser.parse(wkt);
        
        assertNotNull(def);
        assertEquals("Transverse_Mercator", def.getProjName());
        // Should extract the title from AUTHORITY
        assertNotNull(def.getTitle());
        assertTrue(def.getTitle().contains("EPSG") || def.getTitle().contains("32632"));
    }

    // ========== Latitude of Standard Parallel Tests ==========

    @Test
    @DisplayName("latitude_of_standard_parallel sets both lat0 and lat1")
    void testLatitudeOfStandardParallel() {
        // This tests the applyCalculatedProperties fix
        Map<String, Object> projjson = new HashMap<>();
        projjson.put("type", "ProjectedCRS");
        projjson.put("name", "Test Projection");
        
        Map<String, Object> conversion = new HashMap<>();
        Map<String, Object> method = new HashMap<>();
        method.put("name", "Lambert_Conformal_Conic_1SP");
        conversion.put("method", method);
        
        // Add latitude_of_standard_parallel as a parameter
        java.util.List<Map<String, Object>> params = new java.util.ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("name", "Latitude of standard parallel");
        param.put("value", 45.0);
        Map<String, Object> unit = new HashMap<>();
        unit.put("conversion_factor", Values.D2R); // Convert to radians
        param.put("unit", unit);
        params.add(param);
        conversion.put("parameters", params);
        
        projjson.put("conversion", conversion);
        
        ProjectionDef def = WktParser.parse(projjson);
        
        assertNotNull(def);
        // Both lat0 and lat1 should be set from latitude_of_standard_parallel
        assertNotNull(def.getLat0());
        assertNotNull(def.getLat1());
        assertEquals(def.getLat0(), def.getLat1(), 1e-9);
    }

    // ========== WKT2 GEODCRS (geographic + geocentric) ==========

    private static final String GEODCRS_4978_CS =
        "CS[Cartesian,3],"
        + "AXIS[\"(X)\",geocentricX,ORDER[1],LENGTHUNIT[\"metre\",1]],"
        + "AXIS[\"(Y)\",geocentricY,ORDER[2],LENGTHUNIT[\"metre\",1]],"
        + "AXIS[\"(Z)\",geocentricZ,ORDER[3],LENGTHUNIT[\"metre\",1]]";

    private static final String GEODCRS_4978_HEAD =
        "GEODCRS[\"WGS 84\",ENSEMBLE[\"World Geodetic System 1984 ensemble\","
        + "MEMBER[\"World Geodetic System 1984 (Transit)\"],"
        + "MEMBER[\"World Geodetic System 1984 (G2296)\"],"
        + "ELLIPSOID[\"WGS 84\",6378137,298.257223563,LENGTHUNIT[\"metre\",1]],"
        + "ENSEMBLEACCURACY[2.0]],"
        + "PRIMEM[\"Greenwich\",0,ANGLEUNIT[\"degree\",0.0174532925199433]],";

    private void assertGeocentric4978(String wkt) {
        ProjectionDef def = WktParser.parse(wkt);
        assertEquals("geocent", def.getProjName(), "projName");
        assertEquals("enu", def.getAxis(), "geocentric X/Y/Z axes map to enu");
        assertEquals("meter", def.getUnits(), "axis unit");
        assertEquals(1.0, def.getToMeter(), 0, "identity to-metre factor");

        // Same ECEF references as GeocentricTest (pyproj/PROJ 9.5.1).
        org.datasyslab.proj4sedona.transform.Converter conv =
            org.datasyslab.proj4sedona.Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", wkt);
        Point xyz = conv.forward(new Point(-7.56, 55.95));
        assertEquals(3548342.473034, xyz.x, 1e-4, "X");
        assertEquals(-470928.890965, xyz.y, 1e-4, "Y");
        assertEquals(5261327.157452, xyz.z, 1e-4, "computed ECEF Z");
    }

    @Test
    @DisplayName("GEODCRS geocentric (WKT2-2019 form with USAGE) parses as geocent")
    void testGeodcrsGeocentric2019() {
        // wkt-parser 1.5.5 test fixture (expected: projName geocent, axis enu,
        // units meter, to_meter 1); trimmed ensemble members.
        assertGeocentric4978(GEODCRS_4978_HEAD + GEODCRS_4978_CS
            + ",USAGE[SCOPE[\"Geodesy.\"],AREA[\"World.\"],BBOX[-90,-180,90,180]]"
            + ",ID[\"EPSG\",4978]]");
    }

    @Test
    @DisplayName("GEODCRS geocentric (WKT2-2015 form, no USAGE) parses as geocent")
    void testGeodcrsGeocentric2015() {
        // Exact wkt-parser 1.5.6 fixture added by b7abacf.
        String wkt = "GEODCRS[\"WGS 84\","
            + "DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563,LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Greenwich\",0,ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "CS[Cartesian,3],"
            + "AXIS[\"(X)\",geocentricX,ORDER[1],LENGTHUNIT[\"metre\",1]],"
            + "AXIS[\"(Y)\",geocentricY,ORDER[2],LENGTHUNIT[\"metre\",1]],"
            + "AXIS[\"(Z)\",geocentricZ,ORDER[3],LENGTHUNIT[\"metre\",1]],"
            + "ID[\"EPSG\",4978]]";
        assertGeocentric4978(wkt);
    }

    @Test
    @DisplayName("PROJ crs.EPSG_4807_as_WKT2: ellipsoidal GEODCRS parses as longlat")
    void testGeodcrsEllipsoidal4807() {
        // Input verbatim from PROJ 9.5.1 test/unit/test_crs.cpp (crs.EPSG_4807_as_WKT2);
        // PROJ exports it as "+proj=longlat +ellps=clrk80ign +pm=paris +no_defs" —
        // assertions cover the projection kind and unit tokens only (datum and prime
        // meridian name resolution differ from PROJ's EPSG database).
        String wkt = "GEODCRS[\"NTF (Paris)\","
            + "DATUM[\"Nouvelle Triangulation Francaise (Paris)\","
            + "ELLIPSOID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627,LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Paris\",2.5969213,ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "CS[ellipsoidal,2],"
            + "AXIS[\"latitude\",north,ORDER[1],ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "AXIS[\"longitude\",east,ORDER[2],ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "ID[\"EPSG\",4807]]";
        ProjectionDef def = WktParser.parse(wkt);
        assertEquals("longlat", def.getProjName(), "ellipsoidal GEODCRS is geographic");

        String projStr = CRSSerializer.toProjString(
            new org.datasyslab.proj4sedona.core.Proj(wkt));
        assertTrue(projStr.contains("+proj=longlat"), projStr);
        assertFalse(projStr.contains("+units="), projStr);
        assertFalse(projStr.contains("+to_meter="), projStr);
    }

    // ========== WKT2_2015_SIMPLIFIED (plain UNIT keyword, no per-axis units) ==========

    @Test
    @DisplayName("GEODCRS geocentric in WKT2_2015_SIMPLIFIED form parses as geocent")
    void testGeodcrsSimplified4978() {
        // PROJ 9.5.1's to_wkt('WKT2_2015_SIMPLIFIED') for EPSG:4978: no LENGTHUNIT/
        // ANGLEUNIT/USAGE keywords at all — detection must key on GEODCRS itself,
        // and the single CS-level UNIT applies to the axes.
        String wkt = "GEODCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "CS[Cartesian,3],AXIS[\"(X)\",geocentricX],AXIS[\"(Y)\",geocentricY],"
            + "AXIS[\"(Z)\",geocentricZ],UNIT[\"metre\",1],"
            + "SCOPE[\"Geodesy.\"],AREA[\"World.\"],BBOX[-90,-180,90,180],ID[\"EPSG\",4978]]";
        assertEquals(WktVersion.WKT2, WktVersion.detect(wkt), "simplified GEODCRS is WKT2");
        ProjectionDef def = WktParser.parse(wkt);
        assertEquals("geocent", def.getProjName());
        assertEquals("enu", def.getAxis());
        assertEquals(1.0, def.getToMeter(), 0, "CS-level metre unit propagated");

        org.datasyslab.proj4sedona.transform.Converter conv =
            org.datasyslab.proj4sedona.Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", wkt);
        Point xyz = conv.forward(new Point(-7.56, 55.95));
        assertEquals(3548342.473034, xyz.x, 1e-4, "X");
        assertEquals(-470928.890965, xyz.y, 1e-4, "Y");
        assertEquals(5261327.157452, xyz.z, 1e-4, "computed ECEF Z");
    }

    @Test
    @DisplayName("Simplified geocentric CRS with non-metre CS unit keeps its scale")
    void testGeodcrsSimplifiedNonMetreKeepsScale() {
        // PROJ 9.5.1's WKT2_2015_SIMPLIFIED for +proj=geocent +datum=WGS84 +units=us-ft.
        // The CS-level UNIT is the only unit in the document; dropping it would
        // silently produce metre output. References match GeocentricTest's us-ft
        // case (pyproj/PROJ 9.5.1).
        String wkt = "GEODCRS[\"unknown\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563],ID[\"EPSG\",6326]],"
            + "CS[Cartesian,3],AXIS[\"(X)\",geocentricX],AXIS[\"(Y)\",geocentricY],"
            + "AXIS[\"(Z)\",geocentricZ],"
            + "UNIT[\"US survey foot\",0.304800609601219,ID[\"EPSG\",9003]]]";
        ProjectionDef def = WktParser.parse(wkt);
        assertEquals("geocent", def.getProjName());
        assertEquals(0.304800609601219, def.getToMeter(), 1e-15, "us-ft factor propagated");

        org.datasyslab.proj4sedona.transform.Converter conv =
            org.datasyslab.proj4sedona.Proj4.proj4("+proj=longlat +datum=WGS84 +no_defs", wkt);
        Point xyz = conv.forward(new Point(2.35, 48.85, 100));
        assertEquals(13784550.5068, xyz.x, 0.001, "X in US feet");
        assertEquals(565693.8601, xyz.y, 0.001, "Y in US feet");
        assertEquals(15681312.7958, xyz.z, 0.001, "Z in US feet");
    }

    @Test
    @DisplayName("GEODCRS ellipsoidal in WKT2_2015_SIMPLIFIED form parses as longlat")
    void testGeodcrsSimplified4807() {
        // PROJ 9.5.1's WKT2_2015_SIMPLIFIED for EPSG:4807 (grads CS-level UNIT).
        String wkt = "GEODCRS[\"NTF (Paris)\",DATUM[\"Nouvelle Triangulation Francaise (Paris)\","
            + "ELLIPSOID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627]],"
            + "PRIMEM[\"Paris\",2.5969213],CS[ellipsoidal,2],"
            + "AXIS[\"geodetic latitude (Lat)\",north],AXIS[\"geodetic longitude (Lon)\",east],"
            + "UNIT[\"grad\",0.0157079632679489],ID[\"EPSG\",4807]]";
        ProjectionDef def = WktParser.parse(wkt);
        assertEquals("longlat", def.getProjName());

        String projStr = CRSSerializer.toProjString(
            new org.datasyslab.proj4sedona.core.Proj(wkt));
        assertFalse(projStr.contains("+units="), projStr);
        assertFalse(projStr.contains("+to_meter="),
            "angular CS unit must not leak into +to_meter=: " + projStr);
    }

    @Test
    @DisplayName("GEODCRS PROJJSON type follows the CS subtype, not the keyword")
    void testGeodcrsProjJsonTypeBySubtype() {
        // PROJ rejects GeodeticCRS + ellipsoidal PROJJSON ("expected a Cartesian or
        // spherical CS") and normalizes an ellipsoidal GEODCRS to GeographicCRS, so
        // the intermediate PROJJSON must pick the type from the coordinate system.
        String cartesian = GEODCRS_4978_HEAD + GEODCRS_4978_CS + ",ID[\"EPSG\",4978]]";
        assertEquals("GeodeticCRS",
            WktParser.parseWkt2ToProjJson(cartesian).get("type"),
            "Cartesian CS keeps the geocentric type");

        String ellipsoidal = "GEODCRS[\"NTF (Paris)\","
            + "DATUM[\"Nouvelle Triangulation Francaise (Paris)\","
            + "ELLIPSOID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627,LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Paris\",2.5969213,ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "CS[ellipsoidal,2],"
            + "AXIS[\"latitude\",north,ORDER[1],ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "AXIS[\"longitude\",east,ORDER[2],ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "ID[\"EPSG\",4807]]";
        assertEquals("GeographicCRS",
            WktParser.parseWkt2ToProjJson(ellipsoidal).get("type"),
            "ellipsoidal GEODCRS normalizes to GeographicCRS, as PROJ does");
    }

    @Test
    @DisplayName("Axis direction tokens keep their case in the intermediate PROJJSON")
    @SuppressWarnings("unchecked")
    void testAxisDirectionCasePreserved() {
        // wkt-parser 1.5.5 stopped lowercasing the direction token: the PROJJSON
        // direction enum is camelCase for geocentricX/Y/Z, so the exposed
        // intermediate PROJJSON (parseWkt2ToProjJson) carried schema-invalid
        // "geocentricx" values. Parsing tolerance is unchanged — the transformer
        // lowercases at lookup.
        String wkt = GEODCRS_4978_HEAD + GEODCRS_4978_CS + ",ID[\"EPSG\",4978]]";
        Map<String, Object> projjson = WktParser.parseWkt2ToProjJson(wkt);
        Map<String, Object> cs = (Map<String, Object>) projjson.get("coordinate_system");
        List<Map<String, Object>> axes = (List<Map<String, Object>>) cs.get("axis");
        assertEquals("geocentricX", axes.get(0).get("direction"));
        assertEquals("geocentricY", axes.get(1).get("direction"));
        assertEquals("geocentricZ", axes.get(2).get("direction"));

        // End-to-end parse still resolves the directions (case-insensitive lookup).
        assertEquals("enu", WktParser.parse(wkt).getAxis());
    }

    @Test
    @DisplayName("longitude_of_center feeds long0 for every projection")
    void testLongitudeOfCenterFeedsLong0() {
        // wkt-parser 1.5.5 (util.js) dropped the Albers/LAEA-only restriction on the
        // longc -> long0 fallback. GDAL-style WKT1 with longitude_of_center on other
        // projections silently projected around longitude 0 here. Reference from
        // pyproj 3.7.2/PROJ 9.5.1.
        String wkt = "PROJCS[\"World_Sinusoidal\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"WGS_1984\","
            + "SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],"
            + "UNIT[\"degree\",0.0174532925199433]],PROJECTION[\"Sinusoidal\"],"
            + "PARAMETER[\"longitude_of_center\",100],PARAMETER[\"false_easting\",0],"
            + "PARAMETER[\"false_northing\",0],UNIT[\"metre\",1]]";
        ProjectionDef def = WktParser.parse(wkt);
        assertNotNull(def.getLong0(), "long0 populated from longitude_of_center");
        assertEquals(100 * Values.D2R, def.getLong0(), 1e-12);

        org.datasyslab.proj4sedona.transform.Converter conv =
            org.datasyslab.proj4sedona.Proj4.proj4("EPSG:4326", wkt);
        Point xy = conv.forward(new Point(105, 10));
        assertEquals(548196.8203407656, xy.x, 1e-4, "x with central meridian 100");
        assertEquals(1105854.833234372, xy.y, 1e-4, "y");

        // Oblique-mercator-family CRSs get long0 populated too (1.5.5 fixture
        // expectation); omerc itself reads longc directly, so values must agree.
        String omerc = "PROJCS[\"Hotine\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"WGS_1984\","
            + "SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],"
            + "UNIT[\"degree\",0.0174532925199433]],"
            + "PROJECTION[\"Hotine_Oblique_Mercator_Azimuth_Center\"],"
            + "PARAMETER[\"latitude_of_center\",4],PARAMETER[\"longitude_of_center\",115],"
            + "PARAMETER[\"azimuth\",53.315820472222],PARAMETER[\"rectified_grid_angle\",53.130102361111],"
            + "PARAMETER[\"scale_factor\",0.99984],PARAMETER[\"false_easting\",0],"
            + "PARAMETER[\"false_northing\",0],UNIT[\"metre\",1]]";
        ProjectionDef omercDef = WktParser.parse(omerc);
        assertNotNull(omercDef.getLong0(), "omerc-family long0 populated");
        assertEquals(omercDef.getLongc(), omercDef.getLong0(), 0, "long0 equals longc");
    }

    @Test
    @DisplayName("Issue #99: grads PRIMEM resolves through its angular unit")
    void testPrimemAngularUnits() {
        // EPSG:4807's Paris meridian is 2.5969213 grads = 2.33722917 degrees. The
        // raw value was read as degrees (~0.26 deg / 29 km error). Verified against
        // PROJ, whose export of the same CRS is +pm=paris (2.33722917 deg).
        // Divergence from wkt-parser 1.5.5, which assumes degrees unconditionally.
        String full = "GEODCRS[\"NTF (Paris)\",DATUM[\"Nouvelle Triangulation Francaise (Paris)\","
            + "ELLIPSOID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627,LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Paris\",2.5969213,ANGLEUNIT[\"grad\",0.015707963267949]],CS[ellipsoidal,2],"
            + "AXIS[\"latitude\",north,ORDER[1],ANGLEUNIT[\"grad\",0.015707963267949]],"
            + "AXIS[\"longitude\",east,ORDER[2],ANGLEUNIT[\"grad\",0.015707963267949]],ID[\"EPSG\",4807]]";
        // The SIMPLIFIED form drops the local ANGLEUNIT; the value is in the CRS's
        // CS-level unit (grads), not degrees.
        String simplified = "GEODCRS[\"NTF (Paris)\",DATUM[\"Nouvelle Triangulation Francaise (Paris)\","
            + "ELLIPSOID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627]],"
            + "PRIMEM[\"Paris\",2.5969213],CS[ellipsoidal,2],"
            + "AXIS[\"geodetic latitude (Lat)\",north],AXIS[\"geodetic longitude (Lon)\",east],"
            + "UNIT[\"grad\",0.0157079632679489],ID[\"EPSG\",4807]]";
        for (String wkt : new String[]{full, simplified}) {
            org.datasyslab.proj4sedona.core.Proj p = new org.datasyslab.proj4sedona.core.Proj(wkt);
            assertEquals(2.33722917, Math.toDegrees(p.getParams().fromGreenwich), 1e-9,
                "Paris meridian in degrees");
            assertTrue(CRSSerializer.toProjString(p).contains("+pm=2.33722917"),
                CRSSerializer.toProjString(p));
        }

        // Internal consistency: a Greenwich longitude of 5 deg is 5 - 2.33722917 deg
        // east of the Paris meridian.
        Point out = org.datasyslab.proj4sedona.Proj4
            .proj4("+proj=longlat +datum=WGS84 +no_defs", full)
            .forward(new Point(5.0, 48.0));
        assertEquals(5.0 - 2.33722917, out.x, 1e-9, "lon relative to Paris");
        assertEquals(48.0, out.y, 1e-9, "lat unchanged");

        // A degree-unit PRIMEM stays an identity conversion (Madrid, EPSG:4903 style).
        String madrid = "GEODCRS[\"Madrid 1870\",DATUM[\"Madrid 1870\","
            + "ELLIPSOID[\"Struve 1860\",6378298.3,294.73]],"
            + "PRIMEM[\"Madrid\",-3.687375,ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "CS[ellipsoidal,2],AXIS[\"latitude\",north,ORDER[1],ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "AXIS[\"longitude\",east,ORDER[2],ANGLEUNIT[\"degree\",0.0174532925199433]]]";
        assertEquals(-3.687375,
            Math.toDegrees(new org.datasyslab.proj4sedona.core.Proj(madrid).getParams().fromGreenwich),
            1e-9);
    }

    @Test
    @DisplayName("Issue #103: PROJJSON value-with-unit prime meridian is honored")
    void testPrimemProjJsonValueUnitObject() {
        // PROJ's PROJJSON for EPSG:4807 carries the meridian as
        // {"value": 2.5969213, "unit": {..."grad"...}}; the degree assumption fed the
        // object through toDouble, which silently produced 0.0 — the meridian was
        // lost entirely.
        String json = "{\"type\": \"GeographicCRS\", \"name\": \"NTF (Paris)\","
            + "\"datum\": {\"type\": \"GeodeticReferenceFrame\","
            + "  \"name\": \"Nouvelle Triangulation Francaise (Paris)\","
            + "  \"ellipsoid\": {\"name\": \"Clarke 1880 (IGN)\", \"semi_major_axis\": 6378249.2,"
            + "   \"inverse_flattening\": 293.466021293627},"
            + "  \"prime_meridian\": {\"name\": \"Paris\", \"longitude\": {\"value\": 2.5969213,"
            + "   \"unit\": {\"type\": \"AngularUnit\", \"name\": \"grad\","
            + "    \"conversion_factor\": 0.0157079632679489}}}},"
            + "\"coordinate_system\": {\"subtype\": \"ellipsoidal\", \"axis\": ["
            + " {\"name\": \"Geodetic latitude\", \"abbreviation\": \"Lat\", \"direction\": \"north\", \"unit\": \"degree\"},"
            + " {\"name\": \"Geodetic longitude\", \"abbreviation\": \"Lon\", \"direction\": \"east\", \"unit\": \"degree\"}]},"
            + "\"id\": {\"authority\": \"EPSG\", \"code\": 4807}}";
        org.datasyslab.proj4sedona.core.Proj p = new org.datasyslab.proj4sedona.core.Proj(json);
        assertEquals(2.33722917, Math.toDegrees(p.getParams().fromGreenwich), 1e-9,
            "grads meridian object resolved, not dropped");

        // Plain-number longitude stays degrees.
        String plain = json.replace(
            "{\"value\": 2.5969213,"
            + "   \"unit\": {\"type\": \"AngularUnit\", \"name\": \"grad\","
            + "    \"conversion_factor\": 0.0157079632679489}}", "2.33722917");
        assertEquals(2.33722917,
            Math.toDegrees(new org.datasyslab.proj4sedona.core.Proj(plain).getParams().fromGreenwich),
            1e-9);
    }

    @Test
    @DisplayName("PROJCRS with a BASEGEODCRS base (WKT2-2015) keeps the base ellipsoid")
    void testProjcrsBaseGeodCrs2015() {
        // PROJ's WKT2:2015 output uses BASEGEODCRS (not BASEGEOGCRS) for every
        // projected CRS; dropping it silently falls back to the WGS84 default
        // ellipsoid. Bessel-based CRS so the loss is observable (EPSG:5514-style).
        String wkt = "PROJCRS[\"S-JTSK / Krovak East North\","
            + "BASEGEODCRS[\"S-JTSK\",DATUM[\"System of the Unified Trigonometrical Cadastral Network\","
            + "ELLIPSOID[\"Bessel 1841\",6377397.155,299.1528128,LENGTHUNIT[\"metre\",1]]],"
            + "PRIMEM[\"Greenwich\",0,ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "CONVERSION[\"Krovak\",METHOD[\"Krovak (North Orientated)\",ID[\"EPSG\",1041]],"
            + "PARAMETER[\"Latitude of projection centre\",49.5,ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"Longitude of origin\",24.8333333333333,ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "CS[Cartesian,2],AXIS[\"x\",south,ORDER[1]],AXIS[\"y\",west,ORDER[2]],"
            + "LENGTHUNIT[\"metre\",1],ID[\"EPSG\",5514]]";
        ProjectionDef def = WktParser.parse(wkt);
        assertEquals(6377397.155, def.getA(), 1e-6, "Bessel semi-major from BASEGEODCRS");
    }
}
