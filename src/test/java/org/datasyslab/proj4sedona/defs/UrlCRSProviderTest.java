package org.datasyslab.proj4sedona.defs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.datasyslab.proj4sedona.core.ProjectionDef;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UrlCRSProvider} and {@link UrlCRSFetcher}.
 *
 * <p>Uses an embedded HTTP server to avoid real network calls.</p>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class UrlCRSProviderTest {

    /** Embedded HTTP server serving fake CRS files. */
    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static int port;
    private static String baseUrl;
    private static final ConcurrentMap<String, AtomicInteger> requestCounts =
            new ConcurrentHashMap<>();
    private static volatile CountDownLatch oldRequestStarted;
    private static volatile CountDownLatch releaseOldRequest;
    private static volatile CountDownLatch probeRequestStarted;
    private static volatile CountDownLatch releaseProbeRequest;
    private static volatile CountDownLatch slowBodyStopped;
    private static volatile CountDownLatch slowRedirectStopped;

    /** A valid PROJJSON for WGS 84 (EPSG:4326). */
    private static final String WGS84_PROJJSON = "{\n" +
            "  \"$schema\": \"https://proj.org/schemas/v0.7/projjson.schema.json\",\n" +
            "  \"type\": \"GeographicCRS\",\n" +
            "  \"name\": \"WGS 84\",\n" +
            "  \"datum\": {\n" +
            "    \"type\": \"GeodeticReferenceFrame\",\n" +
            "    \"name\": \"World Geodetic System 1984\",\n" +
            "    \"ellipsoid\": {\n" +
            "      \"name\": \"WGS 84\",\n" +
            "      \"semi_major_axis\": 6378137,\n" +
            "      \"inverse_flattening\": 298.257223563\n" +
            "    }\n" +
            "  },\n" +
            "  \"coordinate_system\": {\n" +
            "    \"subtype\": \"ellipsoidal\",\n" +
            "    \"axis\": [\n" +
            "      { \"name\": \"Geodetic latitude\", \"abbreviation\": \"Lat\",\n" +
            "        \"direction\": \"north\", \"unit\": \"degree\" },\n" +
            "      { \"name\": \"Geodetic longitude\", \"abbreviation\": \"Lon\",\n" +
            "        \"direction\": \"east\", \"unit\": \"degree\" }\n" +
            "    ]\n" +
            "  }\n" +
            "}";

    /** A valid PROJ.4 string for a Lambert Conformal Conic projection. */
    private static final String LCC_PROJ4 =
            "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 +x_0=0 +y_0=0 +datum=WGS84 +units=m";

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        // Serve PROJJSON files at /{authority}/{code}.json
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int currentRequestCount =
                    requestCounts.computeIfAbsent(path, ignored -> new AtomicInteger())
                            .incrementAndGet();

            if ("/epsg/4326.json".equals(path)) {
                respond(exchange, 200, WGS84_PROJJSON);
            } else if ("/EPSG/4326.json".equals(path)) {
                // Uppercase authority path
                respond(exchange, 200, WGS84_PROJJSON);
            } else if ("/epsg/9999.json".equals(path)) {
                respond(exchange, 404, "Not Found");
            } else if ("/custom/lcc.json".equals(path)) {
                respond(exchange, 200, LCC_PROJ4);
            } else if ("/error/500.json".equals(path)) {
                respond(exchange, 500, "Internal Server Error");
            } else if ("/error/503.json".equals(path)) {
                respond(exchange, 503, "Service Unavailable");
            } else if ("/error/429.json".equals(path)) {
                respond(exchange, 429, "Too Many Requests");
            } else if ("/error/403.json".equals(path)) {
                respond(exchange, 403, "Forbidden");
            } else if ("/slow/timeout.json".equals(path)) {
                // Simulate a slow response
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                respond(exchange, 200, WGS84_PROJJSON);
            } else if ("/singleflight/4326.json".equals(path)) {
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                respond(exchange, 200, WGS84_PROJJSON);
            } else if ("/slowbody/timeout.json".equals(path)) {
                streamResponse(exchange, 200, 5000, slowBodyStopped);
            } else if ("/redirect/normal.json".equals(path)) {
                exchange.getResponseHeaders().set("Location", "/epsg/4326.json");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else if ("/redirect/slow.json".equals(path)) {
                exchange.getResponseHeaders().set("Location", "/epsg/4326.json");
                streamResponse(exchange, 302, 5000, slowRedirectStopped);
            } else if ("/mixed/failure.json".equals(path)) {
                if (currentRequestCount == 1) {
                    respond(exchange, 503, "Service Unavailable");
                } else {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    respond(exchange, 200, WGS84_PROJJSON);
                }
            } else if ("/race/old.json".equals(path)) {
                oldRequestStarted.countDown();
                awaitLatch(releaseOldRequest);
                respond(exchange, 200, WGS84_PROJJSON);
            } else if ("/race/probe.json".equals(path)) {
                probeRequestStarted.countDown();
                awaitLatch(releaseProbeRequest);
                respond(exchange, 200, WGS84_PROJJSON);
            } else if ("/epsg/2154.json".equals(path)) {
                respond(exchange, 200, WGS84_PROJJSON); // Reuse WGS84 for simplicity
            } else if ("/esri/102001.json".equals(path)) {
                respond(exchange, 200, WGS84_PROJJSON);
            } else if ("/headers-echo/test.json".equals(path)) {
                // Echo back authorization header in body for verification
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                String custom = exchange.getRequestHeaders().getFirst("X-Custom");
                String body = "{\"auth\":\"" + (auth != null ? auth : "") +
                        "\",\"custom\":\"" + (custom != null ? custom : "") + "\"}";
                respond(exchange, 200, body);
            } else {
                respond(exchange, 404, "Not Found");
            }
        });

        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @BeforeEach
    void setUp() {
        Defs.reset();
        requestCounts.clear();
        oldRequestStarted = new CountDownLatch(1);
        releaseOldRequest = new CountDownLatch(1);
        probeRequestStarted = new CountDownLatch(1);
        releaseProbeRequest = new CountDownLatch(1);
        slowBodyStopped = new CountDownLatch(1);
        slowRedirectStopped = new CountDownLatch(1);
    }

    @AfterEach
    void tearDown() {
        Defs.reset();
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static int requestCount(String path) {
        AtomicInteger count = requestCounts.get(path);
        return count == null ? 0 : count.get();
    }

    private static void streamResponse(
            HttpExchange exchange,
            int statusCode,
            long durationMs,
            CountDownLatch stopped)
            throws IOException {
        byte[] chunk = new byte[64 * 1024];
        chunk[0] = '{';
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, 0);
        long deadlineNanos =
                System.nanoTime() + Duration.ofMillis(durationMs).toNanos();
        try (OutputStream os = exchange.getResponseBody()) {
            while (System.nanoTime() < deadlineNanos) {
                os.write(chunk);
                os.flush();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } catch (IOException ignored) {
            // Expected when the client cancels after its overall deadline.
        } finally {
            stopped.countDown();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Builder Validation ====================

    @Test
    void testBuilder_NullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> UrlCRSProvider.builder(null));
    }

    @Test
    void testBuilder_BlankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> UrlCRSProvider.builder("   "));
    }

    @Test
    void testBuilder_MissingBaseUrlThrows() {
        assertThrows(IllegalStateException.class, () ->
                UrlCRSProvider.builder("test").build());
    }

    @Test
    void testBuilder_NullBaseUrlThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                UrlCRSProvider.builder("test").baseUrl(null));
    }

    @Test
    void testBuilder_BlankBaseUrlThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                UrlCRSProvider.builder("test").baseUrl("  "));
    }

    @Test
    void testBuilder_NullFormatThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                UrlCRSProvider.builder("test").baseUrl(baseUrl).format(null));
    }

    @Test
    void testBuilder_InvalidPathTemplateThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                UrlCRSProvider.builder("test").baseUrl(baseUrl).pathTemplate("no-leading-slash"));
    }

    @Test
    void testBuilder_InvalidCircuitBreakerResetTimeoutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                UrlCRSProvider.builder("test")
                        .baseUrl(baseUrl)
                        .circuitBreakerResetTimeout(Duration.ZERO));
    }

    @Test
    void testExistingFailureEnumOrdinalsRemainStable() {
        assertEquals(2, UrlCRSFetcher.FetchResult.Status.NETWORK_ERROR.ordinal());
        assertEquals(1, CRSFetchException.Reason.NETWORK_ERROR.ordinal());
        assertEquals(2, CRSFetchException.Reason.INVALID_RESPONSE.ordinal());
    }

    // ==================== UrlCRSFetcher ====================

    @Test
    void testFetcher_SuccessfulFetch() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "4326");

        assertTrue(result.isSuccess());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().contains("WGS 84"));
        assertEquals(1, result.getAttemptCount());
    }

    @Test
    void testFetcher_NotFound() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "9999");

        assertTrue(result.isNotFound());
        assertNull(result.getBody());
    }

    @Test
    void testFetcher_NegativeCache() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .build();

        // First call → 404 → cached
        UrlCRSFetcher.FetchResult r1 = fetcher.fetch("epsg", "9999");
        assertTrue(r1.isNotFound());
        assertTrue(fetcher.isInNotFoundCache("epsg", "9999"));

        // Second call → 0 attempts (served from cache)
        UrlCRSFetcher.FetchResult r2 = fetcher.fetch("epsg", "9999");
        assertTrue(r2.isNotFound());
        assertEquals(0, r2.getAttemptCount());
    }

    @Test
    void testFetcher_ClearNegativeCache() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .build();

        fetcher.fetch("epsg", "9999");
        assertEquals(1, fetcher.getNotFoundCacheSize());

        fetcher.clearNotFoundCache();
        assertEquals(0, fetcher.getNotFoundCacheSize());
        assertFalse(fetcher.isInNotFoundCache("epsg", "9999"));
    }

    @Test
    void testFetcher_ForbiddenIsHttpErrorAndIsNotNegativeCached() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/error/{code}.json")
                .circuitBreakerFailureThreshold(100)
                .build();

        UrlCRSFetcher.FetchResult first = fetcher.fetch("epsg", "403");
        assertTrue(first.isHttpError());
        assertEquals(403, first.getHttpStatusCode());
        assertEquals(1, first.getAttemptCount());
        assertFalse(fetcher.isInNotFoundCache("epsg", "403"));

        UrlCRSFetcher.FetchResult second = fetcher.fetch("epsg", "403");
        assertTrue(second.isHttpError());
        assertEquals(2, requestCount("/error/403.json"),
                "403 must not be cached as a missing CRS");
    }

    @Test
    void testFetcher_RetryableHttpErrorPreservesStatus() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/error/{code}.json")
                .maxRetries(2)
                .initialBackoffMs(0)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "503");

        assertTrue(result.isHttpError());
        assertEquals(503, result.getHttpStatusCode());
        assertEquals(2, result.getAttemptCount());
        assertEquals(2, requestCount("/error/503.json"));
    }

    @Test
    void testFetcher_TooManyRequestsIsRetriedAndPreservesStatus() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/error/{code}.json")
                .maxRetries(2)
                .initialBackoffMs(0)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "429");

        assertTrue(result.isHttpError());
        assertEquals(429, result.getHttpStatusCode());
        assertEquals(2, result.getAttemptCount());
        assertEquals(2, requestCount("/error/429.json"));
    }

    @Test
    void testFetcher_SingleFlightCoalescesConcurrentLookups() throws Exception {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/singleflight/{code}.json")
                .build();
        int callers = 12;
        ExecutorService callersPool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UrlCRSFetcher.FetchResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < callers; i++) {
                futures.add(callersPool.submit(() -> {
                    start.await();
                    return fetcher.fetch("epsg", "4326");
                }));
            }
            start.countDown();

            for (Future<UrlCRSFetcher.FetchResult> future : futures) {
                assertTrue(future.get(5, TimeUnit.SECONDS).isSuccess());
            }
        } finally {
            callersPool.shutdownNow();
        }

        assertEquals(1, requestCount("/singleflight/4326.json"));
        assertEquals(0, fetcher.getInFlightCount());
    }

    @Test
    void testFetcher_CircuitBreakerRejectsAndRecovers() throws Exception {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .maxRetries(1)
                .circuitBreakerFailureThreshold(2)
                .circuitBreakerResetTimeout(Duration.ofMillis(100))
                .build();

        assertTrue(fetcher.fetch("error", "503").isHttpError());
        assertTrue(fetcher.fetch("error", "503").isHttpError());
        assertTrue(fetcher.isCircuitOpen());

        UrlCRSFetcher.FetchResult rejected = fetcher.fetch("epsg", "4326");
        assertTrue(rejected.isCircuitOpen());
        assertEquals(0, rejected.getAttemptCount());
        assertEquals(0, requestCount("/epsg/4326.json"));

        Thread.sleep(150);
        UrlCRSFetcher.FetchResult recovered = fetcher.fetch("epsg", "4326");
        assertTrue(recovered.isSuccess());
        assertFalse(fetcher.isCircuitOpen());
        assertEquals(0, fetcher.getConsecutiveFailureCount());
    }

    @Test
    void testFetcher_LateRequestCannotReleaseHalfOpenProbe() throws Exception {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .maxRetries(1)
                .circuitBreakerFailureThreshold(1)
                .circuitBreakerResetTimeout(Duration.ofMillis(100))
                .build();
        ExecutorService callersPool = Executors.newFixedThreadPool(2);

        try {
            Future<UrlCRSFetcher.FetchResult> oldRequest =
                    callersPool.submit(() -> fetcher.fetch("race", "old"));
            assertTrue(oldRequestStarted.await(2, TimeUnit.SECONDS));

            assertTrue(fetcher.fetch("error", "503").isHttpError());
            assertTrue(fetcher.isCircuitOpen());

            Thread.sleep(150);
            Future<UrlCRSFetcher.FetchResult> probeRequest =
                    callersPool.submit(() -> fetcher.fetch("race", "probe"));
            assertTrue(probeRequestStarted.await(2, TimeUnit.SECONDS));

            releaseOldRequest.countDown();
            assertTrue(oldRequest.get(2, TimeUnit.SECONDS).isSuccess());

            UrlCRSFetcher.FetchResult rejected = fetcher.fetch("race", "extra");
            assertTrue(rejected.isCircuitOpen());
            assertEquals(0, requestCount("/race/extra.json"));

            releaseProbeRequest.countDown();
            assertTrue(probeRequest.get(2, TimeUnit.SECONDS).isSuccess());
            assertFalse(fetcher.isCircuitOpen());
        } finally {
            releaseOldRequest.countDown();
            releaseProbeRequest.countDown();
            callersPool.shutdownNow();
        }
    }

    @Test
    void testFetcher_OverallTimeoutBoundsRetries() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/slow/{code}.json")
                .readTimeout(5)
                .totalTimeout(1)
                .maxRetries(2)
                .initialBackoffMs(0)
                .build();

        long started = System.nanoTime();
        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "timeout");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertTrue(result.isNetworkError());
        assertTrue(elapsedMs < 2500,
                "overall timeout should bound the lookup, took " + elapsedMs + "ms");
    }

    @Test
    void testFetcher_OverallTimeoutIncludesResponseBody() throws Exception {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/slowbody/{code}.json")
                .readTimeout(5)
                .totalTimeout(1)
                .maxRetries(1)
                .build();

        long started = System.nanoTime();
        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "timeout");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertTrue(result.isNetworkError());
        assertEquals(-1, result.getHttpStatusCode());
        assertTrue(elapsedMs < 2500,
                "response body must be covered by the total timeout, took " + elapsedMs + "ms");
        assertTrue(slowBodyStopped.await(2, TimeUnit.SECONDS),
                "timing out must cancel the response body subscription");
    }

    @Test
    void testFetcher_FollowsRedirectWithinDeadline() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/redirect/{code}.json")
                .readTimeout(5)
                .totalTimeout(2)
                .maxRetries(1)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "normal");

        assertTrue(result.isSuccess());
        assertEquals(1, requestCount("/redirect/normal.json"));
        assertEquals(1, requestCount("/epsg/4326.json"));
    }

    @Test
    void testFetcher_OverallTimeoutCancelsRedirectBody() throws Exception {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/redirect/{code}.json")
                .readTimeout(5)
                .totalTimeout(1)
                .maxRetries(1)
                .build();

        long started = System.nanoTime();
        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "slow");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertTrue(result.isNetworkError());
        assertTrue(elapsedMs < 2500,
                "redirect body must be covered by the total timeout, took "
                        + elapsedMs + "ms");
        assertTrue(slowRedirectStopped.await(2, TimeUnit.SECONDS),
                "timing out must cancel the redirect body subscription");
        assertEquals(0, requestCount("/epsg/4326.json"),
                "the redirect target must not start before its body completes");
    }

    @Test
    void testFetcher_NetworkFailureSupersedesEarlierHttpStatus() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/mixed/{code}.json")
                .readTimeout(1)
                .totalTimeout(3)
                .maxRetries(2)
                .initialBackoffMs(0)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "failure");

        assertTrue(result.isNetworkError());
        assertEquals(-1, result.getHttpStatusCode());
        assertEquals(2, result.getAttemptCount());
        assertEquals(2, requestCount("/mixed/failure.json"));
    }

    @Test
    void testFetcher_InvalidUrlDoesNotOpenCircuit() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .circuitBreakerFailureThreshold(1)
                .build();

        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch("epsg", "%"));
        assertFalse(fetcher.isCircuitOpen());
        assertEquals(0, fetcher.getConsecutiveFailureCount());
        assertTrue(fetcher.fetch("epsg", "4326").isSuccess());
    }

    @Test
    void testFetcher_InvalidHalfOpenProbeReleasesPermit() throws Exception {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .maxRetries(1)
                .circuitBreakerFailureThreshold(1)
                .circuitBreakerResetTimeout(Duration.ofMillis(100))
                .build();

        assertTrue(fetcher.fetch("error", "503").isHttpError());
        Thread.sleep(150);

        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch("epsg", "%"));
        assertTrue(fetcher.isCircuitOpen());
        assertTrue(fetcher.fetch("epsg", "4326").isSuccess());
        assertFalse(fetcher.isCircuitOpen());
    }

    @Test
    void testFetcher_CustomPathTemplate() {
        // Use AUTHORITY (upper-case) placeholder
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/{AUTHORITY}/{code}.json")
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "4326");
        // Should hit /EPSG/4326.json
        assertTrue(result.isSuccess());
    }

    @Test
    void testFetcher_BuildUrl() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("https://example.com")
                .pathTemplate("/{authority}/{code}.json")
                .build();

        assertEquals("https://example.com/epsg/4326.json", fetcher.buildUrl("epsg", "4326"));
        assertEquals("https://example.com/epsg/4326.json", fetcher.buildUrl("EPSG", "4326"));
    }

    @Test
    void testFetcher_BuildUrl_UpperCasePlaceholder() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("https://example.com")
                .pathTemplate("/{AUTHORITY}_{code}.projjson")
                .build();

        assertEquals("https://example.com/EPSG_4326.projjson", fetcher.buildUrl("epsg", "4326"));
    }

    @Test
    void testFetcher_BuildUrl_TrailingSlashStripped() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("https://example.com/")
                .pathTemplate("/{authority}/{code}.json")
                .build();

        assertEquals("https://example.com/epsg/4326.json", fetcher.buildUrl("EPSG", "4326"));
    }

    @Test
    void testFetcher_NetworkError_UnreachableHost() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("http://127.0.0.1:59990")
                .connectTimeout(1)
                .readTimeout(1)
                .maxRetries(1)
                .initialBackoffMs(10)
                .build();

        UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "4326");
        assertTrue(result.isNetworkError());
        assertNotNull(result.getLastException());
    }

    @Test
    void testFetcher_Accessors() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("https://example.com")
                .pathTemplate("/{AUTHORITY}/{code}.json")
                .connectTimeout(5)
                .readTimeout(15)
                .maxRetries(2)
                .initialBackoffMs(100)
                .totalTimeout(20)
                .circuitBreakerFailureThreshold(4)
                .circuitBreakerResetTimeout(Duration.ofSeconds(45))
                .header("X-Key", "abc")
                .build();

        assertEquals("https://example.com", fetcher.getBaseUrl());
        assertEquals("/{AUTHORITY}/{code}.json", fetcher.getPathTemplate());
        assertEquals(5, fetcher.getConnectTimeoutSeconds());
        assertEquals(15, fetcher.getReadTimeoutSeconds());
        assertEquals(2, fetcher.getMaxRetries());
        assertEquals(100, fetcher.getInitialBackoffMs());
        assertEquals(20, fetcher.getTotalTimeoutSeconds());
        assertEquals(4, fetcher.getCircuitBreakerFailureThreshold());
        assertEquals(Duration.ofSeconds(45), fetcher.getCircuitBreakerResetTimeout());
        assertEquals("abc", fetcher.getHeaders().get("X-Key"));
    }

    @Test
    void testFetcher_GenericDefaultsRemainCompatible() {
        UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
                .baseUrl("https://example.com")
                .build();

        assertEquals(10, fetcher.getConnectTimeoutSeconds());
        assertEquals(30, fetcher.getReadTimeoutSeconds());
        assertEquals(3, fetcher.getMaxRetries());
        assertEquals(500, fetcher.getInitialBackoffMs());
        assertEquals(0, fetcher.getTotalTimeoutSeconds());
        assertEquals(0, fetcher.getCircuitBreakerFailureThreshold());
    }

    // ==================== UrlCRSProvider ====================

    @Test
    void testProvider_Name() {
        UrlCRSProvider provider = UrlCRSProvider.builder("my-server")
                .baseUrl(baseUrl)
                .build();
        assertEquals("my-server", provider.getName());
    }

    @Test
    void testProvider_DefaultFormat() {
        UrlCRSProvider provider = UrlCRSProvider.builder("test")
                .baseUrl(baseUrl)
                .build();
        assertEquals(CRSResult.Format.PROJJSON, provider.getFormat());
    }

    @Test
    void testProvider_ResolveProjJson() {
        UrlCRSProvider provider = UrlCRSProvider.builder("test-projjson")
                .baseUrl(baseUrl)
                .build();

        CRSResult result = provider.resolve("epsg", "4326");

        assertNotNull(result);
        assertEquals(CRSResult.Format.PROJJSON, result.getFormat());
        assertTrue(result.getDefinition().contains("WGS 84"));
    }

    @Test
    void testProvider_ResolveProj4Format() {
        UrlCRSProvider provider = UrlCRSProvider.builder("test-proj4")
                .baseUrl(baseUrl)
                .format(CRSResult.Format.PROJ4)
                .build();

        CRSResult result = provider.resolve("custom", "lcc");

        assertNotNull(result);
        assertEquals(CRSResult.Format.PROJ4, result.getFormat());
        assertTrue(result.getDefinition().contains("+proj=lcc"));
    }

    @Test
    void testProvider_ReturnsNullForNotFound() {
        UrlCRSProvider provider = UrlCRSProvider.builder("test-404")
                .baseUrl(baseUrl)
                .build();

        CRSResult result = provider.resolve("epsg", "9999");
        assertNull(result);
    }

    @Test
    void testProvider_ThrowsOnNetworkError() {
        UrlCRSProvider provider = UrlCRSProvider.builder("test-network-err")
                .baseUrl("http://127.0.0.1:59990")
                .connectTimeout(1)
                .readTimeout(1)
                .maxRetries(1)
                .initialBackoffMs(10)
                .build();

        CRSFetchException ex = assertThrows(CRSFetchException.class, () ->
                provider.resolve("epsg", "4326"));
        assertEquals(CRSFetchException.Reason.NETWORK_ERROR, ex.getReason());
        assertTrue(ex.getMessage().contains("test-network-err"));
        assertTrue(ex.getMessage().contains("http://127.0.0.1:59990"));
    }

    @Test
    void testProvider_ForbiddenIncludesHttpStatusAndCrs() {
        UrlCRSProvider provider = UrlCRSProvider.builder("test-http-err")
                .baseUrl(baseUrl)
                .pathTemplate("/error/{code}.json")
                .build();

        CRSFetchException ex = assertThrows(CRSFetchException.class, () ->
                provider.resolve("epsg", "403"));

        assertEquals("EPSG:403", ex.getCrsCode());
        assertEquals(CRSFetchException.Reason.HTTP_ERROR, ex.getReason());
        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertTrue(ex.getMessage().contains("EPSG:403"));
        assertTrue(ex.getMessage().contains(baseUrl));
    }

    @Test
    void testProvider_CircuitOpenHasDistinctReason() {
        UrlCRSProvider provider = UrlCRSProvider.builder("test-circuit")
                .baseUrl(baseUrl)
                .maxRetries(1)
                .circuitBreakerFailureThreshold(1)
                .build();

        assertThrows(CRSFetchException.class, () ->
                provider.resolve("error", "503"));
        CRSFetchException ex = assertThrows(CRSFetchException.class, () ->
                provider.resolve("epsg", "4326"));

        assertEquals(CRSFetchException.Reason.CIRCUIT_OPEN, ex.getReason());
        assertTrue(ex.getMessage().contains("circuit breaker is open"));
        assertTrue(ex.getMessage().contains(baseUrl));
    }

    @Test
    @SuppressWarnings("deprecation")
    void testSpatialReferenceProviderUsesPinnedOsGeoSnapshot() {
        UrlCRSProvider provider = UrlCRSProvider.spatialReference();
        String commit = "c43e4e72634af65fcf684def42ddc2dcfd834938";
        String base =
                "https://cdn.jsdelivr.net/gh/OSGeo/spatialreference.org@" + commit;

        assertEquals(commit, UrlCRSProvider.SPATIAL_REFERENCE_SNAPSHOT_COMMIT);
        assertEquals(base, UrlCRSProvider.SPATIAL_REFERENCE_CDN_BASE_URL);
        assertEquals(base, provider.getFetcher().getBaseUrl());
        assertEquals(
                base + "/ref/epsg/2154/projjson.json",
                provider.getFetcher().buildUrl("epsg", "2154"));
        assertEquals("https://spatialreference.org",
                UrlCRSProvider.SPATIAL_REFERENCE_BASE_URL);
    }

    // ==================== Authority Filtering ====================

    @Test
    void testProvider_AuthorityFilter_AcceptsMatching() {
        UrlCRSProvider provider = UrlCRSProvider.builder("filtered")
                .baseUrl(baseUrl)
                .authorities("epsg")
                .build();

        CRSResult result = provider.resolve("epsg", "4326");
        assertNotNull(result);
    }

    @Test
    void testProvider_AuthorityFilter_RejectsNonMatching() {
        UrlCRSProvider provider = UrlCRSProvider.builder("filtered")
                .baseUrl(baseUrl)
                .authorities("epsg")
                .build();

        // "esri" is not in the allowed set — should return null without making HTTP call
        CRSResult result = provider.resolve("esri", "102001");
        assertNull(result);
    }

    @Test
    void testProvider_AuthorityFilter_CaseInsensitive() {
        UrlCRSProvider provider = UrlCRSProvider.builder("filtered")
                .baseUrl(baseUrl)
                .authorities("EPSG", "ESRI")
                .build();

        // Authority is passed lower-cased by Defs
        CRSResult result = provider.resolve("epsg", "4326");
        assertNotNull(result);
    }

    @Test
    void testProvider_AuthorityFilter_MultipleAuthorities() {
        UrlCRSProvider provider = UrlCRSProvider.builder("multi-auth")
                .baseUrl(baseUrl)
                .authorities("epsg", "esri")
                .build();

        Set<String> auths = provider.getAuthorities();
        assertNotNull(auths);
        assertEquals(2, auths.size());
        assertTrue(auths.contains("epsg"));
        assertTrue(auths.contains("esri"));
    }

    @Test
    void testProvider_NoAuthorityFilter_AcceptsAll() {
        UrlCRSProvider provider = UrlCRSProvider.builder("unfiltered")
                .baseUrl(baseUrl)
                .build();

        assertNull(provider.getAuthorities());
        // Should attempt to fetch for any authority
        CRSResult result = provider.resolve("epsg", "4326");
        assertNotNull(result);
    }

    // ==================== Integration with Defs ====================

    @Test
    void testProvider_RegisterAndResolveViaDefs() {
        Defs.globals();

        UrlCRSProvider provider = UrlCRSProvider.builder("test-server")
                .baseUrl(baseUrl)
                .build();

        // Register before built-in (priority < 100)
        Defs.registerProvider(provider, 50);

        assertEquals(3, Defs.getProviders().size());
        assertEquals("test-server", Defs.getProviders().get(0).getName());
    }

    @Test
    void testProvider_ResolvesViaDefs_ProjJson() {
        Defs.globals();

        UrlCRSProvider provider = UrlCRSProvider.builder("test-server")
                .baseUrl(baseUrl)
                .build();
        Defs.registerProvider(provider, 50);

        // EPSG:2154 is not in BuiltInCRSProvider; this provider is tried first
        // and serves it from our mock server
        ProjectionDef def = Defs.get("EPSG:2154");
        assertNotNull(def, "Should resolve EPSG:2154 via UrlCRSProvider");

        ProjectionDef cached = Defs.get("epsg:2154");
        assertSame(def, cached);
        assertEquals(1, requestCount("/epsg/2154.json"),
                "successful remote definitions should be cached in the JVM");
    }

    @Test
    void testProvider_Proj4Format_ParsedByDefs() {
        Defs.globals();

        UrlCRSProvider provider = UrlCRSProvider.builder("proj4-server")
                .baseUrl(baseUrl)
                .format(CRSResult.Format.PROJ4)
                .authorities("custom")
                .build();
        Defs.registerProvider(provider, 50);

        ProjectionDef def = Defs.get("CUSTOM:lcc");
        assertNotNull(def, "Should resolve CUSTOM:lcc via UrlCRSProvider with PROJ4 format");
        assertEquals("lcc", def.getProjName());
    }

    @Test
    void testProvider_FallsThroughWhen404() {
        Defs.globals();

        UrlCRSProvider provider = UrlCRSProvider.builder("fallthrough")
                .baseUrl(baseUrl)
                .build();
        Defs.registerProvider(provider, 50);

        // EPSG:4326 is served by our mock; it should resolve
        ProjectionDef def = Defs.get("EPSG:4326");
        assertNotNull(def);

        // A code that doesn't exist on our mock server — UrlCRSProvider returns null,
        // falls through to BuiltInCRSProvider which knows EPSG:3857
        ProjectionDef merc = Defs.get("EPSG:3857");
        assertNotNull(merc);
        assertEquals("merc", merc.getProjName());
    }

    @Test
    void testProvider_DuplicateNameRejected() {
        Defs.globals();

        UrlCRSProvider p1 = UrlCRSProvider.builder("url-server")
                .baseUrl(baseUrl)
                .build();
        UrlCRSProvider p2 = UrlCRSProvider.builder("url-server")
                .baseUrl(baseUrl)
                .build();

        Defs.registerProvider(p1, 50);
        assertThrows(IllegalArgumentException.class, () -> Defs.registerProvider(p2, 51));
    }

    @Test
    void testProvider_Removable() {
        Defs.globals();

        UrlCRSProvider provider = UrlCRSProvider.builder("removable")
                .baseUrl(baseUrl)
                .build();
        Defs.registerProvider(provider, 50);
        assertEquals(3, Defs.getProviders().size());

        assertTrue(Defs.removeProvider("removable"));
        assertEquals(2, Defs.getProviders().size());
    }

    // ==================== Custom Headers ====================

    @Test
    void testProvider_CustomHeaders() {
        // The /headers-echo/test.json endpoint echoes headers back
        UrlCRSFetcher fetcherEcho = UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate("/headers-echo/{code}.json")
                .header("Authorization", "token test-fake-token-123")
                .header("X-Custom", "hello")
                .build();

        UrlCRSFetcher.FetchResult result = fetcherEcho.fetch("any", "test");

        assertTrue(result.isSuccess());
        assertTrue(result.getBody().contains("test-fake-token-123"));
        assertTrue(result.getBody().contains("hello"));
    }

    // ==================== Realistic Scenarios ====================

    @Test
    void testScenario_GitHubRawContent() {
        // Simulates: https://raw.githubusercontent.com/myorg/crs-defs/main/epsg/4326.json
        UrlCRSProvider provider = UrlCRSProvider.builder("github-crs")
                .baseUrl(baseUrl)  // In reality: https://raw.githubusercontent.com/myorg/crs-defs/main
                .pathTemplate("/{authority}/{code}.json")
                .authorities("epsg", "esri")
                .build();

        CRSResult result = provider.resolve("epsg", "4326");
        assertNotNull(result);
        assertEquals(CRSResult.Format.PROJJSON, result.getFormat());
    }

    @Test
    void testScenario_S3Bucket() {
        // Simulates: https://my-bucket.s3.amazonaws.com/projjson/EPSG/4326.json
        UrlCRSProvider provider = UrlCRSProvider.builder("s3-crs")
                .baseUrl(baseUrl)  // In reality: https://my-bucket.s3.amazonaws.com/projjson
                .pathTemplate("/{AUTHORITY}/{code}.json")
                .build();

        CRSResult result = provider.resolve("epsg", "4326");
        assertNotNull(result);
    }

    @Test
    void testScenario_PrivateRepoWithAuth() {
        // Simulates a private GitHub repo with token auth
        UrlCRSProvider provider = UrlCRSProvider.builder("private-crs")
                .baseUrl(baseUrl)
                .header("Authorization", "token test-fake-token-456")
                .build();

        CRSResult result = provider.resolve("epsg", "4326");
        assertNotNull(result);
    }
}
