package org.datasyslab.proj4sedona.grid;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.defs.Defs;
import org.datasyslab.proj4sedona.transform.Transform;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransformationRegistry.
 */
class TransformationRegistryTest {

    // ==================== Grid Injection Tests ====================

    @Test
    void testHarnGridIsNotCombinedWithItsHelmert() {
        // EPSG:4152 resolves the EPSG_4152 Helmert through its own id, but the injected
        // NAD83 -> HARN grid already shifts into HARN, so that Helmert must not be added.
        // Reference values from PROJ, which selects the same NADCON5 grid. Needs network
        // access to cdn.proj.org for the grid.
        Point forward = Proj4.proj4("EPSG:4269", "EPSG:4152", new Point(-104.9903, 39.7392));
        assertEquals(-104.990300545, forward.x, 1e-6, "forward longitude");
        assertEquals(39.739202038, forward.y, 1e-6, "forward latitude");

        Point inverse = Proj4.proj4("EPSG:4152", "EPSG:4269", new Point(-104.9903, 39.7392));
        assertEquals(-104.990299455, inverse.x, 1e-6, "inverse longitude");
        assertEquals(39.739197962, inverse.y, 1e-6, "inverse latitude");
    }

    @Test
    void testHelmertKeptForGridsTheDefinitionLists() throws IOException {
        // Nothing is injected when the definition lists grids itself. Those grids shift to
        // WGS84, as in proj4js, whichever of them applies, so the other CRS keeps its
        // Helmert and the result equals a plain transform. That includes a list holding the
        // pair grid behind another grid, or the pair grid alone.
        String harnGrid = TransformationRegistry.getGridFile("EPSG:4269", "EPSG:4152");
        GridLoader.fetchFromCdn("us_noaa_conus.tif");
        GridLoader.fetchFromCdn(harnGrid);
        ProjectionDef saved = Defs.get("EPSG:4269");
        try {
            for (String grids : new String[]{
                "@us_noaa_conus.tif", "@us_noaa_conus.tif,@" + harnGrid, "@" + harnGrid}) {
                Defs.set("EPSG:4269", "+proj=longlat +ellps=GRS80 +nadgrids=" + grids + " +no_defs");
                for (String[] pair : new String[][]{
                    {"EPSG:4269", "EPSG:4152"}, {"EPSG:4152", "EPSG:4269"}}) {
                    String label = grids + " " + pair[0] + " -> " + pair[1];
                    Point viaRegistry = Proj4.proj4(pair[0], pair[1], new Point(-104.9903, 39.7392));
                    Point plain = Transform.transform(
                        new Proj(pair[0]), new Proj(pair[1]), new Point(-104.9903, 39.7392), false);
                    assertTrue(Math.abs(plain.x + 104.9903) > 1e-6, label + ": a grid shift applies");
                    assertEquals(plain.x, viaRegistry.x, 1e-12, label + " longitude");
                    assertEquals(plain.y, viaRegistry.y, 1e-12, label + " latitude");
                }
            }
        } finally {
            if (saved != null) {
                Defs.set("EPSG:4269", saved);
            } else {
                Defs.remove("EPSG:4269");
            }
        }
    }

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
