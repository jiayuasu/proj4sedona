package org.apache.sedona.proj;

import org.junit.jupiter.api.Test;
import org.apache.sedona.proj.core.Point;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MGRS functionality in Proj4Sedona.
 * Tests the MGRS integration with the main Proj4Sedona API.
 */
public class MgrsIntegrationTest {
    
    @Test
    public void testProj4SedonaMgrsMethods() {
        // Test Proj4Sedona MGRS methods
        String mgrs = Proj4Sedona.mgrsForward(16.41450, 48.24949);
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("33UXP"));
        
        // Test with accuracy
        String mgrs1 = Proj4Sedona.mgrsForward(16.41450, 48.24949, 1);
        assertEquals("33UXP04", mgrs1);
        
        // Test inverse conversion
        double[] bbox = Proj4Sedona.mgrsInverse("33UXP04");
        assertNotNull(bbox);
        assertEquals(4, bbox.length);
        
        // Test toPoint conversion
        double[] point = Proj4Sedona.mgrsToPoint("33UXP04");
        assertNotNull(point);
        assertEquals(2, point.length);
        assertEquals(16.41450, point[0], 0.000001);
        assertEquals(48.24949, point[1], 0.000001);
        
        // Test fromMGRS
        Point p = Proj4Sedona.fromMGRS("33UXP04");
        assertNotNull(p);
        assertEquals(16.41450, p.x, 0.000001);
        assertEquals(48.24949, p.y, 0.000001);
    }
    
    @Test
    public void testPointMgrsMethods() {
        // Test Point.fromMGRS
        Point point = Point.fromMGRS("33UXP04");
        assertNotNull(point);
        assertEquals(16.41450, point.x, 0.000001);
        assertEquals(48.24949, point.y, 0.000001);
        
        // Test Point.toMGRS
        Point testPoint = new Point(16.41450, 48.24949);
        String mgrs = testPoint.toMGRS();
        assertNotNull(mgrs);
        assertTrue(mgrs.startsWith("33UXP"));
        
        // Test Point.toMGRS with accuracy
        String mgrs1 = testPoint.toMGRS(1);
        assertEquals("33UXP04", mgrs1);
        
        String mgrs0 = testPoint.toMGRS(0);
        assertEquals("33UXP", mgrs0);
    }
    
    @Test
    public void testMgrsRoundTrip() {
        // Test round-trip conversion
        Point original = new Point(16.41450, 48.24949);
        String mgrs = original.toMGRS();
        Point converted = Point.fromMGRS(mgrs);
        
        // Should be very close to original (our implementation achieves ~2e-6 precision)
        assertEquals(original.x, converted.x, 2e-6);
        assertEquals(original.y, converted.y, 2e-6);
    }
    
    @Test
    public void testMgrsAccuracyLevels() {
        Point point = new Point(16.41450, 48.24949);
        
        // Test different accuracy levels
        String mgrs0 = point.toMGRS(0); // 100 km
        String mgrs1 = point.toMGRS(1); // 10 km
        String mgrs2 = point.toMGRS(2); // 1 km
        String mgrs3 = point.toMGRS(3); // 100 m
        String mgrs4 = point.toMGRS(4); // 10 m
        String mgrs5 = point.toMGRS(5); // 1 m
        
        // Higher accuracy should result in longer strings
        assertTrue(mgrs0.length() < mgrs1.length());
        assertTrue(mgrs1.length() < mgrs2.length());
        assertTrue(mgrs2.length() < mgrs3.length());
        assertTrue(mgrs3.length() < mgrs4.length());
        assertTrue(mgrs4.length() < mgrs5.length());
        
        // All should start with the same zone and 100k designator
        assertTrue(mgrs1.startsWith(mgrs0));
        assertTrue(mgrs2.startsWith(mgrs0));
        assertTrue(mgrs3.startsWith(mgrs0));
        assertTrue(mgrs4.startsWith(mgrs0));
        assertTrue(mgrs5.startsWith(mgrs0));
    }
    
    @Test
    public void testMgrsCompatibilityWithProj4js() {
        // Test cases from proj4js MGRS tests
        String mgrsStr = "33UXP04";
        Point point = Point.fromMGRS(mgrsStr);
        
        // Longitude of point from MGRS correct
        assertEquals(16.41450, point.x, 0.000001);
        
        // Latitude of point from MGRS correct
        assertEquals(48.24949, point.y, 0.000001);
        
        // MGRS reference with highest accuracy correct
        assertEquals("33UXP0500444997", point.toMGRS());
        
        // MGRS reference with 1-digit accuracy correct
        assertEquals(mgrsStr, point.toMGRS(1));
        
        // MGRS reference with 0-digit accuracy correct
        assertEquals("33UXP", point.toMGRS(0));
    }
    
    @Test
    public void testMgrsSpecialCases() {
        // Test special cases from proj4js
        Point point0 = new Point(0, 0);
        assertEquals("31NAA6602100000", point0.toMGRS(5));
        
        Point point1 = new Point(0, 0.00001);
        assertEquals("31NAA6602100001", point1.toMGRS(5));
    }
    
    @Test
    public void testMgrsErrorHandling() {
        // Test error handling
        assertThrows(IllegalArgumentException.class, () -> {
            Proj4Sedona.mgrsForward(200, 0); // Invalid longitude
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Proj4Sedona.mgrsForward(0, 100); // Invalid latitude
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Point.fromMGRS(""); // Empty MGRS string
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Point.fromMGRS("INVALID"); // Invalid MGRS string
        });
    }
}
