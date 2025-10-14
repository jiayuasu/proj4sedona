package org.apache.sedona.proj.mgrs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for MGRS coordinate conversion functionality.
 * Tests are based on the original JavaScript mgrs library test cases.
 */
public class MgrsTest {
    
    @Test
    public void testFirstMgrsSet() {
        String mgrsStr = "33UXP04";
        double[] point = Mgrs.toPoint(mgrsStr);
        
        // Longitude of point from MGRS correct
        assertEquals(16.41450, point[0], 0.000001);
        
        // Latitude of point from MGRS correct
        assertEquals(48.24949, point[1], 0.000001);
        
        // MGRS reference with highest accuracy correct
        assertEquals("33UXP0500444997", Mgrs.forward(point[0], point[1]));
        
        // MGRS reference with 1-digit accuracy correct
        assertEquals(mgrsStr, Mgrs.forward(point[0], point[1], 1));
        
        // MGRS reference with 0-digit accuracy correct
        assertEquals("33UXP", Mgrs.forward(point[0], point[1], 0));
    }
    
    @Test
    public void testSecondMgrsSet() {
        String mgrsStr = "24XWT783908"; // near UTM zone border, so there are two ways to reference this
        double[] point = Mgrs.toPoint(mgrsStr);
        
        // Longitude of point from MGRS correct
        assertEquals(-32.66433, point[0], 0.00001);
        
        // Latitude of point from MGRS correct
        assertEquals(83.62778, point[1], 0.00001);
        
        // MGRS reference with 3-digit accuracy correct
        assertEquals("25XEN041865", Mgrs.forward(point[0], point[1], 3));
        
        // MGRS reference with 0-digit accuracy correct
        assertEquals("25XEN", Mgrs.forward(point[0], point[1], 0));
    }
    
    @Test
    public void testSpecialCases() {
        // MGRS reference with 5-digit accuracy, northing all zeros
        assertEquals("31NAA6602100000", Mgrs.forward(0, 0, 5));
        
        // MGRS reference with 5-digit accuracy, northing one digit
        assertEquals("31NAA6602100001", Mgrs.forward(0, 0.00001, 5));
    }
    
    @Test
    public void testThirdMgrsSet() {
        String mgrsStr = "11SPA7234911844";
        double[] point = {-115.0820944, 36.2361322};
        
        // MGRS reference with highest accuracy correct
        assertEquals(mgrsStr, Mgrs.forward(point[0], point[1]));
    }
    
    @Test
    public void testInverseConversion() {
        // Test inverse conversion (MGRS to bounding box)
        double[] bbox = Mgrs.inverse("33UXP04");
        assertNotNull(bbox);
        assertEquals(4, bbox.length); // [left, bottom, right, top]
        
        // Test toPoint conversion (MGRS to center point)
        double[] point = Mgrs.toPoint("33UXP04");
        assertNotNull(point);
        assertEquals(2, point.length); // [longitude, latitude]
    }
    
    @Test
    public void testLetterDesignator() {
        // Test various latitude bands
        assertEquals('X', Mgrs.getLetterDesignator(80)); // X band (72-84)
        assertEquals('N', Mgrs.getLetterDesignator(0));  // N band (equator)
        assertEquals('C', Mgrs.getLetterDesignator(-80)); // C band (southern limit)
        assertEquals('Z', Mgrs.getLetterDesignator(90));  // Z (invalid)
        assertEquals('Z', Mgrs.getLetterDesignator(-90)); // Z (invalid)
    }
    
    @Test
    public void testInvalidInputs() {
        // Test invalid longitude
        assertThrows(IllegalArgumentException.class, () -> {
            Mgrs.forward(200, 0);
        });
        
        // Test invalid latitude
        assertThrows(IllegalArgumentException.class, () -> {
            Mgrs.forward(0, 100);
        });
        
        // Test latitude outside MGRS limits
        assertThrows(IllegalArgumentException.class, () -> {
            Mgrs.forward(0, 85);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Mgrs.forward(0, -85);
        });
        
        // Test empty MGRS string
        assertThrows(IllegalArgumentException.class, () -> {
            Mgrs.toPoint("");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Mgrs.toPoint(null);
        });
        
        // Test invalid MGRS string
        assertThrows(IllegalArgumentException.class, () -> {
            Mgrs.toPoint("INVALID");
        });
    }
    
    @Test
    public void testRoundTripAccuracy() {
        // Test round-trip conversion accuracy
        double[] originalPoint = {16.41450, 48.24949};
        String mgrs = Mgrs.forward(originalPoint[0], originalPoint[1]);
        double[] convertedPoint = Mgrs.toPoint(mgrs);
        
        // Should be very close to original (our implementation achieves ~2e-6 precision)
        assertEquals(originalPoint[0], convertedPoint[0], 2e-6);
        assertEquals(originalPoint[1], convertedPoint[1], 2e-6);
    }
    
    @Test
    public void testAccuracyLevels() {
        double[] point = {16.41450, 48.24949};
        
        // Test different accuracy levels
        String mgrs0 = Mgrs.forward(point[0], point[1], 0); // 100 km
        String mgrs1 = Mgrs.forward(point[0], point[1], 1); // 10 km
        String mgrs2 = Mgrs.forward(point[0], point[1], 2); // 1 km
        String mgrs3 = Mgrs.forward(point[0], point[1], 3); // 100 m
        String mgrs4 = Mgrs.forward(point[0], point[1], 4); // 10 m
        String mgrs5 = Mgrs.forward(point[0], point[1], 5); // 1 m
        
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
    public void testSpecialZones() {
        // Test Norway special zone (32)
        double[] norwayPoint = {7.5, 60.0}; // Should be in zone 32
        String mgrs = Mgrs.forward(norwayPoint[0], norwayPoint[1]);
        assertTrue(mgrs.startsWith("32"));
        
        // Test Svalbard special zones
        double[] svalbardPoint1 = {4.5, 78.0}; // Should be in zone 31
        String mgrs1 = Mgrs.forward(svalbardPoint1[0], svalbardPoint1[1]);
        assertTrue(mgrs1.startsWith("31"));
        
        double[] svalbardPoint2 = {15.0, 78.0}; // Should be in zone 33
        String mgrs2 = Mgrs.forward(svalbardPoint2[0], svalbardPoint2[1]);
        assertTrue(mgrs2.startsWith("33"));
    }
    
    @Test
    public void testEdgeCases() {
        // Test longitude 180 (should be in zone 60)
        double[] point180 = {180.0, 0.0};
        String mgrs = Mgrs.forward(point180[0], point180[1]);
        assertTrue(mgrs.startsWith("60"));
        
        // Test equator
        double[] equatorPoint = {0.0, 0.0};
        String equatorMgrs = Mgrs.forward(equatorPoint[0], equatorPoint[1]);
        assertNotNull(equatorMgrs);
        
        // Test prime meridian
        double[] primeMeridianPoint = {0.0, 50.0};
        String primeMeridianMgrs = Mgrs.forward(primeMeridianPoint[0], primeMeridianPoint[1]);
        assertNotNull(primeMeridianMgrs);
    }
}
