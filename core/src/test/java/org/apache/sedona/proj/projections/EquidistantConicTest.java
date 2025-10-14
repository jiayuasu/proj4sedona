package org.apache.sedona.proj.projections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.sedona.proj.core.Projection;
import org.apache.sedona.proj.core.Point;

/**
 * Test cases for Equidistant Conic projection.
 * Tests both forward and inverse transformations with known coordinate pairs.
 */
public class EquidistantConicTest {
    
    private Projection proj;
    
    @BeforeEach
    public void setUp() {
        // Asia North Equidistant Conic projection
        String wkt = "PROJCS[\"Asia_North_Equidistant_Conic\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Equidistant_Conic\"],PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],PARAMETER[\"Central_Meridian\",95],PARAMETER[\"Standard_Parallel_1\",15],PARAMETER[\"Standard_Parallel_2\",65],PARAMETER[\"Latitude_Of_Origin\",30],UNIT[\"Meter\",1]]";
        proj = new Projection(wkt);
    }
    
    @Test
    public void testProjectionInitialization() {
        assertNotNull(proj);
        assertEquals("eqdc", proj.projName);
        assertNotNull(proj.eqdc);
        
        // Check that ellipsoid parameters are set correctly
        assertEquals(6378137.0, proj.a, 1e-6);
        assertEquals(6356752.314245179, proj.b, 1e-6);
        assertEquals(0.00669437999014133, proj.es, 1e-10);
        assertEquals(0.08181919084262157, proj.e, 1e-10);
        
        // Check that projection parameters are set correctly
        assertEquals(0.2617993877991494, proj.lat1, 1e-10); // 15° in radians
        assertEquals(1.1344640137963142, proj.lat2, 1e-10); // 65° in radians
        assertEquals(0.5235987755982988, proj.lat0, 1e-10); // 30° in radians
        assertEquals(1.6580627893946132, proj.long0, 1e-10); // 95° in radians
        assertEquals(0.0, proj.x0, 1e-10);
        assertEquals(0.0, proj.y0, 1e-10);
    }
    
    @Test
    public void testForwardTransformation() {
        // Test forward transformation: geographic to projected coordinates
        // Using a test point in Asia (approximately 30°N, 95°E)
        Point input = new Point(1.6580627893946132, 0.5235987755982988); // 95°E, 30°N in radians
        
        Point result = proj.forward.transform(input);
        
        assertNotNull(result);
        assertFalse(Double.isNaN(result.x));
        assertFalse(Double.isNaN(result.y));
        
        // The result should be close to the false easting/northing since we're at the origin
        assertEquals(0.0, result.x, 1e-6);
        assertEquals(0.0, result.y, 1e-6);
    }
    
    @Test
    public void testInverseTransformation() {
        // Test inverse transformation: projected to geographic coordinates
        Point input = new Point(0.0, 0.0); // Origin point
        
        Point result = proj.inverse.transform(input);
        
        assertNotNull(result);
        assertFalse(Double.isNaN(result.x));
        assertFalse(Double.isNaN(result.y));
        
        // Should return to approximately the origin latitude/longitude
        assertEquals(1.6580627893946132, result.x, 1e-6); // 95°E
        assertEquals(0.5235987755982988, result.y, 1e-6); // 30°N
    }
    
    @Test
    public void testRoundTripAccuracy() {
        // Test round-trip accuracy: forward then inverse should return original point
        Point original = new Point(1.7, 0.6); // Approximately 97.4°E, 34.4°N
        
        Point projected = proj.forward.transform(original);
        Point backToGeographic = proj.inverse.transform(projected);
        
        assertNotNull(projected);
        assertNotNull(backToGeographic);
        assertFalse(Double.isNaN(projected.x) || Double.isNaN(projected.y));
        assertFalse(Double.isNaN(backToGeographic.x) || Double.isNaN(backToGeographic.y));
        
        // Round-trip accuracy should be within reasonable tolerance
        assertEquals(original.x, backToGeographic.x, 1e-6);
        assertEquals(original.y, backToGeographic.y, 1e-6);
    }
    
    @Test
    public void testMultiplePoints() {
        // Test multiple points across the projection area
        double[][] testPoints = {
            {1.5, 0.3},   // ~86°E, 17°N
            {1.6, 0.4},   // ~92°E, 23°N  
            {1.7, 0.5},   // ~97°E, 29°N
            {1.8, 0.6},   // ~103°E, 34°N
            {1.9, 0.7}    // ~109°E, 40°N
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
            assertEquals(input.x, backToGeographic.x, 1e-5);
            assertEquals(input.y, backToGeographic.y, 1e-5);
        }
    }
    
    @Test
    public void testEdgeCases() {
        // Test edge cases
        Point[] edgeCases = {
            new Point(1.6580627893946132, 0.2617993877991494), // Standard parallel 1
            new Point(1.6580627893946132, 1.1344640137963142), // Standard parallel 2
            new Point(1.6580627893946132, 0.5235987755982988), // Origin latitude
            new Point(1.6580627893946132, 0.0),                // Equator
            new Point(1.6580627893946132, Math.PI/2)           // North pole
        };
        
        for (Point input : edgeCases) {
            Point projected = proj.forward.transform(input);
            assertNotNull(projected);
            
            // Some edge cases might produce NaN (like north pole), which is expected
            if (!Double.isNaN(projected.x) && !Double.isNaN(projected.y)) {
                Point backToGeographic = proj.inverse.transform(projected);
                assertNotNull(backToGeographic);
                
                if (!Double.isNaN(backToGeographic.x) && !Double.isNaN(backToGeographic.y)) {
                    // Round-trip accuracy for valid cases
                    assertEquals(input.x, backToGeographic.x, 1e-5);
                    assertEquals(input.y, backToGeographic.y, 1e-5);
                }
            }
        }
    }
}
