package org.datasyslab.proj4sedona;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.datasyslab.proj4sedona.transform.Converter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for patches backported from proj4js (one test per upstream
 * commit; reference values verified against pyproj/PROJ 9.5.1).
 */
class Proj4jsBackportsTest {

    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    @Test
    @DisplayName("proj4js 61689a9: unknown datum is NODATUM, not WGS84")
    void testUnknownDatumHandling() {
        // A sphere CRS with no datum/towgs84 (ESRI 53003-style) must not receive a
        // spurious datum shift. Reference from pyproj (matches upstream post-patch).
        Converter conv = Proj4.proj4(WGS84,
            "+proj=mill +lon_0=0 +x_0=0 +y_0=0 +a=6371000 +b=6371000 +units=m +no_defs");
        Point xy = conv.forward(new Point(-1.3973289073953, 12.649176474268513));
        assertEquals(-155375.885356, xy.x, 0.01, "easting");
        assertEquals(1413894.115522, xy.y, 0.01, "northing");

        // Datum types: unknown/unnamed -> NODATUM; +datum=WGS84 (towgs84 0,0,0) -> WGS84
        Proj unknown = new Proj("+proj=mill +lon_0=0 +a=6371000 +b=6371000 +units=m +no_defs");
        assertEquals(Values.PJD_NODATUM, unknown.getParams().datum.getDatumType(),
            "unknown datum must be NODATUM");
        Proj wgs = new Proj("+proj=merc +datum=WGS84 +no_defs");
        assertEquals(Values.PJD_WGS84, wgs.getParams().datum.getDatumType(),
            "+datum=WGS84 must stay WGS84");
    }
}
