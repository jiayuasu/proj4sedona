package org.datasyslab.proj4sedona.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.datasyslab.proj4sedona.core.Proj;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedBenchmarkTest {

    @Test
    void toleranceComesFromParsedProjectionType() {
        assertFalse(SpeedBenchmark.isProjectedCrs("EPSG:4269"));
        assertEquals(1e-6, SpeedBenchmark.toleranceForCrs("EPSG:4269"));

        assertTrue(SpeedBenchmark.isProjectedCrs("EPSG:3857"));
        assertEquals(0.01, SpeedBenchmark.toleranceForCrs("EPSG:3857"));

        String geocentric = "+proj=geocent +datum=WGS84 +units=m +no_defs";
        assertTrue(SpeedBenchmark.isProjectedCrs(geocentric));
        assertEquals(0.01, SpeedBenchmark.toleranceForCrs(geocentric));
    }

    @Test
    void declaredCountsMustBeExactIntegers() {
        Gson gson = new Gson();
        JsonObject integer = gson.fromJson("{\"count\": 12}", JsonObject.class);
        JsonObject equivalentDecimal = gson.fromJson("{\"count\": 12.0}", JsonObject.class);
        JsonObject fractional = gson.fromJson("{\"count\": 12.5}", JsonObject.class);
        JsonObject stringValue = gson.fromJson("{\"count\": \"12\"}", JsonObject.class);

        assertEquals(12, SpeedBenchmark.requiredInt(integer, "count", "fixture"));
        assertEquals(12, SpeedBenchmark.requiredInt(
            equivalentDecimal, "count", "fixture"));
        assertThrows(IllegalArgumentException.class,
            () -> SpeedBenchmark.requiredInt(fractional, "count", "fixture"));
        assertThrows(IllegalArgumentException.class,
            () -> SpeedBenchmark.requiredInt(stringValue, "count", "fixture"));
    }

    @Test
    void serializerSemanticComparisonCoversLossyRoundTripFields() {
        Proj utm = new Proj(
            "+proj=utm +zone=33 +datum=WGS84 +units=m +axis=enu");
        Proj equivalentTransverseMercator = new Proj(
            "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9996 +x_0=500000 +y_0=0 "
                + "+datum=WGS84 +units=m +axis=enu");
        assertNull(SpeedBenchmark.serializerSemanticDifference(
            utm, equivalentTransverseMercator));

        assertSemanticDifferenceContains(utm,
            new Proj("+proj=utm +zone=33 +south +datum=WGS84 +units=m"),
            "UTM zone/hemisphere");
        assertSemanticDifferenceContains(
            new Proj("+proj=merc +datum=WGS84 +units=m"),
            new Proj("+proj=eqc +datum=WGS84 +units=m"),
            "projection method");
        assertSemanticDifferenceContains(
            new Proj("+proj=longlat +ellps=WGS84 +pm=greenwich"),
            new Proj("+proj=longlat +ellps=WGS84 +pm=paris"),
            "prime meridian");
        Proj paris = new Proj("+proj=longlat +ellps=WGS84 +pm=paris");
        assertNull(SpeedBenchmark.serializerSemanticDifference(
            paris, new Proj(paris.toWkt2())),
            "one-nanodegree WKT normalization is semantically lossless");
        assertSemanticDifferenceContains(equivalentTransverseMercator,
            new Proj(
                "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9996 +x_0=500000 +y_0=0 "
                    + "+datum=WGS84 +units=us-ft +axis=enu"),
            "linear unit factor");
        assertSemanticDifferenceContains(equivalentTransverseMercator,
            new Proj(
                "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9996 +x_0=500000 +y_0=0 "
                    + "+datum=WGS84 +units=m +axis=neu"),
            "axis");
        assertSemanticDifferenceContains(
            new Proj("+proj=longlat +ellps=intl +towgs84=-87,-98,-121"),
            new Proj("+proj=longlat +ellps=intl +towgs84=-86,-98,-121"),
            "datum transform");

        Proj lcc = new Proj(
            "+proj=lcc +lat_0=39 +lon_0=-96 +lat_1=33 +lat_2=45 "
                + "+datum=WGS84 +units=m");
        assertSemanticDifferenceContains(lcc,
            new Proj(
                "+proj=lcc +lat_0=38 +lon_0=-96 +lat_1=33 +lat_2=45 "
                    + "+datum=WGS84 +units=m"),
            "latitude of origin");
        assertSemanticDifferenceContains(lcc,
            new Proj(
                "+proj=lcc +lat_0=39 +lon_0=-95 +lat_1=33 +lat_2=45 "
                    + "+datum=WGS84 +units=m"),
            "central meridian");
        assertSemanticDifferenceContains(lcc,
            new Proj(
                "+proj=lcc +lat_0=39 +lon_0=-96 +lat_1=34 +lat_2=45 "
                    + "+datum=WGS84 +units=m"),
            "first standard parallel");
        assertSemanticDifferenceContains(lcc,
            new Proj(
                "+proj=lcc +lat_0=39 +lon_0=-96 +lat_1=33 +lat_2=44 "
                    + "+datum=WGS84 +units=m"),
            "second standard parallel");
        assertSemanticDifferenceContains(
            new Proj("+proj=eqc +lat_0=20 +lat_ts=10 +datum=WGS84 +units=m"),
            new Proj("+proj=eqc +lat_0=20 +lat_ts=11 +datum=WGS84 +units=m"),
            "latitude of true scale");
        assertSemanticDifferenceContains(equivalentTransverseMercator,
            new Proj(
                "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9995 +x_0=500000 +y_0=0 "
                    + "+datum=WGS84 +units=m +axis=enu"),
            "effective scale factor");
        assertSemanticDifferenceContains(equivalentTransverseMercator,
            new Proj(
                "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9996 +x_0=500001 +y_0=0 "
                    + "+datum=WGS84 +units=m +axis=enu"),
            "false easting");
        assertSemanticDifferenceContains(equivalentTransverseMercator,
            new Proj(
                "+proj=tmerc +lat_0=0 +lon_0=15 +k=0.9996 +x_0=500000 +y_0=1 "
                    + "+datum=WGS84 +units=m +axis=enu"),
            "false northing");
    }

    private static void assertSemanticDifferenceContains(
            Proj expected, Proj actual, String fragment) {
        String difference = SpeedBenchmark.serializerSemanticDifference(expected, actual);
        assertTrue(difference != null && difference.contains(fragment),
            () -> "Expected semantic difference containing '" + fragment + "', got: "
                + difference);
    }
}
