package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.defs.Defs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end checks of identification by Esri name with the default providers: the
 * definitions behind these codes are not bundled, so each is fetched from the remote
 * catalog on demand and the WKT, Esri's own for each system, is validated against it. Like
 * the remote-fetch tests in {@code DefsTest}, this needs network access.
 */
class EsriNameLiveIdentificationTest {

    private static final String[][] CASES = {
        {"EPSG:2229", "PROJCS[\"NAD_1983_StatePlane_California_V_FIPS_0405_Feet\",GEOGCS[\"GCS_North_American_1983\",DATUM[\"D_North_American_1983\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Lambert_Conformal_Conic\"],PARAMETER[\"False_Easting\",6561666.666666666],PARAMETER[\"False_Northing\",1640416.666666667],PARAMETER[\"Central_Meridian\",-118.0],PARAMETER[\"Standard_Parallel_1\",34.03333333333333],PARAMETER[\"Standard_Parallel_2\",35.46666666666667],PARAMETER[\"Latitude_Of_Origin\",33.5],UNIT[\"Foot_US\",0.3048006096012192]]"},
        {"EPSG:4258", "GEOGCS[\"GCS_ETRS_1989\",DATUM[\"D_ETRS_1989\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"},
        {"EPSG:25832", "PROJCS[\"ETRS_1989_UTM_Zone_32N\",GEOGCS[\"GCS_ETRS_1989\",DATUM[\"D_ETRS_1989\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",500000.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",9.0],PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]"},
        {"EPSG:4275", "GEOGCS[\"GCS_NTF\",DATUM[\"D_NTF\",SPHEROID[\"Clarke_1880_IGN\",6378249.2,293.4660212936265]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"},
        {"EPSG:4314", "GEOGCS[\"GCS_Deutsches_Hauptdreiecksnetz\",DATUM[\"D_Deutsches_Hauptdreiecksnetz\",SPHEROID[\"Bessel_1841\",6377397.155,299.1528128]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"},
        {"EPSG:3824", "GEOGCS[\"GCS_TWD_1997\",DATUM[\"D_TWD_1997\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"},
        {"EPSG:3826", "PROJCS[\"TWD_1997_TM_Taiwan\",GEOGCS[\"GCS_TWD_1997\",DATUM[\"D_TWD_1997\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",250000.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",121.0],PARAMETER[\"Scale_Factor\",0.9999],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]"},
        {"EPSG:4023", "GEOGCS[\"GCS_MOLDREF99\",DATUM[\"D_MOLDREF99\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"},
        {"EPSG:4026", "PROJCS[\"MOLDREF99_Moldova_TM\",GEOGCS[\"GCS_MOLDREF99\",DATUM[\"D_MOLDREF99\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",200000.0],PARAMETER[\"False_Northing\",-5000000.0],PARAMETER[\"Central_Meridian\",28.4],PARAMETER[\"Scale_Factor\",0.99994],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]"},
        {"EPSG:2193", "PROJCS[\"NZGD_2000_New_Zealand_Transverse_Mercator\",GEOGCS[\"GCS_NZGD_2000\",DATUM[\"D_NZGD_2000\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",1600000.0],PARAMETER[\"False_Northing\",10000000.0],PARAMETER[\"Central_Meridian\",173.0],PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0]]"},
    };

    private static final String NZTM_EASTING_FIRST = "PROJCS[\"NZGD_2000_New_Zealand_Transverse_Mercator\",GEOGCS[\"GCS_NZGD_2000\",DATUM[\"D_NZGD_2000\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",1600000.0],PARAMETER[\"False_Northing\",10000000.0],PARAMETER[\"Central_Meridian\",173.0],PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"Latitude_Of_Origin\",0.0],UNIT[\"Meter\",1.0],AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]]";

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
    @DisplayName("Esri-named systems identify by name with their definitions fetched on demand")
    void esriNamedSystemsIdentifyThroughTheRemoteCatalog() {
        for (String[] c : CASES) {
            assertEquals(c[0], CRSSerializer.toEpsgCode(new Proj(c[1])), c[1]);
        }
    }

    @Test
    @DisplayName("A declared easting-first order is not trusted against EPSG's north-first NZTM")
    void declaredAxesAreHeldAgainstTheFetchedDefinition() {
        assertNotEquals("EPSG:2193", CRSSerializer.toEpsgCode(new Proj(NZTM_EASTING_FIRST)));
    }
}
