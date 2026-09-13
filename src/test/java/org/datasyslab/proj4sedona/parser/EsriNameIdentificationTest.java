package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.defs.CRSProvider;
import org.datasyslab.proj4sedona.defs.CRSResult;
import org.datasyslab.proj4sedona.defs.Defs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Esri-style WKT names its CRSs with Esri's registry names. Those names index authority
 * codes, so identification can resolve the definition behind the code, through the
 * providers, on demand, and validate the WKT against it before trusting the name.
 *
 * <p>The definitions a name resolves to are served by a test provider that stands in for the
 * remote catalog (it declares itself remote, so blind parameter probing cannot use it), and
 * the real remote provider is removed, so the tests are deterministic and offline.</p>
 */
class EsriNameIdentificationTest {

    private static final String NAD83_GCS =
        "GEOGCS[\"GCS_North_American_1983\",DATUM[\"D_North_American_1983\","
        + "SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],"
        + "UNIT[\"Degree\",0.0174532925199433]]";
    private static final String WGS84_GCS =
        "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],"
        + "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]";
    private static final String CUSTOM_DATUM_GCS =
        "GEOGCS[\"GCS_Custom\",DATUM[\"D_Custom_Survey_Datum\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],"
        + "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]";
    private static final String GRS80 = "SPHEROID[\"GRS_1980\",6378137.0,298.257222101]";
    private static final String GREENWICH = "PRIMEM[\"Greenwich\",0.0]";
    private static final String DEGREE = "UNIT[\"Degree\",0.0174532925199433]";

    /**
     * The remote catalog's answers for the codes these tests exercise, as PROJJSON documents
     * captured from spatialreference.org, the production provider's source, so the
     * definitions the name phase validates against have the same shape as in production.
     */
    private final CRSProvider catalog = new CRSProvider() {
        @Override
        public String getName() {
            return "test-catalog";
        }

        @Override
        public CRSResult resolve(String authority, String code) {
            if (!"epsg".equalsIgnoreCase(authority)) {
                return null;
            }
            try (InputStream in = EsriNameIdentificationTest.class.getResourceAsStream(
                    "/esri-name-catalog/EPSG/" + code + ".json")) {
                return in == null ? null
                    : CRSResult.projJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean isRemote() {
            return true;
        }
    };

    @BeforeEach
    void useTestCatalog() {
        Defs.reset();
        Defs.globals();
        Defs.removeProvider("spatialreference.org");
        Defs.registerProvider(catalog, 150);
    }

    @AfterEach
    void resetRegistry() {
        Defs.reset();
    }

    private static String gcs(String name, String datum, String spheroid, String primem, String unit) {
        return "GEOGCS[\"" + name + "\",DATUM[\"" + datum + "\"," + spheroid + "]," + primem + "," + unit + "]";
    }

    private static String lcc(String name, String gcs) {
        return "PROJCS[\"" + name + "\"," + gcs
            + ",PROJECTION[\"Lambert_Conformal_Conic\"],PARAMETER[\"False_Easting\",6561666.666666666],"
            + "PARAMETER[\"False_Northing\",1640416.666666667],PARAMETER[\"Central_Meridian\",-118.0],"
            + "PARAMETER[\"Standard_Parallel_1\",34.03333333333333],"
            + "PARAMETER[\"Standard_Parallel_2\",35.46666666666667],"
            + "PARAMETER[\"Latitude_Of_Origin\",33.5],UNIT[\"Foot_US\",0.3048006096012192]]";
    }

    private static String utm(String name, String gcs, double centralMeridian) {
        return "PROJCS[\"" + name + "\"," + gcs
            + ",PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",500000.0],"
            + "PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\"," + centralMeridian + "],"
            + "PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],"
            + "UNIT[\"Meter\",1.0]]";
    }

    private static String identify(String wkt) {
        return CRSSerializer.toEpsgCode(new Proj(wkt));
    }

    @Test
    @DisplayName("An Esri State Plane name identifies once its definition is resolved")
    void statePlaneIdentifiesByName() {
        assertEquals("EPSG:2229", identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS)));
    }

    @Test
    @DisplayName("An Esri geographic name identifies even when the datum name is not in the hand-written tables")
    void geographicNameIdentifiesThroughDatumIndex() {
        assertEquals("EPSG:4258", identify(gcs("GCS_ETRS_1989", "D_ETRS_1989", GRS80, GREENWICH, DEGREE)));
    }

    @Test
    @DisplayName("A canonical geographic CRS identifies although its definition carries the datum's transformation")
    void canonicalGeographicCrsIdentifiesWithoutStatedOperation() {
        assertEquals("EPSG:4275", identify(gcs("GCS_NTF", "D_NTF",
            "SPHEROID[\"Clarke_1880_IGN\",6378249.2,293.4660212936265]", GREENWICH, DEGREE)));
        assertEquals("EPSG:4314", identify(gcs("GCS_Deutsches_Hauptdreiecksnetz",
            "D_Deutsches_Hauptdreiecksnetz", "SPHEROID[\"Bessel_1841\",6377397.155,299.1528128]",
            GREENWICH, DEGREE)));
    }

    @Test
    @DisplayName("A name whose datum disagrees with the WKT is not trusted")
    void mismatchedDatumRejectsTheName() {
        assertEquals("EPSG:32619", identify(utm("NAD_1983_UTM_Zone_19N", WGS84_GCS, -69.0)));
        assertNull(identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", WGS84_GCS)));
    }

    @Test
    @DisplayName("A name whose parameters disagree with its definition is not trusted")
    void mismatchedParametersRejectTheName() {
        assertEquals("EPSG:26918", identify(utm("NAD_1983_UTM_Zone_19N", NAD83_GCS, -75.0)));

        String wrongParallels = lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS)
            .replace("PARAMETER[\"Standard_Parallel_1\",34.03333333333333]", "PARAMETER[\"Standard_Parallel_1\",30.0]")
            .replace("PARAMETER[\"Standard_Parallel_2\",35.46666666666667]", "PARAMETER[\"Standard_Parallel_2\",31.0]");
        assertNull(identify(wrongParallels));

        String metres = lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS)
            .replace("UNIT[\"Foot_US\",0.3048006096012192]", "UNIT[\"Meter\",1.0]");
        assertNull(identify(metres));
    }

    @Test
    @DisplayName("A projected name is accepted with the standard parallels in either order")
    void projectedNameAcceptsEitherParallelOrder() {
        String swapped = lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS)
            .replace("PARAMETER[\"Standard_Parallel_1\",34.03333333333333]", "PARAMETER[\"Standard_Parallel_1\",35.46666666666667]")
            .replace("PARAMETER[\"Standard_Parallel_2\",35.46666666666667]", "PARAMETER[\"Standard_Parallel_2\",34.03333333333333]");
        assertEquals("EPSG:2229", identify(swapped));
    }

    @Test
    @DisplayName("A geographic name with a different ellipsoid, meridian, unit, or explicit shift is not trusted")
    void mutatedGeographicNameIsRejected() {
        assertNotEquals("EPSG:4258", identify(gcs("GCS_ETRS_1989", "D_ETRS_1989",
            "SPHEROID[\"Clarke_1866\",6378206.4,294.9786982]", GREENWICH, DEGREE)));
        String paris = gcs("GCS_ETRS_1989", "D_ETRS_1989", GRS80, "PRIMEM[\"Paris\",2.337229166666667]", DEGREE);
        assertNotEquals("EPSG:4258", identify(paris));
        assertFalse(CRSSerializer.toProjJson(new Proj(paris)).contains("4258"),
            "PROJJSON must not carry an identifier the parameters contradict");
        assertNotEquals("EPSG:4258", identify(gcs("GCS_ETRS_1989", "D_ETRS_1989", GRS80, GREENWICH,
            "UNIT[\"Grad\",0.01570796326794897]")));
        assertNotEquals("EPSG:4258", identify(gcs("GCS_ETRS_1989", "D_ETRS_1989",
            GRS80 + ",TOWGS84[-8,160,176]", GREENWICH, DEGREE)));
    }

    @Test
    @DisplayName("A name on an unrecognised datum does not identify")
    void unknownDatumDoesNotIdentify() {
        assertNull(identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", CUSTOM_DATUM_GCS)));
    }

    @Test
    @DisplayName("A name whose definition cannot be resolved is not trusted")
    void unresolvableDefinitionIsNotTrusted() {
        // Alabama East (EPSG:26929) is in the index but no provider serves it here.
        String alabama = "PROJCS[\"NAD_1983_StatePlane_Alabama_East_FIPS_0101\"," + NAD83_GCS
            + ",PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",200000.0],"
            + "PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",-85.83333333333333],"
            + "PARAMETER[\"Scale_Factor\",0.99996],PARAMETER[\"Latitude_Of_Origin\",30.5],UNIT[\"Meter\",1.0]]";
        assertNull(identify(alabama));
    }

    @Test
    @DisplayName("Names that are not Esri names fall through to the other phases")
    void nonEsriNamesFallThrough() {
        assertEquals("EPSG:4326", CRSSerializer.toEpsgCode(new Proj("EPSG:4326")));
        assertEquals("EPSG:26919", identify(utm("NAD83 / UTM zone 19N", NAD83_GCS, -69.0)));
    }

    // Esri's own WKT for a few systems whose datum is known only by its Esri spelling, or
    // whose geographic CRS has a 3D sibling; each must identify against the fetched definition.

    @Test
    @DisplayName("A projected name on a datum known only by its Esri spelling identifies")
    void projectedNameOnEsriSpelledDatumIdentifies() {
        assertEquals("EPSG:25832", identify("PROJCS[\"ETRS_1989_UTM_Zone_32N\",GEOGCS[\"GCS_ETRS_1989\",DATUM[\"D_ETRS_1989\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",500000.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",9.0],PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]"));
    }

    @Test
    @DisplayName("A datum with a 3D geographic sibling still identifies its 2D and projected systems")
    void datumWithThreeDimensionalSiblingIdentifies() {
        assertEquals("EPSG:3824", identify("GEOGCS[\"GCS_TWD_1997\",DATUM[\"D_TWD_1997\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"));
        assertEquals("EPSG:3826", identify("PROJCS[\"TWD_1997_TM_Taiwan\",GEOGCS[\"GCS_TWD_1997\",DATUM[\"D_TWD_1997\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",250000.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",121.0],PARAMETER[\"Scale_Factor\",0.9999],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]"));
        assertEquals("EPSG:4023", identify("GEOGCS[\"GCS_MOLDREF99\",DATUM[\"D_MOLDREF99\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"));
        assertEquals("EPSG:4026", identify("PROJCS[\"MOLDREF99_Moldova_TM\",GEOGCS[\"GCS_MOLDREF99\",DATUM[\"D_MOLDREF99\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",200000.0],PARAMETER[\"False_Northing\",-5000000.0],PARAMETER[\"Central_Meridian\",28.4],PARAMETER[\"Scale_Factor\",0.99994],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]"));
    }
}
