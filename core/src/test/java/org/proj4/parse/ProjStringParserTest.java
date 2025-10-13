package org.proj4.parse;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Map;

/**
 * Tests for the ProjStringParser class.
 */
public class ProjStringParserTest {
    
    @Test
    public void testParseBasicProjString() {
        String projString = "+proj=merc +lat_ts=0 +lon_0=0 +k=1.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
        Map<String, Object> params = ProjStringParser.parse(projString);
        
        assertThat(params).containsEntry("proj", "merc");
        assertThat(params).containsEntry("lat_ts", 0.0);
        assertThat(params).containsEntry("lon_0", 0.0);
        assertThat(params).containsEntry("k", 1.0);
        assertThat(params).containsEntry("x_0", 0.0);
        assertThat(params).containsEntry("y_0", 0.0);
        assertThat(params).containsEntry("datum", "WGS84");
        assertThat(params).containsEntry("units", "m");
        assertThat(params).containsEntry("no_defs", true);
    }
    
    @Test
    public void testParseToDefinition() {
        String projString = "+proj=merc +lat_ts=0 +lon_0=0 +k=1.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "merc");
        assertThat(def).containsEntry("datumCode", "WGS84");
        assertThat(def).containsEntry("units", "m");
        assertThat(def).containsKey("lat_ts");
        assertThat(def).containsKey("long0");
        assertThat(def).containsKey("k0");
        assertThat(def).containsKey("x0");
        assertThat(def).containsKey("y0");
    }
    
    @Test
    public void testParseLongLatProjString() {
        String projString = "+proj=longlat +datum=WGS84 +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "longlat");
        assertThat(def).containsEntry("datumCode", "WGS84");
    }
    
    @Test
    public void testParseUTMProjString() {
        String projString = "+proj=utm +zone=10 +datum=WGS84 +units=m +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "utm");
        assertThat(def).containsEntry("datumCode", "WGS84");
        assertThat(def).containsEntry("units", "m");
        assertThat(def).containsEntry("zone", 10.0);
    }
    
    @Test
    public void testParseLambertProjString() {
        String projString = "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "lcc");
        assertThat(def).containsEntry("datumCode", "WGS84");
        assertThat(def).containsEntry("units", "m");
        assertThat(def).containsKey("lat1");
        assertThat(def).containsKey("lat2");
        assertThat(def).containsKey("lat0");
        assertThat(def).containsKey("long0");
    }
    
    @Test
    public void testParseWithEllipsoid() {
        String projString = "+proj=merc +ellps=WGS84 +datum=WGS84 +units=m +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "merc");
        assertThat(def).containsEntry("ellps", "WGS84");
        assertThat(def).containsEntry("datumCode", "WGS84");
    }
    
    @Test
    public void testParseWithCustomEllipsoid() {
        String projString = "+proj=merc +a=6378137 +b=6356752.314245 +datum=WGS84 +units=m +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "merc");
        assertThat(def).containsEntry("a", 6378137.0);
        assertThat(def).containsEntry("b", 6356752.314245);
        assertThat(def).containsEntry("datumCode", "WGS84");
    }
    
    @Test
    public void testParseWithPrimeMeridian() {
        String projString = "+proj=longlat +datum=WGS84 +pm=paris +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "longlat");
        assertThat(def).containsEntry("datumCode", "WGS84");
        assertThat(def).containsKey("from_greenwich");
    }
    
    @Test
    public void testParseWithTowgs84() {
        String projString = "+proj=longlat +datum=WGS84 +towgs84=0,0,0 +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "longlat");
        assertThat(def).containsEntry("datumCode", "WGS84");
        assertThat(def).containsEntry("datum_params", "0,0,0");
    }
    
    @Test
    public void testParseWithNadgrids() {
        String projString = "+proj=longlat +datum=NAD27 +nadgrids=@conus +no_defs";
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        assertThat(def).containsEntry("projName", "longlat");
        assertThat(def).containsEntry("datumCode", "NAD27");
        assertThat(def).containsEntry("nadgrids", "@conus");
    }
    
    @Test
    public void testIsValid() {
        assertThat(ProjStringParser.isValid("+proj=merc +datum=WGS84 +no_defs")).isTrue();
        assertThat(ProjStringParser.isValid("+proj=longlat +datum=WGS84 +no_defs")).isTrue();
        assertThat(ProjStringParser.isValid("invalid string")).isFalse();
        assertThat(ProjStringParser.isValid("")).isFalse();
        assertThat(ProjStringParser.isValid(null)).isFalse();
    }
    
    @Test
    public void testGetProjectionName() {
        assertThat(ProjStringParser.getProjectionName("+proj=merc +datum=WGS84 +no_defs")).isEqualTo("merc");
        assertThat(ProjStringParser.getProjectionName("+proj=longlat +datum=WGS84 +no_defs")).isEqualTo("longlat");
        assertThat(ProjStringParser.getProjectionName("invalid string")).isNull();
    }
    
    @Test
    public void testGetDatum() {
        assertThat(ProjStringParser.getDatum("+proj=merc +datum=WGS84 +no_defs")).isEqualTo("WGS84");
        assertThat(ProjStringParser.getDatum("+proj=longlat +datum=NAD27 +no_defs")).isEqualTo("NAD27");
        assertThat(ProjStringParser.getDatum("+proj=merc +no_defs")).isNull();
    }
    
    @Test
    public void testGetEllipsoid() {
        assertThat(ProjStringParser.getEllipsoid("+proj=merc +ellps=WGS84 +no_defs")).isEqualTo("WGS84");
        assertThat(ProjStringParser.getEllipsoid("+proj=merc +ellps=GRS80 +no_defs")).isEqualTo("GRS80");
        assertThat(ProjStringParser.getEllipsoid("+proj=merc +no_defs")).isNull();
    }
    
    @Test
    public void testParseEmptyString() {
        Map<String, Object> params = ProjStringParser.parse("");
        assertThat(params).isEmpty();
    }
    
    @Test
    public void testParseNullString() {
        Map<String, Object> params = ProjStringParser.parse(null);
        assertThat(params).isEmpty();
    }
    
    @Test
    public void testParseWithSpaces() {
        String projString = "+proj=merc +lat_ts=0 +lon_0=0 +datum=WGS84 +units=m +no_defs";
        Map<String, Object> params = ProjStringParser.parse(projString);
        
        assertThat(params).containsEntry("proj", "merc");
        assertThat(params).containsEntry("lat_ts", 0.0);
        assertThat(params).containsEntry("lon_0", 0.0);
        assertThat(params).containsEntry("datum", "WGS84");
        assertThat(params).containsEntry("units", "m");
    }
    
    @Test
    public void testParseWithBooleanParameters() {
        String projString = "+proj=merc +datum=WGS84 +no_defs +type=crs";
        Map<String, Object> params = ProjStringParser.parse(projString);
        
        assertThat(params).containsEntry("proj", "merc");
        assertThat(params).containsEntry("datum", "WGS84");
        assertThat(params).containsEntry("no_defs", true);
        assertThat(params).containsEntry("type", "crs");
    }
}
