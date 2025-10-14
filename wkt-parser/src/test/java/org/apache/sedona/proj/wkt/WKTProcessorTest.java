package org.apache.sedona.proj.wkt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Map;

/**
 * Tests for the WKT processor.
 */
public class WKTProcessorTest {
    
    @Test
    public void testProcessGeographicWKT() throws WKTParseException {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        
        Map<String, Object> result = WKTProcessor.process(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result.get("projName")).isEqualTo("longlat");
        assertThat(result.get("datumCode")).isEqualTo("wgs84");
        assertThat(result.get("ellps")).isEqualTo("WGS 84");
        assertThat(result.get("a")).isEqualTo(6378137.0);
        assertThat(result.get("rf")).isEqualTo(298.257223563);
    }
    
    @Test
    public void testProcessProjectedWKT() throws WKTParseException {
        String wkt = "PROJCS[\"WGS 84 / UTM zone 32N\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",9],PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],UNIT[\"metre\",1]]";
        
        Map<String, Object> result = WKTProcessor.process(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result.get("projName")).isEqualTo("utm");
        assertThat(result.get("datumCode")).isEqualTo("wgs84");
        assertThat(result.get("lat0")).isEqualTo(0.0); // latitude_of_origin converted to radians
        assertThat(result.get("long0")).isEqualTo(0.15707963267948966); // central_meridian converted to radians (9 degrees)
        assertThat(result.get("k0")).isEqualTo(0.9996);
        assertThat(result.get("x0")).isEqualTo(500000.0);
        assertThat(result.get("y0")).isEqualTo(0.0);
    }
    
    @Test
    public void testProcessWithAuthority() throws WKTParseException {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AUTHORITY[\"EPSG\",\"4326\"]]";
        
        Map<String, Object> result = WKTProcessor.process(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result.get("title")).isEqualTo("EPSG:4326");
    }
    
    @Test
    public void testProcessWithAxis() throws WKTParseException {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AXIS[\"Longitude\",EAST],AXIS[\"Latitude\",NORTH]]";
        
        Map<String, Object> result = WKTProcessor.process(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result.get("axis")).isEqualTo("enu");
    }
    
    @Test
    public void testProcessWithTOWGS84() throws WKTParseException {
        String wkt = "GEOGCS[\"NAD83\",DATUM[\"North_American_Datum_1983\",SPHEROID[\"GRS 1980\",6378137,298.257222101],TOWGS84[0,0,0,0,0,0,0]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        
        Map<String, Object> result = WKTProcessor.process(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result.get("datumCode")).isEqualTo("north_american_datum_1983");
        assertThat(result.get("datum_params")).isNotNull();
    }
    
    @Test
    public void testProcessInvalidInput() {
        assertThatThrownBy(() -> WKTProcessor.process(123))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testProcessNullInput() {
        assertThatThrownBy(() -> WKTProcessor.process(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
