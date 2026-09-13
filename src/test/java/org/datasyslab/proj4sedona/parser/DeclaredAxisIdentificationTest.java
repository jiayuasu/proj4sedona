package org.datasyslab.proj4sedona.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.projection.ProjectionParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An axis order set through the public API is as much a declaration as one parsed from a
 * source: identification must hold it against the registry's definition. Only an order the
 * library filled in by default may be ignored. EPSG:26919 is bundled, so no provider is
 * consulted here.
 */
class DeclaredAxisIdentificationTest {

    private static final String NAD83_UTM_19N = "+proj=utm +zone=19 +datum=NAD83 +units=m +no_defs";

    @Test
    @DisplayName("An axis order set on the definition is validated like a parsed one")
    void axisSetOnTheDefinitionIsValidated() {
        ProjectionDef def = ProjString.parse(NAD83_UTM_19N);
        def.setAxis("wnu");
        assertNull(CRSSerializer.toEpsgCode(new Proj(def)), "a west-first order is not EPSG:26919");
    }

    @Test
    @DisplayName("An axis order assigned to the parameters is validated like a parsed one")
    void axisAssignedToTheParametersIsValidated() {
        Proj proj = new Proj(NAD83_UTM_19N);
        ProjectionParams params = proj.getParams();
        params.axis = "wnu";
        assertNull(CRSSerializer.toEpsgCode(params), "a west-first order is not EPSG:26919");
    }

    @Test
    @DisplayName("A declared order that agrees with the registry still identifies")
    void agreeingDeclaredOrderIdentifies() {
        ProjectionDef def = ProjString.parse(NAD83_UTM_19N);
        def.setAxis("enu");
        assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(def)));
    }

    @Test
    @DisplayName("An order the library filled in by default is not held against the registry")
    void defaultOrderIsNotADeclaration() {
        assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(NAD83_UTM_19N)));
        assertEquals("EPSG:26919", CRSSerializer.toEpsgCode(new Proj(ProjString.parse(NAD83_UTM_19N))));
    }
}
