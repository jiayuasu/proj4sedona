package org.proj4.datum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.proj4.core.Point;
import org.proj4.constants.Values;

/**
 * Tests for grid-based datum shift functionality.
 */
public class GridShiftTest {
    
    @BeforeEach
    public void setUp() {
        // Clear any existing grids
        GridShift.clearGrids();
    }
    
    @AfterEach
    public void tearDown() {
        // Clean up after each test
        GridShift.clearGrids();
    }
    
    @Test
    public void testRegisterAndGetGrid() {
        // Create a test grid
        double[] latShifts = {0.1, 0.2, 0.3, 0.4};
        double[] lonShifts = {0.05, 0.15, 0.25, 0.35};
        
        GridShift.GridFile testGrid = new GridShift.GridFile(
            "test_grid",
            -90.0, 90.0, -180.0, 180.0,  // bounds
            1.0, 1.0,  // step sizes
            2, 2,  // counts
            latShifts, lonShifts
        );
        
        // Register the grid
        GridShift.registerGrid("test", testGrid);
        
        // Verify it can be retrieved
        assertTrue(GridShift.hasGrid("test"));
        GridShift.GridFile retrieved = GridShift.getGrid("test");
        assertNotNull(retrieved);
        assertEquals("test_grid", retrieved.name);
        assertEquals(-90.0, retrieved.minLat, 1e-10);
        assertEquals(90.0, retrieved.maxLat, 1e-10);
        assertEquals(-180.0, retrieved.minLon, 1e-10);
        assertEquals(180.0, retrieved.maxLon, 1e-10);
    }
    
    @Test
    public void testRemoveGrid() {
        // Create and register a test grid
        double[] latShifts = {0.1, 0.2};
        double[] lonShifts = {0.05, 0.15};
        
        GridShift.GridFile testGrid = new GridShift.GridFile(
            "test_grid",
            -90.0, 90.0, -180.0, 180.0,
            1.0, 1.0,
            1, 1,
            latShifts, lonShifts
        );
        
        GridShift.registerGrid("test", testGrid);
        assertTrue(GridShift.hasGrid("test"));
        
        // Remove the grid
        GridShift.GridFile removed = GridShift.removeGrid("test");
        assertNotNull(removed);
        assertEquals("test_grid", removed.name);
        
        // Verify it's no longer available
        assertFalse(GridShift.hasGrid("test"));
        assertNull(GridShift.getGrid("test"));
    }
    
    @Test
    public void testGetGridNames() {
        // Initially should be empty
        String[] names = GridShift.getGridNames();
        assertEquals(0, names.length);
        
        // Add some grids
        double[] latShifts = {0.1};
        double[] lonShifts = {0.05};
        
        GridShift.GridFile grid1 = new GridShift.GridFile(
            "grid1", -90.0, 90.0, -180.0, 180.0, 1.0, 1.0, 1, 1, latShifts, lonShifts
        );
        GridShift.GridFile grid2 = new GridShift.GridFile(
            "grid2", -90.0, 90.0, -180.0, 180.0, 1.0, 1.0, 1, 1, latShifts, lonShifts
        );
        
        GridShift.registerGrid("grid1", grid1);
        GridShift.registerGrid("grid2", grid2);
        
        // Check names
        names = GridShift.getGridNames();
        assertEquals(2, names.length);
        // Note: order is not guaranteed, so we just check that both names are present
        boolean hasGrid1 = false, hasGrid2 = false;
        for (String name : names) {
            if ("grid1".equals(name)) hasGrid1 = true;
            if ("grid2".equals(name)) hasGrid2 = true;
        }
        assertTrue(hasGrid1);
        assertTrue(hasGrid2);
    }
    
    @Test
    public void testInterpolateGrid() {
        // Create a test grid
        double[] latShifts = {0.1, 0.2, 0.3, 0.4};
        double[] lonShifts = {0.05, 0.15, 0.25, 0.35};
        
        GridShift.GridFile testGrid = new GridShift.GridFile(
            "test_grid",
            -90.0, 90.0, -180.0, 180.0,
            1.0, 1.0,
            2, 2,
            latShifts, lonShifts
        );
        
        // Test point within grid bounds
        double lat = 0.0 * Values.D2R;  // 0 degrees
        double lon = 0.0 * Values.D2R;  // 0 degrees
        double[] shifts = GridShift.interpolateGrid(testGrid, lat, lon);
        
        assertNotNull(shifts);
        assertEquals(2, shifts.length);
        // For Phase 2, we return zero shifts
        assertEquals(0.0, shifts[0], 1e-10);
        assertEquals(0.0, shifts[1], 1e-10);
    }
    
    @Test
    public void testInterpolateGridOutsideBounds() {
        // Create a test grid
        double[] latShifts = {0.1, 0.2};
        double[] lonShifts = {0.05, 0.15};
        
        GridShift.GridFile testGrid = new GridShift.GridFile(
            "test_grid",
            -90.0, 90.0, -180.0, 180.0,
            1.0, 1.0,
            1, 1,
            latShifts, lonShifts
        );
        
        // Test point outside grid bounds
        double lat = 100.0 * Values.D2R;  // 100 degrees (outside bounds)
        double lon = 0.0 * Values.D2R;
        double[] shifts = GridShift.interpolateGrid(testGrid, lat, lon);
        
        assertNull(shifts); // Should return null for points outside bounds
    }
    
    @Test
    public void testClearGrids() {
        // Add some grids
        double[] latShifts = {0.1};
        double[] lonShifts = {0.05};
        
        GridShift.GridFile grid1 = new GridShift.GridFile(
            "grid1", -90.0, 90.0, -180.0, 180.0, 1.0, 1.0, 1, 1, latShifts, lonShifts
        );
        GridShift.GridFile grid2 = new GridShift.GridFile(
            "grid2", -90.0, 90.0, -180.0, 180.0, 1.0, 1.0, 1, 1, latShifts, lonShifts
        );
        
        GridShift.registerGrid("grid1", grid1);
        GridShift.registerGrid("grid2", grid2);
        
        assertEquals(2, GridShift.getGridNames().length);
        
        // Clear all grids
        GridShift.clearGrids();
        
        assertEquals(0, GridShift.getGridNames().length);
        assertFalse(GridShift.hasGrid("grid1"));
        assertFalse(GridShift.hasGrid("grid2"));
    }
}
