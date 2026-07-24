package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.datasyslab.proj4sedona.core.Proj;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PolarAxisProjSerializationTest {

    @ParameterizedTest
    @CsvSource({"-90, north, nnu", "90, south, ssu"})
    void omitsMeridianQualifiedPolarAxesFromLegacyProj(
            int latitudeOfOrigin, String direction, String parsedAxis) {
        Proj proj = new Proj(polarProjJson(latitudeOfOrigin, direction));

        assertEquals(parsedAxis, proj.getParams().axis);
        String serialized = proj.toProjString();
        assertFalse(serialized.contains("+axis="), serialized);
        assertEquals(serialized, new Proj(serialized).toProjString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nnu", "ssu"})
    void rejectsDuplicateAxesFromRawProjStrings(String axis) {
        Proj proj = new Proj(
            "+proj=laea +lat_0=90 +datum=WGS84 +units=m +axis=" + axis);

        UnsupportedOperationException error =
            assertThrows(UnsupportedOperationException.class, proj::toProjString);
        assertTrue(error.getMessage().contains("Axis " + axis), error.getMessage());
    }

    @Test
    void preservesValidNonDefaultProjAxis() {
        Proj proj = new Proj(
            "+proj=stere +lat_0=90 +datum=WGS84 +units=m +axis=neu");

        assertTrue(proj.toProjString().contains("+axis=neu"));
    }

    private static String polarProjJson(int latitudeOfOrigin, String direction) {
        int northingMeridian = latitudeOfOrigin > 0 ? 180 : 0;
        return "{\"type\":\"ProjectedCRS\",\"name\":\"Polar\","
            + "\"conversion\":{\"name\":\"Polar\","
            + "\"method\":{\"name\":\"Lambert Azimuthal Equal Area\"},"
            + "\"parameters\":["
            + "{\"name\":\"Latitude of natural origin\",\"value\":"
            + latitudeOfOrigin
            + ",\"unit\":\"degree\"},"
            + "{\"name\":\"Longitude of natural origin\",\"value\":0,"
            + "\"unit\":\"degree\"},"
            + "{\"name\":\"False easting\",\"value\":0,\"unit\":\"metre\"},"
            + "{\"name\":\"False northing\",\"value\":0,\"unit\":\"metre\"}]},"
            + "\"coordinate_system\":{\"subtype\":\"Cartesian\",\"axis\":["
            + "{\"name\":\"Easting\",\"direction\":\""
            + direction
            + "\",\"meridian\":{\"longitude\":90},\"unit\":\"metre\"},"
            + "{\"name\":\"Northing\",\"direction\":\""
            + direction
            + "\",\"meridian\":{\"longitude\":"
            + northingMeridian
            + "},\"unit\":\"metre\"}]}}";
    }
}
