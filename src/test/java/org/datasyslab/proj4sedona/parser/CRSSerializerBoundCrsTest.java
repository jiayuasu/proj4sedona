package org.datasyslab.proj4sedona.parser;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;
import org.datasyslab.proj4sedona.transform.Converter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CRSSerializerBoundCrsTest {

    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";
    private static final String THREE_PARAMETER =
        "+proj=longlat +ellps=bessel +towgs84=674.374,15.056,405.346 +no_defs";
    private static final String SEVEN_PARAMETER =
        "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.9996012717 "
            + "+x_0=400000 +y_0=-100000 +ellps=airy "
            + "+towgs84=446.448,-125.157,542.06,0.1502,0.247,0.8421,-20.4894 "
            + "+units=m +no_defs";
    private static final String[] TOWGS84_PARAMETER_NAMES = {
        "X-axis translation",
        "Y-axis translation",
        "Z-axis translation",
        "X-axis rotation",
        "Y-axis rotation",
        "Z-axis rotation",
        "Scale difference"
    };

    @BeforeAll
    static void setup() {
        ProjectionRegistry.start();
    }

    @Test
    void threeParameterTowgs84ExportsAsGeographicBoundCrs() {
        Proj original = new Proj(THREE_PARAMETER);
        double[] values = {674.374, 15.056, 405.346};

        String wkt2 = CRSSerializer.toWkt2(original);
        assertTrue(wkt2.startsWith("BOUNDCRS[SOURCECRS[GEOGCRS["), wkt2);
        assertBoundCrs(
            WktParser.parseWkt2ToProjJson(wkt2),
            "GeographicCRS", 9603, "Geocentric translations (geog2D domain)", values);

        Map<String, Object> projJson =
            CRSSerializer.toProjJsonMap(original.getParams());
        assertBoundCrs(
            projJson,
            "GeographicCRS", 9603, "Geocentric translations (geog2D domain)", values);
        assertTranslationUnits(projJson);

        assertRoundTripCoordinates(
            original, wkt2, new Point(7.438632, 46.951082));
        assertRoundTripCoordinates(
            original, CRSSerializer.toProjJson(original, false),
            new Point(7.438632, 46.951082));
    }

    @Test
    void sevenParameterTowgs84ExportsAsProjectedBoundCrs() {
        Proj original = new Proj(SEVEN_PARAMETER);
        double[] values = {
            446.448, -125.157, 542.06, 0.1502, 0.247, 0.8421, -20.4894
        };

        String wkt2 = CRSSerializer.toWkt2(original);
        assertTrue(wkt2.startsWith("BOUNDCRS[SOURCECRS[PROJCRS["), wkt2);
        assertTrue(
            wkt2.contains("PARAMETER[\"Scale difference\",0.9999795106,"),
            wkt2);
        Map<String, Object> parsedWkt = WktParser.parseWkt2ToProjJson(wkt2);
        assertBoundCrs(
            parsedWkt,
            "ProjectedCRS", 9606,
            "Position Vector transformation (geog2D domain)", values);
        assertEquals(
            "Transverse Mercator",
            map(map(map(parsedWkt, "source_crs"), "conversion"), "method").get("name"));

        Map<String, Object> projJson =
            CRSSerializer.toProjJsonMap(original.getParams());
        assertBoundCrs(
            projJson,
            "ProjectedCRS", 9606,
            "Position Vector transformation (geog2D domain)", values);
        assertSevenParameterUnits(projJson);

        assertRoundTripCoordinates(
            original, wkt2, new Point(651409.903, 313177.270));
        assertRoundTripCoordinates(
            original, CRSSerializer.toProjJson(original, false),
            new Point(651409.903, 313177.270));
    }

    @Test
    void sevenValueZeroTailStillUsesPositionVectorMethod() {
        Proj original = new Proj(
            "+proj=longlat +ellps=bessel +towgs84=1,2,3,0,0,0,0 +no_defs");
        double[] values = {1, 2, 3, 0, 0, 0, 0};

        String wkt2 = CRSSerializer.toWkt2(original);
        assertTrue(wkt2.contains("PARAMETER[\"Scale difference\",1,"), wkt2);
        assertBoundCrs(
            WktParser.parseWkt2ToProjJson(wkt2),
            "GeographicCRS", 9606,
            "Position Vector transformation (geog2D domain)", values);
        assertBoundCrs(
            CRSSerializer.toProjJsonMap(original.getParams()),
            "GeographicCRS", 9606,
            "Position Vector transformation (geog2D domain)", values);
    }

    @Test
    void zeroTowgs84OnWgs84RemainsAnUnboundCrs() {
        Proj equivalent = new Proj(
            "+proj=longlat +ellps=WGS84 +towgs84=0,0,0 +no_defs");
        assertTrue(CRSSerializer.toWkt2(equivalent).startsWith("GEOGCRS["));
        assertEquals(
            "GeographicCRS",
            CRSSerializer.toProjJsonMap(equivalent.getParams()).get("type"));
    }

    @Test
    void zeroTowgs84OnAnotherEllipsoidStillUsesBoundCrs() {
        Proj threeParameters = new Proj(
            "+proj=longlat +ellps=bessel +towgs84=0,0,0 +no_defs");
        double[] threeValues = {0, 0, 0};
        String threeWkt = CRSSerializer.toWkt2(threeParameters);
        assertBoundCrs(
            WktParser.parseWkt2ToProjJson(threeWkt),
            "GeographicCRS", 9603,
            "Geocentric translations (geog2D domain)", threeValues);
        assertBoundCrs(
            CRSSerializer.toProjJsonMap(threeParameters.getParams()),
            "GeographicCRS", 9603,
            "Geocentric translations (geog2D domain)", threeValues);
        assertRoundTripCoordinates(
            threeParameters, threeWkt, new Point(7.4, 46.9));
        assertRoundTripCoordinates(
            threeParameters, CRSSerializer.toProjJson(threeParameters, false),
            new Point(7.4, 46.9));

        Proj sevenParameters = new Proj(
            "+proj=longlat +ellps=bessel +towgs84=0,0,0,0,0,0,0 +no_defs");
        double[] sevenValues = {0, 0, 0, 0, 0, 0, 0};
        String sevenWkt = CRSSerializer.toWkt2(sevenParameters);
        assertBoundCrs(
            WktParser.parseWkt2ToProjJson(sevenWkt),
            "GeographicCRS", 9606,
            "Position Vector transformation (geog2D domain)", sevenValues);
        assertBoundCrs(
            CRSSerializer.toProjJsonMap(sevenParameters.getParams()),
            "GeographicCRS", 9606,
            "Position Vector transformation (geog2D domain)", sevenValues);
        assertRoundTripCoordinates(
            sevenParameters, sevenWkt, new Point(7.4, 46.9));
    }

    @Test
    void geocentricProjJsonUsesGeocentricTargetAndMethods() {
        Proj threeParameters = new Proj(
            "+proj=geocent +ellps=bessel +towgs84=674.374,15.056,405.346 "
                + "+units=m +no_defs");
        assertGeocentricBoundCrs(
            CRSSerializer.toProjJsonMap(threeParameters.getParams()),
            1031, "Geocentric translations (geocentric domain)",
            new double[]{674.374, 15.056, 405.346});

        Proj sevenParameters = new Proj(
            "+proj=geocent +ellps=airy "
                + "+towgs84=446.448,-125.157,542.06,0.1502,0.247,0.8421,-20.4894 "
                + "+units=m +no_defs");
        Map<String, Object> projJson =
            CRSSerializer.toProjJsonMap(sevenParameters.getParams());
        assertGeocentricBoundCrs(
            projJson,
            1033, "Position Vector transformation (geocentric domain)",
            new double[]{446.448, -125.157, 542.06, 0.1502, 0.247, 0.8421, -20.4894});
        assertSevenParameterUnits(projJson);
        assertRoundTripCoordinates(
            sevenParameters,
            CRSSerializer.toProjJson(sevenParameters, false),
            new Point(3980000, 100000, 4970000));

        assertThrows(
            UnsupportedOperationException.class,
            () -> CRSSerializer.toWkt2(sevenParameters));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unsupportedImportedBoundCrsOperationsAreRejected() {
        Map<String, Object> arbitraryTarget =
            CRSSerializer.toProjJsonMap(new Proj(THREE_PARAMETER).getParams());
        map(map(arbitraryTarget, "target_crs"), "id").put("code", 4269);
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(arbitraryTarget));

        Map<String, Object> misleadingTargetName =
            CRSSerializer.toProjJsonMap(new Proj(THREE_PARAMETER).getParams());
        Map<String, Object> misleadingTarget =
            map(misleadingTargetName, "target_crs");
        misleadingTarget.remove("id");
        map(misleadingTarget, "datum").put("name", "North American Datum 1983");
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(misleadingTargetName));

        String wkt = CRSSerializer.toWkt2(new Proj(THREE_PARAMETER));
        String arbitraryTargetWkt = wkt.replace(
            "ID[\"EPSG\",4326]]],ABRIDGEDTRANSFORMATION",
            "ID[\"EPSG\",4269]]],ABRIDGEDTRANSFORMATION");
        assertNotEquals(wkt, arbitraryTargetWkt);
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(arbitraryTargetWkt));

        Map<String, Object> coordinateFrame =
            CRSSerializer.toProjJsonMap(new Proj(SEVEN_PARAMETER).getParams());
        Map<String, Object> frameMethod =
            map(map(coordinateFrame, "transformation"), "method");
        frameMethod.put(
            "name", "Coordinate Frame rotation (geog2D domain)");
        map(frameMethod, "id").put("code", 9607);
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(coordinateFrame));

        Map<String, Object> wrongArity =
            CRSSerializer.toProjJsonMap(new Proj(THREE_PARAMETER).getParams());
        Map<String, Object> wrongArityMethod =
            map(map(wrongArity, "transformation"), "method");
        wrongArityMethod.put(
            "name", "Position Vector transformation (geog2D domain)");
        map(wrongArityMethod, "id").put("code", 9606);
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(wrongArity));

        Proj geocentric = new Proj(
            "+proj=geocent +ellps=bessel +towgs84=1,2,3 +units=m +no_defs");
        Map<String, Object> wrongDomain =
            CRSSerializer.toProjJsonMap(geocentric.getParams());
        Map<String, Object> wrongDomainMethod =
            map(map(wrongDomain, "transformation"), "method");
        wrongDomainMethod.put(
            "name", "Geocentric translations (geog2D domain)");
        map(wrongDomainMethod, "id").put("code", 9603);
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(wrongDomain));

        Map<String, Object> wrongUnit =
            CRSSerializer.toProjJsonMap(new Proj(THREE_PARAMETER).getParams());
        List<Map<String, Object>> wrongUnitParameters =
            (List<Map<String, Object>>) map(
                wrongUnit, "transformation").get("parameters");
        wrongUnitParameters.get(0).put(
            "unit",
            Map.of(
                "type", "LinearUnit",
                "name", "foot",
                "conversion_factor", 0.3048));
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(wrongUnit));

        Map<String, Object> wrongOrder =
            CRSSerializer.toProjJsonMap(new Proj(THREE_PARAMETER).getParams());
        List<Map<String, Object>> wrongOrderParameters =
            (List<Map<String, Object>>) map(
                wrongOrder, "transformation").get("parameters");
        Map<String, Object> first = wrongOrderParameters.get(0);
        wrongOrderParameters.set(0, wrongOrderParameters.get(1));
        wrongOrderParameters.set(1, first);
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(wrongOrder));
    }

    @Test
    void ntv2BoundCrsImportRemainsSupported() {
        Map<String, Object> bound =
            CRSSerializer.toProjJsonMap(new Proj(THREE_PARAMETER).getParams());
        Map<String, Object> transformation = map(bound, "transformation");
        transformation.put("method", Map.of("name", "NTv2"));
        transformation.put(
            "parameters",
            List.of(Map.of("name", "Latitude and longitude difference file",
                "value", "@example.gsb")));

        assertEquals("@example.gsb", WktParser.parse(bound).getNadgrids());
    }

    @Test
    @SuppressWarnings("unchecked")
    void epsgOperationIdentifiersAreAuthoritativeOverLabels() {
        Proj original = new Proj(THREE_PARAMETER);
        Map<String, Object> bound =
            CRSSerializer.toProjJsonMap(original.getParams());
        Map<String, Object> transformation = map(bound, "transformation");
        map(transformation, "method").put("name", "localized method label");
        List<Map<String, Object>> parameters =
            (List<Map<String, Object>>) transformation.get("parameters");
        for (int i = 0; i < parameters.size(); i++) {
            parameters.get(i).put("name", "localized parameter " + i);
        }

        assertArrayEquals(
            original.getParams().datum.getDatumParams(),
            WktParser.parse(bound).getDatumParams(),
            0.0);
    }

    @Test
    void epsgIdentifiersPreserveLocalizedWktScaleSemantics() {
        Proj original = new Proj(SEVEN_PARAMETER);
        String localized = CRSSerializer.toWkt2(original)
            .replace(
                "Position Vector transformation (geog2D domain)",
                "localized method label");
        for (int i = 0; i < 7; i++) {
            localized = localized.replace(
                TOWGS84_PARAMETER_NAMES[i],
                "localized parameter " + i);
        }

        assertRoundTripCoordinates(
            original, localized, new Point(651409.903, 313177.270));
    }

    @Test
    void idlessWgs84EnsembleTargetIsAcceptedStructurally() {
        Proj original = new Proj(THREE_PARAMETER);
        Map<String, Object> bound =
            CRSSerializer.toProjJsonMap(original.getParams());
        Map<String, Object> target = map(bound, "target_crs");
        target.remove("id");
        Map<String, Object> ensemble = map(target, "datum");
        target.remove("datum");
        ensemble.put("name", "World Geodetic System 1984 ensemble");
        target.put("datum_ensemble", ensemble);

        assertArrayEquals(
            original.getParams().datum.getDatumParams(),
            WktParser.parse(bound).getDatumParams(),
            0.0);
    }

    @SuppressWarnings("unchecked")
    private static void assertBoundCrs(
            Map<String, Object> root, String sourceType, int methodCode,
            String methodName, double[] expectedValues) {
        assertEquals("BoundCRS", root.get("type"));
        assertFalse(root.containsKey("id"), "the source authority must not label the wrapper");

        Map<String, Object> source = map(root, "source_crs");
        assertEquals(sourceType, source.get("type"));

        Map<String, Object> target = map(root, "target_crs");
        assertEquals("GeographicCRS", target.get("type"));
        assertEquals(4326, idCode(target));
        List<Map<String, Object>> axes =
            (List<Map<String, Object>>) map(target, "coordinate_system").get("axis");
        assertEquals("north", axes.get(0).get("direction"));
        assertEquals("east", axes.get(1).get("direction"));

        assertTransformation(root, methodCode, methodName, expectedValues);
    }

    @SuppressWarnings("unchecked")
    private static void assertGeocentricBoundCrs(
            Map<String, Object> root, int methodCode,
            String methodName, double[] expectedValues) {
        assertEquals("BoundCRS", root.get("type"));
        assertEquals("GeodeticCRS", map(root, "source_crs").get("type"));

        Map<String, Object> target = map(root, "target_crs");
        assertEquals("GeodeticCRS", target.get("type"));
        assertEquals(4978, idCode(target));
        List<Map<String, Object>> axes =
            (List<Map<String, Object>>) map(target, "coordinate_system").get("axis");
        assertEquals(3, axes.size());
        assertEquals("geocentricX", axes.get(0).get("direction"));
        assertEquals("geocentricY", axes.get(1).get("direction"));
        assertEquals("geocentricZ", axes.get(2).get("direction"));

        assertTransformation(root, methodCode, methodName, expectedValues);
    }

    @SuppressWarnings("unchecked")
    private static void assertTransformation(
            Map<String, Object> root, int methodCode,
            String methodName, double[] expectedValues) {
        Map<String, Object> transformation = map(root, "transformation");
        Map<String, Object> method = map(transformation, "method");
        assertEquals(methodName, method.get("name"));
        assertEquals(methodCode, idCode(method));

        List<Map<String, Object>> parameters =
            (List<Map<String, Object>>) transformation.get("parameters");
        assertEquals(expectedValues.length, parameters.size());
        for (int i = 0; i < expectedValues.length; i++) {
            assertEquals(8605 + i, idCode(parameters.get(i)));
            assertEquals(expectedValues[i], number(parameters.get(i).get("value")), 1e-9);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertTranslationUnits(Map<String, Object> root) {
        List<Map<String, Object>> parameters =
            (List<Map<String, Object>>) map(root, "transformation").get("parameters");
        for (Map<String, Object> parameter : parameters) {
            assertEquals("metre", parameter.get("unit"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertSevenParameterUnits(Map<String, Object> root) {
        List<Map<String, Object>> parameters =
            (List<Map<String, Object>>) map(root, "transformation").get("parameters");
        for (int i = 0; i < 3; i++) {
            assertEquals("metre", parameters.get(i).get("unit"));
        }
        for (int i = 3; i < 6; i++) {
            Map<String, Object> unit = map(parameters.get(i), "unit");
            assertEquals("AngularUnit", unit.get("type"));
            assertEquals("arc-second", unit.get("name"));
            assertEquals(Values.SEC_TO_RAD, number(unit.get("conversion_factor")), 0.0);
        }
        Map<String, Object> scaleUnit = map(parameters.get(6), "unit");
        assertEquals("ScaleUnit", scaleUnit.get("type"));
        assertEquals("parts per million", scaleUnit.get("name"));
        assertEquals(1e-6, number(scaleUnit.get("conversion_factor")), 0.0);
    }

    private static void assertRoundTripCoordinates(
            Proj original, String serialized, Point sourcePoint) {
        Proj reimported = new Proj(serialized);
        assertEquals(
            original.getProjection().getClass(),
            reimported.getProjection().getClass(),
            serialized);
        assertArrayEquals(
            original.getParams().datum.getDatumParams(),
            reimported.getParams().datum.getDatumParams(),
            1e-12,
            serialized);

        Proj wgs84 = new Proj(WGS84);
        Point expected = new Converter(original, wgs84).forward(
            new Point(sourcePoint.x, sourcePoint.y, sourcePoint.z));
        Point actual = new Converter(reimported, wgs84).forward(
            new Point(sourcePoint.x, sourcePoint.y, sourcePoint.z));
        assertEquals(expected.x, actual.x, 1e-10, serialized);
        assertEquals(expected.y, actual.y, 1e-10, serialized);
        assertEquals(expected.z, actual.z, 1e-8, serialized);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return map(parent.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }

    private static int idCode(Map<String, Object> object) {
        return (int) number(map(object, "id").get("code"));
    }

    private static double number(Object value) {
        assertInstanceOf(Number.class, value);
        return ((Number) value).doubleValue();
    }
}
