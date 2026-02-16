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
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private static int port;
    private static String baseUrl;

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
            } else if ("/slow/timeout.json".equals(path)) {
                // Simulate a slow response
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
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

        server.setExecutor(null);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        Defs.reset();
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
                .header("X-Key", "abc")
                .build();

        assertEquals("https://example.com", fetcher.getBaseUrl());
        assertEquals("/{AUTHORITY}/{code}.json", fetcher.getPathTemplate());
        assertEquals(5, fetcher.getConnectTimeoutSeconds());
        assertEquals(15, fetcher.getReadTimeoutSeconds());
        assertEquals(2, fetcher.getMaxRetries());
        assertEquals(100, fetcher.getInitialBackoffMs());
        assertEquals("abc", fetcher.getHeaders().get("X-Key"));
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
