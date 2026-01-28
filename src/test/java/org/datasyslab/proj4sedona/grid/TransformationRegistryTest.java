package org.datasyslab.proj4sedona.grid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransformationRegistry.
 */
class TransformationRegistryTest {

    // ==================== Basic Functionality Tests ====================

    @Test
    void testGetGridMapping_Exists() {
        // NAD27 -> NAD83 should have a grid mapping
        TransformationRegistry.GridMapping mapping = 
            TransformationRegistry.getGridMapping("EPSG:4267", "EPSG:4269");
        
        assertNotNull(mapping, "Should find grid mapping for NAD27 -> NAD83");
        assertNotNull(mapping.gridFile, "Grid file should not be null");
        assertTrue(mapping.gridFile.contains("ntv2") || mapping.gridFile.contains("nrc"), 
            "Should be NTv2 grid file");
    }

    @Test
    void testGetGridMapping_NotExists() {
        // Random EPSG codes that don't have grid mappings
        TransformationRegistry.GridMapping mapping = 
            TransformationRegistry.getGridMapping("EPSG:4326", "EPSG:3857");
        
        assertNull(mapping, "Should return null for pairs without grid mapping");
    }

    @Test
    void testHasGridMapping() {
        assertTrue(TransformationRegistry.hasGridMapping("EPSG:4267", "EPSG:4269"),
            "Should have NAD27 -> NAD83 mapping");
        assertFalse(TransformationRegistry.hasGridMapping("EPSG:4326", "EPSG:3857"),
            "Should not have WGS84 -> Web Mercator mapping");
    }

    @Test
    void testGetGridFile() {
        String gridFile = TransformationRegistry.getGridFile("EPSG:4267", "EPSG:4269");
        assertNotNull(gridFile);
        assertTrue(gridFile.endsWith(".tif"), "Grid file should be a GeoTIFF");
    }

    @Test
    void testBidirectionalMapping() {
        // Forward direction
        TransformationRegistry.GridMapping forward = 
            TransformationRegistry.getGridMapping("EPSG:4267", "EPSG:4269");
        
        // Reverse direction
        TransformationRegistry.GridMapping reverse = 
            TransformationRegistry.getGridMapping("EPSG:4269", "EPSG:4267");
        
        assertNotNull(forward);
        assertNotNull(reverse);
        
        // Same grid file
        assertEquals(forward.gridFile, reverse.gridFile);
        
        // But different application direction
        assertTrue(forward.applyToSource, "Forward should apply to source");
        assertFalse(reverse.applyToSource, "Reverse should apply to destination");
    }

    // ==================== Case Sensitivity Tests ====================

    @Test
    void testGetGridMapping_CaseInsensitive() {
        // All these should find the same mapping
        TransformationRegistry.GridMapping upper = 
            TransformationRegistry.getGridMapping("EPSG:4267", "EPSG:4269");
        TransformationRegistry.GridMapping lower = 
            TransformationRegistry.getGridMapping("epsg:4267", "epsg:4269");
        TransformationRegistry.GridMapping mixed = 
            TransformationRegistry.getGridMapping("Epsg:4267", "Epsg:4269");
        
        assertNotNull(upper, "Should find with uppercase");
        assertNotNull(lower, "Should find with lowercase");
        assertNotNull(mixed, "Should find with mixed case");
        
        assertEquals(upper.gridFile, lower.gridFile);
        assertEquals(upper.gridFile, mixed.gridFile);
    }

    @Test
    void testHasGridMapping_CaseInsensitive() {
        assertTrue(TransformationRegistry.hasGridMapping("EPSG:4267", "EPSG:4269"));
        assertTrue(TransformationRegistry.hasGridMapping("epsg:4267", "epsg:4269"));
        assertTrue(TransformationRegistry.hasGridMapping("Epsg:4267", "Epsg:4269"));
    }

    @Test
    void testGetGridFile_CaseInsensitive() {
        String upper = TransformationRegistry.getGridFile("EPSG:4267", "EPSG:4269");
        String lower = TransformationRegistry.getGridFile("epsg:4267", "epsg:4269");
        
        assertNotNull(upper);
        assertNotNull(lower);
        assertEquals(upper, lower, "Should return same grid file regardless of case");
    }

    // ==================== Size Test ====================

    @Test
    void testSize() {
        // Should have at least the registered mappings (bidirectional, so 2x)
        assertTrue(TransformationRegistry.size() >= 2, 
            "Should have at least one bidirectional mapping registered");
    }
}
