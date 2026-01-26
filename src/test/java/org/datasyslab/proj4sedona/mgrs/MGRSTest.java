package org.datasyslab.proj4sedona.mgrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MGRS coordinate conversion.
 */
public class MGRSTest {

    private static final double TOLERANCE = 0.0001; // ~11 meters

    // ========== Forward Conversion Tests ==========

    @Test
    void testForwardWashingtonDC() {
        // Washington DC: 38.9072°N, 77.0369°W
        double[] lonLat = {-77.0369, 38.9072};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("18S"), "Should be in UTM zone 18S");
        assertEquals(15, mgrs.length(), "5-digit accuracy should give 15 char MGRS");
        
        System.out.println("Washington DC: " + mgrs);
    }

    @Test
    void testForwardNewYork() {
        // New York: 40.7128°N, 74.0060°W
        double[] lonLat = {-74.0060, 40.7128};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("18T"), "Should be in UTM zone 18T");
        
        System.out.println("New York: " + mgrs);
    }

    @Test
    void testForwardLondon() {
        // London: 51.5074°N, 0.1278°W
        double[] lonLat = {-0.1278, 51.5074};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("30U"), "Should be in UTM zone 30U");
        
        System.out.println("London: " + mgrs);
    }

    @Test
    void testForwardTokyo() {
        // Tokyo: 35.6762°N, 139.6503°E
        double[] lonLat = {139.6503, 35.6762};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("54S"), "Should be in UTM zone 54S");
        
        System.out.println("Tokyo: " + mgrs);
    }

    @Test
    void testForwardSydney() {
        // Sydney: 33.8688°S, 151.2093°E
        double[] lonLat = {151.2093, -33.8688};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("56H"), "Should be in UTM zone 56H (southern hemisphere)");
        
        System.out.println("Sydney: " + mgrs);
    }

    @Test
    void testForwardEquator() {
        // Point on equator
        double[] lonLat = {0.0, 0.0};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("31N"), "Should be in UTM zone 31N");
        
        System.out.println("Equator/Prime Meridian: " + mgrs);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 7",   // 10km accuracy: zone(2) + letter(1) + grid(2) + easting(1) + northing(1)
        "2, 9",   // 1km accuracy: zone(2) + letter(1) + grid(2) + easting(2) + northing(2)
        "3, 11",  // 100m accuracy: zone(2) + letter(1) + grid(2) + easting(3) + northing(3)
        "4, 13",  // 10m accuracy: zone(2) + letter(1) + grid(2) + easting(4) + northing(4)
        "5, 15"   // 1m accuracy: zone(2) + letter(1) + grid(2) + easting(5) + northing(5)
    })
    void testForwardAccuracyLevels(int accuracy, int expectedLength) {
        double[] lonLat = {-77.0, 38.9};
        String mgrs = MGRS.forward(lonLat, accuracy);
        
        assertEquals(expectedLength, mgrs.length(), 
            "Accuracy " + accuracy + " should give " + expectedLength + " char MGRS");
    }

    // ========== Inverse Conversion Tests ==========

    @Test
    void testInverseBasic() {
        String mgrs = "18SUJ2338308450";
        double[] bbox = MGRS.inverse(mgrs);
        
        assertEquals(4, bbox.length);
        // Should be near Washington DC area
        assertTrue(bbox[0] > -78 && bbox[0] < -76, "Longitude should be near -77");
        assertTrue(bbox[1] > 38 && bbox[1] < 40, "Latitude should be near 39");
    }

    @Test
    void testInverseLowerCase() {
        // Should handle lowercase input
        String mgrs = "18suj2338308450";
        double[] bbox = MGRS.inverse(mgrs);
        
        assertNotNull(bbox);
        assertEquals(4, bbox.length);
    }

    // ========== toPoint Tests ==========

    @Test
    void testToPointBasic() {
        String mgrs = "18SUJ2338308450";
        double[] point = MGRS.toPoint(mgrs);
        
        assertEquals(2, point.length);
        assertTrue(point[0] > -78 && point[0] < -76, "Longitude should be near -77");
        assertTrue(point[1] > 38 && point[1] < 40, "Latitude should be near 39");
    }

    @Test
    void testToPointLowerPrecision() {
        // Lower precision MGRS (1km)
        String mgrs = "18SUJ2308";
        double[] point = MGRS.toPoint(mgrs);
        
        assertEquals(2, point.length);
        assertFalse(Double.isNaN(point[0]));
        assertFalse(Double.isNaN(point[1]));
    }

    // ========== Round-Trip Tests ==========

    @ParameterizedTest
    @CsvSource({
        "-77.0369, 38.9072",   // Washington DC
        "-74.0060, 40.7128",   // New York
        "-0.1278, 51.5074",    // London
        "139.6503, 35.6762",   // Tokyo
        "151.2093, -33.8688",  // Sydney
        "0.0, 0.0",            // Equator/Prime Meridian
        "-122.4194, 37.7749",  // San Francisco
        "2.3522, 48.8566",     // Paris
        "-43.1729, -22.9068",  // Rio de Janeiro
        "116.4074, 39.9042"    // Beijing
    })
    void testRoundTrip(double lon, double lat) {
        double[] original = {lon, lat};
        
        // Forward: lon/lat -> MGRS
        String mgrs = MGRS.forward(original, 5);
        assertNotNull(mgrs);
        
        // Inverse: MGRS -> lon/lat
        double[] result = MGRS.toPoint(mgrs);
        
        assertEquals(original[0], result[0], TOLERANCE, 
            "Longitude should match for " + mgrs);
        assertEquals(original[1], result[1], TOLERANCE, 
            "Latitude should match for " + mgrs);
    }

    // ========== Special Zone Tests ==========

    @Test
    void testNorwaySpecialZone() {
        // Norway special zone (32V instead of 31V or 32V)
        // Bergen: 60.39°N, 5.32°E
        double[] lonLat = {5.32, 60.39};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertTrue(mgrs.startsWith("32V"), "Norway should use zone 32V");
        System.out.println("Bergen, Norway: " + mgrs);
    }

    @Test
    void testSvalbardSpecialZones() {
        // Svalbard uses zones 31, 33, 35, 37 (odd numbers only)
        // Longyearbyen: 78.22°N, 15.63°E
        double[] lonLat = {15.63, 78.22};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertTrue(mgrs.startsWith("33X"), "Svalbard should use zone 33X");
        System.out.println("Longyearbyen, Svalbard: " + mgrs);
    }

    // ========== Edge Case Tests ==========

    @Test
    void testLongitude180() {
        // Longitude 180 should be in zone 60
        double[] lonLat = {180.0, 45.0};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertTrue(mgrs.startsWith("60"), "Longitude 180 should be in zone 60");
    }

    @Test
    void testNearPoles() {
        // Near north pole (but within MGRS bounds)
        double[] lonLat = {0.0, 83.0};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.contains("X"), "High latitude should have zone letter X");
    }

    @Test
    void testSouthernHemisphere() {
        // Cape Town: 33.9249°S, 18.4241°E
        double[] lonLat = {18.4241, -33.9249};
        String mgrs = MGRS.forward(lonLat, 5);
        
        assertNotNull(mgrs);
        assertTrue(mgrs.contains("H"), "Southern hemisphere should have zone letter H");
        
        // Round trip
        double[] result = MGRS.toPoint(mgrs);
        assertEquals(lonLat[0], result[0], TOLERANCE);
        assertEquals(lonLat[1], result[1], TOLERANCE);
        
        System.out.println("Cape Town: " + mgrs);
    }

    // ========== Error Handling Tests ==========

    @Test
    void testInvalidMGRSEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            MGRS.toPoint("");
        });
    }

    @Test
    void testInvalidMGRSNull() {
        // Null input throws either IllegalArgumentException or NullPointerException
        assertThrows(Exception.class, () -> {
            MGRS.toPoint(null);
        });
    }

    @Test
    void testInvalidZoneLetter() {
        // 'I' and 'O' are not valid zone letters
        assertThrows(IllegalArgumentException.class, () -> {
            MGRS.toPoint("18IUJ2338308450");
        });
    }

    @Test
    void testInvalidMGRSOddDigits() {
        // MGRS must have even number of digits after grid square
        assertThrows(IllegalArgumentException.class, () -> {
            MGRS.toPoint("18SUJ23383"); // 5 digits instead of 4 or 6
        });
    }

    // ========== Known Coordinate Tests ==========

    @Test
    void testKnownCoordinateStatueOfLiberty() {
        // Statue of Liberty: 40.6892°N, 74.0445°W
        double[] lonLat = {-74.0445, 40.6892};
        String mgrs = MGRS.forward(lonLat, 5);
        
        // Verify round trip
        double[] result = MGRS.toPoint(mgrs);
        assertEquals(lonLat[0], result[0], TOLERANCE);
        assertEquals(lonLat[1], result[1], TOLERANCE);
        
        System.out.println("Statue of Liberty: " + mgrs);
    }

    @Test
    void testKnownCoordinateEiffelTower() {
        // Eiffel Tower: 48.8584°N, 2.2945°E
        double[] lonLat = {2.2945, 48.8584};
        String mgrs = MGRS.forward(lonLat, 5);
        
        double[] result = MGRS.toPoint(mgrs);
        assertEquals(lonLat[0], result[0], TOLERANCE);
        assertEquals(lonLat[1], result[1], TOLERANCE);
        
        System.out.println("Eiffel Tower: " + mgrs);
    }

    @Test
    void testKnownCoordinateSydneyOperaHouse() {
        // Sydney Opera House: 33.8568°S, 151.2153°E
        double[] lonLat = {151.2153, -33.8568};
        String mgrs = MGRS.forward(lonLat, 5);
        
        double[] result = MGRS.toPoint(mgrs);
        assertEquals(lonLat[0], result[0], TOLERANCE);
        assertEquals(lonLat[1], result[1], TOLERANCE);
        
        System.out.println("Sydney Opera House: " + mgrs);
    }

    // ========== Bounding Box Tests ==========

    @Test
    void testBoundingBoxLowPrecision() {
        // 1km precision MGRS
        String mgrs = "18SUJ2308";
        double[] bbox = MGRS.inverse(mgrs);
        
        assertEquals(4, bbox.length);
        // Bounding box should span approximately 1km
        double lonSpan = bbox[2] - bbox[0];
        double latSpan = bbox[3] - bbox[1];
        
        assertTrue(lonSpan > 0, "Longitude span should be positive");
        assertTrue(latSpan > 0, "Latitude span should be positive");
        
        System.out.printf("1km MGRS bbox: lon span=%.6f°, lat span=%.6f°%n", lonSpan, latSpan);
    }

    @Test
    void testBoundingBoxHighPrecision() {
        // 1m precision MGRS
        String mgrs = "18SUJ2338308450";
        double[] bbox = MGRS.inverse(mgrs);
        
        // For 1m precision, bbox should be very small (essentially a point)
        double lonSpan = Math.abs(bbox[2] - bbox[0]);
        double latSpan = Math.abs(bbox[3] - bbox[1]);
        
        assertTrue(lonSpan < 0.0001, "1m precision should have tiny longitude span");
        assertTrue(latSpan < 0.0001, "1m precision should have tiny latitude span");
    }
}
