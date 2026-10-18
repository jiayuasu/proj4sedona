package org.datasyslab.proj4sedona.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.grid.GridLoader;
import org.datasyslab.proj4sedona.grid.NadgridRegistry;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.parser.ProjString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A registry datum that shifts with grids (NAD27: {@code @conus,@alaska,@ntv2_0.gsb,
 * @ntv1_can.dat}) falls back to the registry's Helmert for its geographic CRS,
 * {@code EPSG_4267} (-8,160,176), wherever none of its grids can shift a point: no grid is
 * loaded, or the point is outside every loaded grid. PROJ does the same for a CRS whose
 * datum it knows (it uses a Helmert operation where no grid applies). The fallback follows
 * the datum: any grid list other than the registry datum's own keeps PROJ's behavior for
 * an explicit grid list, with no fallback.
 *
 * <p>Expected values are from PROJ 9.8.1 (pyproj 3.8.0, network off): the Helmert values
 * from {@code +proj=longlat +ellps=clrk66 +towgs84=-8,160,176}, the grid values from
 * {@code +nadgrids=us_noaa_conus.tif}.</p>
 */
class GridDatumHelmertFallbackTest {

    private static final double TOL = 1e-9;

    private static final String CONUS_GRID =
        Path.of("src/test/resources/grids/us_noaa_conus.tif").toAbsolutePath().toString();

    private static final String NAD27 = "+proj=longlat +datum=NAD27 +no_defs";
    private static final String REGISTRY_GRIDS = "@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat";
    private static final String WGS84 = "EPSG:4326";

    /** EPSG:32041, NAD27 / Texas South, as a PROJ string with the registry datum. */
    private static final String TEXAS_SOUTH =
        "+proj=lcc +lat_1=27.83333333333333 +lat_2=26.16666666666667 +lat_0=25.66666666666667"
        + " +lon_0=-98.5 +x_0=609601.2192024384 +y_0=0 +datum=NAD27 +units=us-ft +no_defs";

    private static final String ESRI_NAD27 =
        "GEOGCS[\"GCS_North_American_1927\",DATUM[\"D_North_American_1927\","
        + "SPHEROID[\"Clarke_1866\",6378206.4,294.9786982]],PRIMEM[\"Greenwich\",0.0],"
        + "UNIT[\"Degree\",0.0174532925199433]]";

    private static final String WKT2_NAD27 =
        "GEOGCRS[\"NAD27\",DATUM[\"North American Datum 1927\","
        + "ELLIPSOID[\"Clarke 1866\",6378206.4,294.978698213898,LENGTHUNIT[\"metre\",1]]],"
        + "PRIMEM[\"Greenwich\",0,ANGLEUNIT[\"degree\",0.0174532925199433]],"
        + "CS[ellipsoidal,2],AXIS[\"geodetic latitude (Lat)\",north,ORDER[1]],"
        + "AXIS[\"geodetic longitude (Lon)\",east,ORDER[2]],"
        + "ANGLEUNIT[\"degree\",0.0174532925199433],ID[\"EPSG\",4267]]";

    @AfterEach
    void unloadGrid() {
        NadgridRegistry.remove("conus");
    }

    private static Point transform(String from, String to, double x, double y) {
        return Proj4.transform(new Proj(from), new Proj(to), new Point(x, y));
    }

    private static void assertPoint(double x, double y, Point actual) {
        assertNotNull(actual);
        assertEquals(x, actual.x, TOL);
        assertEquals(y, actual.y, TOL);
    }

    @Test
    @DisplayName("Without grids, +datum=NAD27 to WGS84 uses the EPSG:4267 Helmert")
    void noGridsUsesHelmert() {
        assertPoint(-98.00030416754218, 27.000326243662016, transform(NAD27, WGS84, -98, 27));
        assertPoint(-150.00263478925834, 60.99952534772658, transform(NAD27, WGS84, -150, 61));
    }

    @Test
    @DisplayName("Without grids, WGS84 to +datum=NAD27 uses the inverse Helmert")
    void noGridsInverseUsesHelmert() {
        assertPoint(-97.99969584364611, 26.99967374641001, transform(WGS84, NAD27, -98, 27));
    }

    @Test
    @DisplayName("Esri WKT1 and WKT2 NAD27 get the same Helmert")
    void wktNad27UsesHelmert() {
        assertPoint(-98.00030416754218, 27.000326243662016, transform(ESRI_NAD27, WGS84, -98, 27));
        assertPoint(-98.00030416754218, 27.000326243662016, transform(WKT2_NAD27, WGS84, -98, 27));
    }

    @Test
    @DisplayName("A projected NAD27 CRS gets the Helmert in both directions")
    void projectedNad27UsesHelmert() {
        assertPoint(-97.88733692934359, 26.76615730274582,
            transform(TEXAS_SOUTH, WGS84, 2200000, 400000));

        Point back = transform(WGS84, TEXAS_SOUTH, -98, 27);
        assertNotNull(back);
        assertEquals(2162904.3469499694, back.x, 1e-6);
        assertEquals(484834.98143128195, back.y, 1e-6);
    }

    @Test
    @DisplayName("A transform within NAD27 applies no datum step and stays exact")
    void sameDatumIsExact() {
        // EPSG:32041 to EPSG:4267 and EPSG:26714 to EPSG:4267 in PROJ
        assertPoint(-97.88703650736258, 26.765822655699218,
            transform(TEXAS_SOUTH, NAD27, 2200000, 400000));
        assertPoint(-99.0, 29.832085964986316,
            transform("+proj=utm +zone=14 +datum=NAD27 +units=m +no_defs", NAD27, 500000, 3300000));
    }

    @Test
    @DisplayName("A loaded registry grid is used where it applies, the Helmert elsewhere")
    void loadedGridThenHelmert() throws Exception {
        GridLoader.loadFile("conus", CONUS_GRID);

        // Texas is inside the CONUS grid: NADCON, as PROJ gives it
        assertPoint(-98.00027932444539, 27.000329556392956, transform(NAD27, WGS84, -98, 27));
        assertPoint(-97.99972068340705, 26.99967043289656, transform(WGS84, NAD27, -98, 27));
        // Alaska is outside it, and the Alaska grid is not loaded: the Helmert
        assertPoint(-150.00263478925834, 60.99952534772658, transform(NAD27, WGS84, -150, 61));
    }

    @Test
    @DisplayName("An explicit grid list has no fallback, as in PROJ")
    void explicitGridsHaveNoFallback() {
        // PROJ ignores +towgs84 when +nadgrids is given, and applies no shift when every
        // listed grid is optional and missing
        assertPoint(-98.0, 27.0, transform(
            "+proj=longlat +ellps=clrk66 +nadgrids=@missing.gsb +towgs84=-8,160,176 +no_defs",
            WGS84, -98, 27));
        assertPoint(-98.0, 27.0,
            transform("+proj=longlat +datum=NAD27 +nadgrids=@missing.gsb +no_defs", WGS84, -98, 27));
        // and fails for a point outside the listed grid
        assertNull(transform("+proj=longlat +ellps=clrk66 +nadgrids=@" + CONUS_GRID + " +no_defs",
            WGS84, -150, 61));
    }

    @Test
    @DisplayName("An explicit +towgs84 on NAD27 wins over the registry")
    void explicitTowgs84Wins() {
        assertPoint(-98.00032131725428, 27.000405442963263, transform(
            "+proj=longlat +datum=NAD27 +towgs84=-10,158,187 +no_defs", WGS84, -98, 27));
    }

    @Test
    @DisplayName("The fallback is not written into PROJ string exports")
    void fallbackIsNotExported() {
        assertFalse(new Proj(NAD27).toProjString().contains("towgs84"));
    }

    @Test
    @DisplayName("Restating the registry grid list on +datum=NAD27 keeps the fallback through exports")
    void registryGridListKeepsFallbackThroughExports() {
        // The exports name the registry datum, so the reparsed CRS must transform the same
        Proj explicit = new Proj("+proj=longlat +datum=NAD27 +nadgrids=" + REGISTRY_GRIDS + " +no_defs");
        Point direct = Proj4.transform(explicit, new Proj(WGS84), new Point(-98, 27));
        assertPoint(-98.00030416754218, 27.000326243662016, direct);

        String[] exports = {
            explicit.toProjString(),
            CRSSerializer.toWkt1(explicit),
            CRSSerializer.toWkt2(explicit),
            CRSSerializer.toProjJson(explicit),
        };
        for (String exported : exports) {
            assertPoint(direct.x, direct.y, transform(exported, WGS84, -98, 27));
        }
    }

    @Test
    @DisplayName("The registry grid list on the bare ellipsoid is a different datum")
    void registryGridsOnBareEllipsoidAreADifferentDatum() {
        String bare = "+proj=longlat +ellps=clrk66 +nadgrids=" + REGISTRY_GRIDS + " +no_defs";
        // PROJ applies NAD27's Helmert here (its own CONUS choice), and the explicit grids,
        // all missing, apply nothing
        assertPoint(-98.00030416754218, 27.000326243662016, transform(NAD27, bare, -98, 27));
        // and applies no datum step between two NAD27 definitions
        assertPoint(-98.0, 27.0, transform(NAD27,
            "+proj=longlat +datum=NAD27 +nadgrids=" + REGISTRY_GRIDS + " +no_defs", -98, 27));
    }

    @Test
    @DisplayName("The fallback follows the definition's current grid list")
    void fallbackFollowsCurrentGridList() {
        ProjectionDef def = ProjString.parse(NAD27);
        new Proj(def);
        def.setNadgrids("@proj4sedona_no_such_grid.gsb");

        Point reused = Proj4.transform(new Proj(def), new Proj(WGS84), new Point(-98, 27));
        assertPoint(-98.0, 27.0, reused);
    }
}
