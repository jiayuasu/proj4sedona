package org.datasyslab.proj4sedona.grid;

import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.datasyslab.proj4sedona.transform.Converter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for extended GridLoader functionality:
 * - Local file path support in +nadgrids
 * - URL loading support in +nadgrids
 */
class GridLoaderExtendedTest {

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    // ==================== Local File Path Tests ====================

    @Test
    void testLocalFilePathAbsolute() {
        Path gridPath = Path.of("src/test/resources/grids/ca_nrc_ntv2_0.tif").toAbsolutePath();
        assumeTrue(Files.exists(gridPath), "Grid file not found");

        String projStr = "+proj=longlat +datum=NAD27 +nadgrids=" + gridPath + " +no_defs";
        Proj nad27WithGrid = new Proj(projStr);
        Proj nad83 = new Proj("+proj=longlat +datum=NAD83 +no_defs");
        Converter conv = new Converter(nad27WithGrid, nad83);

        Point toronto = new Point(-79.3832, 43.6532);
        Point result = conv.forward(toronto);

        assertNotNull(result);
        assertNotEquals(toronto.x, result.x, 1e-6, "X should shift");
        assertNotEquals(toronto.y, result.y, 1e-6, "Y should shift");
        assertEquals(toronto.x, result.x, 0.001, "X shift should be < 0.001 deg");
        assertEquals(toronto.y, result.y, 0.001, "Y shift should be < 0.001 deg");
    }

    @Test
    void testLocalFilePathRelative() {
        String relativePath = "./src/test/resources/grids/us_noaa_conus.tif";
        assumeTrue(Files.exists(Path.of(relativePath)), "Grid file not found");

        String projStr = "+proj=longlat +datum=NAD83 +nadgrids=" + relativePath + " +no_defs";
        Proj source = new Proj(projStr);
        
        assertNotNull(source);
    }

    @Test
    void testLocalFilePathOSTN15() {
        Path etrsToOsgbPath = Path.of("src/test/resources/grids/OSTN15_NTv2_ETRStoOSGB.gsb");
        assumeTrue(Files.exists(etrsToOsgbPath), "OSTN15 grid file not found");

        String projStr = "+proj=longlat +ellps=GRS80 +nadgrids=" + etrsToOsgbPath.toAbsolutePath() + " +no_defs";
        Proj etrsWithGrid = new Proj(projStr);
        Proj osgb = new Proj("+proj=longlat +ellps=airy +no_defs");
        Converter conv = new Converter(etrsWithGrid, osgb);

        Point londonEtrs = new Point(-0.1276, 51.5074);
        Point result = conv.forward(londonEtrs);

        assertNotNull(result);
        assertEquals(londonEtrs.x, result.x, 0.01, "X shift should be < 0.01 deg");
        assertEquals(londonEtrs.y, result.y, 0.01, "Y shift should be < 0.01 deg");
    }

    @Test
    void testLocalFileNotFoundOptional() {
        // Optional grid (with @) should NOT throw when file doesn't exist
        String projStr = "+proj=longlat +datum=NAD27 +nadgrids=@/nonexistent/path/grid.gsb +no_defs";
        Proj source = new Proj(projStr);
        Proj target = new Proj("+proj=longlat +datum=NAD83 +no_defs");
        Converter conv = new Converter(source, target);
        // Should not throw - returns null when grid can't be applied
        assertDoesNotThrow(() -> conv.forward(new Point(-79.0, 43.0)));
    }

    @Test
    void testLocalFileNotFoundMandatory() {
        // Mandatory grid (without @) SHOULD throw when file doesn't exist
        String projStr = "+proj=longlat +datum=NAD27 +nadgrids=/nonexistent/path/grid.gsb +no_defs";
        Proj source = new Proj(projStr);
        Proj target = new Proj("+proj=longlat +datum=NAD83 +no_defs");
        Converter conv = new Converter(source, target);
        // Should throw when transform is attempted
        assertThrows(RuntimeException.class, () -> conv.forward(new Point(-79.0, 43.0)));
    }

    // ==================== URL Loading Tests ====================

    @Test
    void testUrlLoadingCdnProj() {
        String cdnUrl = "https://cdn.proj.org/us_noaa_conus.tif";
        String projStr = "+proj=longlat +datum=NAD83 +nadgrids=" + cdnUrl + " +no_defs";
        
        Proj source = new Proj(projStr);
        assertNotNull(source);
        
        Proj nad83 = new Proj("+proj=longlat +datum=NAD83 +no_defs");
        Converter conv = new Converter(source, nad83);
        Point result = conv.forward(new Point(-77.0, 38.9));
        assertNotNull(result);
    }

    @Test
    void testUrlLoadingGitHubOSGeoTif() {
        String githubUrl = "https://github.com/OSGeo/PROJ-data/raw/refs/heads/master/uk_os/uk_os_OSTN15_NTv2_OSGBtoETRS.tif";
        String projStr = "+proj=longlat +ellps=airy +nadgrids=" + githubUrl + " +no_defs";
        
        Proj osgbWithGrid = new Proj(projStr);
        Proj etrs = new Proj("+proj=longlat +ellps=GRS80 +no_defs");
        Converter conv = new Converter(osgbWithGrid, etrs);

        Point londonOsgb = new Point(-0.126, 51.507);
        Point result = conv.forward(londonOsgb);

        assertNotNull(result);
        assertEquals(londonOsgb.x, result.x, 0.01, "X shift should be < 0.01 deg");
        assertEquals(londonOsgb.y, result.y, 0.01, "Y shift should be < 0.01 deg");
    }

    @Test
    void testUrlLoadingGitHubGsb() {
        String githubUrl = "https://github.com/jiayuasu/grid-files/raw/refs/heads/main/us_os/OSTN15-NTv2/OSTN15_NTv2_ETRStoOSGB.gsb";
        String projStr = "+proj=longlat +ellps=GRS80 +nadgrids=" + githubUrl + " +no_defs";
        
        Proj etrsWithGrid = new Proj(projStr);
        Proj osgb = new Proj("+proj=longlat +ellps=airy +no_defs");
        Converter conv = new Converter(etrsWithGrid, osgb);

        Point londonEtrs = new Point(-0.1276, 51.5074);
        Point result = conv.forward(londonEtrs);

        assertNotNull(result);
        assertEquals(londonEtrs.x, result.x, 0.01, "X shift should be < 0.01 deg");
        assertEquals(londonEtrs.y, result.y, 0.01, "Y shift should be < 0.01 deg");
    }

    @Test
    void testUrlLoadingWithTransformation() throws Exception {
        String url = "https://cdn.proj.org/ca_nrc_ntv2_0.tif";
        GridLoader.remove("ca_nrc_ntv2_0.tif");
        
        String projStr = "+proj=longlat +datum=NAD27 +nadgrids=" + url + " +no_defs";
        Proj nad27WithGrid = new Proj(projStr);
        Proj nad83 = new Proj("+proj=longlat +datum=NAD83 +no_defs");
        Converter conv = new Converter(nad27WithGrid, nad83);

        Point[] cities = {
            new Point(-79.3832, 43.6532),   // Toronto
            new Point(-73.5673, 45.5017),   // Montreal
            new Point(-75.6972, 45.4215),   // Ottawa
        };

        for (Point city : cities) {
            Point result = conv.forward(city);
            assertNotNull(result);
            double shift = Math.sqrt(Math.pow(result.x - city.x, 2) + Math.pow(result.y - city.y, 2));
            assertTrue(shift < 0.001, "Shift should be < 0.001 degrees");
        }
    }

    @Test
    void testUrlNotFoundOptional() {
        // Optional URL grid (with @) should NOT throw when URL returns 404
        String projStr = "+proj=longlat +datum=NAD27 +nadgrids=@https://cdn.proj.org/nonexistent_grid.tif +no_defs";
        Proj source = new Proj(projStr);
        Proj target = new Proj("+proj=longlat +datum=NAD83 +no_defs");
        Converter conv = new Converter(source, target);
        // Should not throw - returns null when grid can't be applied
        assertDoesNotThrow(() -> conv.forward(new Point(-79.0, 43.0)));
    }

    @Test
    void testUrlNotFoundMandatory() {
        // Mandatory URL grid (without @) SHOULD throw when URL returns 404
        String projStr = "+proj=longlat +datum=NAD27 +nadgrids=https://cdn.proj.org/nonexistent_grid.tif +no_defs";
        Proj source = new Proj(projStr);
        Proj target = new Proj("+proj=longlat +datum=NAD83 +no_defs");
        Converter conv = new Converter(source, target);
        // Should throw when transform is attempted
        assertThrows(RuntimeException.class, () -> conv.forward(new Point(-79.0, 43.0)));
    }

    // ==================== Combined Tests ====================

    @Test
    void testResolutionOrder() throws Exception {
        Path testGrid = Path.of("src/test/resources/grids/us_noaa_conus.tif");
        assumeTrue(Files.exists(testGrid), "Test grid not found");

        String gridName = "test_priority_grid.tif";
        GridLoader.loadFile(gridName, testGrid);
        
        try {
            String projStr = "+proj=longlat +nadgrids=" + gridName + " +no_defs";
            Proj proj = new Proj(projStr);
            assertNotNull(proj, "Registry lookup should succeed");
        } finally {
            GridLoader.remove(gridName);
        }
    }
}
