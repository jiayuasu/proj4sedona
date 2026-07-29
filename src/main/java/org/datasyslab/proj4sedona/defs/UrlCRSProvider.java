package org.datasyslab.proj4sedona.defs;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
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
     * @deprecated the default provider uses {@link #SPATIAL_REFERENCE_CDN_BASE_URL}
     */
    @Deprecated
    public static final String SPATIAL_REFERENCE_BASE_URL = "https://spatialreference.org";

    /** Generated spatialreference.org data commit based on PROJ 9.8.1. */
    public static final String SPATIAL_REFERENCE_SNAPSHOT_COMMIT =
            "c43e4e72634af65fcf684def42ddc2dcfd834938";

    /** Default CDN URL for the immutable OSGeo spatialreference.org data snapshot. */
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

    /** Path template within the spatialreference.org snapshot. */
    static final String SPATIAL_REFERENCE_PATH = "/ref/{authority}/{code}/projjson.json";

    // ==================== spatialreference.org factories ====================

    /**
     * Create a {@link UrlCRSProvider} pre-configured for
     * the immutable OSGeo
     * <a href="https://github.com/OSGeo/spatialreference.org">spatialreference.org</a>
     * data snapshot.
     *
     * <p>This is the default remote CRS provider registered by
     * {@link Defs#globals()} at priority 101. For a custom mirror or different
     * reliability settings, use {@link #builder(String)} with
     * {@link #SPATIAL_REFERENCE_NAME} as the name and
     * {@code /ref/{authority}/{code}/projjson.json} as the path template.</p>
     *
     * @return a provider that fetches PROJJSON from a pinned OSGeo snapshot
     */
    public static UrlCRSProvider spatialReference() {
        return builder(SPATIAL_REFERENCE_NAME)
                .baseUrl(SPATIAL_REFERENCE_CDN_BASE_URL)
                .pathTemplate(SPATIAL_REFERENCE_PATH)
                .format(CRSResult.Format.PROJJSON)
                .connectTimeout(SPATIAL_REFERENCE_CONNECT_TIMEOUT_SECONDS)
                .readTimeout(SPATIAL_REFERENCE_READ_TIMEOUT_SECONDS)
                .maxRetries(SPATIAL_REFERENCE_MAX_ATTEMPTS)
                .initialBackoffMs(SPATIAL_REFERENCE_INITIAL_BACKOFF_MS)
                .totalTimeout(SPATIAL_REFERENCE_TOTAL_TIMEOUT_SECONDS)
                .circuitBreakerFailureThreshold(
                        SPATIAL_REFERENCE_CIRCUIT_BREAKER_FAILURE_THRESHOLD)
                .circuitBreakerResetTimeout(
                        SPATIAL_REFERENCE_CIRCUIT_BREAKER_RESET_TIMEOUT)
                .header("Accept", "application/json")
                .build();
    }

    // ==================== Instance fields ====================

    private final String name;
    private final UrlCRSFetcher fetcher;
    private final CRSResult.Format format;
    private final Set<String> authorities; // null = accept all

    private UrlCRSProvider(Builder builder) {
        this.name = builder.name;
        this.format = builder.format;
        this.authorities = builder.authorities == null ? null
                : Collections.unmodifiableSet(new LinkedHashSet<>(builder.authorities));
        this.fetcher = builder.fetcherBuilder.build();
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
     * Otherwise, a URL is constructed from the base URL and path template, and an
     * HTTP GET is issued.</p>
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

        UrlCRSFetcher.FetchResult result = fetcher.fetch(authority, code);

        switch (result.getStatus()) {
            case SUCCESS:
                return wrapResult(result.getBody());

            case NOT_FOUND:
                return null;

            case HTTP_ERROR:
                throw new CRSFetchException(fullCode, CRSFetchException.Reason.HTTP_ERROR,
                        "CRS endpoint " + name + " (" + fetcher.getBaseUrl()
                                + ") returned HTTP "
                                + result.getHttpStatusCode() + " for " + fullCode
                                + " after " + result.getAttemptCount() + " attempts",
                        result.getLastException());

            case NETWORK_ERROR:
                throw new CRSFetchException(fullCode, CRSFetchException.Reason.NETWORK_ERROR,
                        "Failed to fetch CRS definition for " + fullCode +
                                " from " + name + " (" + fetcher.getBaseUrl() + ") after "
                                + result.getAttemptCount() + " attempts",
                        result.getLastException());

            case CIRCUIT_OPEN:
                throw new CRSFetchException(fullCode, CRSFetchException.Reason.CIRCUIT_OPEN,
                        "CRS endpoint " + name + " (" + fetcher.getBaseUrl()
                                + ") is temporarily unavailable; "
                                + "circuit breaker is open for " + fullCode,
                        result.getLastException());

            default:
                throw new IllegalStateException(
                        "Unknown fetch status " + result.getStatus() + " for " + fullCode);
        }
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

    /** Get the underlying fetcher (useful for clearing the negative cache, etc.). */
    public UrlCRSFetcher getFetcher() { return fetcher; }

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
     */
    public static final class Builder {
        private final String name;
        private final UrlCRSFetcher.Builder fetcherBuilder = UrlCRSFetcher.builder();
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
         * Set the overall deadline for a logical lookup, including retries and backoff.
         * Set to zero to rely only on per-attempt timeouts. Default: 0.
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
