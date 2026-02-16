package org.datasyslab.proj4sedona.defs;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP fetch engine for retrieving CRS definition files (PROJJSON, WKT, PROJ.4)
 * from any HTTPS endpoint with a predictable URL layout.
 *
 * <p>This class is used by {@link UrlCRSProvider} and handles:</p>
 * <ul>
 *   <li>URL construction from a configurable base URL and path template</li>
 *   <li>Retry with exponential backoff for transient failures</li>
 *   <li>Negative cache to skip known 404s</li>
 *   <li>Custom HTTP headers (e.g., for private GitHub repos or pre-signed S3)</li>
 * </ul>
 *
 * <p>Instances are created via the {@link Builder} and are thread-safe.</p>
 *
 * <h3>URL Template Placeholders</h3>
 * <table>
 *   <caption>Supported URL template placeholders</caption>
 *   <tr><td>{@code {authority}}</td><td>authority lower-cased (e.g., {@code epsg})</td></tr>
 *   <tr><td>{@code {AUTHORITY}}</td><td>authority upper-cased (e.g., {@code EPSG})</td></tr>
 *   <tr><td>{@code {code}}</td><td>the CRS code (e.g., {@code 4326})</td></tr>
 * </table>
 *
 * <h3>Example</h3>
 * <pre>
 * UrlCRSFetcher fetcher = UrlCRSFetcher.builder()
 *     .baseUrl("https://raw.githubusercontent.com/myorg/crs-defs/main")
 *     .pathTemplate("/{authority}/{code}.json")
 *     .build();
 *
 * UrlCRSFetcher.FetchResult result = fetcher.fetch("epsg", "4326");
 * if (result.isSuccess()) {
 *     String json = result.getBody();
 * }
 * </pre>
 *
 * @see UrlCRSProvider
 */
public final class UrlCRSFetcher {

    // ==================== FetchResult ====================

    /**
     * Result of a fetch operation.
     */
    public static final class FetchResult {
        /**
         * Status of the fetch operation.
         */
        public enum Status {
            /** Successfully fetched the CRS definition */
            SUCCESS,
            /** The CRS code was not found (HTTP 404) */
            NOT_FOUND,
            /** A network error occurred after exhausting retries */
            NETWORK_ERROR
        }

        private final Status status;
        private final String body;
        private final Exception lastException;
        private final int attemptCount;

        private FetchResult(Status status, String body, Exception lastException, int attemptCount) {
            this.status = status;
            this.body = body;
            this.lastException = lastException;
            this.attemptCount = attemptCount;
        }

        /** Create a successful result. */
        public static FetchResult success(String body, int attemptCount) {
            return new FetchResult(Status.SUCCESS, body, null, attemptCount);
        }

        /** Create a not-found result. */
        public static FetchResult notFound(int attemptCount) {
            return new FetchResult(Status.NOT_FOUND, null, null, attemptCount);
        }

        /** Create a network error result. */
        public static FetchResult networkError(Exception lastException, int attemptCount) {
            return new FetchResult(Status.NETWORK_ERROR, null, lastException, attemptCount);
        }

        /** Get the status of this result. */
        public Status getStatus() { return status; }

        /** Check if the fetch was successful. */
        public boolean isSuccess() { return status == Status.SUCCESS; }

        /** Check if the CRS code was not found. */
        public boolean isNotFound() { return status == Status.NOT_FOUND; }

        /** Check if a network error occurred. */
        public boolean isNetworkError() { return status == Status.NETWORK_ERROR; }

        /** Get the response body (only valid if status is SUCCESS). */
        public String getBody() { return body; }

        /** Get the last exception (only valid if status is NETWORK_ERROR). */
        public Exception getLastException() { return lastException; }

        /** Get the number of attempts made. */
        public int getAttemptCount() { return attemptCount; }
    }

    // ==================== Defaults ====================

    /** Default path template appended to the base URL. */
    public static final String DEFAULT_PATH_TEMPLATE = "/{authority}/{code}.json";

    /** Default connection timeout in seconds. */
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

    /** Default read timeout in seconds. */
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 30;

    /** Default maximum number of retry attempts. */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Default initial backoff delay in milliseconds. */
    public static final long DEFAULT_INITIAL_BACKOFF_MS = 500;

    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 5000;

    // ==================== Instance Fields ====================

    private final String baseUrl;
    private final String pathTemplate;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    /** Negative cache: track codes that are known 404s. */
    private final Set<String> notFoundCache = ConcurrentHashMap.newKeySet();

    // ==================== Constructor (via Builder) ====================

    private UrlCRSFetcher(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.pathTemplate = builder.pathTemplate;
        this.connectTimeoutSeconds = builder.connectTimeoutSeconds;
        this.readTimeoutSeconds = builder.readTimeoutSeconds;
        this.maxRetries = builder.maxRetries;
        this.initialBackoffMs = builder.initialBackoffMs;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ==================== Public API ====================

    /**
     * Create a new builder.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fetch a CRS definition for the given authority and code.
     *
     * @param authority the authority (e.g., {@code "epsg"}), used as-is for lower-case placeholder
     * @param code      the CRS code (e.g., {@code "4326"})
     * @return a {@link FetchResult}
     */
    public FetchResult fetch(String authority, String code) {
        String cacheKey = authority.toLowerCase(Locale.ROOT) + ":" + code;

        if (notFoundCache.contains(cacheKey)) {
            return FetchResult.notFound(0);
        }

        String url = buildUrl(authority, code);
        Exception lastException = null;
        int attemptCount = 0;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            attemptCount = attempt + 1;

            if (attempt > 0) {
                long backoffMs = calculateBackoff(attempt);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return FetchResult.networkError(e, attemptCount);
                }
            }

            try {
                HttpResponse<String> response = executeRequest(url);
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    return FetchResult.success(response.body(), attemptCount);
                } else if (statusCode == 404) {
                    notFoundCache.add(cacheKey);
                    return FetchResult.notFound(attemptCount);
                } else if (isRetryableStatusCode(statusCode)) {
                    lastException = new IOException("HTTP " + statusCode + " from " + url);
                    continue;
                } else {
                    // Other 4xx — treat as not found
                    return FetchResult.notFound(attemptCount);
                }
            } catch (ConnectException | SocketTimeoutException e) {
                lastException = e;
            } catch (IOException e) {
                if (isRetryableException(e)) {
                    lastException = e;
                } else {
                    return FetchResult.networkError(e, attemptCount);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FetchResult.networkError(e, attemptCount);
            }
        }

        return FetchResult.networkError(lastException, attemptCount);
    }

    // ==================== URL Building ====================

    /**
     * Build the full URL by expanding template placeholders.
     *
     * <p>Supported placeholders:</p>
     * <ul>
     *   <li>{@code {authority}} — lower-cased authority</li>
     *   <li>{@code {AUTHORITY}} — upper-cased authority</li>
     *   <li>{@code {code}} — the CRS code</li>
     * </ul>
     */
    String buildUrl(String authority, String code) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String expanded = pathTemplate
                .replace("{authority}", authority.toLowerCase(Locale.ROOT))
                .replace("{AUTHORITY}", authority.toUpperCase(Locale.ROOT))
                .replace("{code}", code);
        return base + expanded;
    }

    // ==================== HTTP Execution ====================

    private HttpResponse<String> executeRequest(String url) throws IOException, InterruptedException {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .GET();

        // Apply custom headers
        for (Map.Entry<String, String> h : headers.entrySet()) {
            reqBuilder.header(h.getKey(), h.getValue());
        }

        return httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ==================== Retry Helpers ====================

    private long calculateBackoff(int attempt) {
        long backoff = (long) (initialBackoffMs * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
        backoff = Math.min(backoff, MAX_BACKOFF_MS);
        backoff += (long) (backoff * Math.random() * 0.25);
        return backoff;
    }

    private static boolean isRetryableStatusCode(int statusCode) {
        return statusCode >= 500 || statusCode == 429 || statusCode == 408;
    }

    private static boolean isRetryableException(Exception e) {
        if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
            return true;
        }
        String message = e.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            return lower.contains("timed out")
                    || lower.contains("timeout")
                    || lower.contains("connection reset");
        }
        return false;
    }

    // ==================== Accessors (for testing / introspection) ====================

    /** Get the configured base URL. */
    public String getBaseUrl() { return baseUrl; }

    /** Get the configured path template. */
    public String getPathTemplate() { return pathTemplate; }

    /** Get the max retries. */
    public int getMaxRetries() { return maxRetries; }

    /** Get the connect timeout in seconds. */
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }

    /** Get the read timeout in seconds. */
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }

    /** Get the initial backoff in milliseconds. */
    public long getInitialBackoffMs() { return initialBackoffMs; }

    /** Get the unmodifiable map of custom headers. */
    public Map<String, String> getHeaders() { return headers; }

    /** Check if a code is in the negative cache. */
    public boolean isInNotFoundCache(String authority, String code) {
        return notFoundCache.contains(authority.toLowerCase(Locale.ROOT) + ":" + code);
    }

    /** Get the size of the negative cache. */
    public int getNotFoundCacheSize() { return notFoundCache.size(); }

    /** Clear the negative cache. */
    public void clearNotFoundCache() { notFoundCache.clear(); }

    // ==================== Builder ====================

    /**
     * Builder for {@link UrlCRSFetcher}.
     *
     * <p>The only required field is {@link #baseUrl(String)}. All others have sensible defaults.</p>
     */
    public static final class Builder {
        private String baseUrl;
        private String pathTemplate = DEFAULT_PATH_TEMPLATE;
        private int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;
        private int readTimeoutSeconds = DEFAULT_READ_TIMEOUT_SECONDS;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long initialBackoffMs = DEFAULT_INITIAL_BACKOFF_MS;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Set the base URL (required). Must be an {@code https://} or {@code http://} URL.
         * A trailing slash is stripped automatically.
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>{@code "https://raw.githubusercontent.com/myorg/crs-defs/main"}</li>
         *   <li>{@code "https://my-bucket.s3.amazonaws.com/projjson"}</li>
         * </ul>
         *
         * @param baseUrl the base URL
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl must not be null or blank");
            }
            String normalized = baseUrl.trim();
            java.net.URI uri;
            try {
                uri = java.net.URI.create(normalized);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("baseUrl must be a valid http(s) URL", e);
            }
            String scheme = uri.getScheme();
            if (scheme == null) {
                throw new IllegalArgumentException("baseUrl must include a scheme (http or https)");
            }
            String lowerScheme = scheme.toLowerCase(java.util.Locale.ROOT);
            if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
                throw new IllegalArgumentException("baseUrl must use http or https scheme");
            }
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            this.baseUrl = normalized;
            return this;
        }

        /**
         * Set the path template appended to the base URL. Supports these placeholders:
         * <ul>
         *   <li>{@code {authority}} — lower-cased authority (e.g., {@code epsg})</li>
         *   <li>{@code {AUTHORITY}} — upper-cased authority (e.g., {@code EPSG})</li>
         *   <li>{@code {code}} — the CRS code (e.g., {@code 4326})</li>
         * </ul>
         *
         * <p>Default: {@code /{authority}/{code}.json}</p>
         *
         * @param pathTemplate the path template (must start with {@code /})
         * @return this builder
         */
        public Builder pathTemplate(String pathTemplate) {
            if (pathTemplate == null || pathTemplate.trim().isEmpty()) {
                throw new IllegalArgumentException("pathTemplate must not be null or blank");
            }
            if (!pathTemplate.startsWith("/")) {
                throw new IllegalArgumentException("pathTemplate must start with '/'");
            }
            this.pathTemplate = pathTemplate;
            return this;
        }

        /**
         * Set the connection timeout. Default: 10 seconds.
         *
         * @param seconds connection timeout in seconds (minimum 1)
         * @return this builder
         */
        public Builder connectTimeout(int seconds) {
            this.connectTimeoutSeconds = Math.max(1, seconds);
            return this;
        }

        /**
         * Set the read timeout. Default: 30 seconds.
         *
         * @param seconds read timeout in seconds (minimum 1)
         * @return this builder
         */
        public Builder readTimeout(int seconds) {
            this.readTimeoutSeconds = Math.max(1, seconds);
            return this;
        }

        /**
         * Set the maximum number of retry attempts. Default: 3.
         *
         * @param maxRetries maximum retries (minimum 1)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Math.max(1, maxRetries);
            return this;
        }

        /**
         * Set the initial backoff delay for retries. Default: 500ms.
         *
         * @param ms initial backoff in milliseconds
         * @return this builder
         */
        public Builder initialBackoffMs(long ms) {
            this.initialBackoffMs = Math.max(0, ms);
            return this;
        }

        /**
         * Add a custom HTTP header sent with every request.
         *
         * <p>Examples:</p>
         * <ul>
         *   <li>{@code .header("Authorization", "token YOUR_TOKEN_HERE")} — private GitHub repo</li>
         *   <li>{@code .header("Accept", "application/json")}</li>
         * </ul>
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        public Builder header(String name, String value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("header name must not be null or blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("header value must not be null");
            }
            headers.put(name, value);
            return this;
        }

        /**
         * Build the {@link UrlCRSFetcher}.
         *
         * @return a new fetcher instance
         * @throws IllegalStateException if baseUrl has not been set
         */
        public UrlCRSFetcher build() {
            if (baseUrl == null) {
                throw new IllegalStateException("baseUrl is required");
            }
            return new UrlCRSFetcher(this);
        }
    }
}
