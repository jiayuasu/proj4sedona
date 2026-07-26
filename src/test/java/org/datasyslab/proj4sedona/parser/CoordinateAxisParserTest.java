package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.CoordinateAxis;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.junit.jupiter.api.Test;

class CoordinateAxisParserTest {

    @Test
    void retainsPolarAxisMetadataFromWkt2AndPropagatesToProjectionParams() {
        ProjectionDef def = WktParser.parse(upsNorthWkt2());
        List<CoordinateAxis> axes = def.getCoordinateAxes();

        assertEquals(2, axes.size());
        assertAxis(
            axes.get(0), "northing", "N", "south", 1,
            "LinearUnit", "metre", 1.0, 180.0, "degree", Values.D2R);
        assertAxis(
            axes.get(1), "easting", "E", "south", 2,
            "LinearUnit", "metre", 1.0, 90.0, "degree", Values.D2R);
        assertEquals("ssu", def.getAxis());
        assertEquals("Cartesian", def.getCoordinateSystemType());

        Proj proj = new Proj(upsNorthWkt2());
        assertEquals(axes, proj.getParams().coordinateAxes);
        assertEquals("Cartesian", proj.getParams().coordinateSystemType);
    }

    @Test
    void retainsProjJsonNonDegreeMeridiansAndBareUnits() {
        Map<String, Object> grad = object(
            "type", "AngularUnit",
            "name", "grad",
            "conversion_factor", Math.PI / 200.0);
        Map<String, Object> foot = object(
            "type", "LinearUnit",
            "name", "foot",
            "conversion_factor", 0.3048);
        Map<String, Object> firstAxis = object(
            "name", "Grid north",
            "abbreviation", "Gn",
            "direction", "north",
            "order", 1.0,
            "unit", foot,
            "meridian", object(
                "longitude", object("value", 100.0, "unit", grad)));
        Map<String, Object> secondAxis = object(
            "name", "Grid east",
            "abbreviation", "Ge",
            "direction", "north",
            "unit", "metre",
            "meridian", object("longitude", 90.0));
        Map<String, Object> projjson = object(
            "coordinate_system", object(
                "subtype", "Cartesian",
                "axis", Arrays.asList(firstAxis, secondAxis)));

        ProjectionDef def = WktParser.parse(projjson);
        List<CoordinateAxis> axes = def.getCoordinateAxes();

        assertEquals("nnu", def.getAxis());
        assertAxis(
            axes.get(0), "Grid north", "Gn", "north", 1,
            "LinearUnit", "foot", 0.3048,
            100.0, "grad", Math.PI / 200.0);
        assertAxis(
            axes.get(1), "Grid east", "Ge", "north", null,
            "LinearUnit", "metre", 1.0,
            90.0, "degree", Values.D2R);
    }

    @Test
    void appliesSimplifiedCoordinateSystemUnitToEveryAxis() {
        String wkt = "GEODCRS[\"NTF (Paris)\","
            + "DATUM[\"Nouvelle Triangulation Francaise (Paris)\","
            + "ELLIPSOID[\"Clarke 1880 (IGN)\",6378249.2,293.466021293627]],"
            + "PRIMEM[\"Paris\",2.5969213],CS[ellipsoidal,2],"
            + "AXIS[\"geodetic latitude (Lat)\",north],"
            + "AXIS[\"geodetic longitude (Lon)\",east],"
            + "UNIT[\"grad\",0.0157079632679489],ID[\"EPSG\",4807]]";

        List<CoordinateAxis> axes = WktParser.parse(wkt).getCoordinateAxes();

        assertEquals(2, axes.size());
        assertEquals("geodetic latitude", axes.get(0).getName());
        assertEquals("Lat", axes.get(0).getAbbreviation());
        assertEquals("geodetic longitude", axes.get(1).getName());
        assertEquals("Lon", axes.get(1).getAbbreviation());
        for (CoordinateAxis axis : axes) {
            assertUnit(
                axis.getUnit(), "AngularUnit", "grad", 0.0157079632679489);
        }
    }

    @Test
    void resolvesUpsNorthSimplifiedConversionParameterUnits() {
        ProjectionDef def = WktParser.parse(upsNorthSimplifiedWkt2());

        assertEquals(90.0 * Values.D2R, def.getLat0(), 1e-15);
        assertNull(def.getLatTs());
        assertEquals(0.0, def.getLong0(), 0.0);
        assertEquals(0.994, def.getK0(), 0.0);
        assertEquals(2_000_000.0, def.getX0(), 0.0);
        assertEquals(2_000_000.0, def.getY0(), 0.0);
        assertEquals("ssu", def.getAxis());
        assertAxis(
            def.getCoordinateAxes().get(0), "northing", "N", "south", null,
            "LinearUnit", "metre", 1.0, 180.0, "degree", Values.D2R);
    }

    @Test
    void resolvesAntarcticSimplifiedConversionParameterUnits() {
        ProjectionDef def = WktParser.parse(antarcticSimplifiedWkt2());

        assertEquals(-90.0 * Values.D2R, def.getLat0(), 1e-15);
        assertEquals(-71.0 * Values.D2R, def.getLatTs(), 1e-15);
        assertNull(def.getLat1());
        assertEquals(0.0, def.getLong0(), 0.0);
        assertEquals(0.0, def.getX0(), 0.0);
        assertEquals(0.0, def.getY0(), 0.0);
        assertEquals("nnu", def.getAxis());
        assertAxis(
            def.getCoordinateAxes().get(0), "", "E", "north", null,
            "LinearUnit", "metre", 1.0, 90.0, "degree", Values.D2R);
        assertAxis(
            def.getCoordinateAxes().get(1), "", "N", "north", null,
            "LinearUnit", "metre", 1.0, 0.0, "degree", Values.D2R);
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesLexicalAxisOrderWhenOrderMetadataDisagrees() {
        String wkt = "GEOGCRS[\"Reversed metadata\","
            + "DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "PRIMEM[\"Greenwich\",0],CS[ellipsoidal,2],"
            + "AXIS[\"longitude (Lon)\",east,ORDER[2],"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]],"
            + "AXIS[\"latitude (Lat)\",north,ORDER[1],"
            + "ANGLEUNIT[\"degree\",0.0174532925199433]]]";

        Map<String, Object> projjson = WktParser.parseWkt2ToProjJson(wkt);
        Map<String, Object> cs =
            (Map<String, Object>) projjson.get("coordinate_system");
        List<Map<String, Object>> axes =
            (List<Map<String, Object>>) cs.get("axis");

        assertEquals("longitude", axes.get(0).get("name"));
        assertEquals(2, axes.get(0).get("order"));
        assertEquals("latitude", axes.get(1).get("name"));
        assertEquals(1, axes.get(1).get("order"));

        ProjectionDef def = WktParser.parse(wkt);
        assertEquals("enu", def.getAxis());
        assertEquals(Integer.valueOf(2), def.getCoordinateAxes().get(0).getOrder());
        assertEquals(Integer.valueOf(1), def.getCoordinateAxes().get(1).getOrder());
    }

    @Test
    void rejectsMalformedExplicitAxisMetadata() {
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(object(
                "type", "ProjectedCRS",
                "id", object("authority", "EPSG", "code", 32661))));
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(object(
                "type", "ProjectedCRS",
                "coordinate_system", object(
                    "subtype", "Cartesian", "axis", Arrays.asList()),
                "id", object("authority", "EPSG", "code", 5041))));
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(object(
                "type", "ProjectedCRS",
                "coordinate_system", object(
                    "axis", Arrays.asList(
                        object("name", "X", "direction", "east"),
                        object("name", "Y", "direction", "north"))),
                "id", object("authority", "EPSG", "code", 5041))));

        Map<String, Object> fractionalOrder = object(
            "name", "Northing", "direction", "north", "order", 1.5);
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(projJsonWithAxis(fractionalOrder)));

        Map<String, Object> missingLongitude = object(
            "name", "Northing", "direction", "north",
            "meridian", object("type", "Meridian"));
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(projJsonWithAxis(missingLongitude)));

        Map<String, Object> malformedUnit = object(
            "name", "Northing", "direction", "north",
            "meridian", object(
                "longitude", object(
                    "value", 90,
                    "unit", object(
                        "type", "AngularUnit",
                        "name", "degree",
                        "conversion_factor", "invalid"))));
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(projJsonWithAxis(malformedUnit)));

        String missingWktMeridianUnit = upsNorthSimplifiedWkt2().replace(
            "MERIDIAN[180,UNIT[\"degree\",0.0174532925199433]]",
            "MERIDIAN[180]");
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(missingWktMeridianUnit));

        String malformedWktMeridianUnit = upsNorthSimplifiedWkt2().replace(
            "MERIDIAN[180,UNIT[\"degree\",0.0174532925199433]]",
            "MERIDIAN[180,UNIT[\"degree\",\"bogus\"]]");
        assertThrows(
            IllegalArgumentException.class,
            () -> WktParser.parse(malformedWktMeridianUnit));
    }

    private static void assertAxis(
            CoordinateAxis axis,
            String name,
            String abbreviation,
            String direction,
            Integer order,
            String unitType,
            String unitName,
            double unitFactor,
            double meridianLongitude,
            String meridianUnitName,
            double meridianUnitFactor) {
        assertEquals(name, axis.getName());
        assertEquals(abbreviation, axis.getAbbreviation());
        assertEquals(direction, axis.getDirection());
        assertEquals(order, axis.getOrder());
        assertUnit(axis.getUnit(), unitType, unitName, unitFactor);
        assertEquals(meridianLongitude, axis.getMeridian().getLongitude(), 0.0);
        assertUnit(
            axis.getMeridian().getUnit(),
            "AngularUnit",
            meridianUnitName,
            meridianUnitFactor);
    }

    private static void assertUnit(
            CoordinateAxis.Unit unit,
            String type,
            String name,
            double conversionFactor) {
        assertEquals(type, unit.getType());
        assertEquals(name, unit.getName());
        assertEquals(conversionFactor, unit.getConversionFactor(), 1e-15);
    }

    private static Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i].toString(), values[i + 1]);
        }
        return result;
    }

    private static Map<String, Object> projJsonWithAxis(Map<String, Object> axis) {
        return object(
            "coordinate_system", object(
                "subtype", "Cartesian",
                "axis", Arrays.asList(axis)));
    }

    private static String upsNorthWkt2() {
        return "PROJCRS[\"WGS 84 / UPS North (N,E)\","
            + "BASEGEOGCRS[\"WGS 84\",DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "PRIMEM[\"Greenwich\",0]],"
            + "CONVERSION[\"Universal Polar Stereographic North\","
            + "METHOD[\"Polar Stereographic (variant A)\"],"
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

    private static String upsNorthSimplifiedWkt2() {
        return "PROJCRS[\"WGS 84 / UPS North (N,E)\","
            + simplifiedWgs84Base()
            + ",CONVERSION[\"Universal Polar Stereographic North\","
            + "METHOD[\"Polar Stereographic (variant A)\"],"
            + "PARAMETER[\"Latitude of natural origin\",90],"
            + "PARAMETER[\"Longitude of natural origin\",0],"
            + "PARAMETER[\"Scale factor at natural origin\",0.994],"
            + "PARAMETER[\"False easting\",2000000],"
            + "PARAMETER[\"False northing\",2000000]],"
            + "CS[Cartesian,2],"
            + "AXIS[\"northing (N)\",south,MERIDIAN[180,"
            + "UNIT[\"degree\",0.0174532925199433]]],"
            + "AXIS[\"easting (E)\",south,MERIDIAN[90,"
            + "UNIT[\"degree\",0.0174532925199433]]],"
            + "UNIT[\"metre\",1],ID[\"EPSG\",32661]]";
    }

    private static String antarcticSimplifiedWkt2() {
        return "PROJCRS[\"WGS 84 / Antarctic Polar Stereographic\","
            + simplifiedWgs84Base()
            + ",CONVERSION[\"Antarctic Polar Stereographic\","
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

    private static String simplifiedWgs84Base() {
        return "BASEGEOGCRS[\"WGS 84\","
            + "DATUM[\"World Geodetic System 1984\","
            + "ELLIPSOID[\"WGS 84\",6378137,298.257223563]],"
            + "UNIT[\"degree\",0.0174532925199433]]";
    }
}
