package org.datasyslab.proj4sedona.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The generated Esri name index resolves Esri object names to authority codes. */
class EsriAliasesTest {

    @Test
    @DisplayName("The index loads the full Esri object list")
    void indexLoads() {
        assertTrue(EsriAliases.size() >= 9000, "expected thousands of names, got " + EsriAliases.size());
    }

    @Test
    @DisplayName("Projected CRS names resolve with their base geographic CRS")
    void projectedCrsByName() {
        EsriAliases.Alias utm = EsriAliases.crs("NAD_1983_UTM_Zone_19N");
        assertNotNull(utm);
        assertEquals(EsriAliases.Kind.PROJECTED_CRS, utm.getKind());
        assertEquals("EPSG:26919", utm.getCode());
        assertEquals("EPSG:4269", utm.getBaseCrs());
        assertFalse(utm.isDeprecated());

        EsriAliases.Alias statePlane = EsriAliases.crs("NAD_1983_StatePlane_California_V_FIPS_0405_Feet");
        assertNotNull(statePlane);
        assertEquals("EPSG:2229", statePlane.getCode());
        assertEquals("EPSG:4269", statePlane.getBaseCrs());

        assertEquals("EPSG:3857", EsriAliases.crs("WGS_1984_Web_Mercator_Auxiliary_Sphere").getCode());
    }

    @Test
    @DisplayName("Esri-authority objects resolve to ESRI codes")
    void esriAuthorityCodes() {
        EsriAliases.Alias albers = EsriAliases.crs("Canada_Albers_Equal_Area_Conic");
        assertNotNull(albers);
        assertEquals("ESRI:102001", albers.getCode());
        assertEquals("EPSG:4269", albers.getBaseCrs());
    }

    @Test
    @DisplayName("A name whose code changed resolves to the latest code")
    void codeChangeResolvesToLatest() {
        assertEquals("EPSG:2953", EsriAliases.crs("NAD_1983_CSRS_New_Brunswick_Stereographic").getCode());
    }

    @Test
    @DisplayName("A superseded row takes its authority and deprecation from the current object")
    void supersededRowUsesTheCurrentObjectsAuthority() {
        // Esri kept ESRI:102113 as a code-change row pointing at 3785; the object at 3785
        // is EPSG's, and deprecated.
        EsriAliases.Alias webMercator = EsriAliases.crs("WGS_1984_Web_Mercator");
        assertNotNull(webMercator);
        assertEquals("EPSG:3785", webMercator.getCode());
        assertTrue(webMercator.isDeprecated());
    }

    @Test
    @DisplayName("A datum, its 2D and 3D geographic CRSs, and its projected CRSs share one 2D base")
    void basesAreTwoDimensionalAndConsistent() {
        assertEquals("EPSG:3824", EsriAliases.datum("D_TWD_1997").getBaseCrs());
        assertEquals("EPSG:3824", EsriAliases.crs("GCS_TWD_1997").getBaseCrs());
        assertEquals("EPSG:3824", EsriAliases.crs("TWD_1997_3D").getBaseCrs());
        assertEquals("EPSG:3823", EsriAliases.crs("TWD_1997_3D").getCode());
        assertEquals("EPSG:3824", EsriAliases.crs("TWD_1997_TM_Taiwan").getBaseCrs());
        assertEquals("EPSG:4023", EsriAliases.datum("D_MOLDREF99").getBaseCrs());
        assertEquals("EPSG:4023", EsriAliases.crs("MOLDREF99_Moldova_TM").getBaseCrs());
    }

    @Test
    @DisplayName("Geographic CRS names resolve to themselves as base CRS")
    void geographicCrsByName() {
        EsriAliases.Alias nad83 = EsriAliases.crs("GCS_North_American_1983");
        assertNotNull(nad83);
        assertEquals(EsriAliases.Kind.GEODETIC_CRS, nad83.getKind());
        assertEquals("EPSG:4269", nad83.getCode());
        assertEquals("EPSG:4269", nad83.getBaseCrs());
    }

    @Test
    @DisplayName("Datum names resolve to the datum and its geographic CRS")
    void datumByName() {
        EsriAliases.Alias datum = EsriAliases.datum("D_North_American_1983");
        assertNotNull(datum);
        assertEquals(EsriAliases.Kind.GEODETIC_DATUM, datum.getKind());
        assertEquals("EPSG:6269", datum.getCode());
        assertEquals("EPSG:4269", datum.getBaseCrs());
        assertEquals("EPSG:4326", EsriAliases.datum("D_WGS_1984").getBaseCrs());
        assertEquals("EPSG:4275", EsriAliases.datum("D_NTF").getBaseCrs());
    }

    @Test
    @DisplayName("Ellipsoid names resolve without a base CRS")
    void ellipsoidByName() {
        EsriAliases.Alias grs80 = EsriAliases.ellipsoid("GRS_1980");
        assertNotNull(grs80);
        assertEquals("EPSG:7019", grs80.getCode());
        assertNull(grs80.getBaseCrs());
    }

    @Test
    @DisplayName("Lookups ignore case and surrounding whitespace, and datum names are not CRS names")
    void lookupNormalization() {
        assertEquals("EPSG:26919", EsriAliases.crs("  nad_1983_utm_zone_19n ").getCode());
        assertEquals("EPSG:6269", EsriAliases.datum("d_north_american_1983").getCode());
        assertNull(EsriAliases.crs("D_North_American_1983"));
        assertNull(EsriAliases.datum("NAD_1983_UTM_Zone_19N"));
    }

    @Test
    @DisplayName("Unknown and null names resolve to nothing")
    void unknownNames() {
        assertNull(EsriAliases.crs("No_Such_Projection"));
        assertNull(EsriAliases.datum(null));
        assertNull(EsriAliases.ellipsoid(""));
    }
}
