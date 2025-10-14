package org.apache.sedona.proj.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for advanced mathematical utilities.
 */
public class AdvancedMathUtilsTest {
    
    private static final double EPSILON = 1e-10;
    private static final double EARTH_RADIUS = 6371000; // meters
    
    @Test
    public void testAdjustZone() {
        // Test zone calculation from longitude
        assertEquals(1, MathUtils.adjustZone(null, Math.toRadians(-180)));
        assertEquals(1, MathUtils.adjustZone(null, Math.toRadians(-177)));
        assertEquals(30, MathUtils.adjustZone(null, Math.toRadians(-3)));
        assertEquals(31, MathUtils.adjustZone(null, Math.toRadians(3)));
        assertEquals(60, MathUtils.adjustZone(null, Math.toRadians(177)));
        assertEquals(60, MathUtils.adjustZone(null, Math.toRadians(180)));
        
        // Test with predefined zone
        assertEquals(15, MathUtils.adjustZone(15, Math.toRadians(-96)));
    }
    
    @Test
    public void testGatg() {
        // Test Gauss-Krüger transverse Mercator projection
        double[] pp = {0.0, 0.0, 0.0, 0.0, 0.0};
        double B = Math.toRadians(45);
        double result = MathUtils.gatg(pp, B);
        assertEquals(B, result, EPSILON);
        
        // Test with non-zero coefficients
        double[] pp2 = {0.0, 0.0, 0.0, 0.0, 0.1};
        double result2 = MathUtils.gatg(pp2, B);
        assertNotEquals(B, result2);
    }
    
    @Test
    public void testGN() {
        // Test radius of curvature in prime vertical
        double a = 6378137.0; // WGS84 semi-major axis
        double e = 0.08181919084262157; // WGS84 eccentricity
        double sinphi = Math.sin(Math.toRadians(45));
        
        double result = MathUtils.gN(a, e, sinphi);
        assertTrue(result > 0);
        assertTrue(result > a); // Should be greater than semi-major axis
        
        // Test at equator
        double resultEquator = MathUtils.gN(a, e, 0);
        assertEquals(a, resultEquator, EPSILON);
    }
    
    @Test
    public void testFL() {
        // Test fL function
        double x = 1.0;
        double L = 0.5;
        double result = MathUtils.fL(x, L);
        
        assertTrue(result > -Math.PI/2);
        assertTrue(result < Math.PI/2);
    }
    
    @Test
    public void testSrat() {
        // Test srat function
        double esinp = 0.1;
        double exp = 0.5;
        double result = MathUtils.srat(esinp, exp);
        
        assertTrue(result > 0);
        assertTrue(result < 1);
    }
    
    @Test
    public void testGM() {
        // Test radius of curvature in meridian
        double a = 6378137.0;
        double e = 0.08181919084262157;
        double sinphi = Math.sin(Math.toRadians(45));
        
        double result = MathUtils.gM(a, e, sinphi);
        assertTrue(result > 0);
        assertTrue(result < a); // Should be less than semi-major axis
        
        // Test at equator
        double resultEquator = MathUtils.gM(a, e, 0);
        assertEquals(a * (1 - e * e), resultEquator, EPSILON);
    }
    
    @Test
    public void testConvergence() {
        // Test convergence angle calculation
        double lon = Math.toRadians(-96);
        double lat = Math.toRadians(39);
        double lon0 = Math.toRadians(-93); // Central meridian for UTM zone 15
        double e = 0.08181919084262157;
        
        double result = MathUtils.convergence(lon, lat, lon0, e);
        
        // Should be reasonable for points near central meridian
        assertTrue(Math.abs(result) < Math.toRadians(5));
    }
    
    @Test
    public void testScaleFactor() {
        // Test scale factor calculation
        double lat = Math.toRadians(39);
        double e = 0.08181919084262157;
        double k0 = 0.9996;
        
        double result = MathUtils.scaleFactor(lat, e, k0);
        
        assertTrue(result > 0);
        assertTrue(result > k0); // Should be slightly greater than central scale factor
    }
    
    @Test
    public void testUtmConvergence() {
        // Test UTM convergence calculation
        double lon = Math.toRadians(-96);
        double lat = Math.toRadians(39);
        int zone = 15;
        
        double result = MathUtils.utmConvergence(lon, lat, zone);
        
        // Should be reasonable for points in the zone
        assertTrue(Math.abs(result) < Math.toRadians(5));
    }
    
    @Test
    public void testHaversineDistance() {
        // Test haversine distance calculation
        double lat1 = Math.toRadians(39.0);
        double lon1 = Math.toRadians(-96.0);
        double lat2 = Math.toRadians(40.0);
        double lon2 = Math.toRadians(-95.0);
        
        double result = MathUtils.haversineDistance(lat1, lon1, lat2, lon2, EARTH_RADIUS);
        
        assertTrue(result > 0);
        assertTrue(result < 200000); // Should be less than 200km for this distance
    }
    
    @Test
    public void testBearing() {
        // Test bearing calculation
        double lat1 = Math.toRadians(39.0);
        double lon1 = Math.toRadians(-96.0);
        double lat2 = Math.toRadians(40.0);
        double lon2 = Math.toRadians(-95.0);
        
        double result = MathUtils.bearing(lat1, lon1, lat2, lon2);
        
        // Should be in valid range [-π, π]
        assertTrue(result >= -Math.PI);
        assertTrue(result <= Math.PI);
    }
    
    @Test
    public void testMidpoint() {
        // Test midpoint calculation
        double lat1 = Math.toRadians(39.0);
        double lon1 = Math.toRadians(-96.0);
        double lat2 = Math.toRadians(40.0);
        double lon2 = Math.toRadians(-95.0);
        
        double[] result = MathUtils.midpoint(lat1, lon1, lat2, lon2);
        
        assertEquals(2, result.length);
        assertTrue(result[0] >= -Math.PI/2); // latitude
        assertTrue(result[0] <= Math.PI/2);
        assertTrue(result[1] >= -Math.PI); // longitude
        assertTrue(result[1] <= Math.PI);
        
        // Midpoint should be between the two points
        assertTrue(result[0] > Math.min(lat1, lat2));
        assertTrue(result[0] < Math.max(lat1, lat2));
    }
    
    @Test
    public void testGeometricConsistency() {
        // Test that geometric calculations are consistent
        double lat1 = Math.toRadians(39.0);
        double lon1 = Math.toRadians(-96.0);
        double lat2 = Math.toRadians(40.0);
        double lon2 = Math.toRadians(-95.0);
        
        // Calculate distance and bearing
        double distance = MathUtils.haversineDistance(lat1, lon1, lat2, lon2, EARTH_RADIUS);
        double bearing = MathUtils.bearing(lat1, lon1, lat2, lon2);
        
        // Both should be positive and reasonable
        assertTrue(distance > 0);
        assertTrue(Math.abs(bearing) <= Math.PI);
        
        // Calculate midpoint
        double[] midpoint = MathUtils.midpoint(lat1, lon1, lat2, lon2);
        
        // Midpoint should be roughly equidistant from both points
        double dist1 = MathUtils.haversineDistance(lat1, lon1, midpoint[0], midpoint[1], EARTH_RADIUS);
        double dist2 = MathUtils.haversineDistance(lat2, lon2, midpoint[0], midpoint[1], EARTH_RADIUS);
        
        // Distances should be approximately equal (within 1% tolerance)
        assertEquals(dist1, dist2, distance * 0.01);
    }
}
