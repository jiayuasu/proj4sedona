package org.proj4;

import org.junit.jupiter.api.Test;
import org.proj4.core.Point;
import org.proj4.core.Projection;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that our precision levels are on par with proj4js standards.
 * 
 * proj4js standards:
 * - Default xyAcc (projected coordinates): 2 decimal places → 1e-2 tolerance
 * - Default llAcc (longitude/latitude): 6 decimal places → 1e-6 tolerance
 */
public class PrecisionComparisonTest {
    
    @Test
    public void testUTMPrecisionVsProj4js() {
        // Test UTM Zone 15 (same as proj4js test data)
        String utmProj = "+proj=utm +zone=15 +datum=WGS84";
        Projection utm = new Projection(utmProj);
        
        // Test point: -96.0, 39.0 degrees (from proj4js test data)
        Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);
        
        // Forward transformation
        Point forward = utm.forward.transform(original);
        
        // Inverse transformation
        Point inverse = utm.inverse.transform(forward);
        
        // Calculate round-trip error
        double xError = Math.abs(original.x - inverse.x);
        double yError = Math.abs(original.y - inverse.y);
        
        // Convert to degrees for comparison
        double xErrorDegrees = xError * 180.0 / Math.PI;
        double yErrorDegrees = yError * 180.0 / Math.PI;
        
        System.out.println("UTM Round-trip precision test:");
        System.out.println("Original: " + original.x * 180.0 / Math.PI + ", " + original.y * 180.0 / Math.PI);
        System.out.println("Inverse:  " + inverse.x * 180.0 / Math.PI + ", " + inverse.y * 180.0 / Math.PI);
        System.out.println("X error: " + xErrorDegrees + " degrees (" + xError + " radians)");
        System.out.println("Y error: " + yErrorDegrees + " degrees (" + yError + " radians)");
        
        // proj4js standard: 1e-6 degrees for longitude/latitude
        // Our implementation should be at least as good
        assertTrue(xErrorDegrees < 1e-6, 
            "X error should be less than 1e-6 degrees, was: " + xErrorDegrees);
        assertTrue(yErrorDegrees < 1e-6, 
            "Y error should be less than 1e-6 degrees, was: " + yErrorDegrees);
        
        // Our implementation should actually be much better due to the fixes
        assertTrue(xErrorDegrees < 1e-10, 
            "X error should be less than 1e-10 degrees, was: " + xErrorDegrees);
        assertTrue(yErrorDegrees < 1e-10, 
            "Y error should be less than 1e-10 degrees, was: " + yErrorDegrees);
    }
    
    @Test
    public void testLambertConformalConicPrecision() {
        // Test LCC projection
        String lccProj = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +datum=WGS84";
        Projection lcc = new Projection(lccProj);
        
        // Test point: -96.0, 39.0 degrees
        Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);
        
        // Forward transformation
        Point forward = lcc.forward.transform(original);
        
        // Inverse transformation
        Point inverse = lcc.inverse.transform(forward);
        
        // Calculate round-trip error
        double xError = Math.abs(original.x - inverse.x);
        double yError = Math.abs(original.y - inverse.y);
        
        // Convert to degrees for comparison
        double xErrorDegrees = xError * 180.0 / Math.PI;
        double yErrorDegrees = yError * 180.0 / Math.PI;
        
        System.out.println("LCC Round-trip precision test:");
        System.out.println("Original: " + original.x * 180.0 / Math.PI + ", " + original.y * 180.0 / Math.PI);
        System.out.println("Inverse:  " + inverse.x * 180.0 / Math.PI + ", " + inverse.y * 180.0 / Math.PI);
        System.out.println("X error: " + xErrorDegrees + " degrees (" + xError + " radians)");
        System.out.println("Y error: " + yErrorDegrees + " degrees (" + yError + " radians)");
        
        // proj4js standard: 1e-6 degrees for longitude/latitude
        assertTrue(xErrorDegrees < 1e-6, 
            "X error should be less than 1e-6 degrees, was: " + xErrorDegrees);
        assertTrue(yErrorDegrees < 1e-6, 
            "Y error should be less than 1e-6 degrees, was: " + yErrorDegrees);
    }
    
    @Test
    public void testAlbersEqualAreaPrecision() {
        // Test AEA projection
        String aeaProj = "+proj=aea +lat_1=29.5 +lat_2=45.5 +lat_0=23 +lon_0=-96 +datum=WGS84";
        Projection aea = new Projection(aeaProj);
        
        // Test point: -96.0, 39.0 degrees
        Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);
        
        // Forward transformation
        Point forward = aea.forward.transform(original);
        
        // Inverse transformation
        Point inverse = aea.inverse.transform(forward);
        
        // Calculate round-trip error
        double xError = Math.abs(original.x - inverse.x);
        double yError = Math.abs(original.y - inverse.y);
        
        // Convert to degrees for comparison
        double xErrorDegrees = xError * 180.0 / Math.PI;
        double yErrorDegrees = yError * 180.0 / Math.PI;
        
        System.out.println("AEA Round-trip precision test:");
        System.out.println("Original: " + original.x * 180.0 / Math.PI + ", " + original.y * 180.0 / Math.PI);
        System.out.println("Inverse:  " + inverse.x * 180.0 / Math.PI + ", " + inverse.y * 180.0 / Math.PI);
        System.out.println("X error: " + xErrorDegrees + " degrees (" + xError + " radians)");
        System.out.println("Y error: " + yErrorDegrees + " degrees (" + yError + " radians)");
        
        // proj4js standard: 1e-6 degrees for longitude/latitude
        assertTrue(xErrorDegrees < 1e-6, 
            "X error should be less than 1e-6 degrees, was: " + xErrorDegrees);
        assertTrue(yErrorDegrees < 1e-6, 
            "Y error should be less than 1e-6 degrees, was: " + yErrorDegrees);
    }
}
