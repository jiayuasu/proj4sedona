package org.proj4sedona.defs;

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
 * String projJson = SpatialReferenceFetcher.fetchProjJson("epsg", "2154");
 * 
 * // Configure custom base URL
 * SpatialReferenceFetcher.setBaseUrl("https://custom.server.org/");
 * </pre>
 * 
 * <p>The fetcher maintains a negative cache to avoid repeated lookups for
 * codes that don't exist on the server.</p>
 */
public final class SpatialReferenceFetcher {

    /** Default spatialreference.org base URL */
    public static final String DEFAULT_BASE_URL = "https://spatialreference.org/";

    /** Connection timeout in seconds */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** Read timeout in seconds */
    private static final int READ_TIMEOUT_SECONDS = 30;

    /** Maximum number of retry attempts */
    private static final int MAX_RETRIES = 3;

    /** Initial backoff delay in milliseconds */
    private static final long INITIAL_BACKOFF_MS = 500;

    /** Backoff multiplier for exponential growth */
    private static final double BACKOFF_MULTIPLIER = 2.0;

    /** Maximum backoff delay in milliseconds */
    private static final long MAX_BACKOFF_MS = 5000;

    /** Current base URL (can be customized) */
    private static String baseUrl = DEFAULT_BASE_URL;

    /** Shared HTTP client (lazily initialized) */
    private static volatile HttpClient httpClient;

    /** Negative cache: track codes that don't exist to avoid repeated lookups */
    private static final Set<String> notFoundCache = ConcurrentHashMap.newKeySet();

    private SpatialReferenceFetcher() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the HTTP client, creating it lazily with double-checked locking.
     */
    private static HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (SpatialReferenceFetcher.class) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
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
     * @return The PROJJSON string, or null if not found or fetch failed
     */
    public static String fetchProjJson(String authName, String code) {
        String cacheKey = authName.toLowerCase() + ":" + code;

        // Check negative cache first to avoid repeated lookups
        if (notFoundCache.contains(cacheKey)) {
            return null;
        }

        String url = buildUrl(authName, code);
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                // Exponential backoff with jitter before retry
                long backoffMs = calculateBackoff(attempt);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            try {
                HttpResponse<String> response = executeRequest(url);
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    return response.body();
                } else if (statusCode == 404) {
                    // Not found - cache this to avoid future lookups
                    notFoundCache.add(cacheKey);
                    return null;
                } else if (isRetryableStatusCode(statusCode)) {
                    // 5xx errors, 429 (rate limit), 408 (timeout) - retry
                    lastException = new IOException("HTTP " + statusCode + " from " + url);
                    continue;
                } else {
                    // Other 4xx errors - don't retry, they won't succeed
                    return null;
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
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        // All retries exhausted - log if needed (currently silent)
        // Could add logging here: "Failed to fetch " + url + " after " + MAX_RETRIES + " attempts"
        return null;
    }

    /**
     * Execute an HTTP GET request.
     */
    private static HttpResponse<String> executeRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
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
        long backoff = (long) (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
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
     * Reset all state (for testing).
     * Clears the negative cache and resets the base URL.
     */
    public static void reset() {
        resetBaseUrl();
        clearNotFoundCache();
    }
}
