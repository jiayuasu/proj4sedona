package org.datasyslab.proj4sedona.defs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.transform.Converter;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pinned spatialreference.org snapshot provider stack and Defs remote lookup.
 *
 * <p>The spatialreference.org provider is now a pre-configured
 * {@link UrlCRSProvider} (created via {@link UrlCRSProvider#spatialReference()}),
 * so these tests exercise both the provider and the underlying
 * {@link UrlCRSFetcher} directly for retry / negative-cache behaviour.</p>
 *
 * <p>Some tests require network access to the commit-addressed OSGeo CDN
 * snapshot and may be slow due to network latency.</p>
 */
class SpatialReferenceFetcherTest {

    /** A UrlCRSFetcher configured exactly like the default spatialReference() provider. */
    private UrlCRSFetcher defaultFetcher;

    @BeforeEach
    void setUp() {
        Defs.reset();
        defaultFetcher = UrlCRSProvider.spatialReference().getFetcher();
    }

    @AfterEach
    void tearDown() {
        Defs.reset();
    }

    // ==================== Direct UrlCRSFetcher Tests (pinned OSGeo snapshot) ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testFetchProjJson_EPSG2154() {
        // EPSG:2154 - RGF93 / Lambert-93 (French national projection)
        UrlCRSFetcher.FetchResult result = defaultFetcher.fetch("epsg", "2154");

        assertTrue(result.isSuccess(), "Should fetch PROJJSON for EPSG:2154");
        assertNotNull(result.getBody());
        assertTrue(result.getBody().contains("RGF93") || result.getBody().contains("Lambert"),
                "PROJJSON should contain projection name");
        assertTrue(result.getBody().startsWith("{"), "Should be valid JSON");
        assertEquals(1, result.getAttemptCount(), "Should succeed on first attempt");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testFetchProjJson_NonExistent() {
        // EPSG:999999 should not exist
        UrlCRSFetcher.FetchResult result = defaultFetcher.fetch("epsg", "999999");

        assertTrue(result.isNotFound(), "Should return NOT_FOUND for non-existent EPSG code");
        assertNull(result.getBody());
    }

    @Test
    void testNegativeCache() {
        // First call should add to negative cache
        UrlCRSFetcher.FetchResult result1 = defaultFetcher.fetch("epsg", "999999");
        assertTrue(result1.isNotFound());

        // Verify it's in the negative cache
        assertTrue(defaultFetcher.isInNotFoundCache("epsg", "999999"),
                "Non-existent code should be in negative cache");

        // Second call should return from cache immediately (0 attempts)
        UrlCRSFetcher.FetchResult result2 = defaultFetcher.fetch("epsg", "999999");
        assertTrue(result2.isNotFound());
        assertEquals(0, result2.getAttemptCount(), "Should return from cache with 0 attempts");
    }

    @Test
    void testClearNotFoundCache() {
        // Add to negative cache
        defaultFetcher.fetch("epsg", "999999");
        assertTrue(defaultFetcher.isInNotFoundCache("epsg", "999999"));

        // Clear cache
        defaultFetcher.clearNotFoundCache();

        // Should no longer be in cache
        assertFalse(defaultFetcher.isInNotFoundCache("epsg", "999999"));
        assertEquals(0, defaultFetcher.getNotFoundCacheSize());
    }

    @Test
    void testFetcherDefaults() {
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_CDN_BASE_URL, defaultFetcher.getBaseUrl());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_PATH, defaultFetcher.getPathTemplate());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_CONNECT_TIMEOUT_SECONDS,
                defaultFetcher.getConnectTimeoutSeconds());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_READ_TIMEOUT_SECONDS,
                defaultFetcher.getReadTimeoutSeconds());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_MAX_ATTEMPTS,
                defaultFetcher.getMaxRetries());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_INITIAL_BACKOFF_MS,
                defaultFetcher.getInitialBackoffMs());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_TOTAL_TIMEOUT_SECONDS,
                defaultFetcher.getTotalTimeoutSeconds());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
                defaultFetcher.getCircuitBreakerFailureThreshold());
        assertEquals(UrlCRSProvider.SPATIAL_REFERENCE_CIRCUIT_BREAKER_RESET_TIMEOUT,
                defaultFetcher.getCircuitBreakerResetTimeout());
    }

    // ==================== Network Failure Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFetchWithUnreachableServer_RetriesAndFails() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("http://localhost:59999")
                .pathTemplate(UrlCRSProvider.SPATIAL_REFERENCE_PATH)
                .connectTimeout(1)
                .readTimeout(1)
                .maxRetries(3)
                .initialBackoffMs(100)
                .build();

        long startTime = System.currentTimeMillis();
        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "4326");
        long elapsed = System.currentTimeMillis() - startTime;

        assertTrue(result.isNetworkError(), "Should return NETWORK_ERROR for unreachable server");
        assertNotNull(result.getLastException(), "Should have a last exception");
        assertEquals(3, result.getAttemptCount(), "Should have attempted 3 times");

        // Verify it took some time (due to retries and backoff)
        assertTrue(elapsed >= 200, "Should have taken time for retries, took " + elapsed + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFetchRetryCount_VerifyExactlyThreeAttempts() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("http://localhost:59998")
                .pathTemplate(UrlCRSProvider.SPATIAL_REFERENCE_PATH)
                .connectTimeout(1)
                .readTimeout(1)
                .maxRetries(3)
                .initialBackoffMs(50)
                .build();

        UrlCRSFetcher.FetchResult result1 = fetcher.fetch("epsg", "1234");
        assertEquals(3, result1.getAttemptCount(), "First call should make 3 attempts");

        UrlCRSFetcher.FetchResult result2 = fetcher.fetch("epsg", "5678");
        assertEquals(3, result2.getAttemptCount(), "Second call should also make 3 attempts");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFetchWithCustomRetryCount() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("http://localhost:59997")
                .pathTemplate(UrlCRSProvider.SPATIAL_REFERENCE_PATH)
                .connectTimeout(1)
                .readTimeout(1)
                .maxRetries(5)
                .initialBackoffMs(10)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "4326");

        assertTrue(result.isNetworkError());
        assertEquals(5, result.getAttemptCount(), "Should have attempted 5 times");
    }

    // ==================== Defs Remote Lookup Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDefsGet_RemoteFetch_EPSG2154() {
        // EPSG:2154 is not in the default globals, should be fetched remotely
        ProjectionDef def = Defs.get("EPSG:2154");

        assertNotNull(def, "Should fetch EPSG:2154 from the pinned OSGeo snapshot");
        assertEquals("EPSG:2154", def.getSrsCode());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testDefsGet_CachesRemoteFetch() {
        // First fetch
        ProjectionDef def1 = Defs.get("EPSG:2154");
        assertNotNull(def1);

        // Second fetch should return cached value (same object)
        ProjectionDef def2 = Defs.get("EPSG:2154");
        assertSame(def1, def2, "Second call should return cached ProjectionDef");
    }

    @Test
    void testDefsGet_LocalRegistryFirst() {
        // Add a custom definition for an EPSG code
        Defs.set("EPSG:99999", "+proj=longlat +datum=WGS84");

        // Should return local definition, not try remote
        ProjectionDef def = Defs.get("EPSG:99999");
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
    }

    @Test
    void testDefsGet_NoProviders_ReturnsNull() {
        Defs.globals();
        Defs.clearProviders();

        ProjectionDef def = Defs.get("EPSG:2154");
        assertNull(def, "Should return null when no providers are registered");
    }

    @Test
    void testDefsGet_NonAuthorityPattern_ReturnsNull() {
        ProjectionDef def = Defs.get("CUSTOMNAME");
        assertNull(def, "Non-authority pattern should return null");
    }

    @Test
    void testDefsGet_ReturnsNullOnNotFound() {
        ProjectionDef def = Defs.get("EPSG:999999999");
        assertNull(def, "Should return null for non-existent EPSG code");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testDefsGet_ThrowsOnNetworkError() {
        // Replace the default spatialReference() provider with one pointing to unreachable server
        Defs.globals();
        Defs.removeProvider("spatialreference.org");
        Defs.registerProvider(
                UrlCRSProvider.builder(UrlCRSProvider.SPATIAL_REFERENCE_NAME)
                        .baseUrl("http://localhost:59995")
                        .pathTemplate(UrlCRSProvider.SPATIAL_REFERENCE_PATH)
                        .connectTimeout(1).readTimeout(1).maxRetries(2).initialBackoffMs(50)
                        .build(),
                101);

        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Defs.get("ESRI:102001");  // Use ESRI to avoid hitting built-in cache
        });

        assertEquals("ESRI:102001", ex.getCrsCode());
        assertEquals(CRSFetchException.Reason.NETWORK_ERROR, ex.getReason());
        assertTrue(ex.getMessage().contains("Failed to fetch"));
        assertNotNull(ex.getCause(), "Should have underlying cause");
    }

    @Test
    void testCRSFetchException_ContainsAllDetails() {
        Defs.globals();
        Defs.removeProvider("spatialreference.org");
        Defs.registerProvider(
                UrlCRSProvider.builder(UrlCRSProvider.SPATIAL_REFERENCE_NAME)
                        .baseUrl("http://localhost:59993")
                        .pathTemplate(UrlCRSProvider.SPATIAL_REFERENCE_PATH)
                        .connectTimeout(1).readTimeout(1).maxRetries(1).initialBackoffMs(10)
                        .build(),
                101);

        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Defs.get("ESRI:102001");
        });

        assertNotNull(ex.getCrsCode());
        assertNotNull(ex.getReason());
        assertNotNull(ex.getMessage());

        String str = ex.toString();
        assertTrue(str.contains("ESRI:102001"));
        assertTrue(str.contains("NETWORK_ERROR"));
    }

    // ==================== Integration Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testProj4WithRemoteEPSG() {
        Converter conv = Proj4.proj4("EPSG:4326", "EPSG:2154");

        Point paris = new Point(2.35, 48.85);
        Point projected = conv.forward(paris);

        assertTrue(Double.isFinite(projected.x), "Easting should be finite");
        assertTrue(Double.isFinite(projected.y), "Northing should be finite");

        assertTrue(projected.x > 600000 && projected.x < 700000,
                "Easting should be around 650000");
        assertTrue(projected.y > 6800000 && projected.y < 6900000,
                "Northing should be around 6860000");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testRoundTripWithRemoteEPSG() {
        Converter conv = Proj4.proj4("EPSG:4326", "EPSG:2154");

        Point original = new Point(2.35, 48.85);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);

        assertEquals(original.x, restored.x, 1e-6, "Longitude should round-trip");
        assertEquals(original.y, restored.y, 1e-6, "Latitude should round-trip");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testMultipleRemoteEPSGCodes() {
        ProjectionDef def1 = Defs.get("EPSG:32188");
        ProjectionDef def2 = Defs.get("EPSG:28992");

        assertNotNull(def1, "EPSG:32188 should be fetchable");
        assertNotNull(def2, "EPSG:28992 should be fetchable");
    }

    @Test
    void testTransformationThrowsWhenCRSNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            Proj4.proj4("EPSG:4326", "ESRI:999999999");
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testTransformationThrowsOnNetworkError() {
        Defs.globals();
        Defs.removeProvider("spatialreference.org");
        Defs.registerProvider(
                UrlCRSProvider.builder(UrlCRSProvider.SPATIAL_REFERENCE_NAME)
                        .baseUrl("http://localhost:59994")
                        .pathTemplate(UrlCRSProvider.SPATIAL_REFERENCE_PATH)
                        .connectTimeout(1).readTimeout(1).maxRetries(2).initialBackoffMs(50)
                        .build(),
                101);

        CRSFetchException ex = assertThrows(CRSFetchException.class, () -> {
            Proj4.proj4("EPSG:4326", "ESRI:102001");
        });

        assertEquals(CRSFetchException.Reason.NETWORK_ERROR, ex.getReason());
    }

    // ==================== Case Sensitivity Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEpsgPatternCaseInsensitive() {
        ProjectionDef def1 = Defs.get("epsg:2154");
        assertNotNull(def1, "Should handle lowercase 'epsg:'");

        ProjectionDef def2 = Defs.get("EPSG:2154");
        assertSame(def1, def2, "Should return same cached definition regardless of case");
    }
}
