package org.datasyslab.proj4sedona.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
