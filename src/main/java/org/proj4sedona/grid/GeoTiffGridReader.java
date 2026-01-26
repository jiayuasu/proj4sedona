package org.proj4sedona.grid;

import org.datasyslab.geotiff.GeoTiff;
import org.datasyslab.geotiff.GeoTiffImage;
import org.datasyslab.geotiff.ReadRasterResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for GeoTIFF datum grid files (.tif format).
 * Mirrors: lib/nadgrid.js readGeotiffGrid function
 * 
 * Uses geotiff.java library (org.datasyslab:geotiff) for GeoTIFF parsing.
 * Grid files can be obtained from https://cdn.proj.org/
 */
public final class GeoTiffGridReader {
    
    /** Conversion factor from degrees to radians */
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    
    /** Conversion factor from arc-seconds to radians */
    private static final double SECONDS_TO_RADIANS = Math.PI / (180.0 * 3600.0);

    private GeoTiffGridReader() {
        // Utility class
    }

    /**
     * Read a GeoTIFF grid from a file path.
     * 
     * @param path Path to the GeoTIFF file
     * @return The parsed GridData
     * @throws IOException if the file cannot be read or parsed
     */
    public static GridData readFile(Path path) throws IOException {
        GeoTiff geoTiff = GeoTiff.fromFile(path);
        return readFromGeoTiff(geoTiff);
    }

    /**
     * Read a GeoTIFF grid from a byte array.
     * 
     * @param data The GeoTIFF file data
     * @return The parsed GridData
     * @throws IOException if the data cannot be parsed
     */
    public static GridData read(byte[] data) throws IOException {
        GeoTiff geoTiff = GeoTiff.fromArrayBuffer(data);
        return readFromGeoTiff(geoTiff);
    }

    /**
     * Extract grid data from a parsed GeoTIFF object.
     * 
     * The GeoTIFF is expected to contain datum shift grids with:
     * - Band 0: Latitude offsets (in arc-seconds)
     * - Band 1: Longitude offsets (in arc-seconds)
     * 
     * @param geoTiff The parsed GeoTIFF object
     * @return The extracted GridData
     * @throws IOException if the grid data cannot be extracted
     */
    private static GridData readFromGeoTiff(GeoTiff geoTiff) throws IOException {
        List<Subgrid> subgrids = new ArrayList<>();
        int imageCount = geoTiff.getImageCount();

        // Process images in reverse order (like proj4js)
        for (int i = imageCount - 1; i >= 0; i--) {
            GeoTiffImage image = geoTiff.getImage(i);
            
            int width = image.getWidth();
            int height = image.getHeight();
            double[] bbox = image.getBoundingBox();

            // Get pixel scale from file directory
            double[] pixelScale = (double[]) image.getFileDirectory().loadValue("ModelPixelScale");

            // Read raster data (band 0 = lat offsets, band 1 = lon offsets)
            ReadRasterResult readResult = image.readRasters();

            float[] latOffsets = toFloatArray(readResult.getSample(0));
            float[] lonOffsets = toFloatArray(readResult.getSample(1));

            // Calculate grid spacing in radians
            double[] del = new double[]{
                pixelScale[0] * DEGREES_TO_RADIANS,
                pixelScale[1] * DEGREES_TO_RADIANS
            };

            // Calculate grid extent
            double maxX = bbox[0] * DEGREES_TO_RADIANS + (width - 1) * del[0];
            double minY = bbox[3] * DEGREES_TO_RADIANS - (height - 1) * del[1];

            // Build coordinate shift values array
            // Note: Grid is stored with lower-left origin, processed from top-right
            double[][] cvs = new double[width * height][];
            int nodeIndex = 0;
            for (int row = height - 1; row >= 0; row--) {
                for (int col = width - 1; col >= 0; col--) {
                    int srcIndex = row * width + col;
                    cvs[nodeIndex++] = new double[]{
                        -secondsToRadians(lonOffsets[srcIndex]),
                        secondsToRadians(latOffsets[srcIndex])
                    };
                }
            }

            int[] lim = new int[]{width, height};
            double[] ll = new double[]{-maxX, minY};
            subgrids.add(new Subgrid(ll, del, lim, width * height, cvs));
        }

        // Clean up resources
        geoTiff.close();

        NADGridHeader header = new NADGridHeader();
        header.setnSubgrids(subgrids.size());
        return new GridData(header, subgrids);
    }

    /**
     * Convert various array types to float array.
     * 
     * @param array The source array (float[], double[], or int[])
     * @return A float array with the same values
     */
    private static float[] toFloatArray(Object array) {
        if (array instanceof float[]) {
            return (float[]) array;
        }
        if (array instanceof double[]) {
            double[] dArr = (double[]) array;
            float[] fArr = new float[dArr.length];
            for (int i = 0; i < dArr.length; i++) {
                fArr[i] = (float) dArr[i];
            }
            return fArr;
        }
        if (array instanceof int[]) {
            int[] iArr = (int[]) array;
            float[] fArr = new float[iArr.length];
            for (int i = 0; i < iArr.length; i++) {
                fArr[i] = iArr[i];
            }
            return fArr;
        }
        throw new IllegalArgumentException("Unsupported array type: " + array.getClass());
    }

    /**
     * Convert arc-seconds to radians.
     * 
     * @param seconds Value in arc-seconds
     * @return Value in radians
     */
    private static double secondsToRadians(double seconds) {
        return seconds * SECONDS_TO_RADIANS;
    }
}
