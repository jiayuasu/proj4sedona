package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.Converter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression tests against proj4js 2.20.9 lib/projections/eqc.js. */
class EquidistantCylindricalTest {

    private static final double XY_TOLERANCE = 1e-6;
    private static final double ANGULAR_TOLERANCE = 1e-12;
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";
    private static final String EQC =
        "+proj=eqc +lat_0=20 +lat_ts=30 +lon_0=10 "
            + "+x_0=1234 +y_0=-5678 +datum=WGS84 +units=m +no_defs";

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
        ProjectionRegistry.start();
    }

    @Test
    void nonzeroLatitudeOfOriginMatchesProj4jsForwardAndInverse() {
        Converter converter = Proj4.proj4(WGS84, EQC);

        Point projected = converter.forward(new Point(25, 35));
        assertEquals(1447316.6044498428, projected.x, XY_TOLERANCE);
        assertEquals(1664114.3618991035, projected.y, XY_TOLERANCE);

        Point geographic = converter.inverse(projected);
        assertEquals(25.0, geographic.x, ANGULAR_TOLERANCE);
        assertEquals(35.0, geographic.y, ANGULAR_TOLERANCE);
    }

    @Test
    void latitudeNormalizationMatchesProj4jsAtHalfPiBoundary() {
        Proj projection = new Proj(EQC);

        Point projected = projection.forward(new Point(25 * Values.D2R, 110 * Values.D2R));
        assertEquals(1447316.6044498428, projected.x, XY_TOLERANCE);
        assertEquals(-10024432.171394622, projected.y, XY_TOLERANCE);

        Point geographic = projection.inverse(projected);
        assertEquals(25 * Values.D2R, geographic.x, ANGULAR_TOLERANCE);
        assertEquals(-70 * Values.D2R, geographic.y, ANGULAR_TOLERANCE);
    }

    @Test
    void omittedZeroAndNanLatitudeOfTrueScaleAllSelectPlateCarree() {
        String base = "+proj=eqc +lat_0=20 +lon_0=10 +x_0=1234 +y_0=-5678 "
            + "+datum=WGS84 +units=m +no_defs";

        for (String latTs : new String[] {"", " +lat_ts=0", " +lat_ts=NaN"}) {
            Proj projection = new Proj(base + latTs);
            Point projected = projection.forward(new Point(25 * Values.D2R, 35 * Values.D2R));
            assertEquals(1671026.3618991037, projected.x, XY_TOLERANCE, latTs);
            assertEquals(1664114.3618991035, projected.y, XY_TOLERANCE, latTs);
        }
    }

}
