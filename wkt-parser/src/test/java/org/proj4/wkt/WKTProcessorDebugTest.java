package org.proj4.wkt;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

/**
 * Debug test to understand the WKT processing structure for axis and TOWGS84.
 */
public class WKTProcessorDebugTest {
    
    @Test
    public void debugAxisWKT() throws WKTParseException {
        String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433],AXIS[\"Longitude\",EAST],AXIS[\"Latitude\",NORTH]]";
        
        // Parse the WKT string
        List<Object> lisp = WKTParser.parseString(wkt);
        System.out.println("Parsed LISP structure:");
        System.out.println(lisp);
        
        // Convert to object structure
        Map<String, Object> obj = new java.util.HashMap<>();
        SExpressionProcessor.sExpr(lisp, obj);
        System.out.println("\nS-expression processed object:");
        System.out.println(obj);
        
        // Clean and normalize the WKT
        WKTProcessor.cleanWKT(obj);
        System.out.println("\nAfter cleaning:");
        System.out.println(obj);
        
        // Try to get the result
        String type = (String) lisp.get(0);
        System.out.println("\nType: " + type);
        Object result = obj.get(type);
        System.out.println("Result: " + result);
    }
    
    @Test
    public void debugTOWGS84WKT() throws WKTParseException {
        String wkt = "GEOGCS[\"NAD83\",DATUM[\"North_American_Datum_1983\",SPHEROID[\"GRS 1980\",6378137,298.257222101],TOWGS84[0,0,0,0,0,0,0]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        
        // Parse the WKT string
        List<Object> lisp = WKTParser.parseString(wkt);
        System.out.println("Parsed LISP structure:");
        System.out.println(lisp);
        
        // Convert to object structure
        Map<String, Object> obj = new java.util.HashMap<>();
        SExpressionProcessor.sExpr(lisp, obj);
        System.out.println("\nS-expression processed object:");
        System.out.println(obj);
        
        // Clean and normalize the WKT
        WKTProcessor.cleanWKT(obj);
        System.out.println("\nAfter cleaning:");
        System.out.println(obj);
        
        // Try to get the result
        String type = (String) lisp.get(0);
        System.out.println("\nType: " + type);
        Object result = obj.get(type);
        System.out.println("Result: " + result);
    }
}