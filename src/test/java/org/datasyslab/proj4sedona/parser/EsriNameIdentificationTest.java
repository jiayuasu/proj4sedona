package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
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
 * remote catalog. Its documents are synthetic PROJJSON with the shape the production
 * provider returns (datum names and ensembles spelled as the catalog spells them, EPSG's
 * north-first geographic axes, EPSG method and parameter names, a BoundCRS for a datum
 * transformation) but with invented projection parameters, so no registry definition is
 * committed here; the inputs use the same invented parameters. The provider declares
 * itself remote, so blind parameter probing cannot use it, and the real remote provider is
 * removed, so the tests are deterministic and offline. {@link EsriNameLiveIdentificationTest}
 * covers the real definitions.</p>
 */
class EsriNameIdentificationTest {

    // ---- Esri-style inputs ----

    private static final String GRS80 = "SPHEROID[\"GRS_1980\",6378137.0,298.257222101]";
    private static final String GREENWICH = "PRIMEM[\"Greenwich\",0.0]";
    private static final String DEGREE = "UNIT[\"Degree\",0.0174532925199433]";
    private static final String FOOT_US = "UNIT[\"Foot_US\",0.3048006096012192]";
    private static final String METER = "UNIT[\"Meter\",1.0]";

    private static String gcs(String name, String datum, String spheroid, String primem, String unit) {
        return "GEOGCS[\"" + name + "\",DATUM[\"" + datum + "\"," + spheroid + "]," + primem + "," + unit + "]";
    }

    private static String gcs(String name, String datum, String spheroid) {
        return gcs(name, datum, spheroid, GREENWICH, DEGREE);
    }

    private static final String NAD83_GCS = gcs("GCS_North_American_1983", "D_North_American_1983", GRS80);
    private static final String WGS84_GCS =
        gcs("GCS_WGS_1984", "D_WGS_1984", "SPHEROID[\"WGS_1984\",6378137.0,298.257223563]");
    private static final String ETRS89_GCS = gcs("GCS_ETRS_1989", "D_ETRS_1989", GRS80);
    private static final String NZGD2000_GCS = gcs("GCS_NZGD_2000", "D_NZGD_2000", GRS80);
    private static final String TWD97_GCS = gcs("GCS_TWD_1997", "D_TWD_1997", GRS80);
    private static final String MOLDREF99_GCS = gcs("GCS_MOLDREF99", "D_MOLDREF99", GRS80);
    private static final String CUSTOM_DATUM_GCS = gcs("GCS_Custom", "D_Custom_Survey_Datum", GRS80);

    /** Lambert conformal conic with the invented State Plane parameters, parallels ascending as Esri lists them. */
    private static String lcc(String name, String gcs, double parallel1, double parallel2, String unit) {
        return "PROJCS[\"" + name + "\"," + gcs
            + ",PROJECTION[\"Lambert_Conformal_Conic\"],PARAMETER[\"False_Easting\",3000000.0],"
            + "PARAMETER[\"False_Northing\",1500000.0],PARAMETER[\"Central_Meridian\",-120.0],"
            + "PARAMETER[\"Standard_Parallel_1\"," + parallel1 + "],PARAMETER[\"Standard_Parallel_2\"," + parallel2 + "],"
            + "PARAMETER[\"Latitude_Of_Origin\",30.0]," + unit + "]";
    }

    private static String tm(String name, String gcs, double lat0, double lon0, double k, double fe, double fn, String axes) {
        return "PROJCS[\"" + name + "\"," + gcs
            + ",PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\"," + fe + "],"
            + "PARAMETER[\"False_Northing\"," + fn + "],PARAMETER[\"Central_Meridian\"," + lon0 + "],"
            + "PARAMETER[\"Scale_Factor\"," + k + "],PARAMETER[\"Latitude_Of_Origin\"," + lat0 + "],"
            + METER + axes + "]";
    }

    private static String utm(String name, String gcs, double centralMeridian) {
        return tm(name, gcs, 0.0, centralMeridian, 0.9996, 500000.0, 0.0, "");
    }

    private static final String STATE_PLANE = lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS, 31.0, 32.0, FOOT_US);
    private static final String NZTM = tm("NZGD_2000_New_Zealand_Transverse_Mercator", NZGD2000_GCS, -1.0, 172.5, 0.9993, 1550000.0, 9500000.0, "");
    private static final String EASTING_FIRST = ",AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]";
    private static final String NORTHING_FIRST = ",AXIS[\"Northing\",NORTH],AXIS[\"Easting\",EAST]";

    // ---- Synthetic catalog documents ----

    private static String axes(String first, String second, String unit) {
        return "\"coordinate_system\":{\"subtype\":\"" + ("degree".equals(unit) ? "ellipsoidal" : "Cartesian")
            + "\",\"axis\":[" + axis(first, unit) + "," + axis(second, unit) + "]}";
    }

    private static String axis(String direction, String unit) {
        String name = "north".equals(direction) ? ("degree".equals(unit) ? "Geodetic latitude" : "Northing")
            : ("degree".equals(unit) ? "Geodetic longitude" : "Easting");
        String unitJson = "degree".equals(unit) ? "\"degree\""
            : "{\"type\":\"LinearUnit\",\"name\":\"" + unit + "\",\"conversion_factor\":" + ("metre".equals(unit) ? "1" : "0.304800609601219") + "}";
        return "{\"name\":\"" + name + "\",\"direction\":\"" + direction + "\",\"unit\":" + unitJson + "}";
    }

    private static String ellipsoid(String name, double a, String secondKey, double second) {
        return "\"ellipsoid\":{\"name\":\"" + name + "\",\"semi_major_axis\":" + a + ",\"" + secondKey + "\":" + second + "}";
    }

    private static String datum(String name, String ellipsoid) {
        return "\"datum\":{\"type\":\"GeodeticReferenceFrame\",\"name\":\"" + name + "\"," + ellipsoid + "}";
    }

    private static String ensemble(String name, String ellipsoid, int code) {
        return "\"datum_ensemble\":{\"name\":\"" + name + "\",\"members\":[{\"name\":\"" + name.replace(" ensemble", " frame") + "\"}],"
            + ellipsoid + ",\"accuracy\":\"0.1\",\"id\":{\"authority\":\"EPSG\",\"code\":" + code + "}}";
    }

    private static String geographic(String name, int code, String datum) {
        return "{\"type\":\"GeographicCRS\",\"name\":\"" + name + "\"," + datum + ","
            + axes("north", "east", "degree") + ",\"id\":{\"authority\":\"EPSG\",\"code\":" + code + "}}";
    }

    private static String parameter(String name, double value, String unit) {
        // The catalog spells the common units as strings and any other linear unit as an
        // object with its conversion factor.
        String unitJson = "US survey foot".equals(unit)
            ? "{\"type\":\"LinearUnit\",\"name\":\"US survey foot\",\"conversion_factor\":0.304800609601219}"
            : "\"" + unit + "\"";
        return "{\"name\":\"" + name + "\",\"value\":" + value + ",\"unit\":" + unitJson + "}";
    }

    private static String projected(String name, int code, String base, String method, String parameters, String unit, String first, String second) {
        return "{\"type\":\"ProjectedCRS\",\"name\":\"" + name + "\",\"base_crs\":" + base
            + ",\"conversion\":{\"name\":\"" + name + " conversion\",\"method\":{\"name\":\"" + method + "\"},\"parameters\":[" + parameters + "]},"
            + axes(first, second, unit) + ",\"id\":{\"authority\":\"EPSG\",\"code\":" + code + "}}";
    }

    private static String transverseMercator(String name, int code, String base, double lat0, double lon0, double k, double fe, double fn, String first, String second) {
        return projected(name, code, base, "Transverse Mercator",
            parameter("Latitude of natural origin", lat0, "degree") + "," + parameter("Longitude of natural origin", lon0, "degree") + ","
            + parameter("Scale factor at natural origin", k, "unity") + "," + parameter("False easting", fe, "metre") + ","
            + parameter("False northing", fn, "metre"), "metre", first, second);
    }

    private static String bound(String source, double... towgs84) {
        String[] names = {"X-axis translation", "Y-axis translation", "Z-axis translation",
            "X-axis rotation", "Y-axis rotation", "Z-axis rotation", "Scale difference"};
        String[] units = {"metre", "metre", "metre", "arc-second", "arc-second", "arc-second", "parts per million"};
        StringBuilder parameters = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            parameters.append(i > 0 ? "," : "").append(parameter(names[i], towgs84[i], units[i]));
        }
        String wgs84 = geographic("WGS 84", 4326, datum("World Geodetic System 1984", ellipsoid("WGS 84", 6378137, "inverse_flattening", 298.257223563)));
        return "{\"type\":\"BoundCRS\",\"source_crs\":" + source + ",\"target_crs\":" + wgs84
            + ",\"transformation\":{\"name\":\"to WGS 84\",\"method\":{\"name\":\"Position Vector transformation (geog2D domain)\",\"id\":{\"authority\":\"EPSG\",\"code\":9606}},\"parameters\":[" + parameters + "]}}";
    }

    private static final String GRS80_JSON = ellipsoid("GRS 1980", 6378137, "inverse_flattening", 298.257222101);
    private static final Map<String, String> CATALOG = new HashMap<>();

    static {
        String nad83 = geographic("NAD83", 4269, datum("North American Datum 1983", GRS80_JSON));
        String etrs89 = geographic("ETRS89", 4258, ensemble("European Terrestrial Reference System 1989 ensemble", GRS80_JSON, 6258));
        String nzgd2000 = geographic("NZGD2000", 4167, datum("New Zealand Geodetic Datum 2000", GRS80_JSON));
        String twd97 = geographic("TWD97", 3824, datum("Taiwan Datum 1997", GRS80_JSON));
        String moldref99 = geographic("MOLDREF99", 4023, datum("MOLDREF99", GRS80_JSON));

        CATALOG.put("4258", etrs89);
        CATALOG.put("4275", geographic("NTF", 4275, datum("Nouvelle Triangulation Francaise",
            ellipsoid("Clarke 1880 (IGN)", 6378249.2, "semi_minor_axis", 6356515))));
        CATALOG.put("4314", bound(geographic("DHDN", 4314, datum("Deutsches Hauptdreiecksnetz",
            ellipsoid("Bessel 1841", 6377397.155, "inverse_flattening", 299.1528128))), 100, 200, 300, 1, 2, 3, 4));
        CATALOG.put("3824", twd97);
        CATALOG.put("4023", moldref99);
        // Invented parameters; EPSG lists the parallels the other way round from Esri.
        CATALOG.put("2229", projected("NAD83 / California zone 5 (ftUS)", 2229, nad83, "Lambert Conic Conformal (2SP)",
            parameter("Latitude of false origin", 30, "degree") + "," + parameter("Longitude of false origin", -120, "degree") + ","
            + parameter("Latitude of 1st standard parallel", 32, "degree") + "," + parameter("Latitude of 2nd standard parallel", 31, "degree") + ","
            + parameter("Easting at false origin", 3000000, "US survey foot") + "," + parameter("Northing at false origin", 1500000, "US survey foot"),
            "US survey foot", "east", "north"));
        CATALOG.put("25832", transverseMercator("ETRS89 / UTM zone 32N", 25832, etrs89, 0, 9, 0.9996, 500000, 0, "east", "north"));
        CATALOG.put("3826", transverseMercator("TWD97 / TM2 zone 121", 3826, twd97, 0, 120.5, 0.9995, 300000, 0, "east", "north"));
        CATALOG.put("4026", transverseMercator("MOLDREF99 / Moldova TM", 4026, moldref99, 0, 28.5, 0.9994, 150000, -4000000, "east", "north"));
        // EPSG lists NZTM north-first.
        CATALOG.put("2193", transverseMercator("NZGD2000 / New Zealand Transverse Mercator 2000", 2193, nzgd2000, -1, 172.5, 0.9993, 1550000, 9500000, "north", "east"));
    }

    private final CRSProvider catalog = new CRSProvider() {
        @Override
        public String getName() {
            return "test-catalog";
        }

        @Override
        public CRSResult resolve(String authority, String code) {
            String document = "epsg".equalsIgnoreCase(authority) ? CATALOG.get(code) : null;
            return document == null ? null : CRSResult.projJson(document);
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

    private static String identify(String wkt) {
        return CRSSerializer.toEpsgCode(new Proj(wkt));
    }

    @Test
    @DisplayName("An Esri State Plane name identifies once its definition is resolved")
    void statePlaneIdentifiesByName() {
        assertEquals("EPSG:2229", identify(STATE_PLANE));
    }

    @Test
    @DisplayName("An Esri geographic name identifies through a datum known only by its Esri spelling")
    void geographicNameIdentifiesThroughDatumIndex() {
        assertEquals("EPSG:4258", identify(ETRS89_GCS));
    }

    @Test
    @DisplayName("A projected name on a datum known only by its Esri spelling identifies")
    void projectedNameOnEsriSpelledDatumIdentifies() {
        assertEquals("EPSG:25832", identify(utm("ETRS_1989_UTM_Zone_32N", ETRS89_GCS, 9.0)));
    }

    @Test
    @DisplayName("A datum spelled in a way no table knows still compares by identity")
    void datumSpelledOnlyByTheCatalogIdentifies() {
        assertEquals("EPSG:4275", identify(gcs("GCS_NTF", "D_NTF", "SPHEROID[\"Clarke_1880_IGN\",6378249.2,293.4660212936265]")));
        assertEquals("EPSG:3824", identify(TWD97_GCS));
        assertEquals("EPSG:3826", identify(tm("TWD_1997_TM_Taiwan", TWD97_GCS, 0.0, 120.5, 0.9995, 300000.0, 0.0, "")));
        assertEquals("EPSG:4023", identify(MOLDREF99_GCS));
        assertEquals("EPSG:4026", identify(tm("MOLDREF99_Moldova_TM", MOLDREF99_GCS, 0.0, 28.5, 0.9994, 150000.0, -4000000.0, "")));
    }

    @Test
    @DisplayName("A definition that states no datum operation matches a reference bound to WGS 84; a different stated one does not")
    void statedOperationMustAgreeAbsentOneNeedNot() {
        String bessel = "SPHEROID[\"Bessel_1841\",6377397.155,299.1528128]";
        assertEquals("EPSG:4314", identify(gcs("GCS_Deutsches_Hauptdreiecksnetz", "D_Deutsches_Hauptdreiecksnetz", bessel)));
        assertNotEquals("EPSG:4314", identify(gcs("GCS_Deutsches_Hauptdreiecksnetz", "D_Deutsches_Hauptdreiecksnetz",
            bessel + ",TOWGS84[-8,160,176]")));
    }

    @Test
    @DisplayName("A name whose datum disagrees with the WKT is not trusted")
    void mismatchedDatumRejectsTheName() {
        assertEquals("EPSG:32619", identify(utm("NAD_1983_UTM_Zone_19N", WGS84_GCS, -69.0)));
        assertNull(identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", WGS84_GCS, 31.0, 32.0, FOOT_US)));
    }

    @Test
    @DisplayName("A name whose parameters disagree with its definition is not trusted")
    void mismatchedParametersRejectTheName() {
        assertEquals("EPSG:26918", identify(utm("NAD_1983_UTM_Zone_19N", NAD83_GCS, -75.0)));
        assertNull(identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS, 33.0, 34.0, FOOT_US)));
        assertNull(identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS, 31.0, 32.0, METER)));
    }

    @Test
    @DisplayName("A projected name is accepted with the standard parallels in either order")
    void projectedNameAcceptsEitherParallelOrder() {
        assertEquals("EPSG:2229", identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", NAD83_GCS, 32.0, 31.0, FOOT_US)));
    }

    @Test
    @DisplayName("A geographic name with a different ellipsoid, meridian, unit, or explicit shift is not trusted")
    void mutatedGeographicNameIsRejected() {
        assertNotEquals("EPSG:4258", identify(gcs("GCS_ETRS_1989", "D_ETRS_1989", "SPHEROID[\"Clarke_1866\",6378206.4,294.9786982]")));
        String paris = gcs("GCS_ETRS_1989", "D_ETRS_1989", GRS80, "PRIMEM[\"Paris\",2.337229166666667]", DEGREE);
        assertNotEquals("EPSG:4258", identify(paris));
        assertFalse(CRSSerializer.toProjJson(new Proj(paris)).contains("4258"),
            "PROJJSON must not carry an identifier the parameters contradict");
        assertNotEquals("EPSG:4258", identify(gcs("GCS_ETRS_1989", "D_ETRS_1989", GRS80, GREENWICH, "UNIT[\"Grad\",0.01570796326794897]")));
        assertNotEquals("EPSG:4258", identify(gcs("GCS_ETRS_1989", "D_ETRS_1989", GRS80 + ",TOWGS84[-8,160,176]")));
    }

    @Test
    @DisplayName("A name that declares no axes is not held to the registry's north-first convention")
    void undeclaredAxesAcceptTheRegistryOrder() {
        assertEquals("EPSG:2193", identify(NZTM));
        assertEquals("EPSG:2193", identify(tm("NZGD_2000_New_Zealand_Transverse_Mercator", NZGD2000_GCS, -1.0, 172.5, 0.9993, 1550000.0, 9500000.0, NORTHING_FIRST)));
    }

    @Test
    @DisplayName("A name that declares an axis order the registry contradicts is not trusted")
    void declaredAxesMustAgreeWithTheRegistry() {
        String eastingFirst = tm("NZGD_2000_New_Zealand_Transverse_Mercator", NZGD2000_GCS, -1.0, 172.5, 0.9993, 1550000.0, 9500000.0, EASTING_FIRST);
        assertNotEquals("EPSG:2193", identify(eastingFirst));
        assertFalse(CRSSerializer.toProjJson(new Proj(eastingFirst)).contains("2193"),
            "PROJJSON must not pair easting-first axes with a north-first identifier");
    }

    @Test
    @DisplayName("A name on an unrecognised datum does not identify")
    void unknownDatumDoesNotIdentify() {
        assertNull(identify(lcc("NAD_1983_StatePlane_California_V_FIPS_0405_Feet", CUSTOM_DATUM_GCS, 31.0, 32.0, FOOT_US)));
    }

    @Test
    @DisplayName("A name whose definition cannot be resolved is not trusted")
    void unresolvableDefinitionIsNotTrusted() {
        // Alabama East (EPSG:26929) is in the index but no provider serves it here.
        assertNull(identify(tm("NAD_1983_StatePlane_Alabama_East_FIPS_0101", NAD83_GCS, 30.5, -85.83333333333333, 0.99996, 200000.0, 0.0, "")));
    }

    @Test
    @DisplayName("Names that are not Esri names fall through to the other phases")
    void nonEsriNamesFallThrough() {
        assertEquals("EPSG:4326", CRSSerializer.toEpsgCode(new Proj("EPSG:4326")));
        assertEquals("EPSG:26919", identify(utm("NAD83 / UTM zone 19N", NAD83_GCS, -69.0)));
    }
}
