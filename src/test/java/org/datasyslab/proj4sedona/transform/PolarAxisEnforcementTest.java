package org.datasyslab.proj4sedona.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PolarAxisEnforcementTest {

    private static final double PROJECTED_TOLERANCE = 1e-6;
    private static final double GEOGRAPHIC_TOLERANCE = 1e-10;

    @ParameterizedTest(name = "EPSG:{0}")
    @MethodSource("polarCrsCases")
    void destinationUsesProjNativeAxisOrder(
            int code,
            String definition,
            double longitude,
            double latitude,
            double easting,
            double northing,
            boolean northingFirst) {
        Converter converter = new Converter(
            new Proj("EPSG:4326"), new Proj(definition));

        Point nativeOrder = converter.forward(
            new Point(longitude, latitude), true);

        assertNotNull(nativeOrder);
        assertEquals(
            northingFirst ? northing : easting,
            nativeOrder.x,
            PROJECTED_TOLERANCE);
        assertEquals(
            northingFirst ? easting : northing,
            nativeOrder.y,
            PROJECTED_TOLERANCE);
    }

    @ParameterizedTest(name = "EPSG:{0}")
    @MethodSource("polarCrsCases")
    void sourceReadsProjNativeAxisOrder(
            int code,
            String definition,
            double longitude,
            double latitude,
            double easting,
            double northing,
            boolean northingFirst) {
        Converter converter = new Converter(
            new Proj(definition), new Proj("EPSG:4326"));
        Point nativeOrder = northingFirst
            ? new Point(northing, easting)
            : new Point(easting, northing);

        Point geographic = converter.forward(nativeOrder, true);

        assertNotNull(geographic);
        assertEquals(longitude, geographic.x, GEOGRAPHIC_TOLERANCE);
        assertEquals(latitude, geographic.y, GEOGRAPHIC_TOLERANCE);
    }

    @ParameterizedTest(name = "EPSG:{0}")
    @MethodSource("polarCrsCases")
    void defaultTransformKeepsTraditionalEastingNorthingOrder(
            int code,
            String definition,
            double longitude,
            double latitude,
            double easting,
            double northing,
            boolean northingFirst) {
        Converter converter = new Converter(
            new Proj("EPSG:4326"), new Proj(definition));

        Point projected = converter.forward(new Point(longitude, latitude));

        assertNotNull(projected);
        assertEquals(easting, projected.x, PROJECTED_TOLERANCE);
        assertEquals(northing, projected.y, PROJECTED_TOLERANCE);
    }

    @Test
    void northingFirstRoundTripPreservesZAndMeasure() {
        Converter converter = new Converter(
            new Proj("EPSG:4326"), new Proj(upsNorth()));
        Point geographic = new Point(30.0, 85.0, 123.5, 7.25);

        Point nativeOrder = converter.forward(geographic, true);

        assertNotNull(nativeOrder);
        assertEquals(1518959.7883427655, nativeOrder.x, PROJECTED_TOLERANCE);
        assertEquals(2277728.6956913387, nativeOrder.y, PROJECTED_TOLERANCE);
        assertEquals(123.5, nativeOrder.z, PROJECTED_TOLERANCE);
        assertEquals(7.25, nativeOrder.m, 0.0);

        Point roundTrip = converter.inverse(nativeOrder, true);
        assertNotNull(roundTrip);
        assertEquals(30.0, roundTrip.x, GEOGRAPHIC_TOLERANCE);
        assertEquals(85.0, roundTrip.y, GEOGRAPHIC_TOLERANCE);
        assertEquals(123.5, roundTrip.z, PROJECTED_TOLERANCE);
        assertEquals(7.25, roundTrip.m, 0.0);
    }

    @Test
    void malformedOrMissingPolarMetadataFailsSafelyOnlyWhenEnforced() {
        Proj missingMetadata = new Proj(upsNorth());
        missingMetadata.getParams().coordinateAxes = Collections.emptyList();
        Converter converter = new Converter(
            new Proj("EPSG:4326"), missingMetadata);

        assertNull(converter.forward(new Point(30.0, 85.0), true));
        assertNull(new Converter(
            missingMetadata, new Proj("EPSG:4326"))
            .forward(
                new Point(1518959.7883427655, 2277728.6956913387),
                true));

        Point traditionalOrder = converter.forward(new Point(30.0, 85.0));
        assertNotNull(traditionalOrder);
        assertEquals(2277728.6956913387, traditionalOrder.x, PROJECTED_TOLERANCE);
        assertEquals(1518959.7883427655, traditionalOrder.y, PROJECTED_TOLERANCE);

        Proj driftedAxis = new Proj(upsNorth());
        driftedAxis.getParams().axis = "nnu";
        assertNull(new Converter(
            new Proj("EPSG:4326"), driftedAxis)
            .forward(new Point(30.0, 85.0), true));

        Proj duplicateRole = new Proj(upsNorth());
        duplicateRole.getParams().coordinateAxes = Arrays.asList(
            duplicateRole.getParams().coordinateAxes.get(0),
            duplicateRole.getParams().coordinateAxes.get(0));
        assertNull(new Converter(
            new Proj("EPSG:4326"), duplicateRole)
            .forward(new Point(30.0, 85.0), true));
    }

    @ParameterizedTest
    @MethodSource("ordinaryPolarCrs")
    void ordinaryPolarAxesStayOnOrdinaryAxisPath(int code, double latitude) {
        Converter converter = new Converter(
            new Proj("EPSG:4326"), new Proj("EPSG:" + code));

        Point traditional = converter.forward(new Point(30.0, latitude));
        Point enforced = converter.forward(new Point(30.0, latitude), true);

        assertNotNull(traditional);
        assertNotNull(enforced);
        assertEquals(traditional.x, enforced.x, 0.0);
        assertEquals(traditional.y, enforced.y, 0.0);
    }

    @Test
    void ordinaryAxisPermutationWithMeridiansUsesOrdinaryEnforcementPath() {
        Proj projected = new Proj(polarCrs(
            3031,
            "Polar Stereographic (variant B)",
            polarVariantBParameters(-71.0, 0.0),
            axis("Easting", "E", "east", 90.0),
            axis("Northing", "N", "north", 0.0)));
        assertEquals("enu", projected.getParams().axis);

        Point nativeOrder = new Converter(
            new Proj("EPSG:4326"), projected)
            .forward(new Point(30.0, -80.0), true);

        assertNotNull(nativeOrder);
        assertEquals(544589.7278130918, nativeOrder.x, PROJECTED_TOLERANCE);
        assertEquals(943257.0778523809, nativeOrder.y, PROJECTED_TOLERANCE);

        Point geographic = new Converter(
            projected, new Proj("EPSG:4326"))
            .forward(nativeOrder, true);

        assertNotNull(geographic);
        assertEquals(30.0, geographic.x, GEOGRAPHIC_TOLERANCE);
        assertEquals(-80.0, geographic.y, GEOGRAPHIC_TOLERANCE);
    }

    @Test
    void ordinaryProjAxisPermutationsRemainUnchanged() {
        Point transformed = Transform.transform(
            new Proj("+proj=longlat +datum=WGS84 +axis=neu"),
            new Proj("+proj=longlat +datum=WGS84 +axis=uen"),
            new Point(10.0, 20.0, 30.0, 40.0),
            true);

        assertNotNull(transformed);
        assertEquals(30.0, transformed.x, 0.0);
        assertEquals(20.0, transformed.y, 0.0);
        assertEquals(10.0, transformed.z, PROJECTED_TOLERANCE);
        assertEquals(40.0, transformed.m, 0.0);
    }

    /**
     * Reference values generated by pyproj 3.7.1 / PROJ 9.5.1. The easting and
     * northing columns use {@code always_xy=true}; the last column records native
     * PROJ axis order with {@code always_xy=false}.
     */
    private static Stream<Arguments> polarCrsCases() {
        return Stream.of(
            Arguments.of(
                3031, antarcticPolarStereographicWkt2(), 30.0, -80.0,
                544589.7278130918, 943257.0778523809, false),
            Arguments.of(
                32661, upsNorth(), 30.0, 85.0,
                2277728.6956913387, 1518959.7883427655, true),
            Arguments.of(
                32761, upsSouth(), 30.0, -85.0,
                2277728.6956913387, 2481040.2116572345, true),
            Arguments.of(
                3413, nsidcSeaIceNorth(), -30.0, 80.0,
                281056.85442872735, -1048918.4605435, false));
    }

    private static Stream<Arguments> ordinaryPolarCrs() {
        return Stream.of(
            Arguments.of(5041, 85.0),
            Arguments.of(5042, -85.0));
    }

    private static String antarcticPolarStereographicWkt2() {
        return "PROJCRS[\"WGS 84 / Antarctic Polar Stereographic\","
            + "BASEGEOGCRS[\"WGS 84\","
            + "DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "UNIT[\"degree\",0.0174532925199433]],"
            + "CONVERSION[\"Antarctic Polar Stereographic\","
            + "METHOD[\"Polar Stereographic (variant B)\"],"
            + "PARAMETER[\"Latitude of standard parallel\",-71],"
            + "PARAMETER[\"Longitude of origin\",0],"
            + "PARAMETER[\"False easting\",0],"
            + "PARAMETER[\"False northing\",0]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"(E)\",north,MERIDIAN[90,"
            + "UNIT[\"degree\",0.0174532925199433]]],"
            + "AXIS[\"(N)\",north,MERIDIAN[0,"
            + "UNIT[\"degree\",0.0174532925199433]]],"
            + "UNIT[\"metre\",1],ID[\"EPSG\",3031]]";
    }

    private static String upsNorth() {
        return polarCrs(
            32661,
            "Polar Stereographic (variant A)",
            polarVariantAParameters(90.0, 0.0),
            axis("Northing", "N", "south", 180.0),
            axis("Easting", "E", "south", 90.0));
    }

    private static String upsSouth() {
        return polarCrs(
            32761,
            "Polar Stereographic (variant A)",
            polarVariantAParameters(-90.0, 0.0),
            axis("Northing", "N", "north", 0.0),
            axis("Easting", "E", "north", 90.0));
    }

    private static String nsidcSeaIceNorth() {
        return polarCrs(
            3413,
            "Polar Stereographic (variant B)",
            polarVariantBParameters(70.0, -45.0),
            axis("Easting", "X", "south", 45.0),
            axis("Northing", "Y", "south", 135.0));
    }

    private static String polarCrs(
            int code,
            String method,
            String parameters,
            String firstAxis,
            String secondAxis) {
        return "{"
            + "\"type\":\"ProjectedCRS\","
            + "\"name\":\"EPSG:" + code + "\","
            + "\"base_crs\":{"
            + "\"type\":\"GeographicCRS\","
            + "\"name\":\"WGS 84\","
            + "\"datum\":{"
            + "\"type\":\"GeodeticReferenceFrame\","
            + "\"name\":\"World Geodetic System 1984\","
            + "\"ellipsoid\":{"
            + "\"name\":\"WGS 84\","
            + "\"semi_major_axis\":6378137,"
            + "\"inverse_flattening\":298.257223563}}},"
            + "\"conversion\":{"
            + "\"name\":\"unnamed\","
            + "\"method\":{\"name\":\"" + method + "\"},"
            + "\"parameters\":[" + parameters + "]},"
            + "\"coordinate_system\":{"
            + "\"subtype\":\"Cartesian\","
            + "\"axis\":[" + firstAxis + "," + secondAxis + "]},"
            + "\"id\":{\"authority\":\"EPSG\",\"code\":" + code + "}}";
    }

    private static String polarVariantAParameters(
            double latitude, double longitude) {
        return naturalOriginAngles(latitude, longitude)
            + ",{\"name\":\"Scale factor at natural origin\","
            + "\"value\":0.994,\"unit\":\"unity\"}"
            + ",{\"name\":\"False easting\",\"value\":2000000,"
            + "\"unit\":\"metre\"}"
            + ",{\"name\":\"False northing\",\"value\":2000000,"
            + "\"unit\":\"metre\"}";
    }

    private static String polarVariantBParameters(
            double latitudeOfStandardParallel, double longitude) {
        return "{\"name\":\"Latitude of standard parallel\","
            + "\"value\":" + latitudeOfStandardParallel
            + ",\"unit\":\"degree\"},"
            + angularParameter("Longitude of origin", longitude)
            + ",{\"name\":\"False easting\",\"value\":0,\"unit\":\"metre\"}"
            + ",{\"name\":\"False northing\",\"value\":0,\"unit\":\"metre\"}";
    }

    private static String naturalOriginAngles(
            double latitude, double longitude) {
        return angularParameter("Latitude of natural origin", latitude)
            + "," + angularParameter("Longitude of natural origin", longitude);
    }

    private static String angularParameter(String name, double value) {
        return "{\"name\":\"" + name + "\",\"value\":" + value
            + ",\"unit\":\"degree\"}";
    }

    private static String axis(
            String name,
            String abbreviation,
            String direction,
            double meridian) {
        return "{"
            + "\"name\":\"" + name + "\","
            + "\"abbreviation\":\"" + abbreviation + "\","
            + "\"direction\":\"" + direction + "\","
            + "\"meridian\":{\"longitude\":" + meridian + "},"
            + "\"unit\":\"metre\"}";
    }
}
