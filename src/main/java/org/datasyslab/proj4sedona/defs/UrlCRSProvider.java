package org.datasyslab.proj4sedona.defs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A generic {@link CRSProvider} that resolves CRS definitions by fetching files
 * from any HTTP(S) endpoint with a predictable URL layout — for example a public
 * GitHub repo, an S3 bucket, or any static file server hosting PROJJSON files.
 *
 * <p>Instances are created via the fluent {@link Builder}:</p>
 * <pre>
 * // GitHub raw content
 * UrlCRSProvider github = UrlCRSProvider.builder("my-github-crs")
 *     .baseUrl("https://raw.githubusercontent.com/myorg/crs-defs/main")
 *     .build();
 *
 * // S3 bucket
 * UrlCRSProvider s3 = UrlCRSProvider.builder("my-s3-crs")
 *     .baseUrl("https://my-bucket.s3.amazonaws.com/projjson")
 *     .pathTemplate("/{AUTHORITY}/{code}.json")
 *     .build();
 *
 * // Private repo with auth header
 * UrlCRSProvider privateRepo = UrlCRSProvider.builder("private-crs")
 *     .baseUrl("https://raw.githubusercontent.com/myorg/private-defs/main")
 *     .header("Authorization", "token YOUR_TOKEN_HERE")
 *     .build();
 *
 * // Optional fallback, configured separately so credentials are never copied
 * UrlCRSFetcher mirror = UrlCRSFetcher.builder()
 *     .baseUrl("https://mirror.example.com/crs")
 *     .build();
 * UrlCRSProvider resilient = UrlCRSProvider.builder("resilient-crs")
 *     .baseUrl("https://primary.example.com/crs")
 *     .fallbackFetcher(mirror)
 *     .build();
 *
 * // Register before the defaults (priority &lt; 100)
 * Defs.registerProvider(github, 50);
 * </pre>
 *
 * <h3>Expected File Layout</h3>
 * <p>With the default path template ({@code /{authority}/{code}.json}):</p>
 * <pre>
 * https://example.com/
 * ├── epsg/
 * │   ├── 4326.json    ← PROJJSON for EPSG:4326
 * │   ├── 3857.json
 * │   └── 2154.json
 * └── esri/
 *     └── 102001.json
 * </pre>
 *
 * <h3>URL Template Placeholders</h3>
 * <table>
 *   <caption>Supported URL template placeholders</caption>
 *   <tr><td>{@code {authority}}</td><td>authority lower-cased (e.g., {@code epsg})</td></tr>
 *   <tr><td>{@code {AUTHORITY}}</td><td>authority upper-cased (e.g., {@code EPSG})</td></tr>
 *   <tr><td>{@code {code}}</td><td>the CRS code (e.g., {@code 4326})</td></tr>
 * </table>
 *
 * @see UrlCRSFetcher
 * @see Defs#registerProvider(CRSProvider, int)
 */
public final class UrlCRSProvider implements CRSProvider {

    // ==================== spatialreference.org constants ====================

    /**
     * Legacy spatialreference.org origin URL.
     *
     * @deprecated the default provider uses {@link #SPATIAL_REFERENCE_GITHUB_BASE_URL}
     *             with {@link #SPATIAL_REFERENCE_CDN_BASE_URL} as a fallback
     */
    @Deprecated
    public static final String SPATIAL_REFERENCE_BASE_URL = "https://spatialreference.org";

    /** Primary raw GitHub URL for the rolling OSGeo spatialreference.org catalog. */
    public static final String SPATIAL_REFERENCE_GITHUB_BASE_URL =
            "https://raw.githubusercontent.com/OSGeo/spatialreference.org/gh-pages";

    /** Generated spatialreference.org data commit based on PROJ 9.8.1. */
    public static final String SPATIAL_REFERENCE_SNAPSHOT_COMMIT =
            "c43e4e72634af65fcf684def42ddc2dcfd834938";

    /** Backup CDN URL for the immutable OSGeo spatialreference.org data snapshot. */
    public static final String SPATIAL_REFERENCE_CDN_BASE_URL =
            "https://cdn.jsdelivr.net/gh/OSGeo/spatialreference.org@"
                    + SPATIAL_REFERENCE_SNAPSHOT_COMMIT;

    /** Provider name used for the spatialreference.org instance. */
    public static final String SPATIAL_REFERENCE_NAME = "spatialreference.org";

    /** Connection timeout used by the default remote provider. */
    public static final int SPATIAL_REFERENCE_CONNECT_TIMEOUT_SECONDS = 3;

    /** Per-request timeout used by the default remote provider. */
    public static final int SPATIAL_REFERENCE_READ_TIMEOUT_SECONDS = 5;

    /** Total attempts used by the default remote provider. */
    public static final int SPATIAL_REFERENCE_MAX_ATTEMPTS = 2;

    /** Initial retry backoff used by the default remote provider. */
    public static final long SPATIAL_REFERENCE_INITIAL_BACKOFF_MS = 250;

    /** Overall deadline used by the default remote provider. */
    public static final int SPATIAL_REFERENCE_TOTAL_TIMEOUT_SECONDS = 8;

    /** Endpoint failures that open the default provider's circuit. */
    public static final int SPATIAL_REFERENCE_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3;

    /** Delay before the default provider permits a probe through an open circuit. */
    public static final Duration SPATIAL_REFERENCE_CIRCUIT_BREAKER_RESET_TIMEOUT =
            Duration.ofSeconds(30);

    /** Path template within the spatialreference.org catalog. */
    static final String SPATIAL_REFERENCE_PATH = "/ref/{authority}/{code}/projjson.json";

    // ==================== spatialreference.org factories ====================

    /**
     * Create a {@link UrlCRSProvider} pre-configured for
     * the OSGeo
     * <a href="https://github.com/OSGeo/spatialreference.org">spatialreference.org</a>
     * catalog. Raw GitHub content is the primary endpoint; a commit-addressed
     * jsDelivr snapshot is used after endpoint failures.
     *
     * <p>This is the default remote CRS provider registered by
     * {@link Defs#globals()} at priority 101. For a custom mirror or different
     * reliability settings, use {@link #builder(String)} with
     * {@link #SPATIAL_REFERENCE_NAME} as the name and
     * {@code /ref/{authority}/{code}/projjson.json} as the path template.</p>
     *
     * @return a provider that fetches PROJJSON from GitHub with a pinned CDN fallback
     */
    public static UrlCRSProvider spatialReference() {
        UrlCRSFetcher github = spatialReferenceFetcher(
                SPATIAL_REFERENCE_GITHUB_BASE_URL, false);
        UrlCRSFetcher jsDelivr = spatialReferenceFetcher(
                SPATIAL_REFERENCE_CDN_BASE_URL, true);
        return new UrlCRSProvider(
                SPATIAL_REFERENCE_NAME,
                CRSResult.Format.PROJJSON,
                null,
                Arrays.asList(github, jsDelivr));
    }

    private static UrlCRSFetcher spatialReferenceFetcher(
            String baseUrl, boolean cacheNotFound) {
        return UrlCRSFetcher.builder()
                .baseUrl(baseUrl)
                .pathTemplate(SPATIAL_REFERENCE_PATH)
                .connectTimeout(SPATIAL_REFERENCE_CONNECT_TIMEOUT_SECONDS)
                .readTimeout(SPATIAL_REFERENCE_READ_TIMEOUT_SECONDS)
                .maxRetries(SPATIAL_REFERENCE_MAX_ATTEMPTS)
                .initialBackoffMs(SPATIAL_REFERENCE_INITIAL_BACKOFF_MS)
                .totalTimeout(SPATIAL_REFERENCE_TOTAL_TIMEOUT_SECONDS)
                .circuitBreakerFailureThreshold(
                        SPATIAL_REFERENCE_CIRCUIT_BREAKER_FAILURE_THRESHOLD)
                .circuitBreakerResetTimeout(
                        SPATIAL_REFERENCE_CIRCUIT_BREAKER_RESET_TIMEOUT)
                .cacheNotFound(cacheNotFound)
                .header("Accept", "application/json")
                .build();
    }

    // ==================== Instance fields ====================

    private final String name;
    private final UrlCRSFetcher fetcher;
    private final List<UrlCRSFetcher> fetchers;
    private final CRSResult.Format format;
    private final Set<String> authorities; // null = accept all

    private UrlCRSProvider(Builder builder) {
        this(
                builder.name,
                builder.format,
                builder.authorities,
                buildFetchers(builder));
    }

    private UrlCRSProvider(
            String name,
            CRSResult.Format format,
            Set<String> authorities,
            List<UrlCRSFetcher> fetchers) {
        this.name = name;
        this.format = format;
        this.authorities = authorities == null ? null
                : Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
        this.fetchers = Collections.unmodifiableList(new ArrayList<>(fetchers));
        this.fetcher = this.fetchers.get(0);
    }

    private static List<UrlCRSFetcher> buildFetchers(Builder builder) {
        List<UrlCRSFetcher> fetchers = new ArrayList<>();
        fetchers.add(builder.fetcherBuilder.build());
        fetchers.addAll(builder.fallbackFetchers);
        return fetchers;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Resolve a CRS definition by fetching it from the configured URL.
     *
     * <p>If {@link Builder#authorities(String...) authorities} were configured, this
     * method returns {@code null} immediately for any authority not in that set.
     * Otherwise, the configured fetchers are queried in order. Endpoint failures
     * advance to the next fetcher, while a primary HTTP 404 returns {@code null}
     * without consulting older fallbacks.</p>
     *
     * @param authority the authority name, lower-cased (e.g., {@code "epsg"})
     * @param code      the CRS code (e.g., {@code "4326"})
     * @return a {@link CRSResult} on success, {@code null} if the code is not found
     *         or the authority is not handled by this provider
     * @throws CRSFetchException on HTTP errors, network errors, or an open circuit breaker
     */
    @Override
    public CRSResult resolve(String authority, String code) {
        // Quick authority filter
        if (authorities != null && !authorities.contains(authority.toLowerCase(Locale.ROOT))) {
            return null;
        }

        String fullCode = authority.toUpperCase(Locale.ROOT) + ":" + code;

        List<CRSFetchException> endpointFailures = new ArrayList<>();
        List<String> outcomes = new ArrayList<>();

        for (UrlCRSFetcher currentFetcher : fetchers) {
            UrlCRSFetcher.FetchResult result = currentFetcher.fetch(authority, code);

            switch (result.getStatus()) {
                case SUCCESS:
                    return wrapResult(result.getBody());

                case NOT_FOUND:
                    if (endpointFailures.isEmpty()) {
                        return null;
                    }
                    outcomes.add(currentFetcher.getBaseUrl() + ": HTTP 404");
                    break;

                case HTTP_ERROR:
                case NETWORK_ERROR:
                case CIRCUIT_OPEN:
                    CRSFetchException failure =
                            endpointFailure(fullCode, currentFetcher, result);
                    if (isInterrupted(result)) {
                        throw failure;
                    }
                    endpointFailures.add(failure);
                    outcomes.add(currentFetcher.getBaseUrl() + ": "
                            + outcomeDescription(result));
                    break;

                default:
                    throw new IllegalStateException(
                            "Unknown fetch status " + result.getStatus()
                                    + " for " + fullCode);
            }
        }

        throw combinedFailure(fullCode, endpointFailures, outcomes);
    }

    private CRSFetchException endpointFailure(
            String fullCode,
            UrlCRSFetcher currentFetcher,
            UrlCRSFetcher.FetchResult result) {
        switch (result.getStatus()) {
            case HTTP_ERROR:
                return new CRSFetchException(
                        fullCode,
                        CRSFetchException.Reason.HTTP_ERROR,
                        "CRS endpoint " + name + " (" + currentFetcher.getBaseUrl()
                                + ") returned HTTP " + result.getHttpStatusCode()
                                + " for " + fullCode + " after "
                                + result.getAttemptCount() + " attempts",
                        result.getLastException());

            case NETWORK_ERROR:
                return new CRSFetchException(
                        fullCode,
                        CRSFetchException.Reason.NETWORK_ERROR,
                        "Failed to fetch CRS definition for " + fullCode
                                + " from " + name + " ("
                                + currentFetcher.getBaseUrl() + ") after "
                                + result.getAttemptCount() + " attempts",
                        result.getLastException());

            case CIRCUIT_OPEN:
                return new CRSFetchException(
                        fullCode,
                        CRSFetchException.Reason.CIRCUIT_OPEN,
                        "CRS endpoint " + name + " (" + currentFetcher.getBaseUrl()
                                + ") is temporarily unavailable; "
                                + "circuit breaker is open for " + fullCode,
                        result.getLastException());

            default:
                throw new IllegalArgumentException(
                        "Fetch result is not an endpoint failure: " + result.getStatus());
        }
    }

    private static boolean isInterrupted(UrlCRSFetcher.FetchResult result) {
        return Thread.currentThread().isInterrupted()
                && result.isNetworkError()
                && result.getLastException() instanceof InterruptedException;
    }

    private static String outcomeDescription(UrlCRSFetcher.FetchResult result) {
        switch (result.getStatus()) {
            case HTTP_ERROR:
                return "HTTP " + result.getHttpStatusCode();
            case NETWORK_ERROR:
                return "network error";
            case CIRCUIT_OPEN:
                return "circuit open";
            default:
                return result.getStatus().name();
        }
    }

    private static CRSFetchException combinedFailure(
            String fullCode,
            List<CRSFetchException> failures,
            List<String> outcomes) {
        if (failures.size() == 1 && outcomes.size() == 1) {
            return failures.get(0);
        }
        if (failures.isEmpty()) {
            throw new IllegalStateException(
                    "No endpoint failure recorded for " + fullCode);
        }

        CRSFetchException primaryFailure = failures.get(0);
        CRSFetchException combined = new CRSFetchException(
                fullCode,
                primaryFailure.getReason(),
                "Failed to resolve " + fullCode
                        + " from all configured CRS endpoints: "
                        + String.join("; ", outcomes),
                primaryFailure);
        for (int i = 1; i < failures.size(); i++) {
            combined.addSuppressed(failures.get(i));
        }
        return combined;
    }

    /**
     * Wrap the raw response body in a {@link CRSResult} of the configured format.
     */
    private CRSResult wrapResult(String body) {
        switch (format) {
            case PROJ4:   return CRSResult.proj4(body);
            case PROJJSON: return CRSResult.projJson(body);
            case WKT1:    return CRSResult.wkt1(body);
            case WKT2:    return CRSResult.wkt2(body);
            default:       return CRSResult.projJson(body);
        }
    }

    // ==================== Accessors ====================

    /** Get the primary fetcher. */
    public UrlCRSFetcher getFetcher() { return fetcher; }

    /**
     * Get all fetchers in resolution order. The first entry is the primary and
     * subsequent entries are fallbacks.
     *
     * @return an unmodifiable list containing the primary and all fallbacks
     */
    public List<UrlCRSFetcher> getFetchers() { return fetchers; }

    /** Get the expected response format. */
    public CRSResult.Format getFormat() { return format; }

    /** Get the set of authorities this provider handles, or {@code null} for all. */
    public Set<String> getAuthorities() { return authorities; }

    // ==================== Builder ====================

    /**
     * Create a new builder with the given provider name.
     *
     * @param name a unique provider name (used for registration and removal)
     * @return a new builder
     * @throws IllegalArgumentException if name is null or blank
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Fluent builder for {@link UrlCRSProvider}.
     *
     * <p>Required: {@link #baseUrl(String)}. Everything else has sensible defaults.</p>
     *
     * <p>Except for {@link #fallbackFetcher(UrlCRSFetcher)}, these methods configure
     * the primary fetcher only. Each fallback carries its own URL, headers,
     * reliability settings, cache, and circuit breaker.</p>
     */
    public static final class Builder {
        private final String name;
        private final UrlCRSFetcher.Builder fetcherBuilder = UrlCRSFetcher.builder();
        private final List<UrlCRSFetcher> fallbackFetchers = new ArrayList<>();
        private CRSResult.Format format = CRSResult.Format.PROJJSON;
        private Set<String> authorities; // null = all

        private Builder(String name) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("provider name must not be null or blank");
            }
            this.name = name;
        }

        /**
         * Set the base URL (required).
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>{@code "https://raw.githubusercontent.com/myorg/crs-defs/main"}</li>
         *   <li>{@code "https://my-bucket.s3.amazonaws.com/projjson"}</li>
         *   <li>{@code "https://crs.example.com/v1"}</li>
         * </ul>
         *
         * @param baseUrl the base URL
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            fetcherBuilder.baseUrl(baseUrl);
            return this;
        }

        /**
         * Set the path template appended to the base URL. Supports placeholders:
         * {@code {authority}}, {@code {AUTHORITY}}, {@code {code}}.
         *
         * <p>Default: {@code /{authority}/{code}.json}</p>
         *
         * @param pathTemplate the path template (must start with {@code /})
         * @return this builder
         */
        public Builder pathTemplate(String pathTemplate) {
            fetcherBuilder.pathTemplate(pathTemplate);
            return this;
        }

        /**
         * Set the expected response format. Default: {@link CRSResult.Format#PROJJSON}.
         *
         * @param format the expected CRS definition format
         * @return this builder
         */
        public Builder format(CRSResult.Format format) {
            if (format == null) {
                throw new IllegalArgumentException("format must not be null");
            }
            this.format = format;
            return this;
        }

        /**
         * Restrict this provider to specific authorities. If set, the provider returns
         * {@code null} immediately for any authority not in this set, avoiding unnecessary
         * HTTP requests.
         *
         * <p>If not called, the provider attempts to resolve <em>any</em> authority.</p>
         *
         * @param authorities one or more authority names (case-insensitive, stored lower-cased)
         * @return this builder
         */
        public Builder authorities(String... authorities) {
            if (authorities == null || authorities.length == 0) {
                this.authorities = null;
                return this;
            }
            this.authorities = new LinkedHashSet<>();
            for (String auth : authorities) {
                if (auth != null && !auth.trim().isEmpty()) {
                    this.authorities.add(auth.toLowerCase(Locale.ROOT));
                }
            }
            if (this.authorities.isEmpty()) {
                this.authorities = null;
            }
            return this;
        }

        /**
         * Set the connection timeout. Default: 10 seconds.
         *
         * @param seconds connection timeout in seconds
         * @return this builder
         */
        public Builder connectTimeout(int seconds) {
            fetcherBuilder.connectTimeout(seconds);
            return this;
        }

        /**
         * Set the read timeout. Default: 30 seconds.
         *
         * @param seconds read timeout in seconds
         * @return this builder
         */
        public Builder readTimeout(int seconds) {
            fetcherBuilder.readTimeout(seconds);
            return this;
        }

        /**
         * Set the maximum number of total attempts, including the initial request.
         * Default: 3.
         *
         * @param maxRetries maximum total attempts
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            fetcherBuilder.maxRetries(maxRetries);
            return this;
        }

        /**
         * Set the initial backoff delay for retries. Default: 500ms.
         *
         * @param ms initial backoff in milliseconds
         * @return this builder
         */
        public Builder initialBackoffMs(long ms) {
            fetcherBuilder.initialBackoffMs(ms);
            return this;
        }

        /**
         * Set the primary fetcher's overall deadline, including retries and backoff.
         * Each fallback has its own deadline. Set to zero to rely only on
         * per-attempt timeouts. Default: 0.
         *
         * @param seconds overall timeout in seconds, or zero to disable it
         * @return this builder
         */
        public Builder totalTimeout(int seconds) {
            fetcherBuilder.totalTimeout(seconds);
            return this;
        }

        /**
         * Set the number of consecutive failed lookups that opens the endpoint circuit.
         * Set to zero to disable circuit breaking. Default: 0.
         *
         * @param failures failure threshold, or zero to disable circuit breaking
         * @return this builder
         */
        public Builder circuitBreakerFailureThreshold(int failures) {
            fetcherBuilder.circuitBreakerFailureThreshold(failures);
            return this;
        }

        /**
         * Set how long an open circuit waits before permitting one probe request.
         *
         * @param timeout positive reset timeout
         * @return this builder
         */
        public Builder circuitBreakerResetTimeout(Duration timeout) {
            fetcherBuilder.circuitBreakerResetTimeout(timeout);
            return this;
        }

        /**
         * Set whether the primary fetcher caches HTTP 404 responses. Default:
         * {@code true}.
         *
         * @param enabled whether to cache HTTP 404 responses
         * @return this builder
         */
        public Builder cacheNotFound(boolean enabled) {
            fetcherBuilder.cacheNotFound(enabled);
            return this;
        }

        /**
         * Add an independently configured fallback fetcher. Fallbacks are tried
         * in insertion order after HTTP errors, network errors, or an open primary
         * circuit. A primary HTTP 404 remains definitive.
         *
         * <p>Configure each fallback separately so endpoint-specific headers,
         * timeouts, caches, and circuit breakers remain isolated. Every fallback
         * must serve the response format configured on this provider.</p>
         *
         * @param fallbackFetcher independently configured fallback fetcher
         * @return this builder
         */
        public Builder fallbackFetcher(UrlCRSFetcher fallbackFetcher) {
            if (fallbackFetcher == null) {
                throw new IllegalArgumentException(
                        "fallbackFetcher must not be null");
            }
            fallbackFetchers.add(fallbackFetcher);
            return this;
        }

        /**
         * Add a custom HTTP header sent with every request.
         *
         * <p>Example: {@code .header("Authorization", "token YOUR_TOKEN_HERE")}</p>
         *
         * @param headerName  header name
         * @param headerValue header value
         * @return this builder
         */
        public Builder header(String headerName, String headerValue) {
            fetcherBuilder.header(headerName, headerValue);
            return this;
        }

        /**
         * Build the {@link UrlCRSProvider}.
         *
         * @return a new provider instance
         * @throws IllegalStateException if baseUrl has not been set
         */
        public UrlCRSProvider build() {
            return new UrlCRSProvider(this);
        }
    }
}
