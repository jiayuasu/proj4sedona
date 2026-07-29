# CRS Registry

Proj4Sedona uses an extensible provider chain to resolve CRS definitions from authority codes like `EPSG:4326`. This guide covers the built-in registry, URL-based providers, custom providers, and alias management.

## How Resolution Works

When you create a `Proj` from an authority code (e.g., `new Proj("EPSG:4326")`), the library queries a chain of `CRSProvider` instances in priority order. The first provider that returns a result wins.

```
EPSG:4326  -->  BuiltInCRSProvider (priority 100)
           -->  UrlCRSProvider / OSGeo GitHub + CDN fallback (priority 101)
           -->  Custom providers (user-defined priority)
```

## The Defs Registry

The `Defs` class is the central CRS registry. It manages providers and direct definitions.

### Registering Definitions Manually

```java
import org.datasyslab.proj4sedona.defs.Defs;

// Register a PROJ string
Defs.set("MY:CUSTOM_CRS", "+proj=tmerc +lat_0=0 +lon_0=9 +k=0.9996 +x_0=500000 +y_0=0 +datum=WGS84 +units=m");

// Check if defined
boolean exists = Defs.has("MY:CUSTOM_CRS");

// Retrieve
ProjectionDef def = Defs.get("MY:CUSTOM_CRS");

// Remove
Defs.remove("MY:CUSTOM_CRS");
```

### Aliases

Create an alias that points to an existing definition:

```java
// "UTM18N" will resolve to whatever "EPSG:32618" resolves to
Defs.alias("UTM18N", "EPSG:32618");

// Now these are equivalent
Proj p1 = new Proj("EPSG:32618");
Proj p2 = new Proj("UTM18N");
```

### Pre-Registered Aliases

The following aliases are available out of the box:

| Alias | Target |
|-------|--------|
| `WGS84` | EPSG:4326 |
| `GOOGLE` | EPSG:3857 |
| `EPSG:900913` | EPSG:3857 |
| `EPSG:102113` | EPSG:3857 |
| `EPSG:3785` | EPSG:3857 |

## Built-In Provider

The `BuiltInCRSProvider` (priority 100) supplies PROJ strings for common EPSG codes without any network access:

| EPSG Range | CRS |
|------------|-----|
| 4326 | WGS84 geographic |
| 4269 | NAD83 geographic |
| 3857 | Web Mercator |
| 32601 -- 32660 | WGS84 / UTM zones 1-60 North |
| 32701 -- 32760 | WGS84 / UTM zones 1-60 South |
| 5041 | UPS North |
| 5042 | UPS South |

## URL-Based Provider

The `UrlCRSProvider` fetches CRS definitions from HTTP endpoints. The default
remote provider reads the rolling spatialreference.org `gh-pages` catalog from
`raw.githubusercontent.com`. If GitHub has an endpoint failure, it tries a
commit-addressed copy through jsDelivr. The backup was generated from PROJ
9.8.1 and EPSG v12.029 at OSGeo commit
`c43e4e72634af65fcf684def42ddc2dcfd834938`.

The CRS catalog remains an external runtime dependency; it is not bundled in
the proj4sedona JAR.

```java
import org.datasyslab.proj4sedona.defs.UrlCRSProvider;

// The default OSGeo GitHub provider and pinned CDN fallback are registered automatically.
// It resolves codes like EPSG:2154, ESRI:102001, etc.
Proj p = new Proj("EPSG:2154");  // fetched remotely if not built in
```

### Custom URL Provider

Build a provider for your own CRS service:

```java
import org.datasyslab.proj4sedona.defs.UrlCRSProvider;
import org.datasyslab.proj4sedona.defs.UrlCRSFetcher;
import org.datasyslab.proj4sedona.defs.CRSResult;
import java.time.Duration;

UrlCRSFetcher mirror = UrlCRSFetcher.builder()
    .baseUrl("https://mirror.example.com/api")
    .pathTemplate("/{authority}/{code}.proj4")
    .connectTimeout(3)
    .readTimeout(5)
    .totalTimeout(8)
    .maxRetries(2)
    .circuitBreakerFailureThreshold(3)
    .circuitBreakerResetTimeout(Duration.ofSeconds(30))
    .build();

UrlCRSProvider myProvider = UrlCRSProvider.builder("my-crs-service")
    .baseUrl("https://crs.example.com/api")
    .pathTemplate("/{authority}/{code}.proj4")
    .authorities("EPSG", "ESRI")
    .format(CRSResult.Format.PROJ4)
    .connectTimeout(3)             // per connection
    .readTimeout(5)                // per request
    .totalTimeout(8)               // primary only, including retries
    .maxRetries(2)                 // two total attempts
    .circuitBreakerFailureThreshold(3)
    .circuitBreakerResetTimeout(Duration.ofSeconds(30))
    .fallbackFetcher(mirror)       // independently configured endpoint
    .build();
```

Fallback fetchers are tried in insertion order after network failures, non-404
HTTP errors, or an open circuit. A reachable primary that returns HTTP 404 is
authoritative, so an older fallback cannot resurrect a CRS removed from the
current catalog. If the primary fails and every fallback either fails or
returns 404, the endpoint failure is propagated with diagnostics for each
endpoint. An interrupted lookup stops immediately rather than starting another
request.

Configure each fallback explicitly. Its headers are not copied from the
primary, so credentials are scoped to the fetcher on which they were
configured. Redirects are followed only within the same origin. Every endpoint
must serve the format configured on the provider.

The built-in GitHub and jsDelivr fetchers each use the reliability settings in
the example. Concurrent lookups of the same authority and code share one
in-flight HTTP request per endpoint. After three consecutive endpoint
failures—network failures, HTTP 401/403/408/429, or HTTP 5xx—an endpoint's
independent circuit breaker rejects requests locally for 30 seconds before
allowing one probe request.

The eight-second overall deadline applies separately to each endpoint. A
lookup can therefore take up to approximately 16 seconds when both default
endpoints are unhealthy, with at most two attempts per endpoint. An already
open GitHub circuit moves to jsDelivr immediately.

Failover is based on transport and HTTP status. An HTTP 200 response is returned
for parsing and does not trigger a fallback if its body is malformed.

These stricter limits apply to both built-in remote endpoints. Custom
`UrlCRSProvider` instances retain the earlier generic defaults (10-second
connect timeout, 30-second request timeout, three total attempts, no overall
deadline, and no circuit breaker) unless configured explicitly.

### Registering and Managing Providers

```java
import org.datasyslab.proj4sedona.defs.Defs;

// Register with a priority (lower = higher priority)
Defs.registerProvider(myProvider, 50);  // checked before built-in (100)

// List registered providers
List<CRSProvider> providers = Defs.getProviders();

// Remove a provider by name
Defs.removeProvider("my-crs-service");

// Clear all providers and start fresh
Defs.clearProviders();
```

## Writing a Custom Provider

Implement the `CRSProvider` interface to add any resolution logic:

```java
import org.datasyslab.proj4sedona.defs.CRSProvider;
import org.datasyslab.proj4sedona.defs.CRSResult;

public class DatabaseCRSProvider implements CRSProvider {

    @Override
    public String getName() {
        return "database-provider";
    }

    @Override
    public CRSResult resolve(String authority, String code) {
        // Look up in your database
        String projString = myDatabase.lookupCRS(authority, code);
        if (projString != null) {
            return CRSResult.proj4(projString);
        }
        return null;  // not found, try next provider
    }
}
```

### CRSResult Formats

Providers can return definitions in any supported format:

```java
// PROJ string
CRSResult.proj4("+proj=utm +zone=18 +datum=WGS84");

// WKT1
CRSResult.wkt1("PROJCS[\"WGS 84 / UTM zone 18N\", ...]");

// WKT2
CRSResult.wkt2("PROJCRS[\"WGS 84 / UTM zone 18N\", ...]");

// PROJJSON
CRSResult.projJson("{\"type\": \"ProjectedCRS\", ...}");
```

Register your custom provider:

```java
Defs.registerProvider(new DatabaseCRSProvider(), 75);
```

## Provider Priority

Providers are queried in ascending priority order. Lower numbers are checked first.

| Priority | Provider | Description |
|----------|----------|-------------|
| 50 | (custom) | Your high-priority providers |
| 100 | BuiltInCRSProvider | Built-in PROJ strings for common EPSG codes |
| 101 | UrlCRSProvider | Rolling OSGeo GitHub catalog with pinned CDN fallback |

Manual `Defs.set()` definitions always take precedence over providers.

## Caching

Successful provider results are parsed and cached in memory by normalized
authority code. Repeated lookups such as `EPSG:2154` therefore do not repeat the
HTTP request within the same JVM.

The rolling GitHub primary does not cache HTTP 404 responses permanently,
because a missing code can be added to the upstream catalog while a JVM is
running. The immutable jsDelivr fallback does negatively cache 404 responses.
Custom URL fetchers cache 404 responses by default; use
`.cacheNotFound(false)` for another rolling catalog.

The cache is process-local and is not persisted to disk. In a Spark deployment,
each executor JVM may fetch a previously unseen CRS once; an executor restart
starts with an empty cache. HTTP errors and network failures are deliberately
not cached as missing definitions. The circuit breaker bounds repeated failures
without confusing them with 404 responses.

## Error Handling

When a CRS cannot be resolved, a `CRSFetchException` is thrown with a `Reason`:

```java
import org.datasyslab.proj4sedona.defs.CRSFetchException;

try {
    Proj p = new Proj("EPSG:999999");
} catch (CRSFetchException e) {
    switch (e.getReason()) {
        case NOT_FOUND:
            System.out.println("CRS not found");
            break;
        case HTTP_ERROR:
            System.out.println("CRS endpoint rejected the request: " + e.getMessage());
            break;
        case NETWORK_ERROR:
            System.out.println("Network error: " + e.getMessage());
            break;
        case CIRCUIT_OPEN:
            System.out.println("CRS endpoint temporarily unavailable: " + e.getMessage());
            break;
        case INVALID_RESPONSE:
            System.out.println("Invalid response from provider");
            break;
    }
}
```

## Registry Management

```java
// Initialize default providers (called automatically on first use)
Defs.globals();

// Get the number of registered definitions
int count = Defs.size();

// Reset to defaults (clears all manual definitions and re-initializes providers)
Defs.reset();
```

## See Also

- [CRS Formats](crs-formats.md) -- all supported CRS input and output formats
- [Getting Started](getting-started.md) -- basic usage with EPSG codes
