package org.proj4sedona.mgrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Universal Polar Stereographic (UPS) coordinate conversion.
 */
public class UPSTest {

    private static final double TOLERANCE = 0.0001; // ~11 meters

    // ========== Zone Detection Tests ==========

    @Test
    void testIsNorthPolar() {
        assertTrue(UPS.isNorthPolar(85.0));
        assertTrue(UPS.isNorthPolar(90.0));
        assertFalse(UPS.isNorthPolar(84.0));
        assertFalse(UPS.isNorthPolar(83.0));
        assertFalse(UPS.isNorthPolar(0.0));
    }

    @Test
    void testIsSouthPolar() {
        assertTrue(UPS.isSouthPolar(-81.0));
        assertTrue(UPS.isSouthPolar(-90.0));
        assertFalse(UPS.isSouthPolar(-80.0));
        assertFalse(UPS.isSouthPolar(-79.0));
        assertFalse(UPS.isSouthPolar(0.0));
    }

    @Test
    void testIsUPS() {
        assertTrue(UPS.isUPS(85.0));
        assertTrue(UPS.isUPS(90.0));
        assertTrue(UPS.isUPS(-81.0));
        assertTrue(UPS.isUPS(-90.0));
        assertFalse(UPS.isUPS(84.0));
        assertFalse(UPS.isUPS(-80.0));
        assertFalse(UPS.isUPS(0.0));
    }

    // ========== Zone Designation Tests ==========

    @Test
    void testGetZoneNorthEast() {
        assertEquals('Z', UPS.getZone(85.0, 45.0));
        assertEquals('Z', UPS.getZone(89.0, 180.0));
    }

    @Test
    void testGetZoneNorthWest() {
        assertEquals('Y', UPS.getZone(85.0, -45.0));
        assertEquals('Y', UPS.getZone(89.0, -180.0));
    }

    @Test
    void testGetZoneSouthEast() {
        assertEquals('B', UPS.getZone(-81.0, 45.0));
        assertEquals('B', UPS.getZone(-89.0, 180.0));
    }

    @Test
    void testGetZoneSouthWest() {
        assertEquals('A', UPS.getZone(-81.0, -45.0));
        assertEquals('A', UPS.getZone(-89.0, -180.0));
    }

    // ========== Forward Conversion Tests ==========

    @Test
    void testFromLatLonNorthPole() {
        UPS.UPSCoordinate ups = UPS.fromLatLon(90.0, 0.0);
        
        assertNotNull(ups);
        assertEquals('Z', ups.zone);
        assertEquals(2000000.0, ups.easting, 1.0);
        assertEquals(2000000.0, ups.northing, 1.0);
    }

    @Test
    void testFromLatLonSouthPole() {
        UPS.UPSCoordinate ups = UPS.fromLatLon(-90.0, 0.0);
        
        assertNotNull(ups);
        assertEquals('B', ups.zone);
        assertEquals(2000000.0, ups.easting, 1.0);
        assertEquals(2000000.0, ups.northing, 1.0);
    }

    @Test
    void testFromLatLonNorthPolar() {
        // Point in north polar region
        UPS.UPSCoordinate ups = UPS.fromLatLon(85.0, 45.0);
        
        assertNotNull(ups);
        assertEquals('Z', ups.zone);
        assertTrue(ups.easting > 2000000); // East of pole
        assertTrue(ups.northing < 2000000); // South of pole (in grid terms)
        
        System.out.printf("85°N, 45°E: %s%n", ups);
    }

    @Test
    void testFromLatLonSouthPolar() {
        // Point in south polar region
        UPS.UPSCoordinate ups = UPS.fromLatLon(-85.0, 45.0);
        
        assertNotNull(ups);
        assertEquals('B', ups.zone);
        
        System.out.printf("85°S, 45°E: %s%n", ups);
    }

    // ========== Inverse Conversion Tests ==========

    @Test
    void testToLatLonNorthPole() {
        double[] result = UPS.toLatLon('Z', 2000000, 2000000);
        
        assertEquals(90.0, result[0], TOLERANCE);
        // Longitude undefined at pole, but should return a value
    }

    @Test
    void testToLatLonSouthPole() {
        double[] result = UPS.toLatLon('A', 2000000, 2000000);
        
        assertEquals(-90.0, result[0], TOLERANCE);
    }

    // ========== Round-Trip Tests ==========

    @ParameterizedTest
    @CsvSource({
        "85.0, 0.0",
        "85.0, 45.0",
        "85.0, 90.0",
        "85.0, 135.0",
        "85.0, 180.0",
        "85.0, -45.0",
        "85.0, -90.0",
        "85.0, -135.0",
        "87.0, 30.0",
        "89.0, 60.0",
        "89.5, 120.0"
    })
    void testRoundTripNorth(double lat, double lon) {
        UPS.UPSCoordinate ups = UPS.fromLatLon(lat, lon);
        double[] result = UPS.toLatLon(ups);
        
        assertEquals(lat, result[0], TOLERANCE, "Latitude round-trip failed");
        assertEquals(lon, result[1], TOLERANCE, "Longitude round-trip failed");
    }

    @ParameterizedTest
    @CsvSource({
        "-81.0, 0.0",
        "-81.0, 45.0",
        "-81.0, 90.0",
        "-81.0, -45.0",
        "-81.0, -90.0",
        "-85.0, 30.0",
        "-87.0, 60.0",
        "-89.0, 120.0",
        "-89.5, -60.0"
    })
    void testRoundTripSouth(double lat, double lon) {
        UPS.UPSCoordinate ups = UPS.fromLatLon(lat, lon);
        double[] result = UPS.toLatLon(ups);
        
        assertEquals(lat, result[0], TOLERANCE, "Latitude round-trip failed");
        assertEquals(lon, result[1], TOLERANCE, "Longitude round-trip failed");
    }

    // ========== Error Handling Tests ==========

    @Test
    void testFromLatLonWithinUTMBounds() {
        assertThrows(IllegalArgumentException.class, () -> {
            UPS.fromLatLon(45.0, 0.0);
        });
    }

    @Test
    void testGetZoneWithinUTMBounds() {
        assertThrows(IllegalArgumentException.class, () -> {
            UPS.getZone(45.0, 0.0);
        });
    }

    @Test
    void testToLatLonInvalidZone() {
        assertThrows(IllegalArgumentException.class, () -> {
            UPS.toLatLon('X', 2000000, 2000000);
        });
    }

    // ========== MGRS-Style String Tests ==========

    @Test
    void testToMGRSString() {
        UPS.UPSCoordinate ups = UPS.fromLatLon(85.0, 45.0);
        String mgrsStyle = ups.toMGRSString(5);
        
        assertNotNull(mgrsStyle);
        assertTrue(mgrsStyle.startsWith("Z"), "Should start with zone letter");
        assertEquals(13, mgrsStyle.length(), "Should be 13 chars: zone(1) + grid(2) + easting(5) + northing(5)");
        
        System.out.println("85°N, 45°E as UPS MGRS: " + mgrsStyle);
    }

    @Test
    void testToMGRSStringAccuracy() {
        UPS.UPSCoordinate ups = UPS.fromLatLon(85.0, 45.0);
        
        assertEquals(5, ups.toMGRSString(1).length());   // 1 + 2 + 1 + 1 = 5
        assertEquals(7, ups.toMGRSString(2).length());   // 1 + 2 + 2 + 2 = 7
        assertEquals(9, ups.toMGRSString(3).length());   // 1 + 2 + 3 + 3 = 9
        assertEquals(11, ups.toMGRSString(4).length());  // 1 + 2 + 4 + 4 = 11
        assertEquals(13, ups.toMGRSString(5).length());  // 1 + 2 + 5 + 5 = 13
    }

    // ========== Known Locations Tests ==========

    @Test
    void testNorthPoleStation() {
        // North Pole geographic coordinates
        UPS.UPSCoordinate ups = UPS.fromLatLon(90.0, 0.0);
        
        // At the pole, easting and northing should be at false origin
        assertEquals(2000000.0, ups.easting, 10.0);
        assertEquals(2000000.0, ups.northing, 10.0);
    }

    @Test
    void testAmundsenScottStation() {
        // Amundsen-Scott South Pole Station: exactly at 90°S
        UPS.UPSCoordinate ups = UPS.fromLatLon(-90.0, 0.0);
        
        assertEquals(2000000.0, ups.easting, 10.0);
        assertEquals(2000000.0, ups.northing, 10.0);
    }

    @Test
    void testAlertNunavut() {
        // Alert, Nunavut, Canada: northernmost permanently inhabited place
        // 82.5°N, 62.3°W - this is still within UTM/MGRS bounds
        // Using 85°N for UPS test
        double lat = 85.0;
        double lon = -62.3;
        
        UPS.UPSCoordinate ups = UPS.fromLatLon(lat, lon);
        double[] result = UPS.toLatLon(ups);
        
        assertEquals(lat, result[0], TOLERANCE);
        assertEquals(lon, result[1], TOLERANCE);
        
        System.out.printf("Arctic point (85°N, 62.3°W): %s%n", ups);
    }
}
