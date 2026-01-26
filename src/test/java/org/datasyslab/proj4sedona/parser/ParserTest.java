package org.datasyslab.proj4sedona.parser;

import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.constants.PrimeMeridian;
import org.datasyslab.proj4sedona.constants.Units;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.ProjectionDef;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PROJ string parser.
 */
class ParserTest {

    private static final double DELTA = 1e-10;

    // ========== Match Tests ==========

    @Test
    void testMatchDirect() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        assertEquals("value1", Match.match(map, "key1"));
        assertEquals("value2", Match.match(map, "key2"));
        assertNull(Match.match(map, "key3"));
    }

    @Test
    void testMatchCaseInsensitive() {
        Map<String, String> map = new HashMap<>();
        map.put("WGS84", "wgs84_value");

        assertEquals("wgs84_value", Match.match(map, "wgs84"));
        assertEquals("wgs84_value", Match.match(map, "WGS84"));
        assertEquals("wgs84_value", Match.match(map, "Wgs84"));
    }

    @Test
    void testMatchIgnoresSpecialChars() {
        Map<String, String> map = new HashMap<>();
        map.put("us-ft", "us_foot");

        assertEquals("us_foot", Match.match(map, "us-ft"));
        assertEquals("us_foot", Match.match(map, "us_ft"));
        assertEquals("us_foot", Match.match(map, "us ft"));
        assertEquals("us_foot", Match.match(map, "US-FT"));
        assertEquals("us_foot", Match.match(map, "US_FT"));
    }

    @Test
    void testMatchNull() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");

        assertNull(Match.match(map, null));
    }

    // ========== ProjString Basic Tests ==========

    @Test
    void testParseBasicLonglat() {
        ProjectionDef def = ProjString.parse("+proj=longlat +datum=WGS84");

        assertEquals("longlat", def.getProjName());
        assertEquals("WGS84", def.getDatumCode());
        assertEquals("+proj=longlat +datum=WGS84", def.getSrsCode());
    }

    @Test
    void testParseLonglatWithEllps() {
        ProjectionDef def = ProjString.parse("+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs");

        assertEquals("longlat", def.getProjName());
        assertEquals("WGS84", def.getEllps());
        assertEquals("WGS84", def.getDatumCode());
    }

    @Test
    void testParseDatumLowercase() {
        ProjectionDef def = ProjString.parse("+proj=longlat +datum=NAD83");

        assertEquals("nad83", def.getDatumCode());
    }

    // ========== UTM Tests ==========

    @Test
    void testParseUtmNorth() {
        ProjectionDef def = ProjString.parse("+proj=utm +zone=32 +datum=WGS84 +units=m");

        assertEquals("utm", def.getProjName());
        assertEquals(32, def.getZone());
        assertEquals("WGS84", def.getDatumCode());
        assertEquals("m", def.getUnits());
        assertNull(def.getUtmSouth());
    }

    @Test
    void testParseUtmSouth() {
        ProjectionDef def = ProjString.parse("+proj=utm +zone=50 +south +datum=WGS84");

        assertEquals("utm", def.getProjName());
        assertEquals(50, def.getZone());
        assertTrue(def.getUtmSouth());
    }

    // ========== Angle Parameter Tests ==========

    @Test
    void testParseLat0() {
        ProjectionDef def = ProjString.parse("+proj=lcc +lat_0=45.5");

        assertEquals(45.5 * Values.D2R, def.getLat0(), DELTA);
    }

    @Test
    void testParseLonLat() {
        ProjectionDef def = ProjString.parse("+proj=merc +lon_0=-96 +lat_ts=33");

        assertEquals(-96 * Values.D2R, def.getLong0(), DELTA);
        assertEquals(33 * Values.D2R, def.getLatTs(), DELTA);
    }

    @Test
    void testParseStandardParallels() {
        ProjectionDef def = ProjString.parse("+proj=lcc +lat_1=33 +lat_2=45");

        assertEquals(33 * Values.D2R, def.getLat1(), DELTA);
        assertEquals(45 * Values.D2R, def.getLat2(), DELTA);
    }

    @Test
    void testParseAlphaGamma() {
        ProjectionDef def = ProjString.parse("+proj=omerc +alpha=30 +gamma=45");

        assertEquals(30 * Values.D2R, def.getAlpha(), DELTA);
        assertEquals(45 * Values.D2R, def.getRectifiedGridAngle(), DELTA);
    }

    // ========== Scale and Offset Tests ==========

    @Test
    void testParseScaleK0() {
        ProjectionDef def = ProjString.parse("+proj=tmerc +k_0=0.9996");

        assertEquals(0.9996, def.getK0(), DELTA);
    }

    @Test
    void testParseScaleK() {
        ProjectionDef def = ProjString.parse("+proj=merc +k=0.5");

        assertEquals(0.5, def.getK0(), DELTA);
    }

    @Test
    void testParseOffsets() {
        ProjectionDef def = ProjString.parse("+proj=tmerc +x_0=500000 +y_0=0");

        assertEquals(500000, def.getX0(), DELTA);
        assertEquals(0, def.getY0(), DELTA);
    }

    // ========== Ellipsoid Parameters Tests ==========

    @Test
    void testParseEllipsoidAB() {
        ProjectionDef def = ProjString.parse("+proj=merc +a=6378137 +b=6356752.314");

        assertEquals(6378137, def.getA(), DELTA);
        assertEquals(6356752.314, def.getB(), DELTA);
    }

    @Test
    void testParseEllipsoidRf() {
        ProjectionDef def = ProjString.parse("+proj=merc +a=6378137 +rf=298.257223563");

        assertEquals(6378137, def.getA(), DELTA);
        assertEquals(298.257223563, def.getRf(), DELTA);
    }

    @Test
    void testParseSphereRadius() {
        ProjectionDef def = ProjString.parse("+proj=merc +r=6370997");

        assertEquals(6370997, def.getA(), DELTA);
        assertEquals(6370997, def.getB(), DELTA);
    }

    // ========== Datum Transform Tests ==========

    @Test
    void testParseTowgs84_3Param() {
        ProjectionDef def = ProjString.parse("+proj=longlat +towgs84=-8,160,176");

        assertNotNull(def.getDatumParams());
        assertEquals(3, def.getDatumParams().length);
        assertEquals(-8, def.getDatumParams()[0], DELTA);
        assertEquals(160, def.getDatumParams()[1], DELTA);
        assertEquals(176, def.getDatumParams()[2], DELTA);
    }

    @Test
    void testParseTowgs84_7Param() {
        ProjectionDef def = ProjString.parse(
            "+proj=longlat +towgs84=598.1,73.7,418.2,0.202,0.045,-2.455,6.7");

        assertNotNull(def.getDatumParams());
        assertEquals(7, def.getDatumParams().length);
        assertEquals(598.1, def.getDatumParams()[0], DELTA);
        assertEquals(73.7, def.getDatumParams()[1], DELTA);
        assertEquals(418.2, def.getDatumParams()[2], DELTA);
        assertEquals(0.202, def.getDatumParams()[3], DELTA);
        assertEquals(0.045, def.getDatumParams()[4], DELTA);
        assertEquals(-2.455, def.getDatumParams()[5], DELTA);
        assertEquals(6.7, def.getDatumParams()[6], DELTA);
    }

    // ========== Units Tests ==========

    @Test
    void testParseUnitsMeters() {
        ProjectionDef def = ProjString.parse("+proj=utm +zone=32 +units=m");

        assertEquals("m", def.getUnits());
        assertEquals(1.0, def.getToMeter(), DELTA);
    }

    @Test
    void testParseUnitsFeet() {
        ProjectionDef def = ProjString.parse("+proj=lcc +units=ft");

        assertEquals("ft", def.getUnits());
        assertEquals(Units.getToMeter("ft"), def.getToMeter(), DELTA);
    }

    @Test
    void testParseUnitsUsFeet() {
        ProjectionDef def = ProjString.parse("+proj=lcc +units=us-ft");

        assertEquals("us-ft", def.getUnits());
        assertEquals(Units.getToMeter("us-ft"), def.getToMeter(), DELTA);
    }

    @Test
    void testParseToMeterDirect() {
        ProjectionDef def = ProjString.parse("+proj=lcc +to_meter=0.3048");

        assertEquals(0.3048, def.getToMeter(), DELTA);
    }

    // ========== Prime Meridian Tests ==========

    @Test
    void testParsePmParis() {
        ProjectionDef def = ProjString.parse("+proj=longlat +pm=paris");

        assertEquals(PrimeMeridian.get("paris") * Values.D2R, 
                     def.getFromGreenwich(), DELTA);
    }

    @Test
    void testParsePmNumeric() {
        ProjectionDef def = ProjString.parse("+proj=longlat +pm=10.5");

        assertEquals(10.5 * Values.D2R, def.getFromGreenwich(), DELTA);
    }

    @Test
    void testParseFromGreenwich() {
        ProjectionDef def = ProjString.parse("+proj=longlat +from_greenwich=2.337");

        assertEquals(2.337 * Values.D2R, def.getFromGreenwich(), DELTA);
    }

    // ========== NAD Grid Tests ==========

    @Test
    void testParseNadgrids() {
        ProjectionDef def = ProjString.parse("+proj=longlat +nadgrids=@conus,@alaska");

        assertEquals("@conus,@alaska", def.getNadgrids());
    }

    @Test
    void testParseNadgridsNull() {
        ProjectionDef def = ProjString.parse("+proj=longlat +nadgrids=@null");

        assertEquals("none", def.getDatumCode());
        assertNull(def.getNadgrids());
    }

    // ========== Axis Tests ==========

    @Test
    void testParseAxisEnu() {
        ProjectionDef def = ProjString.parse("+proj=longlat +axis=enu");

        assertEquals("enu", def.getAxis());
    }

    @Test
    void testParseAxisNeu() {
        ProjectionDef def = ProjString.parse("+proj=longlat +axis=neu");

        assertEquals("neu", def.getAxis());
    }

    @Test
    void testParseAxisInvalid() {
        ProjectionDef def = ProjString.parse("+proj=longlat +axis=xyz");

        assertEquals("enu", def.getAxis());
    }

    // ========== Boolean Flag Tests ==========

    @Test
    void testParseRA() {
        ProjectionDef def = ProjString.parse("+proj=merc +R_A");

        assertTrue(def.getRA());
    }

    @Test
    void testParseApprox() {
        ProjectionDef def = ProjString.parse("+proj=tmerc +approx");

        assertTrue(def.getApprox());
    }

    @Test
    void testParseOver() {
        ProjectionDef def = ProjString.parse("+proj=merc +over");

        assertTrue(def.getOver());
    }

    // ========== Complex String Tests ==========

    @Test
    void testParseComplexLcc() {
        String proj = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 " +
                      "+x_0=0 +y_0=0 +datum=NAD83 +units=m +no_defs";
        ProjectionDef def = ProjString.parse(proj);

        assertEquals("lcc", def.getProjName());
        assertEquals(33 * Values.D2R, def.getLat1(), DELTA);
        assertEquals(45 * Values.D2R, def.getLat2(), DELTA);
        assertEquals(39 * Values.D2R, def.getLat0(), DELTA);
        assertEquals(-96 * Values.D2R, def.getLong0(), DELTA);
        assertEquals(0.0, def.getX0(), DELTA);
        assertEquals(0.0, def.getY0(), DELTA);
        assertEquals("nad83", def.getDatumCode());
        assertEquals("m", def.getUnits());
    }

    @Test
    void testParseEpsg3857Style() {
        String proj = "+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 " +
                      "+x_0=0 +y_0=0 +k=1 +units=m +no_defs";
        ProjectionDef def = ProjString.parse(proj);

        assertEquals("merc", def.getProjName());
        assertEquals(6378137, def.getA(), DELTA);
        assertEquals(6378137, def.getB(), DELTA);
        assertEquals(0, def.getLatTs(), DELTA);
        assertEquals(0, def.getLong0(), DELTA);
        assertEquals(1.0, def.getK0(), DELTA);
    }

    // ========== Scientific Notation Tests ==========

    @Test
    void testParseScientificNotationPositiveExponent() {
        // 5E+5 = 500000 - the '+' in E+ should not be treated as a parameter separator
        ProjectionDef def = ProjString.parse("+proj=merc +y_0=5E+5");

        assertEquals("merc", def.getProjName());
        assertEquals(500000.0, def.getY0(), DELTA);
    }

    @Test
    void testParseScientificNotationNegativeExponent() {
        ProjectionDef def = ProjString.parse("+proj=merc +k=1e-3");

        assertEquals(0.001, def.getK0(), DELTA);
    }

    @Test
    void testParseScientificNotationNoSign() {
        ProjectionDef def = ProjString.parse("+proj=merc +x_0=5e5");

        assertEquals(500000.0, def.getX0(), DELTA);
    }

    // ========== Error Handling Tests ==========

    @Test
    void testParseNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> ProjString.parse(null));
    }

    @Test
    void testParseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> ProjString.parse(""));
    }

    @Test
    void testParseInvalidNumberThrows() {
        assertThrows(IllegalArgumentException.class, 
            () -> ProjString.parse("+proj=merc +x_0=invalid"));
    }
}
