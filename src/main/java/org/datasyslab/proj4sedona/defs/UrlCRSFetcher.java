package org.datasyslab.proj4sedona.defs;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HTTP fetch engine for retrieving CRS definition files (PROJJSON, WKT, PROJ.4)
 * from any HTTPS endpoint with a predictable URL layout.
 *
 * <p>This class is used by {@link UrlCRSProvider} and handles:</p>
 * <ul>
 *   <li>URL construction from a configurable base URL and path template</li>
 *   <li>Bounded retry with exponential backoff for transient failures</li>
 *   <li>Negative cache to skip known 404s</li>
 *   <li>Single-flight request coalescing for concurrent lookups of the same CRS</li>
 *   <li>A host-level circuit breaker to avoid repeatedly calling an unhealthy endpoint</li>
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

    private enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private static final class CircuitPermit {
        private final long generation;
        private final boolean halfOpenProbe;

        private CircuitPermit(long generation, boolean halfOpenProbe) {
            this.generation = generation;
            this.halfOpenProbe = halfOpenProbe;
        }
    }

    private static final class CancellableBodyHandler<T>
            implements HttpResponse.BodyHandler<T> {
        private final HttpResponse.BodyHandler<T> delegate;
        private volatile CancellableBodySubscriber<T> subscriber;
        private volatile boolean cancelled;

        private CancellableBodyHandler(HttpResponse.BodyHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpResponse.BodySubscriber<T> apply(
                HttpResponse.ResponseInfo responseInfo) {
            CancellableBodySubscriber<T> wrapped =
                    new CancellableBodySubscriber<>(delegate.apply(responseInfo));
            subscriber = wrapped;
            if (cancelled) {
                wrapped.cancel();
            }
            return wrapped;
        }

        private void cancel() {
            cancelled = true;
            CancellableBodySubscriber<T> current = subscriber;
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static final class CancellableBodySubscriber<T>
            implements HttpResponse.BodySubscriber<T> {
        private final HttpResponse.BodySubscriber<T> delegate;
        private volatile Flow.Subscription subscription;
        private volatile boolean cancelled;

        private CancellableBodySubscriber(HttpResponse.BodySubscriber<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionStage<T> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            subscription = newSubscription;
            if (cancelled) {
                newSubscription.cancel();
            }
            delegate.onSubscribe(newSubscription);
            if (cancelled) {
                newSubscription.cancel();
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        private void cancel() {
            cancelled = true;
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
        }
    }

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
            NETWORK_ERROR,
            /** The server returned an HTTP error other than 404 */
            HTTP_ERROR,
            /** The request was skipped because the endpoint circuit breaker is open */
            CIRCUIT_OPEN
        }

        private final Status status;
        private final String body;
        private final Exception lastException;
        private final int attemptCount;
        private final int httpStatusCode;

        private FetchResult(
                Status status,
                String body,
                Exception lastException,
                int attemptCount,
                int httpStatusCode) {
            this.status = status;
            this.body = body;
            this.lastException = lastException;
            this.attemptCount = attemptCount;
            this.httpStatusCode = httpStatusCode;
        }

        /** Create a successful result. */
        public static FetchResult success(String body, int attemptCount) {
            return new FetchResult(Status.SUCCESS, body, null, attemptCount, 200);
        }

        /** Create a not-found result. */
        public static FetchResult notFound(int attemptCount) {
            return new FetchResult(Status.NOT_FOUND, null, null, attemptCount, 404);
        }

        /** Create an HTTP error result. */
        public static FetchResult httpError(
                int statusCode, Exception lastException, int attemptCount) {
            return new FetchResult(
                    Status.HTTP_ERROR, null, lastException, attemptCount, statusCode);
        }

        /** Create a network error result. */
        public static FetchResult networkError(Exception lastException, int attemptCount) {
            return new FetchResult(
                    Status.NETWORK_ERROR, null, lastException, attemptCount, -1);
        }

        /** Create a circuit-open result. */
        public static FetchResult circuitOpen(Exception lastException) {
            return new FetchResult(Status.CIRCUIT_OPEN, null, lastException, 0, -1);
        }

        /** Get the status of this result. */
        public Status getStatus() { return status; }

        /** Check if the fetch was successful. */
        public boolean isSuccess() { return status == Status.SUCCESS; }

        /** Check if the CRS code was not found. */
        public boolean isNotFound() { return status == Status.NOT_FOUND; }

        /** Check if a network error occurred. */
        public boolean isNetworkError() { return status == Status.NETWORK_ERROR; }

        /** Check if the server returned a non-404 HTTP error. */
        public boolean isHttpError() { return status == Status.HTTP_ERROR; }

        /** Check if the request was rejected by the circuit breaker. */
        public boolean isCircuitOpen() { return status == Status.CIRCUIT_OPEN; }

        /** Get the response body (only valid if status is SUCCESS). */
        public String getBody() { return body; }

        /** Get the underlying exception for HTTP, network, or circuit-open failures. */
        public Exception getLastException() { return lastException; }

        /** Get the number of attempts made. */
        public int getAttemptCount() { return attemptCount; }

        /** Get the HTTP status code, or -1 when no response was received. */
        public int getHttpStatusCode() { return httpStatusCode; }
    }

    // ==================== Defaults ====================

    /** Default path template appended to the base URL. */
    public static final String DEFAULT_PATH_TEMPLATE = "/{authority}/{code}.json";

    /** Default connection timeout in seconds. */
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

    /** Default read timeout in seconds. */
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 30;

    /** Default maximum number of total attempts, including the initial request. */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Default initial backoff delay in milliseconds. */
    public static final long DEFAULT_INITIAL_BACKOFF_MS = 500;

    /** Default overall deadline; zero preserves the per-attempt timeouts without a total limit. */
    public static final int DEFAULT_TOTAL_TIMEOUT_SECONDS = 0;

    /** Default circuit-breaker threshold; zero leaves the breaker disabled. */
    public static final int DEFAULT_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 0;

    /** Default behavior for caching HTTP 404 responses. */
    public static final boolean DEFAULT_CACHE_NOT_FOUND = true;

    /** Default delay before allowing a probe request through an open circuit. */
    public static final Duration DEFAULT_CIRCUIT_BREAKER_RESET_TIMEOUT = Duration.ofSeconds(30);

    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 5000;
    private static final int MAX_REDIRECTS = 5;

    // ==================== Instance Fields ====================

    private final String baseUrl;
    private final String pathTemplate;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final int totalTimeoutSeconds;
    private final int circuitBreakerFailureThreshold;
    private final long circuitBreakerResetTimeoutNanos;
    private final boolean cacheNotFound;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    /** Negative cache: track codes that are known 404s. */
    private final Set<String> notFoundCache = ConcurrentHashMap.newKeySet();

    /** Concurrent requests for the same authority/code share one HTTP operation. */
    private final Map<String, CompletableFuture<FetchResult>> inFlight = new ConcurrentHashMap<>();

    /** Guards all circuit-breaker state and generation transitions. */
    private final Object circuitLock = new Object();

    /** Circuit state for the configured endpoint. Guarded by {@link #circuitLock}. */
    private CircuitState circuitState = CircuitState.CLOSED;

    /** Consecutive logical lookup failures. Guarded by {@link #circuitLock}. */
    private int consecutiveFailures;

    /** Monotonic timestamp when the circuit opened. Guarded by {@link #circuitLock}. */
    private long circuitOpenedAtNanos;

    /** Invalidates results from requests admitted under an older circuit state. */
    private long circuitGeneration;

    // ==================== Constructor (via Builder) ====================

    private UrlCRSFetcher(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.pathTemplate = builder.pathTemplate;
        this.connectTimeoutSeconds = builder.connectTimeoutSeconds;
        this.readTimeoutSeconds = builder.readTimeoutSeconds;
        this.maxRetries = builder.maxRetries;
        this.initialBackoffMs = builder.initialBackoffMs;
        this.totalTimeoutSeconds = builder.totalTimeoutSeconds;
        this.circuitBreakerFailureThreshold = builder.circuitBreakerFailureThreshold;
        this.circuitBreakerResetTimeoutNanos =
                builder.circuitBreakerResetTimeout.toNanos();
        this.cacheNotFound = builder.cacheNotFound;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER)
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

        if (cacheNotFound && notFoundCache.contains(cacheKey)) {
            return FetchResult.notFound(0);
        }

        CompletableFuture<FetchResult> request = new CompletableFuture<>();
        CompletableFuture<FetchResult> existing = inFlight.putIfAbsent(cacheKey, request);
        if (existing != null) {
            return awaitInFlight(existing);
        }

        CircuitPermit circuitPermit = null;
        try {
            circuitPermit = acquireCircuitPermit();
            if (circuitPermit == null) {
                FetchResult circuitRejection = circuitOpenResult();
                request.complete(circuitRejection);
                return circuitRejection;
            }

            FetchResult result = fetchWithRetries(authority, code, cacheKey);
            if (isNeutralEndpointResult(result)) {
                releaseCircuitPermit(circuitPermit);
            } else {
                recordEndpointResult(circuitPermit, isEndpointFailure(result));
            }
            request.complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            releaseCircuitPermit(circuitPermit);
            request.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(cacheKey, request);
        }
    }

    private FetchResult fetchWithRetries(String authority, String code, String cacheKey) {
        String url = buildUrl(authority, code);
        Exception lastException = null;
        int lastHttpStatusCode = -1;
        int attemptCount = 0;
        long deadlineNanos = totalTimeoutSeconds == 0
                ? 0
                : System.nanoTime() + Duration.ofSeconds(totalTimeoutSeconds).toNanos();

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (attempt > 0) {
                long backoffMs = calculateBackoff(attempt);
                long remainingNanos = remainingNanos(deadlineNanos);
                if (remainingNanos <= 0) {
                    lastException = overallTimeout(url);
                    lastHttpStatusCode = -1;
                    break;
                }
                long remainingMs = Math.max(1, Duration.ofNanos(remainingNanos).toMillis());
                long boundedBackoffMs = Math.min(backoffMs, remainingMs);
                try {
                    Thread.sleep(boundedBackoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return FetchResult.networkError(e, attemptCount);
                }
            }

            long remainingNanos = remainingNanos(deadlineNanos);
            if (remainingNanos <= 0) {
                lastException = overallTimeout(url);
                lastHttpStatusCode = -1;
                break;
            }

            attemptCount = attempt + 1;
            try {
                HttpResponse<String> response = executeRequest(url, remainingNanos);
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    return FetchResult.success(response.body(), attemptCount);
                } else if (statusCode == 404) {
                    if (cacheNotFound) {
                        notFoundCache.add(cacheKey);
                    }
                    return FetchResult.notFound(attemptCount);
                } else if (isRetryableStatusCode(statusCode)) {
                    lastHttpStatusCode = statusCode;
                    lastException = new IOException("HTTP " + statusCode + " from " + url);
                    continue;
                } else {
                    IOException error = new IOException("HTTP " + statusCode + " from " + url);
                    return FetchResult.httpError(statusCode, error, attemptCount);
                }
            } catch (ConnectException | SocketTimeoutException e) {
                lastException = e;
                lastHttpStatusCode = -1;
            } catch (IOException e) {
                if (isRetryableException(e)) {
                    lastException = e;
                    lastHttpStatusCode = -1;
                } else {
                    return FetchResult.networkError(e, attemptCount);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FetchResult.networkError(e, attemptCount);
            }
        }

        if (lastHttpStatusCode >= 0) {
            return FetchResult.httpError(
                    lastHttpStatusCode, lastException, attemptCount);
        }
        if (lastException == null) {
            lastException = overallTimeout(url);
        }
        return FetchResult.networkError(lastException, attemptCount);
    }

    private FetchResult awaitInFlight(CompletableFuture<FetchResult> request) {
        try {
            FetchResult sharedResult = request.get();
            if (isInterruptedNetworkError(sharedResult)) {
                return FetchResult.networkError(
                        new IOException(
                                "The owner of the shared CRS fetch was interrupted",
                                sharedResult.getLastException()),
                        sharedResult.getAttemptCount());
            }
            return sharedResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FetchResult.networkError(e, 0);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            Exception wrapped = cause instanceof Exception
                    ? (Exception) cause
                    : new IOException("Concurrent CRS fetch failed", cause);
            return FetchResult.networkError(wrapped, 0);
        }
    }

    private FetchResult circuitOpenResult() {
        IOException error = new IOException(
                "Circuit breaker is open for CRS endpoint " + baseUrl);
        return FetchResult.circuitOpen(error);
    }

    private CircuitPermit acquireCircuitPermit() {
        synchronized (circuitLock) {
            if (circuitState == CircuitState.CLOSED) {
                return new CircuitPermit(circuitGeneration, false);
            }
            if (circuitState == CircuitState.OPEN
                    && System.nanoTime() - circuitOpenedAtNanos
                            >= circuitBreakerResetTimeoutNanos) {
                circuitState = CircuitState.HALF_OPEN;
                return new CircuitPermit(circuitGeneration, true);
            }
            return null;
        }
    }

    private void recordEndpointResult(CircuitPermit permit, boolean failed) {
        if (circuitBreakerFailureThreshold == 0) {
            return;
        }
        synchronized (circuitLock) {
            if (permit.generation != circuitGeneration) {
                return;
            }

            if (permit.halfOpenProbe) {
                if (circuitState != CircuitState.HALF_OPEN) {
                    return;
                }
                circuitGeneration++;
                if (failed) {
                    circuitState = CircuitState.OPEN;
                    circuitOpenedAtNanos = System.nanoTime();
                    consecutiveFailures = circuitBreakerFailureThreshold;
                } else {
                    closeCircuit();
                }
                return;
            }

            if (circuitState != CircuitState.CLOSED) {
                return;
            }
            if (!failed) {
                consecutiveFailures = 0;
                return;
            }

            consecutiveFailures++;
            if (consecutiveFailures >= circuitBreakerFailureThreshold) {
                circuitGeneration++;
                circuitState = CircuitState.OPEN;
                circuitOpenedAtNanos = System.nanoTime();
            }
        }
    }

    private void releaseCircuitPermit(CircuitPermit permit) {
        if (permit == null || !permit.halfOpenProbe) {
            return;
        }
        synchronized (circuitLock) {
            if (permit.generation == circuitGeneration
                    && circuitState == CircuitState.HALF_OPEN) {
                circuitGeneration++;
                circuitState = CircuitState.OPEN;
            }
        }
    }

    private void closeCircuit() {
        circuitState = CircuitState.CLOSED;
        consecutiveFailures = 0;
        circuitOpenedAtNanos = 0;
    }

    private static SocketTimeoutException overallTimeout(String url) {
        return new SocketTimeoutException(
                "Overall timeout while fetching CRS definition from " + url);
    }

    private static long remainingNanos(long deadlineNanos) {
        return deadlineNanos == 0 ? Long.MAX_VALUE : deadlineNanos - System.nanoTime();
    }

    private static boolean isEndpointFailure(FetchResult result) {
        if (result.isNetworkError()) {
            return true;
        }
        if (!result.isHttpError()) {
            return false;
        }
        int statusCode = result.getHttpStatusCode();
        return statusCode >= 500
                || statusCode == 401
                || statusCode == 403
                || statusCode == 408
                || statusCode == 429;
    }

    private static boolean isNeutralEndpointResult(FetchResult result) {
        return isInterruptedNetworkError(result);
    }

    private static boolean isInterruptedNetworkError(FetchResult result) {
        return result.isNetworkError()
                && result.getLastException() instanceof InterruptedException;
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

    private HttpResponse<String> executeRequest(String url, long remainingNanos)
            throws IOException, InterruptedException {
        long readTimeoutNanos = Duration.ofSeconds(readTimeoutSeconds).toNanos();
        long requestTimeoutNanos =
                Math.max(1, Math.min(readTimeoutNanos, remainingNanos));
        long requestDeadlineNanos = System.nanoTime() + requestTimeoutNanos;
        URI currentUri = URI.create(url);

        for (int redirects = 0; ; redirects++) {
            long currentRequestTimeoutNanos =
                    requestDeadlineNanos - System.nanoTime();
            if (currentRequestTimeoutNanos <= 0) {
                throw overallTimeout(url);
            }

            HttpResponse<String> response =
                    executeSingleRequest(currentUri, currentRequestTimeoutNanos);
            if (!isRedirectStatusCode(response.statusCode())) {
                return response;
            }

            String location = response.headers().firstValue("location").orElse(null);
            if (location == null) {
                return response;
            }
            if (redirects >= MAX_REDIRECTS) {
                throw new IOException("Too many redirects while fetching " + url);
            }

            URI redirectUri;
            try {
                redirectUri = currentUri.resolve(location);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid redirect from " + currentUri, e);
            }
            if (!isAllowedRedirect(currentUri, redirectUri)) {
                return response;
            }
            currentUri = redirectUri;
        }
    }

    private HttpResponse<String> executeSingleRequest(
            URI uri, long requestTimeoutNanos)
            throws IOException, InterruptedException {
        Duration requestTimeout = Duration.ofNanos(requestTimeoutNanos);
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .GET();

        // Apply custom headers
        for (Map.Entry<String, String> h : headers.entrySet()) {
            reqBuilder.header(h.getKey(), h.getValue());
        }

        CancellableBodyHandler<String> bodyHandler =
                new CancellableBodyHandler<>(HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> responseFuture =
                httpClient.sendAsync(reqBuilder.build(), bodyHandler);
        try {
            return responseFuture.get(requestTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            bodyHandler.cancel();
            responseFuture.cancel(true);
            SocketTimeoutException timeout = new SocketTimeoutException(
                    "Timed out while fetching CRS response body from " + uri);
            timeout.initCause(e);
            throw timeout;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IOException("Failed to fetch CRS definition from " + uri, cause);
        } catch (InterruptedException e) {
            bodyHandler.cancel();
            responseFuture.cancel(true);
            throw e;
        }
    }

    private static boolean isRedirectStatusCode(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static boolean isAllowedRedirect(URI source, URI target) {
        String targetScheme = target.getScheme();
        if (!"http".equalsIgnoreCase(targetScheme)
                && !"https".equalsIgnoreCase(targetScheme)) {
            return false;
        }
        if (target.getUserInfo() != null
                || !source.getScheme().equalsIgnoreCase(targetScheme)
                || source.getHost() == null
                || target.getHost() == null
                || !source.getHost().equalsIgnoreCase(target.getHost())) {
            return false;
        }
        return effectivePort(source) == effectivePort(target);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
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
        if (e instanceof ConnectException
                || e instanceof NoRouteToHostException
                || e instanceof SocketTimeoutException
                || e instanceof UnknownHostException
                || e instanceof HttpTimeoutException) {
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

    /** Get the maximum number of total attempts. */
    public int getMaxRetries() { return maxRetries; }

    /** Get the connect timeout in seconds. */
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }

    /** Get the read timeout in seconds. */
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }

    /** Get the initial backoff in milliseconds. */
    public long getInitialBackoffMs() { return initialBackoffMs; }

    /** Get the overall lookup timeout in seconds. */
    public int getTotalTimeoutSeconds() { return totalTimeoutSeconds; }

    /** Get the number of failed lookups that opens the circuit. */
    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    /** Get the circuit breaker reset timeout. */
    public Duration getCircuitBreakerResetTimeout() {
        return Duration.ofNanos(circuitBreakerResetTimeoutNanos);
    }

    /** Check whether HTTP 404 responses are cached. */
    public boolean isNotFoundCacheEnabled() { return cacheNotFound; }

    /** Get the number of consecutive endpoint failures. */
    public int getConsecutiveFailureCount() {
        synchronized (circuitLock) {
            return consecutiveFailures;
        }
    }

    /** Check whether the endpoint circuit has been opened. */
    public boolean isCircuitOpen() {
        synchronized (circuitLock) {
            return circuitState != CircuitState.CLOSED;
        }
    }

    /** Close the circuit and clear its failure count. */
    public void resetCircuitBreaker() {
        synchronized (circuitLock) {
            circuitGeneration++;
            closeCircuit();
        }
    }

    /** Get the number of currently active logical fetches. */
    int getInFlightCount() { return inFlight.size(); }

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
        private int totalTimeoutSeconds = DEFAULT_TOTAL_TIMEOUT_SECONDS;
        private int circuitBreakerFailureThreshold =
                DEFAULT_CIRCUIT_BREAKER_FAILURE_THRESHOLD;
        private Duration circuitBreakerResetTimeout =
                DEFAULT_CIRCUIT_BREAKER_RESET_TIMEOUT;
        private boolean cacheNotFound = DEFAULT_CACHE_NOT_FOUND;
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
         * Set the maximum number of total attempts, including the initial request.
         * Default: 3.
         *
         * @param maxRetries maximum total attempts (minimum 1)
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
         * Set the overall deadline for a logical lookup, including retries and backoff.
         * Set to zero to rely only on the per-attempt timeouts. Default: 0.
         *
         * @param seconds overall timeout in seconds, or zero to disable it
         * @return this builder
         */
        public Builder totalTimeout(int seconds) {
            this.totalTimeoutSeconds = Math.max(0, seconds);
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
            this.circuitBreakerFailureThreshold = Math.max(0, failures);
            return this;
        }

        /**
         * Set how long an open circuit waits before permitting one probe request.
         * Default: 30 seconds.
         *
         * @param timeout positive reset timeout
         * @return this builder
         */
        public Builder circuitBreakerResetTimeout(Duration timeout) {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException(
                        "circuit breaker reset timeout must be positive");
            }
            this.circuitBreakerResetTimeout = timeout;
            return this;
        }

        /**
         * Set whether HTTP 404 responses are cached for the lifetime of this fetcher.
         * Default: {@code true}.
         *
         * <p>Disable this for a rolling catalog where a previously missing code may
         * be added while the JVM is still running.</p>
         *
         * @param enabled whether to cache HTTP 404 responses
         * @return this builder
         */
        public Builder cacheNotFound(boolean enabled) {
            this.cacheNotFound = enabled;
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
