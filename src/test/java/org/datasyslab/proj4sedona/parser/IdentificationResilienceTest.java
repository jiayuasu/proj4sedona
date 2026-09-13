package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.defs.CRSFetchException;
import org.datasyslab.proj4sedona.defs.CRSProvider;
import org.datasyslab.proj4sedona.defs.CRSResult;
import org.datasyslab.proj4sedona.defs.Defs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A failing provider must not turn identification, and with it serialization, into an
 * exception: identification answers null and the CRS still exports.
 */
class IdentificationResilienceTest {

    private static final String ESRI_STATE_PLANE =
        "PROJCS[\"NAD_1983_StatePlane_California_V_FIPS_0405_Feet\",GEOGCS[\"GCS_North_American_1983\","
        + "DATUM[\"D_North_American_1983\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],"
        + "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],"
        + "PROJECTION[\"Lambert_Conformal_Conic\"],PARAMETER[\"False_Easting\",6561666.666666666],"
        + "PARAMETER[\"False_Northing\",1640416.666666667],PARAMETER[\"Central_Meridian\",-118.0],"
        + "PARAMETER[\"Standard_Parallel_1\",34.03333333333333],"
        + "PARAMETER[\"Standard_Parallel_2\",35.46666666666667],"
        + "PARAMETER[\"Latitude_Of_Origin\",33.5],UNIT[\"Foot_US\",0.3048006096012192]]";

    private final CRSProvider failing = new CRSProvider() {
        @Override
        public String getName() {
            return "failing-catalog";
        }

        @Override
        public CRSResult resolve(String authority, String code) {
            throw new CRSFetchException(authority + ":" + code, CRSFetchException.Reason.NOT_FOUND,
                "provider is unavailable");
        }

        @Override
        public boolean isRemote() {
            return true;
        }
    };

    @BeforeEach
    void useFailingProvider() {
        Defs.reset();
        Defs.globals();
        Defs.removeProvider("spatialreference.org");
        Defs.registerProvider(failing, 150);
    }

    @AfterEach
    void resetRegistry() {
        Defs.reset();
    }

    @Test
    @DisplayName("A provider that throws while a name is resolved leaves identification unanswered")
    void failingProviderYieldsNoIdentification() {
        Proj proj = new Proj(ESRI_STATE_PLANE);
        assertNull(assertDoesNotThrow(() -> CRSSerializer.toEpsgCode(proj)));
    }

    @Test
    @DisplayName("A provider that throws does not break serialization")
    void failingProviderDoesNotBreakSerialization() {
        Proj proj = new Proj(ESRI_STATE_PLANE);
        assertNotNull(assertDoesNotThrow(() -> CRSSerializer.toProjJson(proj)));
        assertNotNull(assertDoesNotThrow(() -> CRSSerializer.toWkt2(proj)));
    }
}
