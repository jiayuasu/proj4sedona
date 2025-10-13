package org.proj4.core;

import org.proj4.wkt.WKTProcessor;
import org.proj4.wkt.WKTParseException;
import java.util.Map;

/**
 * Debug test to inspect WKT processor output.
 */
public class ProjectionWKTDebugTest {
    
    public static void main(String[] args) {
        try {
            String wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
            
            System.out.println("Input WKT: " + wkt);
            System.out.println();
            
            Map<String, Object> result = WKTProcessor.process(wkt);
            
            System.out.println("WKT Processor Output:");
            for (Map.Entry<String, Object> entry : result.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + entry.getValue());
            }
            
        } catch (WKTParseException e) {
            e.printStackTrace();
        }
    }
}
