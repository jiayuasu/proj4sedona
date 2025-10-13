package org.proj4.wkt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * Tests for the WKT parser.
 */
public class WKTParserTest {
    
    @Test
    public void testParseSimpleWKT() throws WKTParseException {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        
        List<Object> result = WKTParser.parseString(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result).hasSizeGreaterThan(1);
        assertThat(result.get(0)).isEqualTo("GEOGCS");
    }
    
    @Test
    public void testParseProjectedWKT() throws WKTParseException {
        String wkt = "PROJCS[\"WGS 84 / UTM zone 32N\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",9],PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],PARAMETER[\"false_northing\",0],UNIT[\"metre\",1]]";
        
        List<Object> result = WKTParser.parseString(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result).hasSizeGreaterThan(1);
        assertThat(result.get(0)).isEqualTo("PROJCS");
    }
    
    @Test
    public void testParseWithQuotedStrings() throws WKTParseException {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\"]]";
        
        List<Object> result = WKTParser.parseString(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result.get(0)).isEqualTo("GEOGCS");
    }
    
    @Test
    public void testParseWithNumbers() throws WKTParseException {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]]]";
        
        List<Object> result = WKTParser.parseString(wkt);
        
        assertThat(result).isNotNull();
        assertThat(result.get(0)).isEqualTo("GEOGCS");
    }
    
    @Test
    public void testParseEmptyString() {
        assertThatThrownBy(() -> WKTParser.parseString(""))
            .isInstanceOf(WKTParseException.class);
    }
    
    @Test
    public void testParseNullString() {
        assertThatThrownBy(() -> new WKTParser(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void testParseInvalidWKT() {
        String invalidWkt = "INVALID[";
        
        assertThatThrownBy(() -> WKTParser.parseString(invalidWkt))
            .isInstanceOf(WKTParseException.class);
    }
}
