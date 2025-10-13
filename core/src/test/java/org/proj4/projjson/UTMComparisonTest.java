package org.proj4.projjson;

import org.junit.jupiter.api.Test;
import org.proj4.Proj4Sedona;
import org.proj4.core.Point;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to compare UTM transformations between direct PROJ strings and PROJJSON.
 */
public class UTMComparisonTest {
    
    @Test
    public void testUTMComparison() throws Exception {
        // Test coordinates: -96.0 degrees longitude, 39.0 degrees latitude
        Point original = new Point(-96.0 * Math.PI / 180.0, 39.0 * Math.PI / 180.0);
        
        // Direct UTM Zone 15 transformation
        String utmProjString = "+proj=tmerc +a=6378137.0 +rf=298.257223563 +datum=WGS84 +lon_0=-93.0 +lat_0=0.0 +k_0=0.9996 +x_0=500000.0 +y_0=0.0 +axis=enu";
        Proj4Sedona.Converter directConverter = Proj4Sedona.converter(utmProjString);
        Point directResult = directConverter.forward(original);
        
        // PROJJSON UTM transformation
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
        Proj4Sedona.Converter projJsonConverter = Proj4Sedona.converter(utmDef);
        Point projJsonResult = projJsonConverter.forward(original);
        
        // Print results for comparison
        System.out.println("Original coordinates: " + original);
        System.out.println("Direct UTM result: " + directResult);
        System.out.println("PROJJSON UTM result: " + projJsonResult);
        System.out.println("Difference: " + (directResult.x - projJsonResult.x) + ", " + (directResult.y - projJsonResult.y));
        
        // They should be very close (within reasonable tolerance)
        assertEquals(directResult.x, projJsonResult.x, 1e-6);
        assertEquals(directResult.y, projJsonResult.y, 1e-6);
    }
}
