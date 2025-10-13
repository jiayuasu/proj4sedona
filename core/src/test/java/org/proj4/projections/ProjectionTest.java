package org.proj4.projections;

import org.junit.jupiter.api.Test;
import org.proj4.Proj4Sedona;
import org.proj4.core.Point;
import org.proj4.core.Projection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the new projection implementations.
 */
public class ProjectionTest {
    
    @Test
    public void testLambertConformalConic() {
        // Test LCC projection with standard parallels
        String projString = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
        Projection proj = new Projection(projString);
        
        assertNotNull(proj);
        assertEquals("lcc", proj.projName);
        assertEquals(33.0 * Math.PI / 180.0, proj.lat1, 1e-10);
        assertEquals(45.0 * Math.PI / 180.0, proj.lat2, 1e-10);
        assertEquals(39.0 * Math.PI / 180.0, proj.lat0, 1e-10);
        assertEquals(-96.0 * Math.PI / 180.0, proj.long0, 1e-10);
        
        // Test forward transformation
        Point input = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);
        Point result = proj.forward.transform(input);
        
        assertNotNull(result);
        assertEquals(0.0, result.x, 1e-6); // Should be at false easting
        assertEquals(0.0, result.y, 1e-6); // Should be at false northing
    }
    
    @Test
    public void testAlbersEqualArea() {
        // Test AEA projection
        String projString = "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
        Projection proj = new Projection(projString);
        
        assertNotNull(proj);
        assertEquals("aea", proj.projName);
        assertEquals(29.5 * Math.PI / 180.0, proj.lat1, 1e-10);
        assertEquals(45.5 * Math.PI / 180.0, proj.lat2, 1e-10);
        assertEquals(23.0 * Math.PI / 180.0, proj.lat0, 1e-10);
        assertEquals(-96.0 * Math.PI / 180.0, proj.long0, 1e-10);
        
        // Test forward transformation
        Point input = new Point(-96.0 * Math.PI / 180.0, 23.0 * Math.PI / 180.0);
        Point result = proj.forward.transform(input);
        
        assertNotNull(result);
        assertEquals(0.0, result.x, 1e-6); // Should be at false easting
        assertEquals(0.0, result.y, 1e-6); // Should be at false northing
    }
    
    @Test
    public void testTransverseMercator() {
        // Test TM projection
        String projString = "+proj=tmerc +lat_0=0 +lon_0=-96 +k=1 +x_0=500000 +y_0=0 +datum=WGS84 +units=m +no_defs";
        Projection proj = new Projection(projString);
        
        assertNotNull(proj);
        assertEquals("tmerc", proj.projName);
        assertEquals(0.0, proj.lat0, 1e-10);
        assertEquals(-96.0 * Math.PI / 180.0, proj.long0, 1e-10);
        assertEquals(1.0, proj.k0, 1e-10);
        assertEquals(500000.0, proj.x0, 1e-10);
        assertEquals(0.0, proj.y0, 1e-10);
        
        // Test forward transformation
        Point input = new Point(-96.0 * Math.PI / 180.0, 0.0);
        Point result = proj.forward.transform(input);
        
        assertNotNull(result);
        assertEquals(500000.0, result.x, 1e-6); // Should be at false easting
        assertEquals(0.0, result.y, 1e-6); // Should be at false northing
    }
    
    @Test
    public void testUTM() {
        // Test UTM Zone 15N
        String projString = "+proj=utm +zone=15 +datum=WGS84 +units=m +no_defs";
        Projection proj = new Projection(projString);
        
        assertNotNull(proj);
        assertEquals("utm", proj.projName);
        assertEquals(15, proj.zone);
        assertEquals(0.9996, proj.k0, 1e-10);
        assertEquals(500000.0, proj.x0, 1e-10);
        assertEquals(0.0, proj.y0, 1e-10);
        assertFalse(proj.utmSouth);
        
        // Test forward transformation
        Point input = new Point(-93.0 * Math.PI / 180.0, 45.0 * Math.PI / 180.0);
        Point result = proj.forward.transform(input);
        
        assertNotNull(result);
        // UTM coordinates should be reasonable for this location
        assertTrue(result.x > 0);
        assertTrue(result.y > 0);
    }
    
    @Test
    public void testUTMSouth() {
        // Test UTM Zone 15S
        String projString = "+proj=utm +zone=15 +south +datum=WGS84 +units=m +no_defs";
        Projection proj = new Projection(projString);
        
        assertNotNull(proj);
        assertEquals("utm", proj.projName);
        assertEquals(15, proj.zone);
        assertEquals(0.9996, proj.k0, 1e-10);
        assertEquals(500000.0, proj.x0, 1e-10);
        assertEquals(10000000.0, proj.y0, 1e-10);
        assertTrue(proj.utmSouth);
    }
    
    @Test
    public void testProj4SedonaIntegration() {
        // Test integration with Proj4Sedona main class
        Point input = new Point(-96.0, 39.0); // Longitude, Latitude in degrees
        
        // Test LCC transformation
        Point result = Proj4Sedona.transform("+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84", input);
        assertNotNull(result);
        
        // Test UTM transformation
        Point utmResult = Proj4Sedona.transform("+proj=utm +zone=15 +datum=WGS84", input);
        assertNotNull(utmResult);
        
        // Test AEA transformation
        Point aeaResult = Proj4Sedona.transform("+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +datum=WGS84", input);
        assertNotNull(aeaResult);
    }
    
    @Test
    public void testInverseTransformations() {
        // Test round-trip transformations
        Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);
        
        // LCC round-trip
        String lccProj = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84";
        Projection lcc = new Projection(lccProj);
        Point lccForward = lcc.forward.transform(original);
        assertNotNull(lccForward);
        Point lccInverse = lcc.inverse.transform(lccForward);
        assertNotNull(lccInverse);
        assertEquals(original.x, lccInverse.x, 1e-10); // Restored strict tolerance
        assertEquals(original.y, lccInverse.y, 1e-10);
        
        // UTM round-trip - test that transformations work (precision may vary)
        String utmProj = "+proj=utm +zone=15 +datum=WGS84";
        Projection utm = new Projection(utmProj);
        Point utmForward = utm.forward.transform(original);
        assertNotNull(utmForward);
        Point utmInverse = utm.inverse.transform(utmForward);
        assertNotNull(utmInverse);
        // Check that the inverse transformation produces accurate results
        // UTM precision should match original proj4js standard of 1e-6 degrees
        assertEquals(original.x, utmInverse.x, 1e-6);
        assertEquals(original.y, utmInverse.y, 1e-6);
        
        // AEA round-trip
        String aeaProj = "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +datum=WGS84";
        Projection aea = new Projection(aeaProj);
        Point aeaForward = aea.forward.transform(original);
        assertNotNull(aeaForward);
        Point aeaInverse = aea.inverse.transform(aeaForward);
        assertNotNull(aeaInverse);
        assertEquals(original.x, aeaInverse.x, 1e-10); // Restored strict tolerance
        assertEquals(original.y, aeaInverse.y, 1e-10);
    }
}
