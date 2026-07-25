package org.datasyslab.proj4sedona.transform;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Transform regressions for authority datums supplied by the generated proj4js
 * registry. The PROJJSON definitions mirror the shape returned by remote CRS
 * providers, where the datum is identified by {@code base_crs.id}. Expected
 * coordinates were cross-checked with PROJ using explicit {@code +towgs84}
 * parameters copied from the proj4js registry, not PROJ's EPSG operation
 * database selection. The RT90 oracle intentionally follows PROJ. This port
 * recognizes the zero-shift target datum as WGS84-equivalent and avoids
 * proj4js's extra WGS84 routing round trip, whose iterative geocentric
 * conversion changes northing by approximately 0.095 mm.
 */
class DatumRegistryTransformTest {

    private static final double TOLERANCE_METRES = 1e-6;

    @Test
    void rt90BaseCrsIdUsesGeneratedHelmertParameters() {
        String rt90 = transverseMercatorProjJson(
                "RT90 2.5 gon V",
                "Rikets koordinatsystem 1990",
                4124,
                "Bessel 1841",
                "6377397.155",
                "299.1528128",
                "15.8082777777778",
                "1",
                "1500000",
                "0",
                "north",
                "east");
        String sweref99 = transverseMercatorProjJson(
                "SWEREF99 TM",
                "SWEREF99",
                4619,
                "GRS 1980",
                "6378137",
                "298.257222101",
                "15",
                "0.9996",
                "500000",
                "0",
                "north",
                "east");

        Proj parsedRt90 = new Proj(rt90);
        assertEquals(Values.PJD_7PARAM, parsedRt90.getParams().datum.getDatumType());
        assertEquals("neu", parsedRt90.getParams().axis);
        // Axis enforcement is off, so the converter receives conventional GIS [E,N].
        // This is the exact RT90 point reported in apache/sedona#3161.
        assertMatchesExplicitTowgs84(
                rt90,
                sweref99,
                "+proj=tmerc +lat_0=0 +lon_0=15.8082777777778 +k=1 "
                        + "+x_0=1500000 +y_0=0 +ellps=bessel "
                        + "+towgs84=419.3836,99.3335,591.3451,"
                        + "0.850389,1.817277,-7.862238,-0.99496 +units=m +no_defs",
                "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9996 "
                        + "+x_0=500000 +y_0=0 +ellps=GRS80 "
                        + "+towgs84=0,0,0 +units=m +no_defs",
                new Point(1272691.622, 6404578.16),
                320728.549061309,
                6400227.973660924);
    }

    @Test
    void agd66BaseCrsIdUsesGeneratedHelmertParameters() {
        String agd66 = transverseMercatorProjJson(
                "AGD66 / AMG zone 55",
                "Australian Geodetic Datum 1966",
                4202,
                "Australian National Spheroid",
                "6378160",
                "298.25",
                "147",
                "0.9996",
                "500000",
                "10000000",
                "east",
                "north");
        String gda94 = transverseMercatorProjJson(
                "GDA94 / MGA zone 55",
                "Geocentric Datum of Australia 1994",
                4283,
                "GRS 1980",
                "6378137",
                "298.257222101",
                "147",
                "0.9996",
                "500000",
                "10000000",
                "east",
                "north");

        assertEquals(Values.PJD_3PARAM, new Proj(agd66).getParams().datum.getDatumType());
        assertMatchesExplicitTowgs84(
                agd66,
                gda94,
                "+proj=tmerc +lat_0=0 +lon_0=147 +k=0.9996 "
                        + "+x_0=500000 +y_0=10000000 +ellps=aust_SA "
                        + "+towgs84=-124,-60,154 +units=m +no_defs",
                "+proj=tmerc +lat_0=0 +lon_0=147 +k=0.9996 "
                        + "+x_0=500000 +y_0=10000000 +ellps=GRS80 "
                        + "+towgs84=0.06155,-0.01087,-0.04019,"
                        + "0.039492,0.032722,0.032898,-0.009994 +units=m +no_defs",
                new Point(500000, 6200000),
                500117.25754570815,
                6200179.615783105);
    }

    private static void assertMatchesExplicitTowgs84(
            String sourceProjJson,
            String targetProjJson,
            String explicitSource,
            String explicitTarget,
            Point input,
            double expectedX,
            double expectedY) {
        Point registryResult = Proj4.proj4(sourceProjJson, targetProjJson)
                .forward(new Point(input.x, input.y));
        Point explicitResult = Proj4.proj4(explicitSource, explicitTarget)
                .forward(new Point(input.x, input.y));

        assertEquals(explicitResult.x, registryResult.x, TOLERANCE_METRES, "easting parity");
        assertEquals(explicitResult.y, registryResult.y, TOLERANCE_METRES, "northing parity");
        assertEquals(expectedX, registryResult.x, TOLERANCE_METRES, "easting");
        assertEquals(expectedY, registryResult.y, TOLERANCE_METRES, "northing");
    }

    private static String transverseMercatorProjJson(
            String crsName,
            String datumName,
            int datumCode,
            String ellipsoidName,
            String semiMajorAxis,
            String inverseFlattening,
            String longitudeOfOrigin,
            String scale,
            String falseEasting,
            String falseNorthing,
            String firstAxisDirection,
            String secondAxisDirection) {
        return "{"
                + "\"type\":\"ProjectedCRS\","
                + "\"name\":\"" + crsName + "\","
                + "\"base_crs\":{"
                + "\"type\":\"GeographicCRS\","
                + "\"name\":\"" + datumName + "\","
                + "\"datum\":{"
                + "\"type\":\"GeodeticReferenceFrame\","
                + "\"name\":\"" + datumName + "\","
                + "\"ellipsoid\":{"
                + "\"name\":\"" + ellipsoidName + "\","
                + "\"semi_major_axis\":" + semiMajorAxis + ","
                + "\"inverse_flattening\":" + inverseFlattening
                + "}},"
                + "\"id\":{\"authority\":\"EPSG\",\"code\":" + datumCode + "}"
                + "},"
                + "\"conversion\":{"
                + "\"method\":{\"name\":\"Transverse Mercator\"},"
                + "\"parameters\":["
                + parameter("Latitude of natural origin", "0", "degree") + ","
                + parameter("Longitude of natural origin", longitudeOfOrigin, "degree") + ","
                + parameter("Scale factor at natural origin", scale, "unity") + ","
                + parameter("False easting", falseEasting, "metre") + ","
                + parameter("False northing", falseNorthing, "metre")
                + "]},"
                + "\"coordinate_system\":{"
                + "\"subtype\":\"Cartesian\","
                + "\"axis\":["
                + axis(firstAxisDirection) + ","
                + axis(secondAxisDirection)
                + "]}"
                + "}";
    }

    private static String parameter(String name, String value, String unit) {
        return "{\"name\":\"" + name + "\",\"value\":" + value
                + ",\"unit\":\"" + unit + "\"}";
    }

    private static String axis(String direction) {
        return "{\"name\":\"" + direction + "\",\"direction\":\"" + direction
                + "\",\"unit\":\"metre\"}";
    }
}
