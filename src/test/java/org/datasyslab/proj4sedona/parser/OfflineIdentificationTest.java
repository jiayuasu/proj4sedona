package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.defs.CRSProvider;
import org.datasyslab.proj4sedona.defs.CRSResult;
import org.datasyslab.proj4sedona.defs.Defs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EPSG identification is a local computation: it must never consult a remote provider,
 * however the candidate codes are chosen. A provider that declares itself remote is
 * registered ahead of the built-in one and counts every call it receives.
 */
class OfflineIdentificationTest {

    private static final String ESRI_NAD83_GCS =
        "GEOGCS[\"GCS_North_American_1983\",DATUM[\"D_North_American_1983\","
        + "SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],"
        + "UNIT[\"Degree\",0.0174532925199433]]";

    private static final String ESRI_STATE_PLANE_CA_V =
        "PROJCS[\"NAD_1983_StatePlane_California_V_FIPS_0405_Feet\"," + ESRI_NAD83_GCS
        + ",PROJECTION[\"Lambert_Conformal_Conic\"],PARAMETER[\"False_Easting\",6561666.666666666],"
        + "PARAMETER[\"False_Northing\",1640416.666666667],PARAMETER[\"Central_Meridian\",-118.0],"
        + "PARAMETER[\"Standard_Parallel_1\",34.03333333333333],"
        + "PARAMETER[\"Standard_Parallel_2\",35.46666666666667],"
        + "PARAMETER[\"Latitude_Of_Origin\",33.5],UNIT[\"Foot_US\",0.3048006096012192]]";

    private static final String ESRI_NAD83_UTM19N =
        "PROJCS[\"NAD_1983_UTM_Zone_19N\"," + ESRI_NAD83_GCS
        + ",PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",500000.0],"
        + "PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",-69.0],"
        + "PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],"
        + "UNIT[\"Meter\",1.0]]";

    /** A code no provider bundles and no other test uses, so lookups are deterministic. */
    private static final String PROBE_CODE = "999999";

    private final AtomicInteger remoteCalls = new AtomicInteger();

    private final CRSProvider countingRemote = new CRSProvider() {
        @Override
        public String getName() {
            return "counting-remote";
        }

        @Override
        public CRSResult resolve(String authority, String code) {
            remoteCalls.incrementAndGet();
            // Answer the probe code so the chain stops here and the real remote provider,
            // which is registered after this one, is never reached during the test.
            return PROBE_CODE.equals(code) ? CRSResult.proj4("+proj=longlat +datum=WGS84 +no_defs") : null;
        }

        @Override
        public boolean isRemote() {
            return true;
        }
    };

    @BeforeEach
    void registerCountingRemote() {
        // A fresh registry for every test: no definition cached by one test may satisfy or
        // defeat a lookup in another, whatever order the methods run in.
        Defs.reset();
        Defs.globals();
        // The counting provider stands in for the remote catalog: the real one is removed so
        // nothing here can reach the network, and the stand-in sits ahead of the built-in
        // provider (priority 100) so any lookup that reaches the chain hits it first.
        Defs.removeProvider("spatialreference.org");
        Defs.registerProvider(countingRemote, 10);
    }

    @AfterEach
    void resetRegistry() {
        Defs.reset();
    }

    @Test
    @DisplayName("A name-directed lookup resolves its one code through the providers; blind probing stays offline")
    void nameDirectedLookupResolvesOnDemand() {
        // The Esri name says exactly which definition to compare against. No local source
        // has it, so that single code is resolved through the full provider chain, remote
        // included. The stand-in answers nothing for it, so the name is not trusted, and the
        // remaining parameter probing never reaches the provider.
        String result = CRSSerializer.toEpsgCode(new Proj(ESRI_STATE_PLANE_CA_V));
        assertEquals(1, remoteCalls.get(), "expected exactly the name-directed lookup");
        assertNull(result, "an unresolvable name must not identify");
    }

    @Test
    @DisplayName("Identifying a bundled UTM zone by its Esri name never consults a remote provider")
    void bundledUtmZoneIdentifiesOffline() {
        String result = CRSSerializer.toEpsgCode(new Proj(ESRI_NAD83_UTM19N));
        assertEquals(0, remoteCalls.get(), "toEpsgCode reached a remote provider");
        assertEquals("EPSG:26919", result);
    }

    @Test
    @DisplayName("Identifying a geographic CRS never consults a remote provider")
    void geographicCrsIdentifiesOffline() {
        String result = CRSSerializer.toEpsgCode(new Proj(ESRI_NAD83_GCS));
        assertEquals(0, remoteCalls.get(), "toEpsgCode reached a remote provider");
        assertEquals("EPSG:4269", result);
    }

    @Test
    @DisplayName("Ordinary definition lookup still reaches remote providers for unbundled codes")
    void definitionLookupStillUsesRemoteProviders() {
        assertNotNull(Defs.get("EPSG:" + PROBE_CODE));
        assertEquals(1, remoteCalls.get(), "Defs.get must still consult the remote provider");
    }

    @Test
    @DisplayName("Offline lookup resolves bundled codes and skips remote providers")
    void localLookupSkipsRemoteProviders() {
        assertNotNull(Defs.getLocal("EPSG:26919"));
        assertNull(Defs.getLocal("EPSG:" + PROBE_CODE));
        assertEquals(0, remoteCalls.get(), "Defs.getLocal reached a remote provider");
    }

    @Test
    @DisplayName("Offline identification never shadows a higher-priority provider in ordinary resolution")
    void identificationDoesNotShadowHigherPriorityProvider() {
        // A remote provider ahead of the built-in one supplies its own EPSG:26919 with an
        // explicit shift. Identification skips it (offline) and matches the bundled
        // definition; afterwards ordinary resolution must still return the provider's.
        CRSProvider custom = new CRSProvider() {
            @Override
            public String getName() {
                return "custom-26919";
            }

            @Override
            public CRSResult resolve(String authority, String code) {
                return "26919".equals(code)
                    ? CRSResult.proj4("+proj=utm +zone=19 +datum=NAD83 +towgs84=1,2,3 +units=m +no_defs")
                    : null;
            }

            @Override
            public boolean isRemote() {
                return true;
            }
        };
        Defs.registerProvider(custom, 50);
        try {
            assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(ESRI_NAD83_UTM19N)));
            ProjectionDef def = Defs.get("EPSG:26919");
            assertNotNull(def);
            assertArrayEquals(new double[] {1, 2, 3}, def.getDatumParams(), 1e-12,
                "identification cached the bundled definition over the higher-priority provider");
        } finally {
            Defs.removeProvider("custom-26919");
        }
    }

    /** OGC spelling, no AUTHORITY: identification must go through parameter matching. */
    private static final String OGC_NAD83_UTM19N =
        "PROJCS[\"NAD83 / UTM zone 19N\",GEOGCS[\"NAD83\",DATUM[\"North_American_Datum_1983\","
        + "SPHEROID[\"GRS 1980\",6378137,298.257222101]],PRIMEM[\"Greenwich\",0],"
        + "UNIT[\"degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],"
        + "PARAMETER[\"latitude_of_origin\",0],PARAMETER[\"central_meridian\",-69],"
        + "PARAMETER[\"scale_factor\",0.9996],PARAMETER[\"false_easting\",500000],"
        + "PARAMETER[\"false_northing\",0],UNIT[\"metre\",1]]";

    private static final String SHIFTED_26919 =
        "+proj=utm +zone=19 +datum=NAD83 +towgs84=1,2,3 +units=m +no_defs";
    private static final String PLAIN_26919 = "+proj=utm +zone=19 +datum=NAD83 +units=m +no_defs";

    /** A local provider whose EPSG:26919 definition can be changed while registered. */
    private static CRSProvider mutableLocal26919(AtomicReference<String> definition) {
        return new CRSProvider() {
            @Override
            public String getName() {
                return "mutable-26919";
            }

            @Override
            public CRSResult resolve(String authority, String code) {
                return "26919".equals(code) ? CRSResult.proj4(definition.get()) : null;
            }
        };
    }

    @Test
    @DisplayName("Removing a code invalidates the offline cache too")
    void removeInvalidatesOfflineCache() {
        AtomicReference<String> definition = new AtomicReference<>(SHIFTED_26919);
        Defs.registerProvider(mutableLocal26919(definition), 50);
        try {
            assertNull(CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)),
                "the provider's shifted definition must not match the unshifted WKT");
            definition.set(PLAIN_26919);
            Defs.remove("EPSG:26919");
            assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)),
                "a stale offline definition survived Defs.remove");
        } finally {
            Defs.removeProvider("mutable-26919");
        }
    }

    @Test
    @DisplayName("Setting a code to an empty definition invalidates the offline cache too")
    void emptySetInvalidatesOfflineCache() {
        AtomicReference<String> definition = new AtomicReference<>(SHIFTED_26919);
        Defs.registerProvider(mutableLocal26919(definition), 50);
        try {
            assertNull(CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)));
            definition.set(PLAIN_26919);
            Defs.set("EPSG:26919", "");
            assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)),
                "a stale offline definition survived an empty Defs.set");
        } finally {
            Defs.removeProvider("mutable-26919");
        }
    }

    @Test
    @DisplayName("Setting a code to a null definition invalidates the offline cache too")
    void nullDefinitionSetInvalidatesOfflineCache() {
        AtomicReference<String> definition = new AtomicReference<>(SHIFTED_26919);
        Defs.registerProvider(mutableLocal26919(definition), 50);
        try {
            assertNull(CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)));
            definition.set(PLAIN_26919);
            Defs.set("EPSG:26919", (ProjectionDef) null);
            assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)),
                "a stale offline definition survived Defs.set(code, (ProjectionDef) null)");
        } finally {
            Defs.removeProvider("mutable-26919");
        }
    }

    @Test
    @DisplayName("Setting a code to a null PROJJSON document invalidates the offline cache too")
    void nullProjJsonSetInvalidatesOfflineCache() {
        AtomicReference<String> definition = new AtomicReference<>(SHIFTED_26919);
        Defs.registerProvider(mutableLocal26919(definition), 50);
        try {
            assertNull(CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)));
            definition.set(PLAIN_26919);
            Defs.set("EPSG:26919", (Map<String, Object>) null);
            assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(OGC_NAD83_UTM19N)),
                "a stale offline definition survived Defs.set(code, (Map) null)");
        } finally {
            Defs.removeProvider("mutable-26919");
        }
    }
}
