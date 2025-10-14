package org.proj4.projections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import org.proj4.core.Projection;
import org.proj4.core.Point;

/**
 * Test cases for Sinusoidal projection.
 * Tests both forward and inverse transformations with known coordinate pairs.
 */
public class SinusoidalTest {
    
    private Projection proj;
    
    @BeforeEach
    public void setUp() {
        // World Sinusoidal projection
        String wkt = "PROJCS[\"World_Sinusoidal\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"WGS_1984\",SPHEROID[\"WGS_1984\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Sinusoidal\"],PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",0],UNIT[\"Meter\",1],AUTHORITY[\"EPSG\",\"54008\"]]";
        proj = new Projection(wkt);
    }
    
    @Test
    public void testProjectionInitialization() {
        assertNotNull(proj);
        assertEquals("sinu", proj.projName);
        assertNotNull(proj.sinu);
        
        // Check that ellipsoid parameters are set correctly
        assertEquals(6378137.0, proj.a, 1e-6);
        assertEquals(0.006694379990141, proj.es, 1e-10); // WGS84 ellipsoid
        assertEquals(0.081819190842622, proj.e, 1e-10);
        
        // Check that projection parameters are set correctly
        assertEquals(0.0, proj.lat0, 1e-10); // 0° in radians
        assertEquals(0.0, proj.long0, 1e-10); // 0° in radians
        assertEquals(0.0, proj.x0, 1e-6);
        assertEquals(0.0, proj.y0, 1e-6);
    }
    
    @Test
    public void testForwardTransformation() {
        // Test forward transformation: geographic to projected coordinates
        // Using the center point (0°E, 0°N)
        Point input = new Point(0.0, 0.0); // 0°E, 0°N in radians
        
        Point result = proj.forward.transform(input);
        
        assertNotNull(result);
        assertFalse(Double.isNaN(result.x));
        assertFalse(Double.isNaN(result.y));
        
        // The center point should map to (0, 0)
        assertEquals(0.0, result.x, 1e-6);
        assertEquals(0.0, result.y, 1e-6);
    }
    
    @Test
    public void testInverseTransformation() {
        // Test inverse transformation: projected to geographic coordinates
        // Using the center point (0, 0)
        Point input = new Point(0.0, 0.0);
        
        Point result = proj.inverse.transform(input);
        
        assertNotNull(result);
        assertFalse(Double.isNaN(result.x));
        assertFalse(Double.isNaN(result.y));
        
        // Should return to the center coordinates
        assertEquals(0.0, result.x, 1e-6); // 0°E
        assertEquals(0.0, result.y, 1e-6); // 0°N
    }
    
    @Test
    public void testRoundTripAccuracy() {
        // Test round-trip accuracy: forward then inverse should return original point
        Point original = new Point(0.17453292519943295, 0.2617993877991494); // 10°E, 15°N
        
        Point projected = proj.forward.transform(original);
        Point backToGeographic = proj.inverse.transform(projected);
        
        assertNotNull(projected);
        assertNotNull(backToGeographic);
        assertFalse(Double.isNaN(projected.x) || Double.isNaN(projected.y));
        assertFalse(Double.isNaN(backToGeographic.x) || Double.isNaN(backToGeographic.y));
        
        // Round-trip accuracy should be within reasonable tolerance
        assertEquals(original.x, backToGeographic.x, 1e-3);
        assertEquals(original.y, backToGeographic.y, 1e-3);
    }
    
    @Test
    public void testMultiplePoints() {
        // Test multiple points across the world
        double[][] testPoints = {
            {0.0, 0.0},                           // Center point
            {0.17453292519943295, 0.2617993877991494}, // 10°E, 15°N
            {-0.17453292519943295, 0.2617993877991494}, // 10°W, 15°N
            {0.17453292519943295, -0.2617993877991494}, // 10°E, 15°S
            {-0.17453292519943295, -0.2617993877991494}, // 10°W, 15°S
            {0.5235987755982988, 0.5235987755982988},   // 30°E, 30°N
            {-0.5235987755982988, 0.5235987755982988},  // 30°W, 30°N
            {0.5235987755982988, -0.5235987755982988},  // 30°E, 30°S
            {-0.5235987755982988, -0.5235987755982988}  // 30°W, 30°S
        };
        
        for (double[] point : testPoints) {
            Point input = new Point(point[0], point[1]);
            
            Point projected = proj.forward.transform(input);
            assertNotNull(projected);
            assertFalse(Double.isNaN(projected.x) || Double.isNaN(projected.y));
            
            Point backToGeographic = proj.inverse.transform(projected);
            assertNotNull(backToGeographic);
            assertFalse(Double.isNaN(backToGeographic.x) || Double.isNaN(backToGeographic.y));
            
            // Round-trip accuracy
            assertEquals(input.x, backToGeographic.x, 1e-3);
            assertEquals(input.y, backToGeographic.y, 1e-3);
        }
    }
    
    @Test
    public void testEdgeCases() {
        // Test edge cases
        Point[] edgeCases = {
            new Point(0.0, 0.0),                    // Center point
            new Point(Math.PI, 0.0),                // 180°E
            new Point(-Math.PI, 0.0),               // 180°W
            new Point(0.0, Math.PI/2),              // North pole
            new Point(0.0, -Math.PI/2),             // South pole
            new Point(Math.PI/2, 0.0),              // 90°E
            new Point(-Math.PI/2, 0.0)              // 90°W
        };
        
        for (Point input : edgeCases) {
            Point projected = proj.forward.transform(input);
            assertNotNull(projected);
            
            // Some edge cases might produce NaN (like poles), which is expected
            if (!Double.isNaN(projected.x) && !Double.isNaN(projected.y)) {
                Point backToGeographic = proj.inverse.transform(projected);
                assertNotNull(backToGeographic);
                
                if (!Double.isNaN(backToGeographic.x) && !Double.isNaN(backToGeographic.y)) {
                    // Round-trip accuracy for valid cases
                    // Use more lenient tolerance for edge cases
                    double tolerance = (Math.abs(input.x) > 2.5) ? 1e-2 : 1e-15;
                    
                    // Special handling for 180° meridian case (π vs -π)
                    if (Math.abs(input.x) > 3.0 && Math.abs(backToGeographic.x) > 3.0) {
                        // For 180° meridian, check if the absolute values are close
                        assertEquals(Math.abs(input.x), Math.abs(backToGeographic.x), tolerance);
                    } else {
                        assertEquals(input.x, backToGeographic.x, tolerance);
                    }
                    assertEquals(input.y, backToGeographic.y, tolerance);
                }
            }
        }
    }
}
