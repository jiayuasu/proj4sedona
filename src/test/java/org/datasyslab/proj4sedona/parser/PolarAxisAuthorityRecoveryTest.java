package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.transform.AdjustAxis;
import org.datasyslab.proj4sedona.transform.Converter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PolarAxisAuthorityRecoveryTest {

    private static final String BASE_CRS =
        "\"base_crs\":{\"type\":\"GeographicCRS\",\"name\":\"WGS 84\","
            + "\"datum\":{\"type\":\"GeodeticReferenceFrame\","
            + "\"name\":\"World Geodetic System 1984\","
            + "\"ellipsoid\":{\"name\":\"WGS 84\",\"semi_major_axis\":6378137,"
            + "\"inverse_flattening\":298.257223563}}},";

    @ParameterizedTest(name = "EPSG:{0} preserves metadata axis {2} via PROJ mapping {3}")
    @MethodSource("polarCrsCases")
    void projJsonPolarAxesUseValidOperationalMapping(
            int epsg, String projJson, String sourceAxis, String operationalAxis) {
        Proj proj = new Proj(projJson);

        assertEquals(sourceAxis, proj.getParams().axis);
        assertEquals(operationalAxis, proj.getParams().getOperationalAxis());

        String projString = proj.toProjString();
        assertFalse(projString.contains("+axis=nnu"), projString);
        assertFalse(projString.contains("+axis=ssu"), projString);
        if ("enu".equals(operationalAxis)) {
            assertFalse(projString.contains("+axis="), projString);
        } else {
            assertTrue(projString.contains("+axis=" + operationalAxis), projString);
        }

        Proj reimported = new Proj(projString);
        assertEquals("EPSG:" + epsg, reimported.toEpsgCode());
        assertEquals(projString, reimported.toProjString());
    }

    @ParameterizedTest(name = "EPSG:{0} keeps strict standard-format rejection")
    @MethodSource("polarCrsCases")
    void sourceAxisDirectionsStillProtectStandardExports(
            int epsg, String projJson, String sourceAxis, String operationalAxis) {
        Proj proj = new Proj(projJson);

        UnsupportedOperationException wktError =
            assertThrows(UnsupportedOperationException.class, proj::toWkt2);
        assertTrue(wktError.getMessage().contains("Axis " + sourceAxis), wktError.getMessage());

        UnsupportedOperationException jsonError =
            assertThrows(UnsupportedOperationException.class, proj::toProjJson);
        assertTrue(jsonError.getMessage().contains("Axis " + sourceAxis), jsonError.getMessage());
    }

    @ParameterizedTest(name = "WKT2 EPSG:{0} preserves {2} via {3}")
    @MethodSource("polarWkt2Cases")
    void wkt2PolarMeridiansProduceTheSameOperationalMapping(
            int epsg, String wkt2, String sourceAxis, String operationalAxis) {
        Proj proj = new Proj(wkt2);

        assertEquals(sourceAxis, proj.getParams().axis);
        assertEquals(operationalAxis, proj.getParams().getOperationalAxis());

        String projString = proj.toProjString();
        if ("enu".equals(operationalAxis)) {
            assertFalse(projString.contains("+axis="), projString);
        } else {
            assertTrue(projString.contains("+axis=" + operationalAxis), projString);
        }
        assertEquals("EPSG:" + epsg, new Proj(projString).toEpsgCode());
        assertThrows(UnsupportedOperationException.class, proj::toWkt2);
        assertThrows(UnsupportedOperationException.class, proj::toProjJson);
    }

    @ParameterizedTest(name = "invalid polar meridian is rejected: {0}")
    @MethodSource("invalidPolarMeridians")
    void invalidPolarMeridiansCannotAuthorizeOrSerialize(
            String name, String projJson) {
        Proj proj = new Proj(projJson);

        assertEquals(proj.getParams().axis, proj.getParams().getOperationalAxis(), name);
        assertNull(proj.toEpsgCode(), name);
        assertThrows(UnsupportedOperationException.class, proj::toProjString, name);
    }

    @Test
    void upsNorthRequiresNorthingEastingAxisOrderForRecovery() {
        String axisless =
            "+proj=stere +lat_0=90 +lon_0=0 +k=0.994 "
                + "+x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m +no_defs";

        assertNull(new Proj(axisless).toEpsgCode());
        assertEquals("EPSG:32661", new Proj(axisless + " +axis=neu").toEpsgCode());

        Proj invalid = new Proj(axisless + " +axis=ssu");
        assertNull(invalid.toEpsgCode());
        assertThrows(UnsupportedOperationException.class, invalid::toProjString);
        assertNull(AdjustAxis.adjustAxisToEnu("ssu", new Point(1, 2), false));
        assertNull(AdjustAxis.adjustAxisFromEnu("nnu", new Point(1, 2), false));
    }

    @Test
    void upsNorthOperationalAxisSwapsWithoutLosingCoordinates() {
        Proj upsNorth = new Proj(upsNorthProjJson());
        Converter converter =
            new Converter(new Proj("+proj=longlat +datum=WGS84"), upsNorth);
        Point input = new Point(35, 82);

        Point eastingNorthing = converter.forward(input);
        Point northingEasting = converter.forward(input, true);
        assertEquals(eastingNorthing.y, northingEasting.x, 1e-9);
        assertEquals(eastingNorthing.x, northingEasting.y, 1e-9);

        Point roundTrip = converter.inverse(northingEasting, true);
        assertEquals(input.x, roundTrip.x, 1e-9);
        assertEquals(input.y, roundTrip.y, 1e-9);
    }

    @ParameterizedTest(name = "near miss does not identify: {0}")
    @MethodSource("nearMisses")
    void polarNearMissesRemainUnidentified(String name, String definition) {
        assertNull(new Proj(definition).toEpsgCode(), name);
    }

    @Test
    void assertedPolarIdentifierIsValidatedAgainstLocalSignature() {
        Proj mislabeled = new Proj(polarCrs(
            32661,
            "Antarctic Polar Stereographic",
            "Polar Stereographic (variant B)",
            "[{\"name\":\"Latitude of standard parallel\",\"value\":-71,\"unit\":\"degree\"},"
                + "{\"name\":\"Longitude of origin\",\"value\":0,\"unit\":\"degree\"},"
                + "{\"name\":\"False easting\",\"value\":0,\"unit\":\"metre\"},"
                + "{\"name\":\"False northing\",\"value\":0,\"unit\":\"metre\"}]",
            "[{\"name\":\"Easting\",\"abbreviation\":\"E\",\"direction\":\"north\","
                + "\"meridian\":{\"longitude\":90},\"unit\":\"metre\"},"
                + "{\"name\":\"Northing\",\"abbreviation\":\"N\",\"direction\":\"north\","
                + "\"meridian\":{\"longitude\":0},\"unit\":\"metre\"}]"));

        assertEquals("EPSG:3031", mislabeled.toEpsgCode());
    }

    private static Stream<Arguments> polarCrsCases() {
        return Stream.of(
            Arguments.of(
                3031,
                antarcticProjJson(),
                "nnu",
                "enu"),
            Arguments.of(
                32661,
                upsNorthProjJson(),
                "ssu",
                "neu"),
            Arguments.of(
                3413,
                polarCrs(
                    3413,
                    "NSIDC Sea Ice Polar Stereographic North",
                    "Polar Stereographic (variant B)",
                    "[{\"name\":\"Latitude of standard parallel\",\"value\":70,"
                        + "\"unit\":\"degree\"},"
                        + "{\"name\":\"Longitude of origin\",\"value\":-45,"
                        + "\"unit\":\"degree\"},"
                        + "{\"name\":\"False easting\",\"value\":0,\"unit\":\"metre\"},"
                        + "{\"name\":\"False northing\",\"value\":0,\"unit\":\"metre\"}]",
                    "[{\"name\":\"Easting\",\"abbreviation\":\"X\",\"direction\":\"south\","
                        + "\"meridian\":{\"longitude\":45},\"unit\":\"metre\"},"
                        + "{\"name\":\"Northing\",\"abbreviation\":\"Y\",\"direction\":\"south\","
                        + "\"meridian\":{\"longitude\":135},\"unit\":\"metre\"}]"),
                "ssu",
                "enu"),
            Arguments.of(
                6931,
                polarCrs(
                    6931,
                    "NSIDC EASE-Grid 2.0 North",
                    "Lambert Azimuthal Equal Area",
                    "[{\"name\":\"Latitude of natural origin\",\"value\":90,"
                        + "\"unit\":\"degree\"},"
                        + "{\"name\":\"Longitude of natural origin\",\"value\":0,"
                        + "\"unit\":\"degree\"},"
                        + "{\"name\":\"False easting\",\"value\":0,\"unit\":\"metre\"},"
                        + "{\"name\":\"False northing\",\"value\":0,\"unit\":\"metre\"}]",
                    "[{\"name\":\"Easting\",\"abbreviation\":\"X\",\"direction\":\"south\","
                        + "\"meridian\":{\"longitude\":90},\"unit\":\"metre\"},"
                        + "{\"name\":\"Northing\",\"abbreviation\":\"Y\",\"direction\":\"south\","
                        + "\"meridian\":{\"longitude\":180},\"unit\":\"metre\"}]"),
                "ssu",
                "enu"));
    }

    private static Stream<Arguments> polarWkt2Cases() {
        return Stream.of(
            Arguments.of(3031, antarcticWkt2(), "nnu", "enu"),
            Arguments.of(32661, upsNorthWkt2(), "ssu", "neu"),
            Arguments.of(3031, antarcticSimplifiedWkt2(), "nnu", "enu"),
            Arguments.of(32661, upsNorthSimplifiedWkt2(), "ssu", "neu"));
    }

    private static Stream<Arguments> invalidPolarMeridians() {
        String valid = antarcticProjJson();
        return Stream.of(
            Arguments.of(
                "wrong easting meridian",
                valid.replace(
                    "\"meridian\":{\"longitude\":90}",
                    "\"meridian\":{\"longitude\":91}")),
            Arguments.of(
                "equal meridians",
                valid.replace(
                    "\"meridian\":{\"longitude\":90}",
                    "\"meridian\":{\"longitude\":0}")),
            Arguments.of(
                "malformed meridian",
                valid.replace(
                    "\"meridian\":{\"longitude\":90}",
                    "\"meridian\":{\"longitude\":\"east\"}")),
            Arguments.of(
                "conflicting axis name and abbreviation",
                valid.replace(
                    "\"name\":\"Easting\",\"abbreviation\":\"E\"",
                    "\"name\":\"Easting\",\"abbreviation\":\"N\"")));
    }

    private static Stream<Arguments> nearMisses() {
        return Stream.of(
            Arguments.of(
                "EPSG:3031 latitude of true scale",
                "+proj=stere +lat_0=-90 +lat_ts=-70 +datum=WGS84 +units=m"),
            Arguments.of(
                "EPSG:32661 scale factor",
                "+proj=stere +lat_0=90 +k=0.995 +x_0=2000000 +y_0=2000000 "
                    + "+datum=WGS84 +units=m +axis=neu"),
            Arguments.of(
                "EPSG:3413 longitude of origin",
                "+proj=stere +lat_0=90 +lat_ts=70 +lon_0=-44 "
                    + "+datum=WGS84 +units=m"),
            Arguments.of(
                "EPSG:6931 latitude of origin",
                "+proj=laea +lat_0=89 +datum=WGS84 +units=m"),
            Arguments.of(
                "polar definition on a different datum",
                "+proj=laea +lat_0=90 +datum=NAD83 +units=m"));
    }

    private static String antarcticProjJson() {
        return polarCrs(
            3031,
            "Antarctic Polar Stereographic",
            "Polar Stereographic (variant B)",
            "[{\"name\":\"Latitude of standard parallel\",\"value\":-71,"
                + "\"unit\":\"degree\"},"
                + "{\"name\":\"Longitude of origin\",\"value\":0,\"unit\":\"degree\"},"
                + "{\"name\":\"False easting\",\"value\":0,\"unit\":\"metre\"},"
                + "{\"name\":\"False northing\",\"value\":0,\"unit\":\"metre\"}]",
            "[{\"name\":\"Easting\",\"abbreviation\":\"E\",\"direction\":\"north\","
                + "\"meridian\":{\"longitude\":90},\"unit\":\"metre\"},"
                + "{\"name\":\"Northing\",\"abbreviation\":\"N\",\"direction\":\"north\","
                + "\"meridian\":{\"longitude\":0},\"unit\":\"metre\"}]");
    }

    private static String polarCrs(
            int epsg, String name, String method, String parameters, String axes) {
        return "{\"type\":\"ProjectedCRS\",\"name\":\""
            + name
            + "\","
            + BASE_CRS
            + "\"conversion\":{\"name\":\""
            + name
            + "\",\"method\":{\"name\":\""
            + method
            + "\"},\"parameters\":"
            + parameters
            + "},\"coordinate_system\":{\"subtype\":\"Cartesian\",\"axis\":"
            + axes
            + "},\"id\":{\"authority\":\"EPSG\",\"code\":"
            + epsg
            + "}}";
    }

    private static String upsNorthProjJson() {
        return polarCrs(
            32661,
            "Universal Polar Stereographic North",
            "Polar Stereographic (variant A)",
            "[{\"name\":\"Latitude of natural origin\",\"value\":90,\"unit\":\"degree\"},"
                + "{\"name\":\"Longitude of natural origin\",\"value\":0,\"unit\":\"degree\"},"
                + "{\"name\":\"Scale factor at natural origin\",\"value\":0.994,"
                + "\"unit\":\"unity\"},"
                + "{\"name\":\"False easting\",\"value\":2000000,\"unit\":\"metre\"},"
                + "{\"name\":\"False northing\",\"value\":2000000,\"unit\":\"metre\"}]",
            "[{\"name\":\"Northing\",\"abbreviation\":\"N\",\"direction\":\"south\","
                + "\"meridian\":{\"longitude\":180},\"unit\":\"metre\"},"
                + "{\"name\":\"Easting\",\"abbreviation\":\"E\",\"direction\":\"south\","
                + "\"meridian\":{\"longitude\":90},\"unit\":\"metre\"}]");
    }

    private static String antarcticWkt2() {
        return "PROJCRS[\"Antarctic\","
            + "BASEGEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "PRIMEM[\"Greenwich\",0]],"
            + "CONVERSION[\"Antarctic\",METHOD[\"Polar Stereographic (variant B)\"],"
            + "PARAMETER[\"Latitude of standard parallel\",-71,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"Longitude of origin\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"False easting\",0,LENGTHUNIT[\"metre\",1]],"
            + "PARAMETER[\"False northing\",0,LENGTHUNIT[\"metre\",1]]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"(E)\",north,MERIDIAN[90,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "ORDER[1],LENGTHUNIT[\"metre\",1]],"
            + "AXIS[\"(N)\",north,MERIDIAN[0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "ORDER[2],LENGTHUNIT[\"metre\",1]],"
            + "ID[\"EPSG\",3031]]";
    }

    private static String upsNorthWkt2() {
        return "PROJCRS[\"UPS\","
            + "BASEGEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "PRIMEM[\"Greenwich\",0]],"
            + "CONVERSION[\"UPS\",METHOD[\"Polar Stereographic (variant A)\"],"
            + "PARAMETER[\"Latitude of natural origin\",90,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"Longitude of natural origin\",0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "PARAMETER[\"Scale factor at natural origin\",0.994,"
            + "SCALEUNIT[\"unity\",1]],"
            + "PARAMETER[\"False easting\",2000000,LENGTHUNIT[\"metre\",1]],"
            + "PARAMETER[\"False northing\",2000000,LENGTHUNIT[\"metre\",1]]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"northing (N)\",south,MERIDIAN[180,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "ORDER[1],LENGTHUNIT[\"metre\",1]],"
            + "AXIS[\"easting (E)\",south,MERIDIAN[90,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "ORDER[2],LENGTHUNIT[\"metre\",1]],"
            + "ID[\"EPSG\",32661]]";
    }

    private static String antarcticSimplifiedWkt2() {
        return "PROJCRS[\"Antarctic\","
            + "BASEGEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "UNIT[\"degree\",0.0174532925199433]],"
            + "CONVERSION[\"Antarctic\",METHOD[\"Polar Stereographic (variant B)\"],"
            + "PARAMETER[\"Latitude of standard parallel\",-71],"
            + "PARAMETER[\"Longitude of origin\",0],"
            + "PARAMETER[\"False easting\",0],"
            + "PARAMETER[\"False northing\",0]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"(E)\",north,MERIDIAN[90,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "AXIS[\"(N)\",north,MERIDIAN[0,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "UNIT[\"metre\",1],ID[\"EPSG\",3031]]";
    }

    private static String upsNorthSimplifiedWkt2() {
        return "PROJCRS[\"UPS\","
            + "BASEGEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "UNIT[\"degree\",0.0174532925199433]],"
            + "CONVERSION[\"UPS\",METHOD[\"Polar Stereographic (variant A)\"],"
            + "PARAMETER[\"Latitude of natural origin\",90],"
            + "PARAMETER[\"Longitude of natural origin\",0],"
            + "PARAMETER[\"Scale factor at natural origin\",0.994],"
            + "PARAMETER[\"False easting\",2000000],"
            + "PARAMETER[\"False northing\",2000000]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"northing (N)\",south,MERIDIAN[180,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "AXIS[\"easting (E)\",south,MERIDIAN[90,"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]]],"
            + "UNIT[\"metre\",1],ID[\"EPSG\",32661]]";
    }
}
