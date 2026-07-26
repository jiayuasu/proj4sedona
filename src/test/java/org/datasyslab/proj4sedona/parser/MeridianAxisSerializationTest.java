package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.CoordinateAxis;
import org.datasyslab.proj4sedona.core.Proj;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MeridianAxisSerializationTest {

    private static final double GRAD_TO_RAD = Math.PI / 200.0;

    @ParameterizedTest(name = "EPSG:{0}")
    @MethodSource("sedonaPolarCrs")
    void roundTripsPolarAxesThroughBothStandardFormats(
            int code, String definition, String compactAxis) {
        Proj proj = new Proj(definition);

        assertEquals(compactAxis, proj.getParams().axis);
        assertEquals(2, proj.getParams().coordinateAxes.size());

        String legacyProj = proj.toProjString();
        assertFalse(legacyProj.contains("+axis="), legacyProj);

        String wkt2 = proj.toWkt2();
        assertTrue(wkt2.contains("MERIDIAN["), wkt2);
        assertFalse(wkt2.contains(",]"), wkt2);
        assertEquals(wkt2, new Proj(wkt2).toWkt2());

        String projJson = proj.toProjJson(false);
        assertTrue(projJson.contains("\"meridian\""), projJson);
        assertEquals(projJson, new Proj(projJson).toProjJson(false));

        String jsonFromWkt = new Proj(wkt2).toProjJson(false);
        assertEquals(jsonFromWkt, new Proj(jsonFromWkt).toProjJson(false));

        assertThrows(UnsupportedOperationException.class, proj::toWkt1);
        assertEquals("EPSG:" + code, proj.toEpsgCode());
    }

    @Test
    void preservesNonDegreeAxisMeridianUnits() {
        String degreeLongitude = "\"meridian\":{\"longitude\":90}";
        String gradLongitude =
            "\"meridian\":{\"longitude\":{\"value\":100,\"unit\":{"
                + "\"type\":\"AngularUnit\",\"name\":\"grad\","
                + "\"conversion_factor\":" + GRAD_TO_RAD + "}}}";
        String definition = polarCrs(
            3031,
            "Polar Stereographic (variant B)",
            polarVariantBParameters(-71, 0),
            axis("Easting", "E", "north", degreeLongitude, "\"metre\"", null),
            axis("Northing", "N", "north", meridian(0), "\"metre\"", null))
            .replace(degreeLongitude, gradLongitude);

        Proj proj = new Proj(definition);
        String wkt2 = proj.toWkt2();
        String projJson = proj.toProjJson(false);

        assertTrue(
            wkt2.contains(
                "MERIDIAN[100,ANGLEUNIT[\"grad\"," + GRAD_TO_RAD + "]]"),
            wkt2);
        assertTrue(projJson.contains("\"name\":\"grad\""), projJson);
        assertEquals(wkt2, new Proj(wkt2).toWkt2());
        assertEquals(projJson, new Proj(projJson).toProjJson(false));
    }

    @Test
    void preservesCustomDegreeEquivalentMeridianUnitName() {
        String customDegree =
            "\"meridian\":{\"longitude\":{\"value\":90,\"unit\":{"
                + "\"type\":\"AngularUnit\",\"name\":\"arc degree\","
                + "\"conversion_factor\":" + Values.D2R + "}}}";
        String definition = polarCrs(
            3031,
            "Polar Stereographic (variant B)",
            polarVariantBParameters(-71, 0),
            axis(
                "Easting", "E", "north", customDegree,
                "\"metre\"", null),
            axis(
                "Northing", "N", "north", meridian(0),
                "\"metre\"", null));

        String projJson = new Proj(definition).toProjJson(false);

        assertTrue(projJson.contains("\"name\":\"arc degree\""), projJson);
        assertEquals(projJson, new Proj(projJson).toProjJson(false));
    }

    @Test
    void canonicalizesPublicModelUnitTypesInProjJson() {
        Proj proj = new Proj(polarCrs(
            6931,
            "Lambert Azimuthal Equal Area",
            naturalOriginParameters(90, 0),
            axis(
                "Easting", "E", "south", meridian(90),
                "\"metre\"", null),
            axis(
                "Northing", "N", "south", meridian(180),
                "\"metre\"", null)));
        CoordinateAxis.Unit metre =
            new CoordinateAxis.Unit(null, "metre", 1.0);
        CoordinateAxis.Unit degree =
            new CoordinateAxis.Unit("angularunit", "degree", Values.D2R);
        proj.getParams().coordinateAxes = Arrays.asList(
            new CoordinateAxis(
                "Easting", "E", "south", null, metre,
                new CoordinateAxis.Meridian(90.0, degree)),
            new CoordinateAxis(
                "Northing", "N", "south", null, metre,
                new CoordinateAxis.Meridian(180.0, degree)));

        String projJson = proj.toProjJson(false);

        assertTrue(projJson.contains("\"type\":\"LinearUnit\""), projJson);
        assertTrue(projJson.contains("\"type\":\"AngularUnit\""), projJson);
        assertEquals(projJson, new Proj(projJson).toProjJson(false));
    }

    @Test
    void retainsAuthorityFromPluralProjJsonIds() {
        String definition = polarCrs(
            3031,
            "Polar Stereographic (variant B)",
            polarVariantBParameters(-71, 0),
            axis(
                "Easting", "E", "north", meridian(90),
                "\"metre\"", null),
            axis(
                "Northing", "N", "north", meridian(0),
                "\"metre\"", null))
            .replace(
                "\"id\":{\"authority\":\"EPSG\",\"code\":3031}",
                "\"ids\":[{\"authority\":\"EPSG\",\"code\":3031}]");

        Proj proj = new Proj(definition);

        assertEquals("EPSG:3031", proj.toEpsgCode());
        assertTrue(proj.toProjJson(false).contains(
            "\"id\":{\"authority\":\"EPSG\",\"code\":3031}"));
    }

    @Test
    void derivesAxisRolesFromMeridiansInsteadOfDisplayLabels() {
        String definition = polarCrs(
            6931,
            "Lambert Azimuthal Equal Area",
            naturalOriginParameters(90, 0),
            axis(
                "First grid coordinate", "A", "south", meridian(90),
                "\"metre\"", null),
            axis(
                "Second grid coordinate", "B", "south", meridian(180),
                "\"metre\"", null));

        Proj proj = new Proj(definition);
        String wkt2 = proj.toWkt2();
        String projJson = proj.toProjJson(false);

        assertTrue(wkt2.contains("\"First grid coordinate (A)\""), wkt2);
        assertTrue(wkt2.contains("\"Second grid coordinate (B)\""), wkt2);
        assertTrue(projJson.contains("\"abbreviation\":\"A\""), projJson);
        assertTrue(projJson.contains("\"abbreviation\":\"B\""), projJson);
        assertEquals(wkt2, new Proj(wkt2).toWkt2());
        assertEquals(projJson, new Proj(projJson).toProjJson(false));
    }

    @Test
    void escapesRetainedAxisAndUnitLabelsInWkt2() {
        String quotedMetre = "{\"type\":\"LinearUnit\","
            + "\"name\":\"metre \\\"quoted\\\"\",\"conversion_factor\":1}";
        String quotedDegree = "{\"type\":\"AngularUnit\","
            + "\"name\":\"degree \\\"quoted\\\"\","
            + "\"conversion_factor\":" + Values.D2R + "}";
        String eastingMeridian =
            "\"meridian\":{\"longitude\":{\"value\":90,\"unit\":"
                + quotedDegree + "}}";
        String definition = polarCrs(
            6931,
            "Lambert Azimuthal Equal Area",
            naturalOriginParameters(90, 0),
            axis(
                "Easting \\\"quoted\\\"", "E", "south", eastingMeridian,
                quotedMetre, null),
            axis(
                "Northing", "N", "south", meridian(180),
                quotedMetre, null));

        String wkt2 = new Proj(definition).toWkt2();

        assertTrue(
            wkt2.contains("AXIS[\"Easting \"\"quoted\"\" (E)\""),
            wkt2);
        assertTrue(wkt2.contains("LENGTHUNIT[\"metre \"\"quoted\"\"\",1"), wkt2);
        assertTrue(
            wkt2.contains("ANGLEUNIT[\"degree \"\"quoted\"\"\","),
            wkt2);
        assertEquals(wkt2, new Proj(wkt2).toWkt2());
    }

    @Test
    void rejectsMeridianMetadataBeforeKrovakAxisSpecialCase() {
        Proj proj = new Proj(
            "+proj=krovak +ellps=bessel +units=m +axis=swu +no_defs");
        CoordinateAxis.Unit metre =
            new CoordinateAxis.Unit("LinearUnit", "metre", 1.0);
        CoordinateAxis.Unit degree =
            new CoordinateAxis.Unit("AngularUnit", "degree", Values.D2R);
        proj.getParams().coordinateSystemType = "Cartesian";
        proj.getParams().coordinateAxes = Arrays.asList(
            new CoordinateAxis(
                "Southing", "S", "south", 1, metre,
                new CoordinateAxis.Meridian(0.0, degree)),
            new CoordinateAxis(
                "Westing", "W", "west", 2, metre, null));

        assertThrows(UnsupportedOperationException.class, proj::toProjString);
        assertThrows(UnsupportedOperationException.class, proj::toWkt1);
        assertThrows(UnsupportedOperationException.class, proj::toWkt2);
        assertThrows(UnsupportedOperationException.class, proj::toProjJson);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPolarMetadata")
    void rejectsMalformedPolarMetadataAtEveryExportBoundary(
            String description, String definition) {
        Proj proj = new Proj(definition);

        assertNull(proj.toAuthority(), description);
        assertThrows(
            UnsupportedOperationException.class, proj::toProjString, description);
        assertThrows(
            UnsupportedOperationException.class, proj::toWkt2, description);
        assertThrows(
            UnsupportedOperationException.class, proj::toProjJson, description);
    }

    @ParameterizedTest(name = "EPSG:{0}")
    @MethodSource("bundledUpsCrs")
    void retainsBundledUpsAuthorityWithMeridianAxes(
            int code, String definition) {
        Proj proj = new Proj(definition);

        assertEquals("EPSG:" + code, proj.toEpsgCode());
        assertEquals("EPSG", proj.toAuthority()[0]);
        assertEquals(Integer.toString(code), proj.toAuthority()[1]);
    }

    private static Stream<Arguments> sedonaPolarCrs() {
        return Stream.of(
            Arguments.of(
                3031,
                polarCrs(
                    3031,
                    "Polar Stereographic (variant B)",
                    polarVariantBParameters(-71, 0),
                    axis(
                        "Easting", "E", "north", meridian(90),
                        "\"metre\"", null),
                    axis(
                        "Northing", "N", "north", meridian(0),
                        "\"metre\"", null)),
                "nnu"),
            Arguments.of(
                32661,
                polarCrs(
                    32661,
                    "Polar Stereographic (variant A)",
                    polarVariantAParameters(90, 0),
                    axis(
                        "Northing", "N", "south", meridian(180),
                        "\"metre\"", null),
                    axis(
                        "Easting", "E", "south", meridian(90),
                        "\"metre\"", null)),
                "ssu"),
            Arguments.of(
                3413,
                polarCrs(
                    3413,
                    "Polar Stereographic (variant B)",
                    polarVariantBParameters(70, -45),
                    axis(
                        "Easting", "X", "south", meridian(45),
                        "\"metre\"", null),
                    axis(
                        "Northing", "Y", "south", meridian(135),
                        "\"metre\"", null)),
                "ssu"),
            Arguments.of(
                6931,
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    naturalOriginParameters(90, 0),
                    axis(
                        "Easting", "X", "south", meridian(90),
                        "\"metre\"", null),
                    axis(
                        "Northing", "Y", "south", meridian(180),
                        "\"metre\"", null)),
                "ssu"));
    }

    private static Stream<Arguments> bundledUpsCrs() {
        return Stream.of(
            Arguments.of(
                5041,
                polarCrs(
                    5041,
                    "Polar Stereographic (variant A)",
                    polarVariantAParameters(90, 0),
                    axis(
                        "Easting", "E", "south", meridian(90),
                        "\"metre\"", null),
                    axis(
                        "Northing", "N", "south", meridian(180),
                        "\"metre\"", null))),
            Arguments.of(
                5042,
                polarCrs(
                    5042,
                    "Polar Stereographic (variant A)",
                    polarVariantAParameters(-90, 0),
                    axis(
                        "Easting", "E", "north", meridian(90),
                        "\"metre\"", null),
                    axis(
                        "Northing", "N", "north", meridian(0),
                        "\"metre\"", null))));
    }

    private static Stream<Arguments> invalidPolarMetadata() {
        String parameters = naturalOriginParameters(90, 0);
        String validEasting =
            axis("Easting", "X", "south", meridian(90), "\"metre\"", null);
        String validNorthing =
            axis("Northing", "Y", "south", meridian(180), "\"metre\"", null);
        String foot = "{\"type\":\"LinearUnit\",\"name\":\"foot\","
            + "\"conversion_factor\":0.3048}";

        return Stream.of(
            Arguments.of(
                "ordinary east/north directions omit required polar meridians",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    axis("Easting", "X", "east", null, "\"metre\"", null),
                    axis("Northing", "Y", "north", null, "\"metre\"", null))),
            Arguments.of(
                "one meridian is missing",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    axis("Easting", "X", "south", null, "\"metre\"", null),
                    validNorthing)),
            Arguments.of(
                "easting meridian disagrees with the projection origin",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    axis(
                        "Easting", "X", "south", meridian(91),
                        "\"metre\"", null),
                    validNorthing)),
            Arguments.of(
                "directions disagree with the pole hemisphere",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    axis(
                        "Easting", "X", "north", meridian(90),
                        "\"metre\"", null),
                    axis(
                        "Northing", "Y", "north", meridian(180),
                        "\"metre\"", null))),
            Arguments.of(
                "both meridians identify the easting role",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    axis(
                        "First", "A", "south", meridian(90),
                        "\"metre\"", null),
                    axis(
                        "Second", "B", "south", meridian(90),
                        "\"metre\"", null))),
            Arguments.of(
                "horizontal units disagree",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    axis(
                        "Easting", "X", "south", meridian(90), foot, null),
                    validNorthing)),
            Arguments.of(
                "ORDER is only present on one axis",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    axis(
                        "Easting", "X", "south", meridian(90),
                        "\"metre\"", 1),
                    validNorthing)),
            Arguments.of(
                "coordinate system is not Cartesian",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    parameters,
                    validEasting,
                    validNorthing)
                    .replace("\"subtype\":\"Cartesian\"",
                        "\"subtype\":\"ellipsoidal\"")),
            Arguments.of(
                "periodic latitude alias is outside the valid pole range",
                polarCrs(
                    6931,
                    "Lambert Azimuthal Equal Area",
                    naturalOriginParameters(270, 0),
                    validEasting,
                    validNorthing)));
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
            + "\"value\":" + latitudeOfStandardParallel + ",\"unit\":\"degree\"},"
            + angularParameter("Longitude of origin", longitude)
            + ",{\"name\":\"False easting\",\"value\":0,\"unit\":\"metre\"}"
            + ",{\"name\":\"False northing\",\"value\":0,\"unit\":\"metre\"}";
    }

    private static String naturalOriginParameters(
            double latitude, double longitude) {
        return naturalOriginAngles(latitude, longitude)
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
            String meridian,
            String unit,
            Integer order) {
        return "{"
            + "\"name\":\"" + name + "\","
            + "\"abbreviation\":\"" + abbreviation + "\","
            + "\"direction\":\"" + direction + "\","
            + (meridian != null ? meridian + "," : "")
            + "\"unit\":" + unit
            + (order != null ? ",\"order\":" + order : "")
            + "}";
    }

    private static String meridian(double longitude) {
        return "\"meridian\":{\"longitude\":" + longitude + "}";
    }
}
