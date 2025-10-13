package org.proj4.projjson;

import org.junit.jupiter.api.Test;
import org.proj4.Proj4Sedona;
import org.proj4.core.Point;
import org.proj4.core.Projection;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PROJJSON support functionality.
 */
public class ProjJsonTest {
    
    @Test
    public void testParseProjJsonString() throws Exception {
        // Test PROJJSON for WGS84 (Geographic CRS)
        String wgs84ProjJson = "{\n" +
            "    \"$schema\": \"https://proj.org/schemas/v0.7/projjson.schema.json\",\n" +
            "    \"type\": \"GeographicCRS\",\n" +
            "    \"name\": \"WGS 84\",\n" +
            "    \"datum\": {\n" +
            "        \"type\": \"GeodeticReferenceFrame\",\n" +
            "        \"name\": \"World Geodetic System 1984\",\n" +
            "        \"ellipsoid\": {\n" +
            "            \"name\": \"WGS 84\",\n" +
            "            \"semi_major_axis\": 6378137,\n" +
            "            \"inverse_flattening\": 298.257223563\n" +
            "        }\n" +
            "    },\n" +
            "    \"coordinate_system\": {\n" +
            "        \"subtype\": \"ellipsoidal\",\n" +
            "        \"axis\": [\n" +
            "            {\n" +
            "                \"name\": \"Longitude\",\n" +
            "                \"abbreviation\": \"lon\",\n" +
            "                \"direction\": \"east\",\n" +
            "                \"unit\": \"degree\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"Latitude\",\n" +
            "                \"abbreviation\": \"lat\",\n" +
            "                \"direction\": \"north\",\n" +
            "                \"unit\": \"degree\"\n" +
            "            }\n" +
            "        ]\n" +
            "    }\n" +
            "}";
        
        Projection proj = Proj4Sedona.fromProjJson(wgs84ProjJson);
        assertNotNull(proj);
        assertEquals("longlat", proj.projName);
        assertEquals("WGS84", proj.datumCode);
    }
    
    @Test
    public void testParseProjJsonDefinition() {
        // Create a PROJJSON definition for UTM Zone 15
        ProjJsonDefinition definition = new ProjJsonDefinition();
        definition.setSchema("https://proj.org/schemas/v0.7/projjson.schema.json");
        definition.setType("ProjectedCRS");
        definition.setName("WGS 84 / UTM zone 15N");
        
        // Set base CRS
        ProjJsonDefinition.BaseCrs baseCrs = new ProjJsonDefinition.BaseCrs();
        baseCrs.setType("GeographicCRS");
        baseCrs.setName("WGS 84");
        
        ProjJsonDefinition.Datum datum = new ProjJsonDefinition.Datum();
        datum.setType("GeodeticReferenceFrame");
        datum.setName("World Geodetic System 1984");
        
        ProjJsonDefinition.Ellipsoid ellipsoid = new ProjJsonDefinition.Ellipsoid();
        ellipsoid.setName("WGS 84");
        ellipsoid.setSemiMajorAxis(6378137.0);
        ellipsoid.setInverseFlattening(298.257223563);
        datum.setEllipsoid(ellipsoid);
        baseCrs.setDatum(datum);
        definition.setBaseCrs(baseCrs);
        
        // Set conversion
        ProjJsonDefinition.Conversion conversion = new ProjJsonDefinition.Conversion();
        conversion.setName("UTM zone 15N");
        
        ProjJsonDefinition.Conversion.Method method = new ProjJsonDefinition.Conversion.Method();
        method.setName("Transverse Mercator");
        conversion.setMethod(method);
        
        // Add parameters
        java.util.List<ProjJsonDefinition.Conversion.Parameter> parameters = new java.util.ArrayList<>();
        
        ProjJsonDefinition.Conversion.Parameter lon0 = new ProjJsonDefinition.Conversion.Parameter();
        lon0.setName("Longitude of natural origin");
        lon0.setValue(-93.0);
        parameters.add(lon0);
        
        ProjJsonDefinition.Conversion.Parameter lat0 = new ProjJsonDefinition.Conversion.Parameter();
        lat0.setName("Latitude of natural origin");
        lat0.setValue(0.0);
        parameters.add(lat0);
        
        ProjJsonDefinition.Conversion.Parameter k0 = new ProjJsonDefinition.Conversion.Parameter();
        k0.setName("Scale factor at natural origin");
        k0.setValue(0.9996);
        parameters.add(k0);
        
        ProjJsonDefinition.Conversion.Parameter x0 = new ProjJsonDefinition.Conversion.Parameter();
        x0.setName("False easting");
        x0.setValue(500000.0);
        parameters.add(x0);
        
        ProjJsonDefinition.Conversion.Parameter y0 = new ProjJsonDefinition.Conversion.Parameter();
        y0.setName("False northing");
        y0.setValue(0.0);
        parameters.add(y0);
        
        conversion.setParameters(parameters);
        definition.setConversion(conversion);
        
        // Set coordinate system
        ProjJsonDefinition.CoordinateSystem coordSys = new ProjJsonDefinition.CoordinateSystem();
        coordSys.setSubtype("cartesian");
        definition.setCoordinateSystem(coordSys);
        
        // Test parsing
        Projection proj = Proj4Sedona.fromProjJson(definition);
        assertNotNull(proj);
        assertEquals("tmerc", proj.projName);
        assertEquals("WGS84", proj.datumCode);
    }
    
    @Test
    public void testProjJsonToProjString() {
        // Create a simple PROJJSON definition
        ProjJsonDefinition definition = new ProjJsonDefinition();
        definition.setType("GeographicCRS");
        definition.setName("WGS 84");
        
        ProjJsonDefinition.Datum datum = new ProjJsonDefinition.Datum();
        datum.setName("WGS 84");
        
        ProjJsonDefinition.Ellipsoid ellipsoid = new ProjJsonDefinition.Ellipsoid();
        ellipsoid.setName("WGS 84");
        ellipsoid.setSemiMajorAxis(6378137.0);
        ellipsoid.setInverseFlattening(298.257223563);
        datum.setEllipsoid(ellipsoid);
        definition.setDatum(datum);
        
        String projString = Proj4Sedona.toProjString(definition);
        assertNotNull(projString);
        assertTrue(projString.contains("+proj=longlat"));
        assertTrue(projString.contains("+datum=WGS84"));
    }
    
    @Test
    public void testProjStringToProjJson() {
        String projString = "+proj=longlat +ellps=WGS84 +datum=WGS84 +units=degrees";
        ProjJsonDefinition definition = Proj4Sedona.toProjJson(projString);
        
        assertNotNull(definition);
        assertEquals("GeographicCRS", definition.getType());
        assertNotNull(definition.getName());
    }
    
    @Test
    public void testProjJsonTransformation() throws Exception {
        // Create WGS84 PROJJSON
        String wgs84ProjJson = "{\n" +
            "    \"type\": \"GeographicCRS\",\n" +
            "    \"name\": \"WGS 84\",\n" +
            "    \"datum\": {\n" +
            "        \"name\": \"WGS 84\",\n" +
            "        \"ellipsoid\": {\n" +
            "            \"name\": \"WGS 84\",\n" +
            "            \"semi_major_axis\": 6378137,\n" +
            "            \"inverse_flattening\": 298.257223563\n" +
            "        }\n" +
            "    }\n" +
            "}";
        
        // Create UTM Zone 15 PROJJSON
        String utmProjJson = "{\n" +
            "    \"type\": \"ProjectedCRS\",\n" +
            "    \"name\": \"WGS 84 / UTM zone 15N\",\n" +
            "    \"base_crs\": {\n" +
            "        \"type\": \"GeographicCRS\",\n" +
            "        \"name\": \"WGS 84\",\n" +
            "        \"datum\": {\n" +
            "            \"name\": \"WGS 84\",\n" +
            "            \"ellipsoid\": {\n" +
            "                \"name\": \"WGS 84\",\n" +
            "                \"semi_major_axis\": 6378137,\n" +
            "                \"inverse_flattening\": 298.257223563\n" +
            "            }\n" +
            "        }\n" +
            "    },\n" +
            "    \"conversion\": {\n" +
            "        \"name\": \"UTM zone 15N\",\n" +
            "        \"method\": {\n" +
            "            \"name\": \"Transverse Mercator\"\n" +
            "        },\n" +
            "        \"parameters\": [\n" +
            "            {\n" +
            "                \"name\": \"Longitude of natural origin\",\n" +
            "                \"value\": -93.0\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"Latitude of natural origin\",\n" +
            "                \"value\": 0.0\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"Scale factor at natural origin\",\n" +
            "                \"value\": 0.9996\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"False easting\",\n" +
            "                \"value\": 500000.0\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"False northing\",\n" +
            "                \"value\": 0.0\n" +
            "            }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"coordinate_system\": {\n" +
            "        \"subtype\": \"cartesian\"\n" +
            "    }\n" +
            "}";
        
        ProjJsonDefinition wgs84Def = ProjJsonParser.parseDefinition(wgs84ProjJson);
        ProjJsonDefinition utmDef = ProjJsonParser.parseDefinition(utmProjJson);
        
        // Note: Input coordinates should be in degrees for longlat projection
        Point original = new Point(-96.0, 39.0);
        
        // Transform using PROJJSON definitions
        Point transformed = Proj4Sedona.transform(wgs84Def, utmDef, original);
        assertNotNull(transformed);
        
        // Transform back
        Point backTransformed = Proj4Sedona.transform(utmDef, wgs84Def, transformed);
        assertNotNull(backTransformed);
        
        // Check round-trip accuracy (relaxed tolerance for PROJJSON)
        assertEquals(original.x, backTransformed.x, 1e-5);
        assertEquals(original.y, backTransformed.y, 1e-5);
    }
    
    @Test
    public void testProjJsonConverter() throws Exception {
        String utmProjJson = "{\n" +
            "    \"type\": \"ProjectedCRS\",\n" +
            "    \"name\": \"WGS 84 / UTM zone 15N\",\n" +
            "    \"base_crs\": {\n" +
            "        \"type\": \"GeographicCRS\",\n" +
            "        \"name\": \"WGS 84\",\n" +
            "        \"datum\": {\n" +
            "            \"name\": \"WGS 84\",\n" +
            "            \"ellipsoid\": {\n" +
            "                \"name\": \"WGS 84\",\n" +
            "                \"semi_major_axis\": 6378137,\n" +
            "                \"inverse_flattening\": 298.257223563\n" +
            "            }\n" +
            "        }\n" +
            "    },\n" +
            "    \"conversion\": {\n" +
            "        \"name\": \"UTM zone 15N\",\n" +
            "        \"method\": {\n" +
            "            \"name\": \"Transverse Mercator\"\n" +
            "        },\n" +
            "        \"parameters\": [\n" +
            "            {\n" +
            "                \"name\": \"Longitude of natural origin\",\n" +
            "                \"value\": -93.0\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"Latitude of natural origin\",\n" +
            "                \"value\": 0.0\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"Scale factor at natural origin\",\n" +
            "                \"value\": 0.9996\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"False easting\",\n" +
            "                \"value\": 500000.0\n" +
            "            },\n" +
            "            {\n" +
            "                \"name\": \"False northing\",\n" +
            "                \"value\": 0.0\n" +
            "            }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"coordinate_system\": {\n" +
            "        \"subtype\": \"cartesian\"\n" +
            "    }\n" +
            "}";
        
        ProjJsonDefinition utmDef = ProjJsonParser.parseDefinition(utmProjJson);
        Proj4Sedona.Converter converter = Proj4Sedona.converter(utmDef);
        
        // Note: Input coordinates should be in degrees for longlat projection
        Point original = new Point(-96.0, 39.0);
        
        // Forward transformation
        Point transformed = converter.forward(original);
        assertNotNull(transformed);
        
        // Inverse transformation
        Point backTransformed = converter.inverse(transformed);
        assertNotNull(backTransformed);
        
        // Check round-trip accuracy (relaxed tolerance for PROJJSON)
        assertEquals(original.x, backTransformed.x, 1e-5);
        assertEquals(original.y, backTransformed.y, 1e-5);
    }
}
