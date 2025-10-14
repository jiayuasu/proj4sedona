package org.apache.sedona.proj;

import org.apache.sedona.proj.core.Point;
import org.apache.sedona.proj.core.Projection;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.projjson.ProjJsonDefinition;
import org.apache.sedona.proj.projjson.ProjJsonParser;
import org.apache.sedona.proj.mgrs.Mgrs;
import org.apache.sedona.proj.cache.ProjectionCache;
import org.apache.sedona.proj.optimization.BatchTransformer;
import org.apache.sedona.proj.datum.GeoTiffReader;
import org.apache.sedona.proj.datum.ProjCdnClient;

import java.io.IOException;
import java.util.function.Function;

/**
 * Main entry point for the Proj4Sedona library.
 * This class provides the same API as the JavaScript proj4 library.
 */
public class Proj4Sedona {
    
    /**
     * Represents a coordinate transformation converter.
     */
    public static class Converter {
        private final Projection fromProj;
        private final Projection toProj;
        private final boolean single;
        
        public Converter(Projection fromProj, Projection toProj, boolean single) {
            this.fromProj = fromProj;
            this.toProj = toProj;
            this.single = single;
        }
        
        /**
         * Transforms coordinates from source to destination projection.
         * @param coords the coordinates to transform
         * @param enforceAxis whether to enforce axis order
         * @return transformed coordinates
         */
        public Point forward(Point coords, boolean enforceAxis) {
            return transform(fromProj, toProj, coords, enforceAxis);
        }
        
        /**
         * Transforms coordinates from source to destination projection.
         * @param coords the coordinates to transform
         * @return transformed coordinates
         */
        public Point forward(Point coords) {
            return forward(coords, false);
        }
        
        /**
         * Transforms coordinates from destination to source projection.
         * @param coords the coordinates to transform
         * @param enforceAxis whether to enforce axis order
         * @return transformed coordinates
         */
        public Point inverse(Point coords, boolean enforceAxis) {
            return transform(toProj, fromProj, coords, enforceAxis);
        }
        
        /**
         * Transforms coordinates from destination to source projection.
         * @param coords the coordinates to transform
         * @return transformed coordinates
         */
        public Point inverse(Point coords) {
            return inverse(coords, false);
        }
        
        /**
         * Gets the destination projection (for single projections).
         * @return the destination projection, or null if not a single projection
         */
        public Projection getProjection() {
            return single ? toProj : null;
        }
    }
    
    // Default WGS84 projection
    private static final Projection WGS84 = new Projection("WGS84");
    
    /**
     * Transforms coordinates between two projections.
     * @param fromProj the source projection
     * @param toProj the destination projection
     * @param coords the coordinates to transform
     * @return transformed coordinates
     */
    public static Point transform(String fromProj, String toProj, Point coords) {
        return transform(fromProj, toProj, coords, false);
    }
    
    /**
     * Transforms coordinates between two projections.
     * @param fromProj the source projection
     * @param toProj the destination projection
     * @param coords the coordinates to transform
     * @param enforceAxis whether to enforce axis order
     * @return transformed coordinates
     */
    public static Point transform(String fromProj, String toProj, Point coords, boolean enforceAxis) {
        Projection from = ProjectionCache.getProjection(fromProj);
        Projection to = ProjectionCache.getProjection(toProj);
        return transform(from, to, coords, enforceAxis);
    }
    
    /**
     * Transforms coordinates between two projections.
     * @param fromProj the source projection
     * @param toProj the destination projection
     * @param coords the coordinates to transform
     * @param enforceAxis whether to enforce axis order
     * @return transformed coordinates
     */
    public static Point transform(Projection fromProj, Projection toProj, Point coords, boolean enforceAxis) {
        // Clone the point to avoid modifying the original
        Point point = coords.copy();
        
        // Check for invalid coordinates
        if (Double.isNaN(point.x) || Double.isNaN(point.y)) {
            return new Point(Double.NaN, Double.NaN, point.z, point.m);
        }
        
        // Handle axis order adjustment if needed
        if (enforceAxis && !Values.AXIS_ENU.equals(fromProj.axis)) {
            point = adjustAxis(fromProj, false, point);
        }
        
        // Transform source points to long/lat if they aren't already
        if ("longlat".equals(fromProj.projName)) {
            point = new Point(point.x * Values.D2R, point.y * Values.D2R, point.z, point.m);
        } else {
            if (fromProj.to_meter != 0) {
                point = new Point(point.x * fromProj.to_meter, point.y * fromProj.to_meter, point.z, point.m);
            }
            point = fromProj.inverse.transform(point);
            if (point == null) {
                return null;
            }
        }
        
        // Adjust for prime meridian if necessary
        if (fromProj.from_greenwich != 0) {
            point = new Point(point.x + fromProj.from_greenwich, point.y, point.z, point.m);
        }
        
        // Convert datums if needed, and if possible
        if (fromProj.datum != null && toProj.datum != null) {
            point = org.apache.sedona.proj.datum.DatumTransform.transform(fromProj.datum, toProj.datum, point);
            if (point == null) {
                return null;
            }
        }
        
        // Adjust for prime meridian if necessary
        if (toProj.from_greenwich != 0) {
            point = new Point(point.x - toProj.from_greenwich, point.y, point.z, point.m);
        }
        
        if ("longlat".equals(toProj.projName)) {
            // Convert radians to decimal degrees
            point = new Point(point.x * Values.R2D, point.y * Values.R2D, point.z, point.m);
        } else {
            // Project to destination coordinate system
            point = toProj.forward.transform(point);
            if (toProj.to_meter != 0) {
                point = new Point(point.x / toProj.to_meter, point.y / toProj.to_meter, point.z, point.m);
            }
        }
        
        // Handle axis order adjustment if needed
        if (enforceAxis && !Values.AXIS_ENU.equals(toProj.axis)) {
            point = adjustAxis(toProj, true, point);
        }
        
        // Remove z coordinate if it wasn't in the original
        if (!coords.hasZ()) {
            point = new Point(point.x, point.y, 0, point.m);
        }
        
        return point;
    }
    
    /**
     * Creates a converter between two projections.
     * @param fromProj the source projection
     * @param toProj the destination projection
     * @return a converter object
     */
    public static Converter converter(String fromProj, String toProj) {
        Projection from = new Projection(fromProj);
        Projection to = new Projection(toProj);
        return new Converter(from, to, false);
    }
    
    /**
     * Creates a converter from WGS84 to the specified projection.
     * @param toProj the destination projection
     * @return a converter object
     */
    public static Converter converter(String toProj) {
        Projection to = new Projection(toProj);
        return new Converter(WGS84, to, true);
    }
    
    /**
     * Transforms coordinates from WGS84 to the specified projection.
     * @param toProj the destination projection
     * @param coords the coordinates to transform
     * @return transformed coordinates
     */
    public static Point transform(String toProj, Point coords) {
        return transform(WGS84, new Projection(toProj), coords, false);
    }
    
    /**
     * Transforms coordinates from WGS84 to the specified projection.
     * @param toProj the destination projection
     * @param coords the coordinates to transform
     * @param enforceAxis whether to enforce axis order
     * @return transformed coordinates
     */
    public static Point transform(String toProj, Point coords, boolean enforceAxis) {
        return transform(WGS84, new Projection(toProj), coords, enforceAxis);
    }
    
    /**
     * Adjusts axis order for a point.
     * @param proj the projection
     * @param toGeographic whether converting to geographic coordinates
     * @param point the point to adjust
     * @return adjusted point
     */
    private static Point adjustAxis(Projection proj, boolean toGeographic, Point point) {
        // TODO: Implement axis order adjustment
        // This is a placeholder implementation
        return point;
    }
    
    /**
     * Gets the WGS84 projection.
     * @return the WGS84 projection
     */
    public static Projection getWGS84() {
        return WGS84;
    }
    
    /**
     * Creates a projection from a PROJJSON string.
     * @param projJsonString the PROJJSON string
     * @return a Projection object
     * @throws IOException if parsing fails
     */
    public static Projection fromProjJson(String projJsonString) throws IOException {
        return ProjJsonParser.parse(projJsonString);
    }
    
    /**
     * Creates a projection from a PROJJSON definition object.
     * @param definition the PROJJSON definition
     * @return a Projection object
     */
    public static Projection fromProjJson(ProjJsonDefinition definition) {
        return ProjJsonParser.parse(definition);
    }
    
    /**
     * Converts a PROJJSON definition to a PROJ string.
     * @param definition the PROJJSON definition
     * @return a PROJ string
     */
    public static String toProjString(ProjJsonDefinition definition) {
        return ProjJsonParser.toProjString(definition);
    }
    
    /**
     * Converts a PROJ string to a PROJJSON definition.
     * @param projString the PROJ string
     * @return a PROJJSON definition
     */
    public static ProjJsonDefinition toProjJson(String projString) {
        return ProjJsonParser.fromProjString(projString);
    }
    
    /**
     * Transforms coordinates using PROJJSON definitions.
     * @param fromProjJson the source PROJJSON definition
     * @param toProjJson the destination PROJJSON definition
     * @param coords the coordinates to transform
     * @return transformed coordinates
     */
    public static Point transform(ProjJsonDefinition fromProjJson, ProjJsonDefinition toProjJson, Point coords) {
        Projection from = fromProjJson(fromProjJson);
        Projection to = fromProjJson(toProjJson);
        return transform(from, to, coords, false);
    }
    
    /**
     * Transforms coordinates from WGS84 to a PROJJSON definition.
     * @param toProjJson the destination PROJJSON definition
     * @param coords the coordinates to transform
     * @return transformed coordinates
     */
    public static Point transform(ProjJsonDefinition toProjJson, Point coords) {
        Projection to = fromProjJson(toProjJson);
        return transform(WGS84, to, coords, false);
    }
    
    /**
     * Creates a converter from a PROJJSON definition.
     * @param toProjJson the destination PROJJSON definition
     * @return a Converter object
     */
    public static Converter converter(ProjJsonDefinition toProjJson) {
        Projection to = fromProjJson(toProjJson);
        return new Converter(WGS84, to, true);
    }
    
    /**
     * Creates a converter between two PROJJSON definitions.
     * @param fromProjJson the source PROJJSON definition
     * @param toProjJson the destination PROJJSON definition
     * @return a Converter object
     */
    public static Converter converter(ProjJsonDefinition fromProjJson, ProjJsonDefinition toProjJson) {
        Projection from = fromProjJson(fromProjJson);
        Projection to = fromProjJson(toProjJson);
        return new Converter(from, to, false);
    }
    
    /**
     * Gets the version of the library.
     * @return the version string
     */
    public static String getVersion() {
        return "1.0.0-SNAPSHOT";
    }
    
    // ===== Performance Optimizations =====
    
    /**
     * Creates a batch transformer for efficient processing of multiple points.
     * @param fromProj the source projection string
     * @param toProj the destination projection string
     * @return a BatchTransformer instance
     */
    public static BatchTransformer createBatchTransformer(String fromProj, String toProj) {
        return new BatchTransformer(fromProj, toProj, false);
    }
    
    /**
     * Creates a batch transformer for efficient processing of multiple points.
     * @param fromProj the source projection string
     * @param toProj the destination projection string
     * @param enforceAxis whether to enforce axis order
     * @return a BatchTransformer instance
     */
    public static BatchTransformer createBatchTransformer(String fromProj, String toProj, boolean enforceAxis) {
        return new BatchTransformer(fromProj, toProj, enforceAxis);
    }
    
    /**
     * Creates a batch transformer for efficient processing of multiple points.
     * @param fromProj the source projection
     * @param toProj the destination projection
     * @param enforceAxis whether to enforce axis order
     * @return a BatchTransformer instance
     */
    public static BatchTransformer createBatchTransformer(Projection fromProj, Projection toProj, boolean enforceAxis) {
        return new BatchTransformer(fromProj, toProj, enforceAxis);
    }
    
    /**
     * Transforms multiple points in batch for better performance.
     * @param fromProj the source projection string
     * @param toProj the destination projection string
     * @param points the points to transform
     * @return list of transformed points
     */
    public static java.util.List<Point> transformBatch(String fromProj, String toProj, java.util.List<Point> points) {
        BatchTransformer transformer = createBatchTransformer(fromProj, toProj);
        return transformer.transformBatch(points);
    }
    
    /**
     * Transforms arrays of coordinates in batch for better performance.
     * @param fromProj the source projection string
     * @param toProj the destination projection string
     * @param xCoords array of x coordinates
     * @param yCoords array of y coordinates
     * @return array of transformed points
     */
    public static Point[] transformArrays(String fromProj, String toProj, double[] xCoords, double[] yCoords) {
        BatchTransformer transformer = createBatchTransformer(fromProj, toProj);
        return transformer.transformArrays(xCoords, yCoords);
    }
    
    /**
     * Clears the projection cache to free memory.
     */
    public static void clearProjectionCache() {
        ProjectionCache.clearCache();
    }
    
    /**
     * Gets the current projection cache size.
     * @return the number of cached projections
     */
    public static int getProjectionCacheSize() {
        return ProjectionCache.getCacheSize();
    }
    
    /**
     * Utility method to create a point from coordinates.
     * @param x the x coordinate
     * @param y the y coordinate
     * @return a new Point
     */
    public static Point toPoint(double x, double y) {
        return new Point(x, y);
    }
    
    /**
     * Utility method to create a point from coordinates.
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return a new Point
     */
    public static Point toPoint(double x, double y, double z) {
        return new Point(x, y, z);
    }
    
    /**
     * Utility method to create a point from an array.
     * @param coords array of coordinates
     * @return a new Point
     */
    public static Point toPoint(double[] coords) {
        return Point.fromArray(coords);
    }
    
    // ===== MGRS Support =====
    
    /**
     * Convert lat/lon to MGRS.
     * @param lon longitude in degrees
     * @param lat latitude in degrees
     * @param accuracy accuracy in digits (5 for 1 m, 4 for 10 m, 3 for 100 m, 2 for 1 km, 1 for 10 km or 0 for 100 km)
     * @return the MGRS string for the given location and accuracy
     */
    public static String mgrsForward(double lon, double lat, int accuracy) {
        return Mgrs.forward(lon, lat, accuracy);
    }
    
    /**
     * Convert lat/lon to MGRS with default accuracy (5 digits = 1 meter).
     * @param lon longitude in degrees
     * @param lat latitude in degrees
     * @return the MGRS string for the given location
     */
    public static String mgrsForward(double lon, double lat) {
        return Mgrs.forward(lon, lat);
    }
    
    /**
     * Convert MGRS to lat/lon bounding box.
     * @param mgrs MGRS string
     * @return array with [left, bottom, right, top] values in WGS84 degrees
     */
    public static double[] mgrsInverse(String mgrs) {
        return Mgrs.inverse(mgrs);
    }
    
    /**
     * Convert MGRS to lat/lon point (center of bounding box).
     * @param mgrs MGRS string
     * @return array with [longitude, latitude] in degrees
     */
    public static double[] mgrsToPoint(String mgrs) {
        return Mgrs.toPoint(mgrs);
    }
    
    /**
     * Create a Point from MGRS string.
     * @param mgrs MGRS string
     * @return Point with longitude and latitude in degrees
     */
    public static Point fromMGRS(String mgrs) {
        double[] coords = Mgrs.toPoint(mgrs);
        return new Point(coords[0], coords[1]);
    }
    
    // ===== GeoTIFF Datum Grid Support =====
    
    /**
     * Loads a GeoTIFF datum grid from a URL (e.g., from PROJ CDN).
     * This is equivalent to proj4js nadgrid() function for GeoTIFF files.
     * @param key the key to associate with the loaded grid
     * @param url the URL of the GeoTIFF file
     * @return the loaded GeoTIFF grid
     * @throws IOException if reading fails
     */
    public static GeoTiffReader.GeoTiffGrid nadgrid(String key, String url) throws IOException {
        return GeoTiffReader.loadFromUrl(key, url);
    }
    
    /**
     * Loads a GeoTIFF datum grid from an input stream.
     * @param key the key to associate with the loaded grid
     * @param inputStream the input stream containing the GeoTIFF data
     * @return the loaded GeoTIFF grid
     * @throws IOException if reading fails
     */
    public static GeoTiffReader.GeoTiffGrid nadgrid(String key, java.io.InputStream inputStream) throws IOException {
        return GeoTiffReader.loadFromStream(key, inputStream);
    }
    
    /**
     * Registers a GeoTIFF datum grid for use in transformations.
     * @param key the grid name/key
     * @param grid the GeoTIFF grid data
     */
    public static void registerNadgrid(String key, GeoTiffReader.GeoTiffGrid grid) {
        GeoTiffReader.registerGrid(key, grid);
    }
    
    /**
     * Gets a registered GeoTIFF datum grid.
     * @param key the grid name/key
     * @return the GeoTIFF grid, or null if not found
     */
    public static GeoTiffReader.GeoTiffGrid getNadgrid(String key) {
        return GeoTiffReader.getGrid(key);
    }
    
    /**
     * Checks if a GeoTIFF datum grid is registered.
     * @param key the grid name/key
     * @return true if the grid is registered
     */
    public static boolean hasNadgrid(String key) {
        return GeoTiffReader.hasGrid(key);
    }
    
    /**
     * Removes a GeoTIFF datum grid from the registry.
     * @param key the grid name/key
     * @return the removed GeoTIFF grid, or null if not found
     */
    public static GeoTiffReader.GeoTiffGrid removeNadgrid(String key) {
        return GeoTiffReader.removeGrid(key);
    }
    
    /**
     * Gets all registered GeoTIFF datum grid names.
     * @return array of grid names
     */
    public static String[] getNadgridNames() {
        return GeoTiffReader.getGridNames();
    }
    
    /**
     * Gets the number of registered GeoTIFF datum grids.
     * @return the number of registered grids
     */
    public static int getNadgridCount() {
        return GeoTiffReader.getGridCount();
    }
    
    /**
     * Clears all registered GeoTIFF datum grids.
     */
    public static void clearNadgrids() {
        GeoTiffReader.clearGrids();
    }
    
    // ===== PROJ CDN Support =====
    
    /**
     * Downloads and loads a datum grid from the PROJ CDN.
     * This is a convenience method that downloads grids from https://cdn.proj.org/
     * @param gridName the name of the grid file (e.g., "ca_nrc_NA83SCRS.tif")
     * @return the loaded GeoTIFF grid
     * @throws IOException if downloading or parsing fails
     */
    public static GeoTiffReader.GeoTiffGrid downloadGrid(String gridName) throws IOException {
        return ProjCdnClient.downloadGrid(gridName);
    }
    
    /**
     * Downloads and loads a datum grid from the PROJ CDN with a custom key.
     * @param key the key to associate with the loaded grid
     * @param gridName the name of the grid file (e.g., "ca_nrc_NA83SCRS.tif")
     * @return the loaded GeoTIFF grid
     * @throws IOException if downloading or parsing fails
     */
    public static GeoTiffReader.GeoTiffGrid downloadGrid(String key, String gridName) throws IOException {
        return ProjCdnClient.downloadGrid(key, gridName);
    }
    
    /**
     * Downloads and loads a datum grid from a custom CDN URL.
     * @param key the key to associate with the loaded grid
     * @param url the full URL to the grid file
     * @return the loaded GeoTIFF grid
     * @throws IOException if downloading or parsing fails
     */
    public static GeoTiffReader.GeoTiffGrid downloadGridFromUrl(String key, String url) throws IOException {
        return ProjCdnClient.downloadGridFromUrl(key, url);
    }
    
    /**
     * Checks if a grid is available in the CDN cache.
     * @param key the grid key
     * @return true if the grid is cached
     */
    public static boolean isGridCached(String key) {
        return ProjCdnClient.isGridCached(key);
    }
    
    /**
     * Removes a grid from the CDN cache.
     * @param key the grid key
     * @return the removed grid, or null if not found
     */
    public static GeoTiffReader.GeoTiffGrid removeFromCache(String key) {
        return ProjCdnClient.removeFromCache(key);
    }
    
    /**
     * Clears the CDN grid cache.
     */
    public static void clearGridCache() {
        ProjCdnClient.clearCache();
    }
    
    /**
     * Gets the number of grids in the CDN cache.
     * @return the cache size
     */
    public static int getGridCacheSize() {
        return ProjCdnClient.getCacheSize();
    }
    
    /**
     * Gets all cached grid keys from the CDN cache.
     * @return array of cached grid keys
     */
    public static String[] getCachedGridKeys() {
        return ProjCdnClient.getCachedGridKeys();
    }
}
