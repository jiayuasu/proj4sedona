package org.proj4.debug;

import org.junit.jupiter.api.Test;
import org.proj4.constants.Ellipsoid;
import org.proj4.core.Projection;
import static org.assertj.core.api.Assertions.*;

/**
 * Debug test to understand ellipsoid parameter issues.
 */
public class EllipsoidDebugTest {
    
    @Test
    public void testEllipsoidParameters() {
        String projString = "+proj=merc +lat_ts=0 +lon_0=0 +k=1.0 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs";
        Projection proj = new Projection(projString);
        
        System.out.println("Projection ellipsoid: " + proj.ellps);
        System.out.println("Projection a: " + proj.a);
        System.out.println("Projection b: " + proj.b);
        System.out.println("Projection rf: " + proj.rf);
        System.out.println("Projection e: " + proj.e);
        System.out.println("Projection sphere: " + proj.sphere);
        
        // Check if ellipsoid is found
        Ellipsoid.EllipsoidDef ellipsoid = Ellipsoid.get(proj.ellps);
        if (ellipsoid != null) {
            System.out.println("Found ellipsoid: " + ellipsoid.ellipseName);
            System.out.println("Ellipsoid a: " + ellipsoid.a);
            System.out.println("Ellipsoid b: " + ellipsoid.b);
            System.out.println("Ellipsoid rf: " + ellipsoid.rf);
        } else {
            System.out.println("Ellipsoid not found for: " + proj.ellps);
        }
        
        // Test transformation
        org.proj4.core.Point input = new org.proj4.core.Point(0.0, 0.0);
        org.proj4.core.Point result = proj.forward.transform(input);
        System.out.println("Input: " + input);
        System.out.println("Output: " + result);
    }
}
