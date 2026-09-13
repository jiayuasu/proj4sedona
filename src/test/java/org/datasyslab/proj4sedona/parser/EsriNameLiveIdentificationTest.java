package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.defs.Defs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of identification by Esri name with the default providers: the
 * definition behind a State Plane code is not bundled, so it is fetched from the remote
 * catalog on demand. Like the remote-fetch tests in {@code DefsTest}, this needs network
 * access.
 */
class EsriNameLiveIdentificationTest {

    private static final String ESRI_STATE_PLANE_CA_V =
        "PROJCS[\"NAD_1983_StatePlane_California_V_FIPS_0405_Feet\",GEOGCS[\"GCS_North_American_1983\","
        + "DATUM[\"D_North_American_1983\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],"
        + "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],"
        + "PROJECTION[\"Lambert_Conformal_Conic\"],PARAMETER[\"False_Easting\",6561666.666666666],"
        + "PARAMETER[\"False_Northing\",1640416.666666667],PARAMETER[\"Central_Meridian\",-118.0],"
        + "PARAMETER[\"Standard_Parallel_1\",34.03333333333333],"
        + "PARAMETER[\"Standard_Parallel_2\",35.46666666666667],"
        + "PARAMETER[\"Latitude_Of_Origin\",33.5],UNIT[\"Foot_US\",0.3048006096012192]]";

    @BeforeEach
    void freshRegistry() {
        Defs.reset();
        Defs.globals();
    }

    @AfterEach
    void resetRegistry() {
        Defs.reset();
    }

    @Test
    @DisplayName("A State Plane zone identifies by name with its definition fetched on demand")
    void statePlaneIdentifiesThroughTheRemoteCatalog() {
        assertEquals("EPSG:2229", CRSSerializer.toEpsgCode(new Proj(ESRI_STATE_PLANE_CA_V)));
    }
}
