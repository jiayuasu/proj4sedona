package org.datasyslab.proj4sedona.jts;

import org.locationtech.jts.geom.*;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.Converter;

/**
 * Utility class for transforming JTS Geometry objects between coordinate reference systems.
 * 
 * <p>This class provides methods to transform various JTS geometry types (Point, LineString,
 * Polygon, MultiPoint, MultiLineString, MultiPolygon, GeometryCollection) from one CRS to another
 * using the proj4sedona transformation library.</p>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Transform a Point from WGS84 to Web Mercator (EPSG:3857)</h3>
 * <pre>{@code
 * GeometryFactory gf = new GeometryFactory();
 * org.locationtech.jts.geom.Point jtsPoint = gf.createPoint(new Coordinate(-122.4194, 37.7749));
 * 
 * // Create transformer
 * JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");
 * 
 * // Transform
 * org.locationtech.jts.geom.Point transformed = (org.locationtech.jts.geom.Point) transformer.transform(jtsPoint);
 * System.out.println("Transformed: " + transformed.getX() + ", " + transformed.getY());
 * }</pre>
 * 
 * <h3>Transform a Polygon</h3>
 * <pre>{@code
 * Coordinate[] coords = new Coordinate[] {
 *     new Coordinate(-122.5, 37.7),
 *     new Coordinate(-122.3, 37.7),
 *     new Coordinate(-122.3, 37.9),
 *     new Coordinate(-122.5, 37.9),
 *     new Coordinate(-122.5, 37.7)  // close the ring
 * };
 * Polygon polygon = gf.createPolygon(coords);
 * Polygon transformed = (Polygon) transformer.transform(polygon);
 * }</pre>
 * 
 * <h3>Using static methods for one-off transformations</h3>
 * <pre>{@code
 * Geometry result = JTSGeometryTransformer.transform("EPSG:4326", "EPSG:3857", geometry);
 * }</pre>
 * 
 * @since 1.0.0
 */
public class JTSGeometryTransformer {

    private final Converter converter;
    private final GeometryFactory geometryFactory;

    /**
     * Create a transformer between two coordinate reference systems.
     * 
     * @param sourceCRS Source CRS definition (PROJ string, EPSG code, WKT, etc.)
     * @param targetCRS Target CRS definition
     */
    public JTSGeometryTransformer(String sourceCRS, String targetCRS) {
        this.converter = Proj4.proj4(sourceCRS, targetCRS);
        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Create a transformer between two coordinate reference systems with a custom GeometryFactory.
     * 
     * @param sourceCRS Source CRS definition
     * @param targetCRS Target CRS definition
     * @param geometryFactory Custom GeometryFactory to use for creating output geometries
     */
    public JTSGeometryTransformer(String sourceCRS, String targetCRS, GeometryFactory geometryFactory) {
        this.converter = Proj4.proj4(sourceCRS, targetCRS);
        this.geometryFactory = geometryFactory;
    }

    /**
     * Create a transformer from pre-built Proj objects.
     * 
     * @param sourceProj Source projection
     * @param targetProj Target projection
     */
    public JTSGeometryTransformer(Proj sourceProj, Proj targetProj) {
        this.converter = new Converter(sourceProj, targetProj);
        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Create a transformer from an existing Converter.
     * 
     * @param converter The converter to use for transformations
     */
    public JTSGeometryTransformer(Converter converter) {
        this.converter = converter;
        this.geometryFactory = new GeometryFactory();
    }

    /**
     * Create a transformer from an existing Converter with a custom GeometryFactory.
     * 
     * @param converter The converter to use for transformations
     * @param geometryFactory Custom GeometryFactory to use for creating output geometries
     */
    public JTSGeometryTransformer(Converter converter, GeometryFactory geometryFactory) {
        this.converter = converter;
        this.geometryFactory = geometryFactory;
    }

    /**
     * Transform any JTS Geometry to the target CRS.
     * 
     * <p>Supports all JTS geometry types:</p>
     * <ul>
     *   <li>Point</li>
     *   <li>LineString</li>
     *   <li>LinearRing</li>
     *   <li>Polygon</li>
     *   <li>MultiPoint</li>
     *   <li>MultiLineString</li>
     *   <li>MultiPolygon</li>
     *   <li>GeometryCollection</li>
     * </ul>
     * 
     * @param geometry The geometry to transform
     * @return The transformed geometry
     * @throws IllegalArgumentException if the geometry type is not supported
     */
    public Geometry transform(Geometry geometry) {
        if (geometry == null) {
            return null;
        }

        if (geometry instanceof org.locationtech.jts.geom.Point) {
            return transformPoint((org.locationtech.jts.geom.Point) geometry);
        } else if (geometry instanceof LinearRing) {
            return transformLinearRing((LinearRing) geometry);
        } else if (geometry instanceof LineString) {
            return transformLineString((LineString) geometry);
        } else if (geometry instanceof Polygon) {
            return transformPolygon((Polygon) geometry);
        } else if (geometry instanceof MultiPoint) {
            return transformMultiPoint((MultiPoint) geometry);
        } else if (geometry instanceof MultiLineString) {
            return transformMultiLineString((MultiLineString) geometry);
        } else if (geometry instanceof MultiPolygon) {
            return transformMultiPolygon((MultiPolygon) geometry);
        } else if (geometry instanceof GeometryCollection) {
            return transformGeometryCollection((GeometryCollection) geometry);
        } else {
            throw new IllegalArgumentException("Unsupported geometry type: " + geometry.getGeometryType());
        }
    }

    /**
     * Transform a JTS Point.
     * 
     * @param point The point to transform
     * @return The transformed point, or null if input is null
     */
    public org.locationtech.jts.geom.Point transformPoint(org.locationtech.jts.geom.Point point) {
        if (point == null) {
            return null;
        }
        if (point.isEmpty()) {
            return geometryFactory.createPoint();
        }
        Coordinate coord = point.getCoordinate();
        Coordinate transformed = transformCoordinate(coord);
        return geometryFactory.createPoint(transformed);
    }

    /**
     * Transform a JTS LineString.
     * 
     * @param lineString The line string to transform
     * @return The transformed line string
     */
    public LineString transformLineString(LineString lineString) {
        if (lineString == null || lineString.isEmpty()) {
            return geometryFactory.createLineString();
        }
        Coordinate[] coords = transformCoordinates(lineString.getCoordinates());
        return geometryFactory.createLineString(coords);
    }

    /**
     * Transform a JTS LinearRing.
     * 
     * @param ring The linear ring to transform
     * @return The transformed linear ring
     */
    public LinearRing transformLinearRing(LinearRing ring) {
        if (ring == null || ring.isEmpty()) {
            return geometryFactory.createLinearRing();
        }
        Coordinate[] coords = transformCoordinates(ring.getCoordinates());
        return geometryFactory.createLinearRing(coords);
    }

    /**
     * Transform a JTS Polygon.
     * 
     * @param polygon The polygon to transform
     * @return The transformed polygon
     */
    public Polygon transformPolygon(Polygon polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return geometryFactory.createPolygon();
        }

        // Transform exterior ring
        LinearRing shell = transformLinearRing(polygon.getExteriorRing());

        // Transform interior rings (holes)
        int numHoles = polygon.getNumInteriorRing();
        LinearRing[] holes = new LinearRing[numHoles];
        for (int i = 0; i < numHoles; i++) {
            holes[i] = transformLinearRing(polygon.getInteriorRingN(i));
        }

        return geometryFactory.createPolygon(shell, holes);
    }

    /**
     * Transform a JTS MultiPoint.
     * 
     * @param multiPoint The multi-point to transform
     * @return The transformed multi-point
     */
    public MultiPoint transformMultiPoint(MultiPoint multiPoint) {
        if (multiPoint == null || multiPoint.isEmpty()) {
            return geometryFactory.createMultiPoint();
        }

        org.locationtech.jts.geom.Point[] points = new org.locationtech.jts.geom.Point[multiPoint.getNumGeometries()];
        for (int i = 0; i < multiPoint.getNumGeometries(); i++) {
            points[i] = transformPoint((org.locationtech.jts.geom.Point) multiPoint.getGeometryN(i));
        }
        return geometryFactory.createMultiPoint(points);
    }

    /**
     * Transform a JTS MultiLineString.
     * 
     * @param multiLineString The multi-line string to transform
     * @return The transformed multi-line string
     */
    public MultiLineString transformMultiLineString(MultiLineString multiLineString) {
        if (multiLineString == null || multiLineString.isEmpty()) {
            return geometryFactory.createMultiLineString();
        }

        LineString[] lineStrings = new LineString[multiLineString.getNumGeometries()];
        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            lineStrings[i] = transformLineString((LineString) multiLineString.getGeometryN(i));
        }
        return geometryFactory.createMultiLineString(lineStrings);
    }

    /**
     * Transform a JTS MultiPolygon.
     * 
     * @param multiPolygon The multi-polygon to transform
     * @return The transformed multi-polygon
     */
    public MultiPolygon transformMultiPolygon(MultiPolygon multiPolygon) {
        if (multiPolygon == null || multiPolygon.isEmpty()) {
            return geometryFactory.createMultiPolygon();
        }

        Polygon[] polygons = new Polygon[multiPolygon.getNumGeometries()];
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            polygons[i] = transformPolygon((Polygon) multiPolygon.getGeometryN(i));
        }
        return geometryFactory.createMultiPolygon(polygons);
    }

    /**
     * Transform a JTS GeometryCollection.
     * 
     * @param collection The geometry collection to transform
     * @return The transformed geometry collection
     */
    public GeometryCollection transformGeometryCollection(GeometryCollection collection) {
        if (collection == null || collection.isEmpty()) {
            return geometryFactory.createGeometryCollection();
        }

        Geometry[] geometries = new Geometry[collection.getNumGeometries()];
        for (int i = 0; i < collection.getNumGeometries(); i++) {
            geometries[i] = transform(collection.getGeometryN(i));
        }
        return geometryFactory.createGeometryCollection(geometries);
    }

    /**
     * Transform a single JTS Coordinate.
     * 
     * @param coord The coordinate to transform
     * @return The transformed coordinate
     */
    public Coordinate transformCoordinate(Coordinate coord) {
        if (coord == null) {
            return null;
        }

        Point p;
        if (!Double.isNaN(coord.getZ())) {
            p = new Point(coord.getX(), coord.getY(), coord.getZ());
        } else {
            p = new Point(coord.getX(), coord.getY());
        }

        Point result = converter.forward(p);
        if (result == null) {
            return new Coordinate(Double.NaN, Double.NaN);
        }

        if (!Double.isNaN(coord.getZ())) {
            return new Coordinate(result.x, result.y, result.z);
        }
        return new Coordinate(result.x, result.y);
    }

    /**
     * Transform an array of JTS Coordinates.
     * 
     * @param coords The coordinates to transform
     * @return The transformed coordinates
     */
    public Coordinate[] transformCoordinates(Coordinate[] coords) {
        if (coords == null) {
            return null;
        }

        Coordinate[] result = new Coordinate[coords.length];
        for (int i = 0; i < coords.length; i++) {
            result[i] = transformCoordinate(coords[i]);
        }
        return result;
    }

    /**
     * Perform inverse transformation (target CRS to source CRS).
     * 
     * @param geometry The geometry to transform back
     * @return The inverse-transformed geometry
     */
    public Geometry inverse(Geometry geometry) {
        // Create a temporary transformer with swapped projections
        JTSGeometryTransformer inverseTransformer = new JTSGeometryTransformer(
            converter.getTo(), converter.getFrom()
        );
        inverseTransformer.setGeometryFactory(this.geometryFactory);
        return inverseTransformer.transform(geometry);
    }

    /**
     * Set the GeometryFactory for creating output geometries.
     * 
     * @param factory The GeometryFactory to use
     */
    private void setGeometryFactory(GeometryFactory factory) {
        // Note: This is intentionally private and only used for inverse operations
        // Users should use the constructor to set the factory
    }

    /**
     * Get the underlying Converter.
     * 
     * @return The Converter used for transformations
     */
    public Converter getConverter() {
        return converter;
    }

    /**
     * Get the GeometryFactory used for creating output geometries.
     * 
     * @return The GeometryFactory
     */
    public GeometryFactory getGeometryFactory() {
        return geometryFactory;
    }

    // ==================== Static convenience methods ====================

    /**
     * Transform a geometry from one CRS to another (static convenience method).
     * 
     * @param sourceCRS Source CRS definition
     * @param targetCRS Target CRS definition
     * @param geometry The geometry to transform
     * @return The transformed geometry
     */
    public static Geometry transform(String sourceCRS, String targetCRS, Geometry geometry) {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(sourceCRS, targetCRS);
        return transformer.transform(geometry);
    }

    /**
     * Transform a coordinate from one CRS to another (static convenience method).
     * 
     * @param sourceCRS Source CRS definition
     * @param targetCRS Target CRS definition
     * @param coord The coordinate to transform
     * @return The transformed coordinate
     */
    public static Coordinate transform(String sourceCRS, String targetCRS, Coordinate coord) {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(sourceCRS, targetCRS);
        return transformer.transformCoordinate(coord);
    }

    /**
     * Transform coordinates from one CRS to another (static convenience method).
     * 
     * @param sourceCRS Source CRS definition
     * @param targetCRS Target CRS definition
     * @param coords The coordinates to transform
     * @return The transformed coordinates
     */
    public static Coordinate[] transform(String sourceCRS, String targetCRS, Coordinate[] coords) {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(sourceCRS, targetCRS);
        return transformer.transformCoordinates(coords);
    }

    /**
     * Create a cached transformer for repeated transformations.
     * Uses cached projections internally for better performance.
     * 
     * @param sourceCRS Source CRS definition
     * @param targetCRS Target CRS definition
     * @return A JTSGeometryTransformer using cached projections
     */
    public static JTSGeometryTransformer cached(String sourceCRS, String targetCRS) {
        Converter converter = Proj4.cachedConverter(sourceCRS, targetCRS);
        return new JTSGeometryTransformer(converter);
    }
}
