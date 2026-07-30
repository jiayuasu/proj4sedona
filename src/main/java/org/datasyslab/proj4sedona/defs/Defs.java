package org.datasyslab.proj4sedona.defs;

import com.google.gson.Gson;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.parser.ProjString;
import org.datasyslab.proj4sedona.parser.WktParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.datasyslab.proj4sedona.util.CRSUtils;

/**
 * Global registry of named projection definitions and CRS providers.
 *
 * <p>This class maintains a cache of parsed {@link ProjectionDef} objects and a
 * priority-ordered chain of {@link CRSProvider} instances that are consulted on cache
 * miss. Providers are tried in ascending priority order (lower value = tried first);
 * the first non-null {@link CRSResult} is parsed and cached. A provider exception
 * stops resolution and is propagated rather than being treated as not-found.</p>
 *
 * <p>By default, {@link #globals()} registers two providers:</p>
 * <ul>
 *   <li>{@link BuiltInCRSProvider} at priority <b>100</b> — instant map lookup, no network</li>
 *   <li>{@link UrlCRSProvider#spatialReference()} at priority <b>101</b> — fetches
 *       the OSGeo spatialreference.org catalog through jsDelivr with a raw GitHub fallback</li>
 * </ul>
 *
 * <p>Users can register custom providers at a lower priority to override defaults,
 * or at a higher priority to act as fallback when earlier providers return
 * {@code null}:</p>
 * <pre>
 * Defs.registerProvider(new MyCRSProvider(), 50);  // tried before built-in
 * </pre>
 *
 * <p>Manual overrides via {@link #set(String, String)} and {@link #set(String, ProjectionDef)}
 * go directly into the cache and always take precedence over providers.</p>
 */
public final class Defs {

    /** Cache of parsed projection definitions, keyed by normalized name */
    private static final Map<String, ProjectionDef> definitions = new ConcurrentHashMap<>();

    /** Shared Gson instance — thread-safe for read operations. */
    private static final Gson GSON = new Gson();

    /** Flag indicating whether global definitions have been initialized */
    private static volatile boolean globalsInitialized = false;

    /** Pattern to match authority:code (e.g., "EPSG:4326", "ESRI:102001", "IAU_2015:49900") */
    private static final Pattern AUTHORITY_CODE_PATTERN =
        Pattern.compile("^([A-Za-z][A-Za-z0-9_]*):(\\S+)$");

    // ==================== Provider Chain ====================

    /** A provider paired with its priority for ordering. */
    private static final class ProviderEntry implements Comparable<ProviderEntry> {
        final CRSProvider provider;
        final int priority;

        ProviderEntry(CRSProvider provider, int priority) {
            this.provider = provider;
            this.priority = priority;
        }

        @Override
        public int compareTo(ProviderEntry o) {
            return Integer.compare(this.priority, o.priority);
        }
    }

    /** Priority-ordered list of registered providers (thread-safe for reads). */
    private static volatile CopyOnWriteArrayList<ProviderEntry> providers = new CopyOnWriteArrayList<>();

    private Defs() {
        // Utility class - prevent instantiation
    }

    // ==================== Provider Registration ====================

    /**
     * Register a CRS provider at the given priority.
     *
     * <p>Lower priority values are tried first. The default providers use
     * priorities 100 ({@link BuiltInCRSProvider}) and 101
     * ({@link UrlCRSProvider#spatialReference()}), so registering at &lt; 100 will
     * override them.</p>
     *
     * @param provider the provider to register (must have a unique
     *                 {@link CRSProvider#getName() name})
     * @param priority ordering priority (lower = tried first)
     * @throws IllegalArgumentException if a provider with the same name is already registered
     */
    public static synchronized void registerProvider(CRSProvider provider, int priority) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        String name = provider.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("provider name must not be null or blank");
        }
        for (ProviderEntry e : providers) {
            if (e.provider.getName().equals(name)) {
                throw new IllegalArgumentException(
                    "Provider already registered: " + provider.getName());
            }
        }
        List<ProviderEntry> snapshot = new ArrayList<>(providers);
        snapshot.add(new ProviderEntry(provider, priority));
        Collections.sort(snapshot);
        // Atomic swap: avoids a window where concurrent readers see an empty list
        CopyOnWriteArrayList<ProviderEntry> replacement = new CopyOnWriteArrayList<>(snapshot);
        providers = replacement;
    }

    /**
     * Remove a registered provider by name.
     *
     * @param name the {@link CRSProvider#getName()} of the provider to remove
     * @return {@code true} if a provider was removed
     * @throws IllegalArgumentException if {@code name} is {@code null}
     */
    public static synchronized boolean removeProvider(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Provider name must not be null");
        }
        boolean removed = false;
        List<ProviderEntry> filtered = new ArrayList<>();
        for (ProviderEntry entry : providers) {
            if (entry.provider.getName().equals(name)) {
                removed = true;
            } else {
                filtered.add(entry);
            }
        }
        if (removed) {
            providers = new CopyOnWriteArrayList<>(filtered);
        }
        return removed;
    }

    /**
     * Get an unmodifiable, priority-ordered list of the currently registered providers.
     *
     * @return list of providers (lowest priority first)
     */
    public static List<CRSProvider> getProviders() {
        List<CRSProvider> result = new ArrayList<>();
        for (ProviderEntry e : providers) {
            result.add(e.provider);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Remove all registered providers.
     *
     * <p><strong>Warning:</strong> primarily intended for testing.</p>
     */
    public static synchronized void clearProviders() {
        providers = new CopyOnWriteArrayList<>();
    }

    // ==================== Definition Cache ====================

    /**
     * Set (register) a projection definition by name.
     *
     * <p>Accepts a PROJ string (starting with "+"), a PROJJSON document (starting
     * with "{"), or a WKT string — mirroring proj4js {@code defs(code, definition)}
     * (incl. proj4js 1a3b130, which fixed PROJJSON registration).</p>
     *
     * @param name The name/code to register the definition under (e.g., "EPSG:4326")
     * @param definition The PROJ string, PROJJSON, or WKT definition
     */
    public static void set(String name, String definition) {
        // Normalize like get()/has()/remove(), so set("epsg:4326", ...) is
        // retrievable via get("EPSG:4326") instead of silently shadowed by providers.
        String key = CRSUtils.normalizeAuthorityCode(name);
        String trimmed = definition == null ? "" : definition.trim();
        if (trimmed.isEmpty()) {
            definitions.remove(key);
            return;
        }

        ProjectionDef def;
        if (trimmed.charAt(0) == '+') {
            def = ProjString.parse(trimmed);
        } else if (trimmed.charAt(0) == '{') {
            // PROJJSON
            @SuppressWarnings("unchecked")
            Map<String, Object> json = GSON.fromJson(trimmed, Map.class);
            def = WktParser.parse(json);
        } else {
            // WKT1 / WKT2
            def = WktParser.parse(trimmed);
        }
        // The registered code identifies the definition (parsers may have set srsCode
        // to the raw definition or its embedded name).
        def.setSrsCode(key);
        definitions.put(key, def);
    }

    /**
     * Set (register) a PROJJSON definition by name.
     * Mirrors proj4js {@code defs(code, projjsonObject)} (proj4js 1a3b130).
     *
     * @param name The name/code to register the definition under
     * @param projjson The PROJJSON document as a parsed Map
     */
    public static void set(String name, Map<String, Object> projjson) {
        String key = CRSUtils.normalizeAuthorityCode(name);
        if (projjson == null) {
            definitions.remove(key);
            return;
        }
        ProjectionDef def = WktParser.parse(projjson);
        def.setSrsCode(key);
        definitions.put(key, def);
    }

    /**
     * Set (register) a projection definition by name.
     *
     * @param name The name/code to register the definition under
     * @param def The ProjectionDef object
     */
    public static void set(String name, ProjectionDef def) {
        String key = CRSUtils.normalizeAuthorityCode(name);
        if (def == null) {
            definitions.remove(key);
        } else {
            if (def.getSrsCode() == null) {
                def.setSrsCode(key);
            }
            definitions.put(key, def);
        }
    }

    // ==================== Lookup ====================

    /**
     * Get a projection definition by name.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Local cache (populated by {@link #set} and previous lookups)</li>
     *   <li>Registered {@link CRSProvider}s in priority order</li>
     * </ol>
     *
     * <p>Authority codes are case-insensitive for the authority part (e.g., "epsg:4326"
     * and "EPSG:4326" are equivalent).</p>
     *
     * @param name The name/code to look up (e.g., "EPSG:4326", "WGS84", "ESRI:102001")
     * @return The ProjectionDef, or null if no provider can resolve the code
     * @throws CRSFetchException if a provider encounters an HTTP or network failure,
     *         open circuit, or invalid response
     */
    public static ProjectionDef get(String name) {
        // Auto-initialize globals if not yet done
        if (!globalsInitialized) {
            globals();
        }

        // Normalize authority codes for case-insensitive lookup
        String normalizedName = CRSUtils.normalizeAuthorityCode(name);

        // Check local cache first
        ProjectionDef def = definitions.get(normalizedName);
        if (def != null) {
            return def;
        }

        // Try providers if code matches authority:code pattern
        Matcher matcher = AUTHORITY_CODE_PATTERN.matcher(normalizedName);
        if (matcher.matches()) {
            String authority = matcher.group(1).toLowerCase();
            String code = matcher.group(2);
            def = resolveFromProviders(authority, code, normalizedName);
            if (def != null) {
                definitions.put(normalizedName, def);
                return def;
            }
        }

        return null;
    }

    /**
     * Get a projection definition by name, throwing if not found.
     *
     * <p>Behaves identically to {@link #get(String)} but throws a
     * {@link CRSFetchException} with {@link CRSFetchException.Reason#NOT_FOUND}
     * instead of returning {@code null}.</p>
     *
     * @param name The name/code to look up (e.g., "EPSG:4326", "WGS84")
     * @return The ProjectionDef (never null)
     * @throws CRSFetchException with {@link CRSFetchException.Reason#NOT_FOUND} if no
     *         provider can resolve the code
     * @throws CRSFetchException if a provider encounters an HTTP or network failure,
     *         open circuit, or invalid response
     */
    public static ProjectionDef getOrThrow(String name) {
        ProjectionDef def = get(name);
        if (def == null) {
            throw new CRSFetchException(name, CRSFetchException.Reason.NOT_FOUND,
                    "CRS definition not found: " + name);
        }
        return def;
    }

    /**
     * Iterate through providers in priority order, parse the first non-null result.
     */
    private static ProjectionDef resolveFromProviders(String authority, String code, String fullCode) {
        for (ProviderEntry entry : providers) {
            CRSResult result = entry.provider.resolve(authority, code);
            if (result != null) {
                return parseResult(result, fullCode);
            }
        }
        return null;
    }

    /**
     * Parse a {@link CRSResult} into a {@link ProjectionDef} based on its format.
     */
    @SuppressWarnings("unchecked")
    private static ProjectionDef parseResult(CRSResult result, String fullCode) {
        try {
            ProjectionDef def;
            switch (result.getFormat()) {
                case PROJ4:
                    def = ProjString.parse(result.getDefinition());
                    break;
                case PROJJSON:
                    Map<String, Object> json = GSON.fromJson(result.getDefinition(), Map.class);
                    def = WktParser.parse(json);
                    break;
                case WKT1:
                case WKT2:
                    def = WktParser.parse(result.getDefinition());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown CRSResult format: " + result.getFormat());
            }
            def.setSrsCode(fullCode);
            return def;
        } catch (CRSFetchException e) {
            throw e; // re-throw provider exceptions as-is
        } catch (Exception e) {
            throw new CRSFetchException(fullCode, CRSFetchException.Reason.INVALID_RESPONSE,
                "Failed to parse CRS definition for " + fullCode + ": " + e.getMessage(), e);
        }
    }

    // ==================== Cache Inspection ====================

    /**
     * Check if a definition exists in the local cache.
     *
     * <p>Authority codes are case-insensitive for the authority part.</p>
     *
     * @param name The name/code to check
     * @return true if the definition exists in cache
     */
    public static boolean has(String name) {
        if (!globalsInitialized) {
            globals();
        }
        return definitions.containsKey(CRSUtils.normalizeAuthorityCode(name));
    }

    /**
     * Remove a definition from the local cache.
     *
     * <p>Authority codes are case-insensitive for the authority part.</p>
     *
     * @param name The name/code to remove
     * @return The removed definition, or null if it didn't exist
     */
    public static ProjectionDef remove(String name) {
        return definitions.remove(CRSUtils.normalizeAuthorityCode(name));
    }

    /**
     * Create an alias for an existing cached definition.
     *
     * <p>The alias will point to the same ProjectionDef object as the original.</p>
     * <p>Authority codes are case-insensitive for the authority part.</p>
     *
     * @param alias The new alias name
     * @param existingName The existing definition name
     * @throws IllegalArgumentException if the existing definition doesn't exist
     */
    public static void alias(String alias, String existingName) {
        if (!globalsInitialized) {
            globals();
        }
        String normalizedExisting = CRSUtils.normalizeAuthorityCode(existingName);
        ProjectionDef def = definitions.get(normalizedExisting);
        if (def == null) {
            throw new IllegalArgumentException("Definition not found: " + existingName);
        }
        definitions.put(CRSUtils.normalizeAuthorityCode(alias), def);
    }

    // ==================== Initialization ====================

    /**
     * Initialize the registry with default providers.
     *
     * <p>This method is idempotent — calling it multiple times has no effect
     * after the first call. It registers:</p>
     * <ul>
     *   <li>{@link BuiltInCRSProvider} at priority 100</li>
     *   <li>{@link UrlCRSProvider#spatialReference()} at priority 101, backed by
     *       the OSGeo catalog through jsDelivr and raw GitHub</li>
     * </ul>
     *
     * <p>It also pre-populates the cache with common aliases that are not in
     * authority:code format (e.g., {@code WGS84}, {@code GOOGLE}).</p>
     */
    public static synchronized void globals() {
        if (globalsInitialized) {
            return;
        }

        // Register default providers
        BuiltInCRSProvider builtIn = new BuiltInCRSProvider();
        registerProvider(builtIn, 100);
        registerProvider(UrlCRSProvider.spatialReference(), 101);

        // Pre-cache aliases that don't match the authority:code pattern.
        // These names (WGS84, GOOGLE) cannot be resolved by providers because
        // they lack a colon, so we eagerly resolve and cache them here.
        // Resolve directly via provider to avoid re-entering get() → globals().
        CRSResult wgs84Result = builtIn.resolve("epsg", "4326");
        CRSResult webMercResult = builtIn.resolve("epsg", "3857");
        if (wgs84Result != null) {
            ProjectionDef wgs84 = parseResult(wgs84Result, "EPSG:4326");
            definitions.put("EPSG:4326", wgs84);
            definitions.put(CRSUtils.normalizeAuthorityCode("WGS84"), wgs84);
        }
        if (webMercResult != null) {
            ProjectionDef webMerc = parseResult(webMercResult, "EPSG:3857");
            definitions.put("EPSG:3857", webMerc);
            definitions.put(CRSUtils.normalizeAuthorityCode("EPSG:3785"), webMerc);
            definitions.put(CRSUtils.normalizeAuthorityCode("GOOGLE"), webMerc);
            definitions.put(CRSUtils.normalizeAuthorityCode("EPSG:900913"), webMerc);
            definitions.put(CRSUtils.normalizeAuthorityCode("EPSG:102113"), webMerc);
        }

        // Set flag last so concurrent callers of get() see a fully-initialized state
        globalsInitialized = true;
    }

    /**
     * Get the number of cached definitions.
     *
     * @return The count of definitions in cache
     */
    public static int size() {
        return definitions.size();
    }

    /**
     * Clear all definitions, providers, and reset all flags.
     *
     * <p><strong>Warning:</strong> This is primarily intended for testing.
     * Do not call in production code.</p>
     */
    public static synchronized void reset() {
        definitions.clear();
        providers = new CopyOnWriteArrayList<>();
        globalsInitialized = false;
    }

    /**
     * Check if globals have been initialized.
     *
     * @return true if {@link #globals()} has been called
     */
    public static boolean isGlobalsInitialized() {
        return globalsInitialized;
    }
}
