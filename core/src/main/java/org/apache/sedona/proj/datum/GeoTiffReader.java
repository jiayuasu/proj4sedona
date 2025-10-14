package org.apache.sedona.proj.datum;

import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.constants.Values;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight GeoTIFF reader for PROJ datum grids.
 * This implementation uses Java's built-in javax.imageio package and custom
 * GeoTIFF metadata parsing to avoid heavy dependencies.
 */
public class GeoTiffReader {
    
    // Registry of loaded GeoTIFF grids
    private static final Map<String, GeoTiffGrid> GRID_REGISTRY = new ConcurrentHashMap<>();
    
    /**
     * Represents a GeoTIFF datum grid with subgrids.
     */
    public static class GeoTiffGrid {
        public final String name;
        public final GeoTiffSubgrid[] subgrids;
        
        public GeoTiffGrid(String name, GeoTiffSubgrid[] subgrids) {
            this.name = name;
            this.subgrids = subgrids;
        }
    }
    
    /**
     * Represents a subgrid within a GeoTIFF datum grid.
     */
    public static class GeoTiffSubgrid {
        public final double minLon, maxLon, minLat, maxLat;
        public final double lonStep, latStep;
        public final int width, height;
        public final double[] latShifts;  // latitude shifts in radians
        public final double[] lonShifts;  // longitude shifts in radians
        
        public GeoTiffSubgrid(double minLon, double maxLon, double minLat, double maxLat,
                             double lonStep, double latStep, int width, int height,
                             double[] latShifts, double[] lonShifts) {
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.lonStep = lonStep;
            this.latStep = latStep;
            this.width = width;
            this.height = height;
            this.latShifts = latShifts;
            this.lonShifts = lonShifts;
        }
    }
    
    /**
     * Loads a GeoTIFF datum grid from a URL (e.g., from PROJ CDN).
     * @param key the key to associate with the loaded grid
     * @param url the URL of the GeoTIFF file
     * @return the loaded GeoTIFF grid
     * @throws IOException if reading fails
     */
    public static GeoTiffGrid loadFromUrl(String key, String url) throws IOException {
        try (InputStream inputStream = new URL(url).openStream()) {
            return loadFromStream(key, inputStream);
        }
    }
    
    /**
     * Loads a GeoTIFF datum grid from an input stream.
     * @param key the key to associate with the loaded grid
     * @param inputStream the input stream containing the GeoTIFF data
     * @return the loaded GeoTIFF grid
     * @throws IOException if reading fails
     */
    public static GeoTiffGrid loadFromStream(String key, InputStream inputStream) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IOException("No TIFF reader found");
            }
            
            ImageReader reader = readers.next();
            reader.setInput(imageInputStream);
            
            int imageCount = reader.getNumImages(true);
            GeoTiffSubgrid[] subgrids = new GeoTiffSubgrid[imageCount];
            
            // PROJ GeoTIFF files organize lower resolution subgrids first, higher resolution last
            // We process them in reverse order to match proj4js behavior
            for (int i = imageCount - 1; i >= 0; i--) {
                subgrids[imageCount - 1 - i] = readSubgrid(reader, i);
            }
            
            GeoTiffGrid grid = new GeoTiffGrid(key, subgrids);
            GRID_REGISTRY.put(key, grid);
            return grid;
        }
    }
    
    /**
     * Reads a single subgrid from the GeoTIFF.
     */
    private static GeoTiffSubgrid readSubgrid(ImageReader reader, int imageIndex) throws IOException {
        // Read the image data
        BufferedImage image = reader.read(imageIndex);
        Raster raster = image.getRaster();
        
        int width = raster.getWidth();
        int height = raster.getHeight();
        
        // Read GeoTIFF metadata
        IIOMetadata metadata = reader.getImageMetadata(imageIndex);
        GeoTiffMetadata geoMetadata = parseGeoTiffMetadata(metadata);
        
        // Extract shift data from raster bands
        // PROJ GeoTIFF files typically have:
        // Band 0: Latitude shifts (in arcseconds)
        // Band 1: Longitude shifts (in arcseconds)
        double[] latShifts = new double[width * height];
        double[] lonShifts = new double[width * height];
        
        // Read latitude shifts (band 0)
        float[] latData = new float[width * height];
        raster.getSamples(0, 0, width, height, 0, latData);
        
        // Read longitude shifts (band 1)
        float[] lonData = new float[width * height];
        raster.getSamples(0, 0, width, height, 1, lonData);
        
        // Convert from arcseconds to radians and flip Y axis to match proj4js
        for (int row = height - 1; row >= 0; row--) {
            for (int col = 0; col < width; col++) {
                int index = (height - 1 - row) * width + col;
                int sourceIndex = row * width + col;
                
                // Convert arcseconds to radians
                latShifts[index] = latData[sourceIndex] * Values.ARCSEC_TO_RAD;
                lonShifts[index] = -lonData[sourceIndex] * Values.ARCSEC_TO_RAD; // Negative for longitude
            }
        }
        
        return new GeoTiffSubgrid(
            geoMetadata.minLon, geoMetadata.maxLon,
            geoMetadata.minLat, geoMetadata.maxLat,
            geoMetadata.lonStep, geoMetadata.latStep,
            width, height,
            latShifts, lonShifts
        );
    }
    
    /**
     * Parses GeoTIFF metadata from IIOMetadata.
     */
    private static GeoTiffMetadata parseGeoTiffMetadata(IIOMetadata metadata) throws IOException {
        GeoTiffMetadata geoMetadata = new GeoTiffMetadata();
        
        // Parse TIFF tags to extract geospatial information
        // TODO: Implement full GeoTIFF metadata parsing
        // IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
        
        // Look for GeoTIFF tags
        // This is a simplified parser - in a full implementation, you'd parse all GeoTIFF keys
        geoMetadata.minLon = -180.0; // Default values
        geoMetadata.maxLon = 180.0;
        geoMetadata.minLat = -90.0;
        geoMetadata.maxLat = 90.0;
        geoMetadata.lonStep = 1.0;
        geoMetadata.latStep = 1.0;
        
        // TODO: Implement full GeoTIFF metadata parsing
        // This would involve parsing ModelTiepointTag, ModelPixelScaleTag, etc.
        
        return geoMetadata;
    }
    
    /**
     * Helper class for GeoTIFF metadata.
     */
    private static class GeoTiffMetadata {
        double minLon, maxLon, minLat, maxLat;
        double lonStep, latStep;
    }
    
    /**
     * Interpolates grid shift values for a given point.
     * @param grid the GeoTIFF grid
     * @param lat the latitude in radians
     * @param lon the longitude in radians
     * @return array with [latShift, lonShift] in radians, or null if outside grid
     */
    public static double[] interpolateGrid(GeoTiffGrid grid, double lat, double lon) {
        if (grid == null || grid.subgrids == null) {
            return null;
        }
        
        // Convert to degrees for grid lookup
        double latDeg = lat * Values.R2D;
        double lonDeg = lon * Values.R2D;
        
        // Find the appropriate subgrid
        for (GeoTiffSubgrid subgrid : grid.subgrids) {
            if (latDeg >= subgrid.minLat && latDeg <= subgrid.maxLat &&
                lonDeg >= subgrid.minLon && lonDeg <= subgrid.maxLon) {
                
                return interpolateSubgrid(subgrid, latDeg, lonDeg);
            }
        }
        
        return null; // Point outside all subgrids
    }
    
    /**
     * Interpolates shift values within a subgrid using bilinear interpolation.
     */
    private static double[] interpolateSubgrid(GeoTiffSubgrid subgrid, double latDeg, double lonDeg) {
        // Calculate grid indices
        double latIndex = (latDeg - subgrid.minLat) / subgrid.latStep;
        double lonIndex = (lonDeg - subgrid.minLon) / subgrid.lonStep;
        
        int lat0 = (int) Math.floor(latIndex);
        int lon0 = (int) Math.floor(lonIndex);
        
        // Check bounds - need to ensure we can access lat0+1 and lon0+1
        if (lat0 < 0 || lat0 >= subgrid.height - 1 || lon0 < 0 || lon0 >= subgrid.width - 1) {
            return null;
        }
        
        int lat1 = lat0 + 1;
        int lon1 = lon0 + 1;
        
        // Get fractional parts
        double latFrac = latIndex - lat0;
        double lonFrac = lonIndex - lon0;
        
        // Get shift values at the four corners
        int idx00 = lat0 * subgrid.width + lon0;
        int idx01 = lat0 * subgrid.width + lon1;
        int idx10 = lat1 * subgrid.width + lon0;
        int idx11 = lat1 * subgrid.width + lon1;
        
        double latShift00 = subgrid.latShifts[idx00];
        double latShift01 = subgrid.latShifts[idx01];
        double latShift10 = subgrid.latShifts[idx10];
        double latShift11 = subgrid.latShifts[idx11];
        
        double lonShift00 = subgrid.lonShifts[idx00];
        double lonShift01 = subgrid.lonShifts[idx01];
        double lonShift10 = subgrid.lonShifts[idx10];
        double lonShift11 = subgrid.lonShifts[idx11];
        
        // Bilinear interpolation
        double latShift = (1 - latFrac) * (1 - lonFrac) * latShift00 +
                         (1 - latFrac) * lonFrac * latShift01 +
                         latFrac * (1 - lonFrac) * latShift10 +
                         latFrac * lonFrac * latShift11;
        
        double lonShift = (1 - latFrac) * (1 - lonFrac) * lonShift00 +
                         (1 - latFrac) * lonFrac * lonShift01 +
                         latFrac * (1 - lonFrac) * lonShift10 +
                         latFrac * lonFrac * lonShift11;
        
        return new double[]{latShift, lonShift};
    }
    
    /**
     * Registers a GeoTIFF grid for use in transformations.
     * @param key the grid name/key
     * @param grid the GeoTIFF grid data
     */
    public static void registerGrid(String key, GeoTiffGrid grid) {
        if (key != null && grid != null) {
            GRID_REGISTRY.put(key, grid);
        }
    }
    
    /**
     * Gets a registered GeoTIFF grid.
     * @param key the grid name/key
     * @return the GeoTIFF grid, or null if not found
     */
    public static GeoTiffGrid getGrid(String key) {
        if (key == null) {
            return null;
        }
        return GRID_REGISTRY.get(key);
    }
    
    /**
     * Checks if a GeoTIFF grid is registered.
     * @param key the grid name/key
     * @return true if the grid is registered
     */
    public static boolean hasGrid(String key) {
        if (key == null) {
            return false;
        }
        return GRID_REGISTRY.containsKey(key);
    }
    
    /**
     * Removes a GeoTIFF grid from the registry.
     * @param key the grid name/key
     * @return the removed GeoTIFF grid, or null if not found
     */
    public static GeoTiffGrid removeGrid(String key) {
        if (key == null) {
            return null;
        }
        return GRID_REGISTRY.remove(key);
    }
    
    /**
     * Gets all registered GeoTIFF grid names.
     * @return array of grid names
     */
    public static String[] getGridNames() {
        return GRID_REGISTRY.keySet().toArray(new String[0]);
    }
    
    /**
     * Gets the number of registered GeoTIFF grids.
     * @return the number of registered grids
     */
    public static int getGridCount() {
        return GRID_REGISTRY.size();
    }
    
    /**
     * Clears all registered GeoTIFF grids.
     */
    public static void clearGrids() {
        GRID_REGISTRY.clear();
    }
}
