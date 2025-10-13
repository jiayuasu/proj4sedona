package org.proj4;

import org.junit.jupiter.api.Test;
import org.proj4.core.Point;
import static org.assertj.core.api.Assertions.*;

/**
 * Basic tests for the Proj4Java library.
 */
public class Proj4JavaTest {
    
    @Test
    public void testPointCreation() {
        Point point = new Point(10.0, 20.0);
        assertThat(point.x).isEqualTo(10.0);
        assertThat(point.y).isEqualTo(20.0);
        assertThat(point.z).isEqualTo(0.0);
        assertThat(point.m).isEqualTo(0.0);
    }
    
    @Test
    public void testPointFromArray() {
        double[] coords = {10.0, 20.0, 30.0, 40.0};
        Point point = Point.fromArray(coords);
        assertThat(point.x).isEqualTo(10.0);
        assertThat(point.y).isEqualTo(20.0);
        assertThat(point.z).isEqualTo(30.0);
        assertThat(point.m).isEqualTo(40.0);
    }
    
    @Test
    public void testPointFromString() {
        Point point = Point.fromString("10.0,20.0,30.0,40.0");
        assertThat(point.x).isEqualTo(10.0);
        assertThat(point.y).isEqualTo(20.0);
        assertThat(point.z).isEqualTo(30.0);
        assertThat(point.m).isEqualTo(40.0);
    }
    
    @Test
    public void testPointCopy() {
        Point original = new Point(10.0, 20.0, 30.0, 40.0);
        Point copy = original.copy();
        assertThat(copy).isNotSameAs(original);
        assertThat(copy).isEqualTo(original);
    }
    
    @Test
    public void testPointToString() {
        Point point = new Point(10.123456, 20.789012);
        String str = point.toString();
        assertThat(str).contains("10.123456");
        assertThat(str).contains("20.789012");
    }
    
    @Test
    public void testWGS84Projection() {
        Point point = new Point(-71.0, 41.0);
        Point result = Proj4Java.transform("WGS84", point);
        
        // For WGS84 to WGS84, should be identity transformation
        assertThat(result.x).isCloseTo(-71.0, within(1e-10));
        assertThat(result.y).isCloseTo(41.0, within(1e-10));
    }
    
    @Test
    public void testConverterCreation() {
        Proj4Java.Converter converter = Proj4Java.converter("WGS84");
        assertThat(converter).isNotNull();
        assertThat(converter.getProjection()).isNotNull();
    }
    
    @Test
    public void testToPointUtility() {
        Point point = Proj4Java.toPoint(10.0, 20.0);
        assertThat(point.x).isEqualTo(10.0);
        assertThat(point.y).isEqualTo(20.0);
        
        double[] coords = {30.0, 40.0, 50.0};
        Point point2 = Proj4Java.toPoint(coords);
        assertThat(point2.x).isEqualTo(30.0);
        assertThat(point2.y).isEqualTo(40.0);
        assertThat(point2.z).isEqualTo(50.0);
    }
    
    @Test
    public void testVersion() {
        String version = Proj4Java.getVersion();
        assertThat(version).isEqualTo("1.0.0-SNAPSHOT");
    }
}
