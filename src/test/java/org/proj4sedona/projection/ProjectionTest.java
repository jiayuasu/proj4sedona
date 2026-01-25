package org.proj4sedona.projection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.proj4sedona.constants.Values;
import org.proj4sedona.core.DeriveConstants;
import org.proj4sedona.core.Point;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 4: Derive Constants and Projection infrastructure.
 */
class ProjectionTest {

    private static final double DELTA = 1e-10;

    @BeforeEach
    void setUp() {
        ProjectionRegistry.reset();
    }

    // ========== DeriveConstants.sphere() Tests ==========

    @Test
    void testSphereWithExplicitParams() {
        DeriveConstants.SphereResult result = DeriveConstants.sphere(
            6378137.0, 6356752.314, null, null, null
        );

        assertEquals(6378137.0, result.a, DELTA);
        assertEquals(6356752.314, result.b, DELTA);
        assertFalse(result.sphere);
    }

    @Test
    void testSphereWithRfOnly() {
        // Only a and rf provided, b should be calculated
        DeriveConstants.SphereResult result = DeriveConstants.sphere(
            6378137.0, null, 298.257223563, null, null
        );

        assertEquals(6378137.0, result.a, DELTA);
        assertEquals(298.257223563, result.rf, DELTA);
        // b = (1 - 1/rf) * a
        double expectedB = (1.0 - 1.0 / 298.257223563) * 6378137.0;
        assertEquals(expectedB, result.b, 0.001);
        assertFalse(result.sphere);
    }

    @Test
    void testSphereWithEllipsoidName() {
        // No explicit params, lookup by name
        DeriveConstants.SphereResult result = DeriveConstants.sphere(
            null, null, null, "wgs84", null
        );

        assertEquals(6378137.0, result.a, DELTA);
        assertFalse(result.sphere);
    }

    @Test
    void testSphereDefaultsToWgs84() {
        // No params at all, should default to WGS84
        DeriveConstants.SphereResult result = DeriveConstants.sphere(
            null, null, null, null, null
        );

        assertEquals(6378137.0, result.a, DELTA);
    }

    @Test
    void testSphereTrueSpherical() {
        // Sphere with a = b
        DeriveConstants.SphereResult result = DeriveConstants.sphere(
            6370997.0, 6370997.0, null, null, null
        );

        assertEquals(6370997.0, result.a, DELTA);
        assertEquals(6370997.0, result.b, DELTA);
        assertTrue(result.sphere);
    }

    @Test
    void testSphereLookupSphere() {
        // Look up the "sphere" ellipsoid (no explicit a)
        DeriveConstants.SphereResult result = DeriveConstants.sphere(
            null, null, null, "sphere", null
        );

        assertEquals(6370997.0, result.a, DELTA);
        assertEquals(6370997.0, result.b, DELTA);
        assertTrue(result.sphere);
    }

    @Test
    void testSphereForcedByFlag() {
        // Force sphere via the sphere flag
        DeriveConstants.SphereResult result = DeriveConstants.sphere(
            6378137.0, 6356752.314, null, null, true
        );

        // When sphere flag is true, a and b should be equal (a = b)
        // But in proj4js, the sphere flag is only checked when a == b is detected
        // So this test verifies flag behavior
        assertTrue(result.sphere);
    }

    // ========== DeriveConstants.eccentricity() Tests ==========

    @Test
    void testEccentricityWgs84() {
        // WGS84 values
        double a = 6378137.0;
        double b = 6356752.314245179;

        DeriveConstants.EccentricityResult result = DeriveConstants.eccentricity(
            a, b, 298.257223563, null
        );

        // es = (a^2 - b^2) / a^2
        double a2 = a * a;
        double b2 = b * b;
        double expectedEs = (a2 - b2) / a2;
        assertEquals(expectedEs, result.es, DELTA);

        // e = sqrt(es)
        assertEquals(Math.sqrt(expectedEs), result.e, DELTA);

        // ep2 = (a^2 - b^2) / b^2
        double expectedEp2 = (a2 - b2) / b2;
        assertEquals(expectedEp2, result.ep2, DELTA);
    }

    @Test
    void testEccentricitySphere() {
        // For a sphere, a = b, so es = 0
        double a = 6370997.0;
        double b = 6370997.0;

        DeriveConstants.EccentricityResult result = DeriveConstants.eccentricity(
            a, b, 0, null
        );

        assertEquals(0.0, result.es, DELTA);
        assertEquals(0.0, result.e, DELTA);
    }

    @Test
    void testEccentricityWithAuthalicRadius() {
        // R_A flag adjusts the semi-major axis
        double a = 6378137.0;
        double b = 6356752.314245179;

        DeriveConstants.EccentricityResult result = DeriveConstants.eccentricity(
            a, b, 298.257223563, true
        );

        // With R_A, es should be 0
        assertEquals(0.0, result.es, DELTA);
        assertEquals(0.0, result.e, DELTA);
    }

    // ========== ProjectionRegistry Tests ==========

    @Test
    void testRegistryAddAndGet() {
        // Create a mock projection
        ProjectionRegistry.add(() -> new MockProjection("test", "Test Projection"));

        Projection proj = ProjectionRegistry.get("test");
        assertNotNull(proj);
        assertEquals("test", proj.getNames()[0]);
    }

    @Test
    void testRegistryGetCaseInsensitive() {
        ProjectionRegistry.add(() -> new MockProjection("merc", "Mercator"));

        assertNotNull(ProjectionRegistry.get("merc"));
        assertNotNull(ProjectionRegistry.get("MERC"));
        assertNotNull(ProjectionRegistry.get("Merc"));
    }

    @Test
    void testRegistryGetUnknown() {
        assertNull(ProjectionRegistry.get("nonexistent"));
        assertNull(ProjectionRegistry.get(null));
        assertNull(ProjectionRegistry.get(""));
    }

    @Test
    void testRegistryMultipleNames() {
        ProjectionRegistry.add(() -> new MockProjection(
            new String[]{"longlat", "lonlat", "latlon", "latlong"},
            "Long/Lat"
        ));

        assertNotNull(ProjectionRegistry.get("longlat"));
        assertNotNull(ProjectionRegistry.get("lonlat"));
        assertNotNull(ProjectionRegistry.get("latlon"));
        assertNotNull(ProjectionRegistry.get("latlong"));
    }

    @Test
    void testRegistryNormalizedName() {
        ProjectionRegistry.add(() -> new MockProjection("lambert_conformal_conic", "LCC"));

        // Should match with different separators
        assertNotNull(ProjectionRegistry.get("lambert_conformal_conic"));
        assertNotNull(ProjectionRegistry.get("lambert conformal conic"));
    }

    // ========== ProjectionParams Tests ==========

    @Test
    void testProjectionParamsDefaults() {
        ProjectionParams params = new ProjectionParams();

        assertEquals(1.0, params.k0, DELTA);
        assertEquals(0.0, params.x0, DELTA);
        assertEquals(0.0, params.y0, DELTA);
        assertEquals("enu", params.axis);
    }

    @Test
    void testProjectionParamsGetLat1Default() {
        ProjectionParams params = new ProjectionParams();
        params.lat0 = 0.5;

        // lat1 defaults to lat0 if not set
        assertEquals(0.5, params.getLat1(), DELTA);
    }

    @Test
    void testProjectionParamsGetLat2Default() {
        ProjectionParams params = new ProjectionParams();
        params.lat1 = 0.6;

        // lat2 defaults to lat1 if not set
        assertEquals(0.6, params.getLat2(), DELTA);
    }

    // ========== Mock Projection for Testing ==========

    static class MockProjection implements Projection {
        private final String[] names;
        private final String title;

        MockProjection(String name, String title) {
            this.names = new String[]{name};
            this.title = title;
        }

        MockProjection(String[] names, String title) {
            this.names = names;
            this.title = title;
        }

        @Override
        public String[] getNames() {
            return names;
        }

        @Override
        public void init(ProjectionParams params) {
            // No-op for mock
        }

        @Override
        public Point forward(Point p) {
            return p; // Identity for mock
        }

        @Override
        public Point inverse(Point p) {
            return p; // Identity for mock
        }
    }
}
