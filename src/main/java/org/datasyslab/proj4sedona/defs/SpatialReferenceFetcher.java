package org.datasyslab.proj4sedona.defs;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches EPSG/CRS definitions from spatialreference.org.
 * 
 * <p>This class provides automatic downloading of PROJJSON definitions for
 * coordinate reference systems. It includes retry logic with exponential
 * backoff for handling transient failures.</p>
 * 
 * <p>Usage:</p>
 * <pre>
 * // Fetch PROJJSON for EPSG:2154
 * FetchResult result = SpatialReferenceFetcher.fetchProjJson("epsg", "2154");
 * if (result.isSuccess()) {
 *     String projJson = result.getProjJson();
 * }
 * 
 * // Configure custom base URL
 * SpatialReferenceFetcher.setBaseUrl("https://custom.server.org/");
 * </pre>
 * 
 * <p>The fetcher maintains a negative cache to avoid repeated lookups for
 * codes that don't exist on the server.</p>
 */
public final class SpatialReferenceFetcher {

    /**
     * Result of a fetch operation.
     */
    public static final class FetchResult {
        /**
         * Status of the fetch operation.
         */
        public enum Status {
            /** Successfully fetched the PROJJSON */
            SUCCESS,
            /** The CRS code was not found (HTTP 404) */
            NOT_FOUND,
            /** A network error occurred after exhausting retries */
            NETWORK_ERROR
        }

        private final Status status;
        private final String projJson;
        private final Exception lastException;
        private final int attemptCount;

        private FetchResult(Status status, String projJson, Exception lastException, int attemptCount) {
            this.status = status;
            this.projJson = projJson;
            this.lastException = lastException;
            this.attemptCount = attemptCount;
        }

        /**
         * Create a successful result.
         */
        public static FetchResult success(String projJson, int attemptCount) {
            return new FetchResult(Status.SUCCESS, projJson, null, attemptCount);
        }

        /**
         * Create a not-found result.
         */
        public static FetchResult notFound(int attemptCount) {
            return new FetchResult(Status.NOT_FOUND, null, null, attemptCount);
        }

        /**
         * Create a network error result.
         */
        public static FetchResult networkError(Exception lastException, int attemptCount) {
            return new FetchResult(Status.NETWORK_ERROR, null, lastException, attemptCount);
        }

        /**
         * Get the status of this result.
         */
        public Status getStatus() {
            return status;
        }

        /**
         * Check if the fetch was successful.
         */
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        /**
         * Check if the CRS code was not found.
         */
        public boolean isNotFound() {
            return status == Status.NOT_FOUND;
        }

        /**
         * Check if a network error occurred.
         */
        public boolean isNetworkError() {
            return status == Status.NETWORK_ERROR;
        }

        /**
         * Get the fetched PROJJSON (only valid if status is SUCCESS).
         */
        public String getProjJson() {
            return projJson;
        }

        /**
         * Get the last exception that occurred (only valid if status is NETWORK_ERROR).
         */
        public Exception getLastException() {
            return lastException;
        }

        /**
         * Get the number of attempts made before this result.
         */
        public int getAttemptCount() {
            return attemptCount;
        }
    }

    /** Default spatialreference.org base URL */
    public static final String DEFAULT_BASE_URL = "https://spatialreference.org/";

    /** Default connection timeout in seconds */
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

    /** Default read timeout in seconds */
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 30;

    /** Default maximum number of retry attempts */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Default initial backoff delay in milliseconds */
    public static final long DEFAULT_INITIAL_BACKOFF_MS = 500;

    /** Backoff multiplier for exponential growth */
    private static final double BACKOFF_MULTIPLIER = 2.0;

    /** Maximum backoff delay in milliseconds */
    private static final long MAX_BACKOFF_MS = 5000;

    /** Current base URL (can be customized) */
    private static String baseUrl = DEFAULT_BASE_URL;

    /** Connection timeout in seconds */
    private static int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;

    /** Read timeout in seconds */
    private static int readTimeoutSeconds = DEFAULT_READ_TIMEOUT_SECONDS;

    /** Maximum number of retry attempts */
    private static int maxRetries = DEFAULT_MAX_RETRIES;

    /** Initial backoff delay in milliseconds */
    private static long initialBackoffMs = DEFAULT_INITIAL_BACKOFF_MS;

    /** Shared HTTP client (lazily initialized, recreated when timeouts change) */
    private static volatile HttpClient httpClient;

    /** Flag to track if client needs recreation due to timeout changes */
    private static volatile boolean clientNeedsRecreation = false;

    /** Negative cache: track codes that don't exist to avoid repeated lookups */
    private static final Set<String> notFoundCache = ConcurrentHashMap.newKeySet();

    /** Counter for tracking total fetch attempts (for testing) */
    private static final AtomicInteger totalAttemptCounter = new AtomicInteger(0);

    private SpatialReferenceFetcher() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the HTTP client, creating it lazily with double-checked locking.
     */
    private static HttpClient getHttpClient() {
        if (httpClient == null || clientNeedsRecreation) {
            synchronized (SpatialReferenceFetcher.class) {
                if (httpClient == null || clientNeedsRecreation) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    clientNeedsRecreation = false;
                }
            }
        }
        return httpClient;
    }

    /**
     * Fetch PROJJSON for a coordinate reference system.
     * 
     * <p>This method includes retry logic with exponential backoff for handling
     * transient failures like network issues or server errors.</p>
     * 
     * @param authName The authority name (e.g., "epsg", "esri")
     * @param code The CRS code (e.g., "4326", "2154")
     * @return A FetchResult indicating success, not-found, or network error
     */
    public static FetchResult fetchProjJson(String authName, String code) {
        String cacheKey = authName.toLowerCase() + ":" + code;

        // Check negative cache first to avoid repeated lookups
        if (notFoundCache.contains(cacheKey)) {
            return FetchResult.notFound(0);
        }

        String url = buildUrl(authName, code);
        Exception lastException = null;
        int attemptCount = 0;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            attemptCount = attempt + 1;
            totalAttemptCounter.incrementAndGet();

            if (attempt > 0) {
                // Exponential backoff with jitter before retry
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
                    // Not found - cache this to avoid future lookups
                    notFoundCache.add(cacheKey);
                    return FetchResult.notFound(attemptCount);
                } else if (isRetryableStatusCode(statusCode)) {
                    // 5xx errors, 429 (rate limit), 408 (timeout) - retry
                    lastException = new IOException("HTTP " + statusCode + " from " + url);
                    continue;
                } else {
                    // Other 4xx errors - don't retry, treat as not found
                    return FetchResult.notFound(attemptCount);
                }
            } catch (ConnectException e) {
                // Connection failed - retry
                lastException = e;
            } catch (SocketTimeoutException e) {
                // Read timeout - retry
                lastException = e;
            } catch (IOException e) {
                // Other IO errors - check if retryable
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

        // All retries exhausted
        return FetchResult.networkError(lastException, attemptCount);
    }

    /**
     * Execute an HTTP GET request.
     */
    private static HttpResponse<String> executeRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .header("Accept", "application/json")
                .GET()
                .build();

        return getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Build the full URL for a CRS definition.
     * 
     * <p>URL pattern: https://spatialreference.org/ref/{auth_name}/{code}/projjson.json</p>
     */
    private static String buildUrl(String authName, String code) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return base + "ref/" + authName.toLowerCase() + "/" + code + "/projjson.json";
    }

    /**
     * Calculate backoff delay with exponential growth and jitter.
     * 
     * @param attempt The current attempt number (1-based for backoff calculation)
     * @return The delay in milliseconds
     */
    private static long calculateBackoff(int attempt) {
        // Exponential backoff: 500ms, 1000ms, 2000ms, ...
        long backoff = (long) (initialBackoffMs * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
        backoff = Math.min(backoff, MAX_BACKOFF_MS);
        // Add jitter (0-25% of backoff) to prevent thundering herd
        backoff += (long) (backoff * Math.random() * 0.25);
        return backoff;
    }

    /**
     * Check if an HTTP status code indicates a retryable error.
     */
    private static boolean isRetryableStatusCode(int statusCode) {
        // 5xx = server errors (should retry)
        // 429 = rate limited (should retry after backoff)
        // 408 = request timeout (should retry)
        return statusCode >= 500 || statusCode == 429 || statusCode == 408;
    }

    /**
     * Check if an exception indicates a retryable error.
     */
    private static boolean isRetryableException(Exception e) {
        if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
            return true;
        }
        // Check for timeout-related messages in other exceptions
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("timed out") 
                || lowerMessage.contains("timeout")
                || lowerMessage.contains("connection reset");
        }
        return false;
    }

    // ========== Configuration Methods ==========

    /**
     * Set the base URL for fetching CRS definitions.
     * 
     * @param url The base URL (e.g., "https://spatialreference.org/")
     */
    public static void setBaseUrl(String url) {
        baseUrl = url;
    }

    /**
     * Get the current base URL.
     * 
     * @return The base URL
     */
    public static String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Reset the base URL to the default (spatialreference.org).
     */
    public static void resetBaseUrl() {
        baseUrl = DEFAULT_BASE_URL;
    }

    /**
     * Set the connection timeout.
     * 
     * @param seconds Connection timeout in seconds
     */
    public static void setConnectTimeout(int seconds) {
        connectTimeoutSeconds = seconds;
        clientNeedsRecreation = true;
    }

    /**
     * Get the current connection timeout.
     * 
     * @return Connection timeout in seconds
     */
    public static int getConnectTimeout() {
        return connectTimeoutSeconds;
    }

    /**
     * Set the read timeout.
     * 
     * @param seconds Read timeout in seconds
     */
    public static void setReadTimeout(int seconds) {
        readTimeoutSeconds = seconds;
    }

    /**
     * Get the current read timeout.
     * 
     * @return Read timeout in seconds
     */
    public static int getReadTimeout() {
        return readTimeoutSeconds;
    }

    /**
     * Set the maximum number of retry attempts.
     * 
     * @param retries Maximum retry attempts (minimum 1)
     */
    public static void setMaxRetries(int retries) {
        maxRetries = Math.max(1, retries);
    }

    /**
     * Get the current maximum retry attempts.
     * 
     * @return Maximum retry attempts
     */
    public static int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Set the initial backoff delay for retries.
     * 
     * @param ms Initial backoff in milliseconds
     */
    public static void setInitialBackoffMs(long ms) {
        initialBackoffMs = Math.max(0, ms);
    }

    /**
     * Get the current initial backoff delay.
     * 
     * @return Initial backoff in milliseconds
     */
    public static long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    /**
     * Clear the negative cache (codes known not to exist).
     * 
     * <p>This is useful if you want to retry lookups for codes that
     * previously returned 404.</p>
     */
    public static void clearNotFoundCache() {
        notFoundCache.clear();
    }

    /**
     * Check if a code is in the negative cache.
     * 
     * @param authName The authority name
     * @param code The CRS code
     * @return true if the code is known not to exist
     */
    public static boolean isInNotFoundCache(String authName, String code) {
        return notFoundCache.contains(authName.toLowerCase() + ":" + code);
    }

    /**
     * Get the size of the negative cache.
     * 
     * @return The number of codes in the negative cache
     */
    public static int getNotFoundCacheSize() {
        return notFoundCache.size();
    }

    /**
     * Get the total number of fetch attempts made (for testing).
     * 
     * @return Total attempt count
     */
    public static int getTotalAttemptCount() {
        return totalAttemptCounter.get();
    }

    /**
     * Reset the total attempt counter (for testing).
     */
    public static void resetAttemptCounter() {
        totalAttemptCounter.set(0);
    }

    /**
     * Reset all state (for testing).
     * Clears the negative cache, resets the base URL, and resets all configuration to defaults.
     */
    public static void reset() {
        resetBaseUrl();
        clearNotFoundCache();
        resetAttemptCounter();
        connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;
        readTimeoutSeconds = DEFAULT_READ_TIMEOUT_SECONDS;
        maxRetries = DEFAULT_MAX_RETRIES;
        initialBackoffMs = DEFAULT_INITIAL_BACKOFF_MS;
        clientNeedsRecreation = true;
    }
}
