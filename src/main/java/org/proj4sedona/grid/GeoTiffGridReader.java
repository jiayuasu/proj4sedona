package org.proj4sedona.grid;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for GeoTIFF datum grid files (.tif format).
 * Uses geotiff.java library via reflection to avoid hard dependency.
 */
public final class GeoTiffGridReader {
    private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    private static final double SECONDS_TO_RADIANS = Math.PI / (180.0 * 3600.0);

    private GeoTiffGridReader() {}

    public static GridData readFile(Path path) throws IOException {
        try {
            Class<?> geoTiffClass = Class.forName("io.github.geotiff.GeoTiff");
            Object geoTiff = geoTiffClass.getMethod("fromFile", Path.class).invoke(null, path);
            return readFromGeoTiff(geoTiff, geoTiffClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("geotiff.java library is required for GeoTIFF grid support.", e);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to read GeoTIFF file: " + path, e);
        }
    }

    public static GridData read(byte[] data) throws IOException {
        try {
            Class<?> geoTiffClass = Class.forName("io.github.geotiff.GeoTiff");
            Object geoTiff = geoTiffClass.getMethod("fromArrayBuffer", byte[].class).invoke(null, data);
            return readFromGeoTiff(geoTiff, geoTiffClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("geotiff.java library is required for GeoTIFF grid support.", e);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to read GeoTIFF data", e);
        }
    }

    private static GridData readFromGeoTiff(Object geoTiff, Class<?> geoTiffClass) 
            throws ReflectiveOperationException, IOException {
        List<Subgrid> subgrids = new ArrayList<>();
        int imageCount = (int) geoTiffClass.getMethod("getImageCount").invoke(geoTiff);

        for (int i = imageCount - 1; i >= 0; i--) {
            Object image = geoTiffClass.getMethod("getImage", int.class).invoke(geoTiff, i);
            Class<?> imageClass = image.getClass();

            int width = (int) imageClass.getMethod("getWidth").invoke(image);
            int height = (int) imageClass.getMethod("getHeight").invoke(image);
            double[] bbox = (double[]) imageClass.getMethod("getBoundingBox").invoke(image);

            // Get pixel scale - use loadValue for deferred fields
            Object fileDirectory = imageClass.getMethod("getFileDirectory").invoke(image);
            double[] pixelScale = (double[]) fileDirectory.getClass()
                    .getMethod("loadValue", Object.class).invoke(fileDirectory, "ModelPixelScale");

            Object readResult = imageClass.getMethod("readRasters").invoke(image);
            Class<?> resultClass = readResult.getClass();
            Object[] bands = (Object[]) resultClass.getMethod("getData").invoke(readResult);

            float[] latOffsets = toFloatArray(bands[0]);
            float[] lonOffsets = toFloatArray(bands[1]);

            double[] del = new double[]{
                    pixelScale[0] * DEGREES_TO_RADIANS,
                    pixelScale[1] * DEGREES_TO_RADIANS
            };

            double maxX = bbox[0] * DEGREES_TO_RADIANS + (width - 1) * del[0];
            double minY = bbox[3] * DEGREES_TO_RADIANS - (height - 1) * del[1];

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

        if (geoTiff instanceof AutoCloseable) {
            try { ((AutoCloseable) geoTiff).close(); } catch (Exception e) {}
        }

        NADGridHeader header = new NADGridHeader();
        header.setnSubgrids(subgrids.size());
        return new GridData(header, subgrids);
    }

    private static float[] toFloatArray(Object array) {
        if (array instanceof float[]) return (float[]) array;
        if (array instanceof double[]) {
            double[] dArr = (double[]) array;
            float[] fArr = new float[dArr.length];
            for (int i = 0; i < dArr.length; i++) fArr[i] = (float) dArr[i];
            return fArr;
        }
        if (array instanceof int[]) {
            int[] iArr = (int[]) array;
            float[] fArr = new float[iArr.length];
            for (int i = 0; i < iArr.length; i++) fArr[i] = iArr[i];
            return fArr;
        }
        throw new IllegalArgumentException("Unsupported array type: " + array.getClass());
    }

    private static double secondsToRadians(double seconds) {
        return seconds * SECONDS_TO_RADIANS;
    }
}
