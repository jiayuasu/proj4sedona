package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.datasyslab.proj4sedona.core.Proj;
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

    private final AtomicInteger remoteCalls = new AtomicInteger();

    private final CRSProvider countingRemote = new CRSProvider() {
        @Override
        public String getName() {
            return "counting-remote";
        }

        @Override
        public CRSResult resolve(String authority, String code) {
            remoteCalls.incrementAndGet();
            return null;
        }

        @Override
        public boolean isRemote() {
            return true;
        }
    };

    @BeforeEach
    void registerCountingRemote() {
        Defs.globals();
        // Ahead of the built-in provider (priority 100), so any lookup that reaches the
        // provider chain hits the remote first.
        Defs.registerProvider(countingRemote, 10);
    }

    @AfterEach
    void removeCountingRemote() {
        Defs.removeProvider("counting-remote");
    }

    @Test
    @DisplayName("Identifying an unbundled projected CRS never consults a remote provider")
    void unidentifiableProjectedCrsStaysOffline() {
        String result = CRSSerializer.toEpsgCode(new Proj(ESRI_STATE_PLANE_CA_V));
        assertEquals(0, remoteCalls.get(), "toEpsgCode reached a remote provider");
        assertNull(result, "no bundled definition matches a State Plane zone");
    }

    @Test
    @DisplayName("Identifying a bundled UTM zone never consults a remote provider")
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
        Defs.get("EPSG:2229");
        assertEquals(1, remoteCalls.get(), "Defs.get must still consult the remote provider");
    }

    @Test
    @DisplayName("Offline lookup resolves bundled codes and skips remote providers")
    void localLookupSkipsRemoteProviders() {
        assertNotNull(Defs.getLocal("EPSG:26919"));
        assertNull(Defs.getLocal("EPSG:2229"));
        assertEquals(0, remoteCalls.get(), "Defs.getLocal reached a remote provider");
    }
}
