package org.apache.sedona.proj.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WKT integration in the Projection class.
 */
public class ProjectionWKTTest {
    
    @Test
    public void testCreateProjectionFromGeographicWKT() {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        
        Projection proj = new Projection(wkt);
        
        assertThat(proj).isNotNull();
        assertThat(proj.projName).isEqualTo("longlat");
        assertThat(proj.name).isEqualTo("WGS 84");
        assertThat(proj.datumCode).isEqualTo("wgs84");
        assertThat(proj.ellps).isEqualTo("WGS 84");
        assertThat(proj.a).isEqualTo(6378137.0);
        assertThat(proj.rf).isEqualTo(298.257223563);
        assertThat(proj.units).isEqualTo("degree");
    }
    
    @Test
    public void testCreateProjectionFromWKTWithAxis() {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AXIS[\"Longitude\",EAST],AXIS[\"Latitude\",NORTH]]";
        
        Projection proj = new Projection(wkt);
        
        assertThat(proj).isNotNull();
        assertThat(proj.projName).isEqualTo("longlat");
        assertThat(proj.axis).isEqualTo("enu");
        assertThat(proj.name).isEqualTo("WGS 84");
    }
    
    @Test
    public void testCreateProjectionFromWKTWithTOWGS84() {
        String wkt = "GEOGCS[\"NAD83\",DATUM[\"North_American_Datum_1983\",SPHEROID[\"GRS 1980\",6378137,298.257222101],TOWGS84[0,0,0,0,0,0,0]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        
        Projection proj = new Projection(wkt);
        
        assertThat(proj).isNotNull();
        assertThat(proj.projName).isEqualTo("longlat");
        assertThat(proj.name).isEqualTo("NAD83");
        assertThat(proj.datumCode).isEqualTo("north_american_datum_1983");
        assertThat(proj.datum_params).isNotNull();
        assertThat(proj.datum_params).hasSize(7);
        assertThat(proj.datum_params[0]).isEqualTo("0.0");
    }
    
    @Test
    public void testCreateProjectionFromInvalidWKT() {
        String invalidWkt = "INVALID_WKT_STRING";
        
        assertThatThrownBy(() -> new Projection(invalidWkt))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported SRS code");
    }
    
    @Test
    public void testCreateProjectionFromMalformedWKT() {
        String malformedWkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]";
        
        assertThatThrownBy(() -> new Projection(malformedWkt))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed to parse WKT string");
    }
}
