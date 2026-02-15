package org.datasyslab.proj4sedona.defs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.datasyslab.proj4sedona.Proj4;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.transform.Converter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CRSProvider interface, provider registration, and resolution chain.
 */
class CRSProviderTest {

    @BeforeEach
    void setUp() {
        Defs.reset();
    }

    @AfterEach
    void tearDown() {
        Defs.reset();
    }

    // ==================== CRSResult ====================

    @Test
    void testCRSResult_Proj4() {
        CRSResult result = CRSResult.proj4("+proj=longlat +datum=WGS84");
        assertEquals(CRSResult.Format.PROJ4, result.getFormat());
        assertEquals("+proj=longlat +datum=WGS84", result.getDefinition());
    }

    @Test
    void testCRSResult_ProjJson() {
        CRSResult result = CRSResult.projJson("{\"type\":\"GeographicCRS\"}");
        assertEquals(CRSResult.Format.PROJJSON, result.getFormat());
    }

    @Test
    void testCRSResult_Wkt() {
        CRSResult wkt2 = CRSResult.wkt2("GEOGCRS[\"WGS 84\"]");
        assertEquals(CRSResult.Format.WKT2, wkt2.getFormat());
        CRSResult wkt1 = CRSResult.wkt1("GEOGCS[\"WGS 84\"]");
        assertEquals(CRSResult.Format.WKT1, wkt1.getFormat());
    }

    @Test
    void testCRSResult_NullThrows() {
        assertThrows(IllegalArgumentException.class, () -> CRSResult.proj4(null));
        assertThrows(IllegalArgumentException.class, () -> CRSResult.proj4(""));
    }

    @Test
    void testCRSResult_ToString() {
        CRSResult result = CRSResult.proj4("+proj=longlat");
        String str = result.toString();
        assertTrue(str.contains("PROJ4"));
        assertTrue(str.contains("+proj=longlat"));
    }

    // ==================== Provider Registration ====================

    @Test
    void testRegisterAndGetProviders() {
        Defs.globals();

        List<CRSProvider> providers = Defs.getProviders();
        assertEquals(2, providers.size());
        assertEquals("built-in", providers.get(0).getName());
        assertEquals("spatialreference.org", providers.get(1).getName());
    }

    @Test
    void testRegisterProvider_PriorityOrdering() {
        Defs.globals();

        // Register a custom provider at priority 50 (before defaults at 100/101)
        CRSProvider custom = new CRSProvider() {
            public String getName() { return "custom-first"; }
            public CRSResult resolve(String authority, String code) { return null; }
        };
        Defs.registerProvider(custom, 50);

        List<CRSProvider> providers = Defs.getProviders();
        assertEquals(3, providers.size());
        assertEquals("custom-first", providers.get(0).getName());
        assertEquals("built-in", providers.get(1).getName());
        assertEquals("spatialreference.org", providers.get(2).getName());
    }

    @Test
    void testRegisterProvider_HighPriority_AppendsAfterDefaults() {
        Defs.globals();

        CRSProvider fallback = new CRSProvider() {
            public String getName() { return "fallback"; }
            public CRSResult resolve(String authority, String code) { return null; }
        };
        Defs.registerProvider(fallback, 200);

        List<CRSProvider> providers = Defs.getProviders();
        assertEquals(3, providers.size());
        assertEquals("fallback", providers.get(2).getName());
    }

    @Test
    void testRegisterProvider_DuplicateNameThrows() {
        Defs.globals();

        CRSProvider duplicate = new CRSProvider() {
            public String getName() { return "built-in"; }
            public CRSResult resolve(String authority, String code) { return null; }
        };

        assertThrows(IllegalArgumentException.class, () ->
                Defs.registerProvider(duplicate, 50));
    }

    @Test
    void testRegisterProvider_NullThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                Defs.registerProvider(null, 50));
    }

    @Test
    void testRegisterProvider_NullNameThrows() {
        CRSProvider nullName = new CRSProvider() {
            public String getName() { return null; }
            public CRSResult resolve(String authority, String code) { return null; }
        };
        assertThrows(IllegalArgumentException.class, () ->
                Defs.registerProvider(nullName, 50));
    }

    @Test
    void testRegisterProvider_BlankNameThrows() {
        CRSProvider blankName = new CRSProvider() {
            public String getName() { return "   "; }
            public CRSResult resolve(String authority, String code) { return null; }
        };
        assertThrows(IllegalArgumentException.class, () ->
                Defs.registerProvider(blankName, 50));
    }

    @Test
    void testRemoveProvider() {
        Defs.globals();
        assertEquals(2, Defs.getProviders().size());

        boolean removed = Defs.removeProvider("spatialreference.org");
        assertTrue(removed);
        assertEquals(1, Defs.getProviders().size());
        assertEquals("built-in", Defs.getProviders().get(0).getName());
    }

    @Test
    void testRemoveProvider_NonExistent() {
        Defs.globals();
        boolean removed = Defs.removeProvider("nonexistent");
        assertFalse(removed);
        assertEquals(2, Defs.getProviders().size());
    }

    @Test
    void testClearProviders() {
        Defs.globals();
        Defs.clearProviders();
        assertEquals(0, Defs.getProviders().size());
    }

    @Test
    void testGetProviders_Unmodifiable() {
        Defs.globals();
        List<CRSProvider> providers = Defs.getProviders();
        assertThrows(UnsupportedOperationException.class, () ->
                providers.add(UrlCRSProvider.spatialReference()));
    }

    // ==================== BuiltInCRSProvider ====================

    @Test
    void testBuiltInProvider_ResolvesEPSG4326() {
        BuiltInCRSProvider provider = new BuiltInCRSProvider();
        CRSResult result = provider.resolve("epsg", "4326");

        assertNotNull(result);
        assertEquals(CRSResult.Format.PROJ4, result.getFormat());
        assertTrue(result.getDefinition().contains("+proj=longlat"));
        assertTrue(result.getDefinition().contains("+datum=WGS84"));
    }

    @Test
    void testBuiltInProvider_ResolvesEPSG3857() {
        BuiltInCRSProvider provider = new BuiltInCRSProvider();
        CRSResult result = provider.resolve("epsg", "3857");

        assertNotNull(result);
        assertTrue(result.getDefinition().contains("+proj=merc"));
    }

    @Test
    void testBuiltInProvider_ResolvesUTMZones() {
        BuiltInCRSProvider provider = new BuiltInCRSProvider();

        CRSResult utmN = provider.resolve("epsg", "32632");
        assertNotNull(utmN);
        assertTrue(utmN.getDefinition().contains("+proj=utm"));
        assertTrue(utmN.getDefinition().contains("+zone=32"));

        CRSResult utmS = provider.resolve("epsg", "32701");
        assertNotNull(utmS);
        assertTrue(utmS.getDefinition().contains("+south"));
    }

    @Test
    void testBuiltInProvider_ResolvesUPS() {
        BuiltInCRSProvider provider = new BuiltInCRSProvider();
        assertNotNull(provider.resolve("epsg", "5041"));
        assertNotNull(provider.resolve("epsg", "5042"));
    }

    @Test
    void testBuiltInProvider_ReturnsNullForUnknown() {
        BuiltInCRSProvider provider = new BuiltInCRSProvider();
        assertNull(provider.resolve("epsg", "999999"));
        assertNull(provider.resolve("esri", "102001"));
    }

    @Test
    void testBuiltInProvider_Name() {
        assertEquals("built-in", new BuiltInCRSProvider().getName());
    }

    // ==================== spatialreference.org Provider ====================

    @Test
    void testSpatialReferenceProvider_Name() {
        assertEquals("spatialreference.org", UrlCRSProvider.spatialReference().getName());
    }

    @Test
    void testSpatialReferenceProvider_ReturnsNullForNotFound() {
        UrlCRSProvider provider = UrlCRSProvider.spatialReference();
        CRSResult result = provider.resolve("epsg", "999999999");
        assertNull(result, "Should return null for non-existent code");
    }

    @Test
    void testSpatialReferenceProvider_ThrowsOnNetworkError() {
        // Create provider pointed to unreachable server
        UrlCRSProvider provider = UrlCRSProvider.builder(UrlCRSProvider.SPATIAL_REFERENCE_NAME)
                .baseUrl("http://localhost:59990")
                .pathTemplate(UrlCRSProvider.SPATIAL_REFERENCE_PATH)
                .connectTimeout(1).readTimeout(1).maxRetries(1).initialBackoffMs(10)
                .build();

        assertThrows(CRSFetchException.class, () ->
                provider.resolve("epsg", "4326"));
    }

    // ==================== Custom Provider Resolution ====================

    @Test
    void testCustomProvider_WKT1_ParsedAndCached() {
        Defs.globals();

        String wkt1 = "GEOGCS[\"WGS 84\"," +
                "DATUM[\"WGS_1984\"," +
                "SPHEROID[\"WGS 84\",6378137,298.257223563]]," +
                "PRIMEM[\"Greenwich\",0]," +
                "UNIT[\"degree\",0.0174532925199433]]";

        CRSProvider wkt1Provider = new CRSProvider() {
            public String getName() { return "wkt1-provider"; }
            public CRSResult resolve(String authority, String code) {
                if ("test".equals(authority) && "wkt1".equals(code)) {
                    return CRSResult.wkt1(wkt1);
                }
                return null;
            }
        };
        Defs.registerProvider(wkt1Provider, 50);

        ProjectionDef def = Defs.get("TEST:wkt1");
        assertNotNull(def, "WKT1 provider result should be parsed");
        assertEquals("longlat", def.getProjName());
        assertEquals("wgs84", def.getDatumCode());
        assertEquals("TEST:wkt1", def.getSrsCode());

        // Verify caching — second call should return cached value
        ProjectionDef cached = Defs.get("TEST:wkt1");
        assertSame(def, cached, "Result should be cached");
    }

    @Test
    void testCustomProvider_WKT2_ParsedAndCached() {
        Defs.globals();

        String wkt2 = "GEOGCRS[\"WGS 84\"," +
                "DATUM[\"World Geodetic System 1984\"," +
                "ELLIPSOID[\"WGS 84\",6378137,298.257223563]]," +
                "CS[ellipsoidal,2]," +
                "AXIS[\"geodetic latitude (Lat)\",north]," +
                "AXIS[\"geodetic longitude (Lon)\",east]," +
                "UNIT[\"degree\",0.0174532925199433]]";

        CRSProvider wkt2Provider = new CRSProvider() {
            public String getName() { return "wkt2-provider"; }
            public CRSResult resolve(String authority, String code) {
                if ("test".equals(authority) && "wkt2".equals(code)) {
                    return CRSResult.wkt2(wkt2);
                }
                return null;
            }
        };
        Defs.registerProvider(wkt2Provider, 50);

        ProjectionDef def = Defs.get("TEST:wkt2");
        assertNotNull(def, "WKT2 provider result should be parsed");
        assertEquals("longlat", def.getProjName());
        assertEquals("TEST:wkt2", def.getSrsCode());

        // Verify caching
        ProjectionDef cached = Defs.get("TEST:wkt2");
        assertSame(def, cached, "Result should be cached");
    }

    @Test
    void testCustomProvider_ResolvesCustomCode() {
        Defs.globals();

        // Register a provider that knows about CUSTOM authority
        CRSProvider custom = new CRSProvider() {
            public String getName() { return "custom"; }
            public CRSResult resolve(String authority, String code) {
                if ("custom".equals(authority) && "1234".equals(code)) {
                    return CRSResult.proj4(
                        "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 " +
                        "+x_0=0 +y_0=0 +datum=WGS84 +units=m");
                }
                return null;
            }
        };
        Defs.registerProvider(custom, 50);

        ProjectionDef def = Defs.get("CUSTOM:1234");
        assertNotNull(def, "Custom provider should resolve CUSTOM:1234");
        assertEquals("lcc", def.getProjName());
        assertEquals("CUSTOM:1234", def.getSrsCode());
    }

    @Test
    void testCustomProvider_TransformWithCustomCode() {
        Defs.globals();

        CRSProvider custom = new CRSProvider() {
            public String getName() { return "custom"; }
            public CRSResult resolve(String authority, String code) {
                if ("custom".equals(authority) && "1".equals(code)) {
                    return CRSResult.proj4(
                        "+proj=lcc +lat_1=33 +lat_2=45 +lat_0=39 +lon_0=-96 " +
                        "+x_0=0 +y_0=0 +datum=WGS84 +units=m");
                }
                return null;
            }
        };
        Defs.registerProvider(custom, 50);

        Converter conv = Proj4.proj4("EPSG:4326", "CUSTOM:1");

        Point original = new Point(-100, 40);
        Point projected = conv.forward(original);
        Point restored = conv.inverse(projected);

        assertEquals(original.x, restored.x, 1e-6);
        assertEquals(original.y, restored.y, 1e-6);
    }

    // ==================== Provider Chain Behavior ====================

    @Test
    void testProviderChain_HigherPriorityWins() {
        Defs.globals();

        // Register a custom provider at priority 50 that resolves EPSG:99887
        CRSProvider custom = new CRSProvider() {
            public String getName() { return "override"; }
            public CRSResult resolve(String authority, String code) {
                if ("epsg".equals(authority) && "99887".equals(code)) {
                    return CRSResult.proj4("+proj=merc +datum=WGS84 +units=m");
                }
                return null;
            }
        };
        Defs.registerProvider(custom, 50);

        // EPSG:99887 is not in the built-in provider, but our custom one resolves it
        ProjectionDef def = Defs.get("EPSG:99887");
        assertNotNull(def);
        assertEquals("merc", def.getProjName());
    }

    @Test
    void testProviderChain_FallThrough() {
        Defs.globals();

        // Register a provider that always returns null
        CRSProvider nullProvider = new CRSProvider() {
            public String getName() { return "null-provider"; }
            public CRSResult resolve(String authority, String code) {
                return null;
            }
        };
        Defs.registerProvider(nullProvider, 50);

        // EPSG:4269 should still resolve via the BuiltInCRSProvider at priority 100
        ProjectionDef def = Defs.get("EPSG:4269");
        assertNotNull(def, "Should fall through to BuiltInCRSProvider");
        assertEquals("longlat", def.getProjName());
    }

    @Test
    void testProviderChain_AllReturnNull() {
        Defs.globals();
        // Explicitly resolve and cache EPSG:4326 before clearing providers,
        // so the cache assertion below does not depend on globals() internals.
        Defs.get("EPSG:4326");

        // Remove default providers and add one that always returns null
        Defs.clearProviders();
        CRSProvider nullProvider = new CRSProvider() {
            public String getName() { return "null"; }
            public CRSResult resolve(String authority, String code) { return null; }
        };
        Defs.registerProvider(nullProvider, 50);

        // EPSG:4326 was explicitly cached above, so it should still resolve
        ProjectionDef def = Defs.get("EPSG:4326");
        assertNotNull(def, "EPSG:4326 should still be in cache");

        // But an uncached code should return null
        ProjectionDef uncached = Defs.get("EPSG:9999");
        assertNull(uncached, "Uncached code should return null when all providers return null");
    }

    @Test
    void testProviderChain_ExceptionPropagates() {
        Defs.globals();
        Defs.clearProviders();

        // Register a provider that throws
        CRSProvider failProvider = new CRSProvider() {
            public String getName() { return "fail"; }
            public CRSResult resolve(String authority, String code) {
                throw new CRSFetchException(authority + ":" + code,
                        CRSFetchException.Reason.NETWORK_ERROR, "deliberate failure");
            }
        };
        Defs.registerProvider(failProvider, 50);

        // Uncached code should propagate the exception
        CRSFetchException ex = assertThrows(CRSFetchException.class, () ->
                Defs.get("CUSTOM:999"));
        assertEquals(CRSFetchException.Reason.NETWORK_ERROR, ex.getReason());
    }

    // ==================== getOrThrow ====================

    @Test
    void testGetOrThrow_ReturnsDefOnHit() {
        Defs.globals();
        ProjectionDef def = Defs.getOrThrow("EPSG:4326");
        assertNotNull(def);
        assertEquals("longlat", def.getProjName());
    }

    @Test
    void testGetOrThrow_ThrowsNotFoundOnMiss() {
        Defs.globals();
        CRSFetchException ex = assertThrows(CRSFetchException.class, () ->
                Defs.getOrThrow("EPSG:999999"));
        assertEquals(CRSFetchException.Reason.NOT_FOUND, ex.getReason());
        assertTrue(ex.getMessage().contains("EPSG:999999"));
    }

    // ==================== Cache Interaction ====================

    @Test
    void testManualSetOverridesProvider() {
        Defs.globals();

        // Manually set a definition for an EPSG code
        Defs.set("EPSG:4326", "+proj=merc +datum=WGS84 +units=m");

        // Should return the manually set definition, not the provider's
        ProjectionDef def = Defs.get("EPSG:4326");
        assertEquals("merc", def.getProjName(), "Manual set should override provider");
    }

    @Test
    void testProviderResultIsCached() {
        Defs.globals();
        Defs.clearProviders();

        // Register a counting provider
        final int[] callCount = {0};
        CRSProvider counting = new CRSProvider() {
            public String getName() { return "counting"; }
            public CRSResult resolve(String authority, String code) {
                callCount[0]++;
                if ("test".equals(authority) && "1".equals(code)) {
                    return CRSResult.proj4("+proj=longlat +datum=WGS84");
                }
                return null;
            }
        };
        Defs.registerProvider(counting, 50);

        // First call resolves via provider
        Defs.get("TEST:1");
        assertEquals(1, callCount[0]);

        // Second call should use cache
        Defs.get("TEST:1");
        assertEquals(1, callCount[0], "Second call should use cache, not provider");
    }

    // ==================== Reset Behavior ====================

    @Test
    void testResetClearsProviders() {
        Defs.globals();
        assertEquals(2, Defs.getProviders().size());

        Defs.reset();
        assertEquals(0, Defs.getProviders().size());
        assertEquals(0, Defs.size());
        assertFalse(Defs.isGlobalsInitialized());

        // After reset, globals() re-registers providers
        Defs.globals();
        assertEquals(2, Defs.getProviders().size());
    }
}
