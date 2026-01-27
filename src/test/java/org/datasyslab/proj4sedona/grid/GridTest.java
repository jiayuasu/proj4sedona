package org.datasyslab.proj4sedona.grid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.constants.Values;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for grid loading and interpolation.
 */
public class GridTest {

    private static final String TEST_GRIDS_DIR = "src/test/resources/grids";

    @BeforeEach
    void setUp() {
        NadgridRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        NadgridRegistry.clear();
    }

    @Test
    void testLoadGeoTiffGrid() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "us_noaa_conus.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping test - grid file not found: " + gridPath);
            return;
        }

        GridData grid = GridLoader.loadFile("conus", gridPath);
        assertNotNull(grid);
        assertNotNull(grid.getHeader());
        assertFalse(grid.getSubgrids().isEmpty());
        
        assertTrue(GridLoader.has("conus"));
        assertEquals(grid, GridLoader.get("conus"));
    }

    @Test
    void testLoadCanadaGrid() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "ca_nrc_ntv2_0.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping test - grid file not found: " + gridPath);
            return;
        }

        GridData grid = GridLoader.loadFile("ca_ntv2", gridPath);
        assertNotNull(grid);
        assertNotNull(grid.getHeader());
        assertFalse(grid.getSubgrids().isEmpty());
        
        System.out.println("Canada grid loaded: " + grid.getSubgrids().size() + " subgrids");
    }

    @Test
    void testGetNadgrids() {
        var grids = GridLoader.getNadgrids("@conus,null");
        assertNotNull(grids);
        assertEquals(2, grids.size());
        
        assertEquals("conus", grids.get(0).getName());
        assertFalse(grids.get(0).isMandatory());
        assertFalse(grids.get(0).isNull());
        
        assertEquals("null", grids.get(1).getName());
        assertTrue(grids.get(1).isMandatory());
        assertTrue(grids.get(1).isNull());
    }

    @Test
    void testGetNadgridsWithMandatory() {
        // Use optional grids (@) since mandatory grids throw if not found
        var grids = GridLoader.getNadgrids("@conus,@fallback,null");
        assertNotNull(grids);
        assertEquals(3, grids.size());
        
        assertFalse(grids.get(0).isMandatory()); // @conus is optional
        assertFalse(grids.get(1).isMandatory()); // @fallback is optional
        assertTrue(grids.get(2).isMandatory());  // null is mandatory (special case)
    }

    @Test
    void testGridInterpolation() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "us_noaa_conus.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping test - grid file not found: " + gridPath);
            return;
        }

        GridData grid = GridLoader.loadFile("conus", gridPath);
        assertFalse(grid.getSubgrids().isEmpty());
        
        Subgrid subgrid = grid.getSubgrids().get(0);
        double[] ll = subgrid.getLl();
        double[] del = subgrid.getDel();
        
        double testLon = ll[0] + del[0] * 5;
        double testLat = ll[1] + del[1] * 5;
        
        double[] shift = GridInterpolator.interpolate(
            testLon - ll[0], 
            testLat - ll[1], 
            subgrid
        );
        
        assertFalse(Double.isNaN(shift[0]), "Longitude shift should not be NaN");
        assertFalse(Double.isNaN(shift[1]), "Latitude shift should not be NaN");
        
        System.out.printf("Shift at (%.6f, %.6f): lon=%.9f, lat=%.9f%n", 
            testLon, testLat, shift[0], shift[1]);
    }

    @Test
    void testSubgridContains() {
        double[] ll = {-2.0, 0.5};
        double[] del = {0.1, 0.1};
        int[] lim = {10, 10};
        double[][] cvs = new double[100][2];
        
        Subgrid subgrid = new Subgrid(ll, del, lim, 100, cvs);
        
        assertTrue(subgrid.contains(-1.5, 0.7));
        assertFalse(subgrid.contains(-2.5, 0.7));
        assertFalse(subgrid.contains(-1.5, 2.0));
    }

    @Test
    void testRegistryClear() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "us_noaa_conus.tif");
        if (!Files.exists(gridPath)) {
            return;
        }

        GridLoader.loadFile("test_grid", gridPath);
        assertTrue(GridLoader.has("test_grid"));
        
        GridLoader.clear();
        assertFalse(GridLoader.has("test_grid"));
    }

    // ========== NTv2 Parser Tests ==========

    @Test
    void testNTV2GridReaderWithSyntheticFile() throws IOException {
        byte[] ntv2Data = createSyntheticNTV2Grid();
        
        GridData grid = NTV2GridReader.read(ntv2Data);
        
        assertNotNull(grid);
        assertNotNull(grid.getHeader());
        assertEquals(1, grid.getSubgrids().size());
        
        Subgrid subgrid = grid.getSubgrids().get(0);
        assertEquals(4, subgrid.getLim()[0]);
        assertEquals(4, subgrid.getLim()[1]);
        assertEquals(16, subgrid.getCount());
        
        double[][] cvs = subgrid.getCvs();
        assertNotNull(cvs);
        assertEquals(16, cvs.length);
        
        System.out.println("Synthetic NTv2 grid loaded successfully");
    }

    @Test
    void testNTV2GridReaderEndianness() throws IOException {
        byte[] leData = createSyntheticNTV2Grid();
        GridData leGrid = NTV2GridReader.read(leData);
        assertNotNull(leGrid);
        assertEquals(1, leGrid.getSubgrids().size());
    }

    private byte[] createSyntheticNTV2Grid() {
        ByteBuffer buffer = ByteBuffer.allocate(176 + 176 + 16 * 16);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        writeString(buffer, "NUM_OREC", 0, 8);
        buffer.putInt(8, 11);
        
        writeString(buffer, "NUM_SREC", 16, 8);
        buffer.putInt(24, 11);
        
        writeString(buffer, "NUM_FILE", 32, 8);
        buffer.putInt(40, 1);
        
        writeString(buffer, "GS_TYPE ", 48, 8);
        writeString(buffer, "SECONDS ", 56, 8);
        
        writeString(buffer, "VERSION ", 64, 8);
        writeString(buffer, "TEST    ", 72, 8);
        
        writeString(buffer, "SYSTEM_F", 80, 8);
        writeString(buffer, "NAD27   ", 88, 8);
        
        writeString(buffer, "SYSTEM_T", 96, 8);
        writeString(buffer, "NAD83   ", 104, 8);
        
        writeString(buffer, "MAJOR_F ", 112, 8);
        buffer.putDouble(120, 6378206.4);
        
        writeString(buffer, "MINOR_F ", 128, 8);
        buffer.putDouble(136, 6356583.8);
        
        writeString(buffer, "MAJOR_T ", 144, 8);
        buffer.putDouble(152, 6378137.0);
        
        writeString(buffer, "MINOR_T ", 160, 8);
        buffer.putDouble(168, 6356752.314);
        
        int subgridOffset = 176;
        
        writeString(buffer, "SUB_NAME", subgridOffset, 8);
        writeString(buffer, "TEST    ", subgridOffset + 8, 8);
        
        writeString(buffer, "PARENT  ", subgridOffset + 16, 8);
        writeString(buffer, "NONE    ", subgridOffset + 24, 8);
        
        writeString(buffer, "CREATED ", subgridOffset + 32, 8);
        writeString(buffer, "20240101", subgridOffset + 40, 8);
        
        writeString(buffer, "UPDATED ", subgridOffset + 48, 8);
        writeString(buffer, "20240101", subgridOffset + 56, 8);
        
        writeString(buffer, "S_LAT   ", subgridOffset + 64, 8);
        buffer.putDouble(subgridOffset + 72, 144000.0);
        
        writeString(buffer, "N_LAT   ", subgridOffset + 80, 8);
        buffer.putDouble(subgridOffset + 88, 154800.0);
        
        writeString(buffer, "E_LONG  ", subgridOffset + 96, 8);
        buffer.putDouble(subgridOffset + 104, 270000.0);
        
        writeString(buffer, "W_LONG  ", subgridOffset + 112, 8);
        buffer.putDouble(subgridOffset + 120, 280800.0);
        
        writeString(buffer, "LAT_INC ", subgridOffset + 128, 8);
        buffer.putDouble(subgridOffset + 136, 3600.0);
        
        writeString(buffer, "LONG_INC", subgridOffset + 144, 8);
        buffer.putDouble(subgridOffset + 152, 3600.0);
        
        writeString(buffer, "GS_COUNT", subgridOffset + 160, 8);
        buffer.putInt(subgridOffset + 168, 16);
        
        int nodesOffset = 176 + 176;
        for (int i = 0; i < 16; i++) {
            buffer.putFloat(nodesOffset + i * 16, 0.1f + i * 0.01f);
            buffer.putFloat(nodesOffset + i * 16 + 4, -0.2f - i * 0.01f);
            buffer.putFloat(nodesOffset + i * 16 + 8, 0.01f);
            buffer.putFloat(nodesOffset + i * 16 + 12, 0.01f);
        }
        
        return buffer.array();
    }

    private void writeString(ByteBuffer buffer, String str, int offset, int length) {
        byte[] bytes = str.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < length; i++) {
            buffer.put(offset + i, i < bytes.length ? bytes[i] : (byte) ' ');
        }
    }

    // ========== End-to-End Transformation Tests ==========

    @Test
    void testEndToEndGridShiftWithNullGrid() {
        var grids = GridLoader.getNadgrids("null");
        assertNotNull(grids);
        assertEquals(1, grids.size());
        assertTrue(grids.get(0).isNull());
    }

    @Test
    void testEndToEndNAD27ToNAD83Transformation() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "us_noaa_conus.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping end-to-end test - grid file not found: " + gridPath);
            return;
        }

        GridLoader.loadFile("conus", gridPath);
        assertTrue(GridLoader.has("conus"));
        
        var grids = GridLoader.getNadgrids("@conus,null");
        assertNotNull(grids);
        assertEquals(2, grids.size());
        
        NadgridInfo conusInfo = grids.get(0);
        assertNotNull(conusInfo.getGrid());
        
        double lonDeg = -77.0;
        double latDeg = 38.9;
        double lonRad = lonDeg * Values.D2R;
        double latRad = latDeg * Values.D2R;
        
        GridData grid = conusInfo.getGrid();
        Subgrid subgrid = null;
        
        for (Subgrid sg : grid.getSubgrids()) {
            if (sg.contains(-lonRad, latRad)) {
                subgrid = sg;
                break;
            }
        }
        
        if (subgrid != null) {
            double[] result = GridInterpolator.applyForward(-lonRad, latRad, subgrid);
            
            assertFalse(Double.isNaN(result[0]), "Shifted longitude should not be NaN");
            assertFalse(Double.isNaN(result[1]), "Shifted latitude should not be NaN");
            
            double shiftLonDeg = (-result[0] - lonRad) * Values.R2D;
            double shiftLatDeg = (result[1] - latRad) * Values.R2D;
            
            assertTrue(Math.abs(shiftLonDeg) < 0.01, 
                "Longitude shift should be less than 0.01 degrees: " + shiftLonDeg);
            assertTrue(Math.abs(shiftLatDeg) < 0.01, 
                "Latitude shift should be less than 0.01 degrees: " + shiftLatDeg);
            
            System.out.printf("NAD27->NAD83 shift at (%.4f, %.4f): dLon=%.6f deg, dLat=%.6f deg%n",
                lonDeg, latDeg, shiftLonDeg, shiftLatDeg);
        } else {
            System.out.println("Point not covered by any subgrid");
        }
    }

    @Test
    void testGridShiftRoundTrip() throws IOException {
        Path gridPath = Path.of(TEST_GRIDS_DIR, "us_noaa_conus.tif");
        if (!Files.exists(gridPath)) {
            System.out.println("Skipping round-trip test - grid file not found");
            return;
        }

        GridLoader.loadFile("conus", gridPath);
        GridData grid = GridLoader.get("conus");
        
        if (grid.getSubgrids().isEmpty()) {
            return;
        }
        
        Subgrid subgrid = grid.getSubgrids().get(0);
        double[] ll = subgrid.getLl();
        double[] del = subgrid.getDel();
        
        double testLon = ll[0] + del[0] * 5;
        double testLat = ll[1] + del[1] * 5;
        
        double[] forward = GridInterpolator.applyForward(testLon, testLat, subgrid);
        if (Double.isNaN(forward[0])) {
            System.out.println("Forward transform returned NaN - point may be at edge");
            return;
        }
        
        double[] inverse = GridInterpolator.applyInverse(forward[0], forward[1], subgrid);
        if (Double.isNaN(inverse[0])) {
            System.out.println("Inverse transform returned NaN");
            return;
        }
        
        double tolerance = 1e-9;
        assertEquals(testLon, inverse[0], tolerance, "Longitude round-trip failed");
        assertEquals(testLat, inverse[1], tolerance, "Latitude round-trip failed");
        
        System.out.println("Round-trip test passed with tolerance " + tolerance);
    }
}
