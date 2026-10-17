package org.datasyslab.proj4sedona.jts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.Converter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

/**
 * A coordinate that the core transform cannot map (it returns null, as proj4js returns
 * undefined) must fail the JTS transform with an exception instead of producing a geometry
 * with NaN coordinates. The coordinate-array and batch APIs keep the proj4js NaN result.
 *
 * <p>The failing cases are real null-producing inputs: a point outside a loaded mandatory
 * grid (no listed grid contains it, so the grid shift fails), and a pole in Mercator (the
 * forward projection is undefined there).</p>
 */
class JTSTransformFailureTest {

    private static final String CONUS_GRID =
        Path.of("src/test/resources/grids/us_noaa_conus.tif").toAbsolutePath().toString();
    /** Clarke 1866 longlat whose datum shift requires the NADCON CONUS grid. */
    private static final String NAD27_CONUS =
        "+proj=longlat +ellps=clrk66 +nadgrids=" + CONUS_GRID + " +no_defs";

    /** Inside the CONUS grid (Texas). */
    private static final Coordinate TEXAS = new Coordinate(-98.0, 27.0);
    /** Outside the CONUS grid (Paris). */
    private static final Coordinate PARIS = new Coordinate(2.35, 48.85);

    private GeometryFactory gf;

    @BeforeEach
    void setup() {
        gf = new GeometryFactory();
    }

    @Test
    @DisplayName("A point outside every loaded grid throws and names the point and both CRSs")
    void pointOutsideLoadedGridThrows() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(NAD27_CONUS, "EPSG:4326");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> transformer.transform(gf.createPoint(PARIS)));

        String message = e.getMessage();
        assertTrue(message.contains("(2.35, 48.85)"), message);
        assertTrue(message.contains(NAD27_CONUS), message);
        assertTrue(message.contains("EPSG:4326"), message);
    }

    @Test
    @DisplayName("A Mercator pole throws instead of returning NaN")
    void mercatorPoleThrows() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer("EPSG:4326", "EPSG:3857");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> transformer.transform(gf.createPoint(new Coordinate(0.0, 90.0))));
        assertTrue(e.getMessage().contains("(0.0, 90.0)"), e.getMessage());
    }

    @Test
    @DisplayName("transformCoordinate throws for a failing coordinate")
    void transformCoordinateThrows() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(NAD27_CONUS, "EPSG:4326");
        assertThrows(IllegalArgumentException.class, () -> transformer.transformCoordinate(PARIS));
    }

    @Test
    @DisplayName("A point the transform can map is unchanged")
    void successfulTransformUnchanged() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(NAD27_CONUS, "EPSG:4326");

        Point result = (Point) transformer.transform(gf.createPoint(TEXAS));

        org.datasyslab.proj4sedona.core.Point expected = Proj4.transform(
            new Proj(NAD27_CONUS), new Proj("EPSG:4326"),
            new org.datasyslab.proj4sedona.core.Point(TEXAS.x, TEXAS.y));
        assertNotNull(expected);
        assertEquals(expected.x, result.getX(), 0.0);
        assertEquals(expected.y, result.getY(), 0.0);
        // NADCON shift from NAD27 at (-98, 27), as PROJ's us_noaa_conus.tif gives it
        assertEquals(-98.000279324, result.getX(), 1e-9);
        assertEquals(27.000329556, result.getY(), 1e-9);
    }

    @Test
    @DisplayName("Empty geometries still transform when the CRS pair has failing points")
    void emptyGeometriesStillWork() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(NAD27_CONUS, "EPSG:4326");

        assertTrue(transformer.transform(gf.createPoint()).isEmpty());
        assertTrue(transformer.transform(gf.createLineString()).isEmpty());
        assertTrue(transformer.transform(gf.createPolygon()).isEmpty());
        assertTrue(transformer.transform(gf.createGeometryCollection()).isEmpty());
    }

    @Test
    @DisplayName("One failing coordinate fails the whole geometry")
    void oneFailingCoordinateFailsTheGeometry() {
        JTSGeometryTransformer transformer = new JTSGeometryTransformer(NAD27_CONUS, "EPSG:4326");

        LineString line = gf.createLineString(new Coordinate[] {TEXAS, PARIS});
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> transformer.transform(line));
        assertTrue(e.getMessage().contains("(2.35, 48.85)"), e.getMessage());

        Geometry multiPoint = gf.createMultiPointFromCoords(new Coordinate[] {TEXAS, PARIS});
        assertThrows(IllegalArgumentException.class, () -> transformer.transform(multiPoint));
    }

    @Test
    @DisplayName("Coordinate-array and batch APIs keep the proj4js NaN result")
    void arrayApisKeepNaN() {
        Converter converter = Proj4.proj4(NAD27_CONUS, "EPSG:4326");
        double[] failed = converter.forward(new double[] {PARIS.x, PARIS.y});
        assertArrayEquals(new double[] {Double.NaN, Double.NaN}, failed);

        double[][] batch = Proj4.transformBatch(NAD27_CONUS, "EPSG:4326",
            new double[][] {{TEXAS.x, TEXAS.y}, {PARIS.x, PARIS.y}});
        assertEquals(-98.000279324, batch[0][0], 1e-9);
        assertTrue(Double.isNaN(batch[1][0]) && Double.isNaN(batch[1][1]));
    }
}
