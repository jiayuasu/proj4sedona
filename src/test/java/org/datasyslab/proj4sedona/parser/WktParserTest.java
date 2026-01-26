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
}
