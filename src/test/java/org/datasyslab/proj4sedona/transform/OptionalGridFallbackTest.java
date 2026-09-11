package org.datasyslab.proj4sedona.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A grid-shift datum whose grids are all optional ({@code @name}) and none of which is
 * available behaves like {@code +datum=none}: the transform proceeds with no datum shift
 * instead of failing. This follows PROJ, which skips a missing optional grid file, and
 * matches how an unrecognised datum name has always behaved. A missing mandatory grid
 * still fails.
 */
class OptionalGridFallbackTest {

    private static final String OPTIONAL_MISSING =
        "+proj=longlat +ellps=clrk66 +nadgrids=@proj4sedona_no_such_grid.gsb +no_defs";
    private static final String MANDATORY_MISSING =
        "+proj=longlat +ellps=clrk66 +nadgrids=proj4sedona_no_such_grid.gsb +no_defs";
    private static final String ESRI_NAD27_NO_TOWGS84 =
        "GEOGCS[\"GCS_North_American_1927\",DATUM[\"D_North_American_1927\","
        + "SPHEROID[\"Clarke_1866\",6378206.4,294.9786982]],PRIMEM[\"Greenwich\",0.0],"
        + "UNIT[\"Degree\",0.0174532925199433]]";

    private static Point transform(String from, String to) {
        return Proj4.transform(new Proj(from), new Proj(to), new Point(-100, 40));
    }

    @Test
    @DisplayName("A source datum with only unavailable optional grids applies no shift")
    void unavailableOptionalSourceGridsApplyNoShift() {
        Point result = transform(OPTIONAL_MISSING, "EPSG:4326");
        assertNotNull(result, "optional grids that are not available must not fail the transform");
        assertEquals(-100.0, result.x, 1e-12);
        assertEquals(40.0, result.y, 1e-12);
    }

    @Test
    @DisplayName("A destination datum with only unavailable optional grids applies no shift")
    void unavailableOptionalDestinationGridsApplyNoShift() {
        Point result = transform("EPSG:4326", OPTIONAL_MISSING);
        assertNotNull(result, "optional grids that are not available must not fail the transform");
        assertEquals(-100.0, result.x, 1e-12);
        assertEquals(40.0, result.y, 1e-12);
    }

    @Test
    @DisplayName("A missing mandatory grid still fails the transform")
    void missingMandatoryGridStillFails() {
        // Existing contract: a mandatory grid that cannot be found is an error at load time.
        assertThrows(RuntimeException.class, () -> transform(MANDATORY_MISSING, "EPSG:4326"));
    }

    @Test
    @DisplayName("Esri NAD27 WKT without TOWGS84 transforms even when no grid file is loaded")
    void esriNad27WithoutTowgs84Transforms() {
        // With the registry's optional grids loaded this applies a real shift; without them it
        // applies none. Either way it must produce a point.
        assertNotNull(transform(ESRI_NAD27_NO_TOWGS84, "EPSG:4326"));
    }

    @Test
    @DisplayName("+datum=NAD27 transforms even when no grid file is loaded")
    void projDatumNad27Transforms() {
        assertNotNull(transform("+proj=longlat +datum=NAD27 +no_defs", "EPSG:4326"));
    }

    @Test
    @DisplayName("The explicit null grid still passes coordinates through unchanged")
    void nullGridStillPassesThrough() {
        Point result = transform("+proj=longlat +ellps=clrk66 +nadgrids=@null +no_defs", "EPSG:4326");
        assertNotNull(result);
        assertEquals(-100.0, result.x, 1e-12);
        assertEquals(40.0, result.y, 1e-12);
    }
}
