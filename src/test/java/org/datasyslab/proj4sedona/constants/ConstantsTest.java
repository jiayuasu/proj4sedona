package org.datasyslab.proj4sedona.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for constants to verify they match proj4js values.
 */
class ConstantsTest {

    // Tolerance for floating point comparisons
    private static final double DELTA = 1e-15;

    @Test
    void testValuesConstants() {
        // Verify values match lib/constants/values.js
        assertEquals(1, Values.PJD_3PARAM);
        assertEquals(2, Values.PJD_7PARAM);
        assertEquals(3, Values.PJD_GRIDSHIFT);
        assertEquals(4, Values.PJD_WGS84);
        assertEquals(5, Values.PJD_NODATUM);

        assertEquals(6378137.0, Values.SRS_WGS84_SEMIMAJOR, DELTA);
        assertEquals(6356752.314, Values.SRS_WGS84_SEMIMINOR, DELTA);
        assertEquals(0.0066943799901413165, Values.SRS_WGS84_ESQUARED, DELTA);

        assertEquals(4.84813681109535993589914102357e-6, Values.SEC_TO_RAD, DELTA);
        assertEquals(0.01745329251994329577, Values.D2R, DELTA);
        assertEquals(57.29577951308232088, Values.R2D, DELTA);

        assertEquals(Math.PI / 2, Values.HALF_PI, DELTA);
        assertEquals(Math.PI / 4, Values.FORTPI, DELTA);
        assertEquals(Math.PI * 2, Values.TWO_PI, DELTA);

        assertEquals(0.1666666666666666667, Values.SIXTH, DELTA);
        assertEquals(0.04722222222222222222, Values.RA4, DELTA);
        assertEquals(0.02215608465608465608, Values.RA6, DELTA);

        assertEquals(1.0e-10, Values.EPSLN, DELTA);
        assertEquals(3.14159265359, Values.SPI, DELTA);
    }

    @Test
    void testEllipsoidWGS84() {
        Ellipsoid wgs84 = Ellipsoid.WGS84;
        assertNotNull(wgs84);
        assertEquals(6378137, wgs84.getA(), DELTA);
        assertEquals(298.257223563, wgs84.getRf(), DELTA);
        assertEquals("WGS 84", wgs84.getEllipseName());

        // Verify b is calculated correctly from a and rf
        double expectedB = (1.0 - 1.0 / 298.257223563) * 6378137;
        assertEquals(expectedB, wgs84.getB(), DELTA);
    }

    @Test
    void testEllipsoidGRS80() {
        Ellipsoid grs80 = Ellipsoid.GRS80;
        assertNotNull(grs80);
        assertEquals(6378137, grs80.getA(), DELTA);
        assertEquals(298.257222101, grs80.getRf(), DELTA);
    }

    @Test
    void testEllipsoidWithBParameter() {
        // Airy uses b instead of rf in proj4js
        Ellipsoid airy = Ellipsoid.AIRY;
        assertNotNull(airy);
        assertEquals(6377563.396, airy.getA(), DELTA);
        assertEquals(6356256.91, airy.getB(), DELTA);
    }

    @Test
    void testEllipsoidLookup() {
        // Case-insensitive lookup
        assertNotNull(Ellipsoid.get("WGS84"));
        assertNotNull(Ellipsoid.get("wgs84"));
        assertNotNull(Ellipsoid.get("GRS80"));
        assertNotNull(Ellipsoid.get("grs80"));
        assertNotNull(Ellipsoid.get("bessel"));
        assertNotNull(Ellipsoid.get("clrk66"));

        assertNull(Ellipsoid.get("nonexistent"));
        assertNull(Ellipsoid.get(null));
    }

    @Test
    void testDatumWGS84() {
        Datum wgs84 = Datum.WGS84;
        assertNotNull(wgs84);
        assertEquals("0,0,0", wgs84.getTowgs84());
        assertEquals("WGS84", wgs84.getEllipse());
        assertEquals("WGS84", wgs84.getDatumName());

        double[] params = wgs84.getTowgs84Array();
        assertNotNull(params);
        assertEquals(3, params.length);
        assertEquals(0.0, params[0], DELTA);
        assertEquals(0.0, params[1], DELTA);
        assertEquals(0.0, params[2], DELTA);
    }

    @Test
    void testDatumWith7Params() {
        // Potsdam has 7 parameters
        Datum potsdam = Datum.POTSDAM;
        assertNotNull(potsdam);

        double[] params = potsdam.getTowgs84Array();
        assertNotNull(params);
        assertEquals(7, params.length);
        assertEquals(598.1, params[0], DELTA);
        assertEquals(73.7, params[1], DELTA);
        assertEquals(418.2, params[2], DELTA);
        assertEquals(0.202, params[3], DELTA);
        assertEquals(0.045, params[4], DELTA);
        assertEquals(-2.455, params[5], DELTA);
        assertEquals(6.7, params[6], DELTA);
    }

    @Test
    void testDatumWithNadgrids() {
        Datum nad27 = Datum.NAD27;
        assertNotNull(nad27);
        assertNull(nad27.getTowgs84());
        assertEquals("@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat", nad27.getNadgrids());
        assertEquals("clrk66", nad27.getEllipse());
    }

    @Test
    void testDatumLookup() {
        // Case-insensitive lookup
        assertNotNull(Datum.get("wgs84"));
        assertNotNull(Datum.get("WGS84"));
        assertNotNull(Datum.get("nad83"));
        assertNotNull(Datum.get("nad27"));

        // Lookup by datum name
        assertNotNull(Datum.get("North_American_Datum_1983"));

        assertNull(Datum.get("nonexistent"));
        assertNull(Datum.get(null));
    }

    @Test
    void testPrimeMeridians() {
        // Verify values match lib/constants/PrimeMeridian.js
        assertEquals(0.0, PrimeMeridian.get("greenwich"), DELTA);
        assertEquals(-9.131906111111, PrimeMeridian.get("lisbon"), DELTA);
        assertEquals(2.337229166667, PrimeMeridian.get("paris"), DELTA);
        assertEquals(-74.080916666667, PrimeMeridian.get("bogota"), DELTA);
        assertEquals(-3.687938888889, PrimeMeridian.get("madrid"), DELTA);
        assertEquals(12.452333333333, PrimeMeridian.get("rome"), DELTA);
        assertEquals(7.439583333333, PrimeMeridian.get("bern"), DELTA);
        assertEquals(106.807719444444, PrimeMeridian.get("jakarta"), DELTA);
        assertEquals(-17.666666666667, PrimeMeridian.get("ferro"), DELTA);
        assertEquals(4.367975, PrimeMeridian.get("brussels"), DELTA);
        assertEquals(18.058277777778, PrimeMeridian.get("stockholm"), DELTA);
        assertEquals(23.7163375, PrimeMeridian.get("athens"), DELTA);
        assertEquals(10.722916666667, PrimeMeridian.get("oslo"), DELTA);

        // Case-insensitive
        assertEquals(0.0, PrimeMeridian.get("GREENWICH"), DELTA);
        assertEquals(2.337229166667, PrimeMeridian.get("PARIS"), DELTA);

        assertNull(PrimeMeridian.get("nonexistent"));
        assertNull(PrimeMeridian.get(null));
    }

    @Test
    void testUnits() {
        // Verify values match lib/constants/units.js
        assertEquals(0.001, Units.getToMeter("mm"), DELTA);
        assertEquals(0.01, Units.getToMeter("cm"), DELTA);
        assertEquals(0.3048, Units.getToMeter("ft"), DELTA);
        assertEquals(1200.0 / 3937.0, Units.getToMeter("us-ft"), DELTA);
        assertEquals(1.8288, Units.getToMeter("fath"), DELTA);
        assertEquals(1852.0, Units.getToMeter("kmi"), DELTA);
        assertEquals(20.1168402336805, Units.getToMeter("us-ch"), DELTA);
        assertEquals(1609.34721869444, Units.getToMeter("us-mi"), DELTA);
        assertEquals(1000.0, Units.getToMeter("km"), DELTA);
        assertEquals(0.30479841, Units.getToMeter("ind-ft"), DELTA);
        assertEquals(0.91439523, Units.getToMeter("ind-yd"), DELTA);
        assertEquals(1609.344, Units.getToMeter("mi"), DELTA);
        assertEquals(0.9144, Units.getToMeter("yd"), DELTA);
        assertEquals(20.1168, Units.getToMeter("ch"), DELTA);
        assertEquals(0.201168, Units.getToMeter("link"), DELTA);
        assertEquals(0.1, Units.getToMeter("dm"), DELTA);
        assertEquals(0.0254, Units.getToMeter("in"), DELTA);
        assertEquals(20.11669506, Units.getToMeter("ind-ch"), DELTA);
        assertEquals(0.025400050800101, Units.getToMeter("us-in"), DELTA);
        assertEquals(0.914401828803658, Units.getToMeter("us-yd"), DELTA);
        assertEquals(1.0, Units.getToMeter("m"), DELTA);

        assertNull(Units.getToMeter("nonexistent"));
        assertNull(Units.getToMeter(null));
    }

    @Test
    void testAllEllipsoidsHaveValidParameters() {
        // Test that all static ellipsoids are properly initialized
        Ellipsoid[] ellipsoids = {
            Ellipsoid.MERIT, Ellipsoid.SGS85, Ellipsoid.GRS80, Ellipsoid.IAU76,
            Ellipsoid.AIRY, Ellipsoid.APL4, Ellipsoid.NWL9D, Ellipsoid.MOD_AIRY,
            Ellipsoid.ANDRAE, Ellipsoid.AUST_SA, Ellipsoid.GRS67, Ellipsoid.BESSEL,
            Ellipsoid.BESS_NAM, Ellipsoid.CLRK66, Ellipsoid.CLRK80, Ellipsoid.CLRK80IGN,
            Ellipsoid.CLRK58, Ellipsoid.CPM, Ellipsoid.DELMBR, Ellipsoid.ENGELIS,
            Ellipsoid.EVRST30, Ellipsoid.EVRST48, Ellipsoid.EVRST56, Ellipsoid.EVRST69,
            Ellipsoid.EVRSTSS, Ellipsoid.FSCHR60, Ellipsoid.FSCHR60M, Ellipsoid.FSCHR68,
            Ellipsoid.HELMERT, Ellipsoid.HOUGH, Ellipsoid.INTL, Ellipsoid.KAULA,
            Ellipsoid.LERCH, Ellipsoid.MPRTS, Ellipsoid.NEW_INTL, Ellipsoid.PLESSIS,
            Ellipsoid.KRASS, Ellipsoid.SEASIA, Ellipsoid.WALBECK, Ellipsoid.WGS60,
            Ellipsoid.WGS66, Ellipsoid.WGS7, Ellipsoid.WGS84, Ellipsoid.SPHERE
        };

        for (Ellipsoid e : ellipsoids) {
            assertNotNull(e, "Ellipsoid should not be null");
            assertTrue(e.getA() > 0, "Semi-major axis should be positive");
            assertTrue(e.getB() > 0, "Semi-minor axis should be positive");
            assertTrue(e.getA() >= e.getB(), "Semi-major axis should be >= semi-minor axis");
            assertNotNull(e.getCode(), "Code should not be null");
            assertNotNull(e.getEllipseName(), "Name should not be null");
        }
    }
}
