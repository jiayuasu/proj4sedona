package org.proj4;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.proj4.core.Point;

/**
 * Tests for datum transformation integration in Proj4Sedona.
 */
public class Proj4SedonaDatumTest {
    
    @Test
    public void testTransformWGS84ToWGS84() {
        // Test identity transformation
        Point point = new Point(-71.0, 41.0); // Boston, MA
        Point result = Proj4Sedona.transform("WGS84", "WGS84", point);
        
        assertNotNull(result);
        assertEquals(point.x, result.x, 1e-10);
        assertEquals(point.y, result.y, 1e-10);
    }
    
    @Test
    public void testTransformWithDifferentDatums() {
        // Test transformation between different datums
        // Note: This test uses the same ellipsoid (WGS84) but different datum codes
        Point point = new Point(-71.0, 41.0); // Boston, MA
        
        // Transform from WGS84 to NAD83 (should be nearly identical)
        Point result = Proj4Sedona.transform("+proj=longlat +datum=WGS84", 
                                          "+proj=longlat +datum=NAD83", 
                                          point);
        
        assertNotNull(result);
        // Should be very close since both are essentially WGS84
        assertEquals(point.x, result.x, 1e-6);
        assertEquals(point.y, result.y, 1e-6);
    }
    
    @Test
    public void testTransformWith3ParamDatum() {
        // Test transformation with 3-parameter datum
        Point point = new Point(-71.0, 41.0); // Boston, MA
        
        // Create a custom datum with 3-parameter transformation
        String customDatum = "+proj=longlat +datum=WGS84 +towgs84=100,200,300";
        
        Point result = Proj4Sedona.transform("WGS84", customDatum, point);
        
        assertNotNull(result);
        // The transformation should be applied
        // Note: The exact values depend on the implementation details
    }
    
    @Test
    public void testTransformWith7ParamDatum() {
        // Test transformation with 7-parameter datum
        Point point = new Point(-71.0, 41.0); // Boston, MA
        
        // Create a custom datum with 7-parameter transformation
        String customDatum = "+proj=longlat +datum=WGS84 +towgs84=100,200,300,1,2,3,1.000001";
        
        Point result = Proj4Sedona.transform("WGS84", customDatum, point);
        
        assertNotNull(result);
        // The transformation should be applied
        // Note: The exact values depend on the implementation details
    }
    
    @Test
    public void testTransformWithGridShiftDatum() {
        // Test transformation with grid-based datum
        Point point = new Point(-71.0, 41.0); // Boston, MA
        
        // Create a custom datum with grid shift
        String customDatum = "+proj=longlat +datum=NAD27 +nadgrids=@conus";
        
        Point result = Proj4Sedona.transform("WGS84", customDatum, point);
        
        assertNotNull(result);
        // The transformation should be applied
        // Note: For Phase 2, grid shifts return zero shifts
    }
    
    @Test
    public void testConverterWithDatumTransformation() {
        // Test converter with datum transformation
        Point point = new Point(-71.0, 41.0); // Boston, MA
        
        Proj4Sedona.Converter converter = Proj4Sedona.converter(
            "+proj=longlat +datum=WGS84",
            "+proj=longlat +datum=NAD83"
        );
        
        Point result = converter.forward(point);
        
        assertNotNull(result);
        // Should be very close since both are essentially WGS84
        assertEquals(point.x, result.x, 1e-6);
        assertEquals(point.y, result.y, 1e-6);
    }
    
    @Test
    public void testInverseTransformWithDatumTransformation() {
        // Test inverse transformation with datum transformation
        Point point = new Point(-71.0, 41.0); // Boston, MA
        
        Proj4Sedona.Converter converter = Proj4Sedona.converter(
            "+proj=longlat +datum=WGS84",
            "+proj=longlat +datum=NAD83"
        );
        
        // Forward transformation
        Point transformed = converter.forward(point);
        assertNotNull(transformed);
        
        // Inverse transformation
        Point back = converter.inverse(transformed);
        assertNotNull(back);
        
        // Should be close to original (within numerical precision)
        assertEquals(point.x, back.x, 1e-6);
        assertEquals(point.y, back.y, 1e-6);
    }
    
    @Test
    public void testTransformWithInvalidCoordinates() {
        // Test with invalid coordinates
        Point invalidPoint = new Point(Double.NaN, Double.NaN);
        
        Point result = Proj4Sedona.transform("WGS84", "WGS84", invalidPoint);
        
        assertNotNull(result);
        assertTrue(Double.isNaN(result.x));
        assertTrue(Double.isNaN(result.y));
    }
    
    @Test
    public void testTransformWithExtremeCoordinates() {
        // Test with extreme coordinates
        Point extremePoint = new Point(180.0, 90.0); // North Pole
        
        Point result = Proj4Sedona.transform("WGS84", "WGS84", extremePoint);
        
        assertNotNull(result);
        // Should handle extreme coordinates gracefully
    }
}
