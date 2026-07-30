# CRS Registry

Proj4Sedona uses an extensible provider chain to resolve CRS definitions from authority codes like `EPSG:4326`. This guide covers the built-in registry, URL-based providers, custom providers, and alias management.

## How Resolution Works

When you create a `Proj` from an authority code (e.g., `new Proj("EPSG:4326")`), the library queries a chain of `CRSProvider` instances in priority order. The first provider that returns a result wins.

```
EPSG:4326  -->  BuiltInCRSProvider (priority 100)
           -->  UrlCRSProvider / OSGeo jsDelivr + GitHub fallback (priority 101)
           -->  Custom providers (user-defined priority)
```

A provider returning `null` allows resolution to continue. A provider exception
stops the chain and is propagated.

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
jsDelivr:

```
https://cdn.jsdelivr.net/gh/OSGeo/spatialreference.org@gh-pages
```

If that endpoint fails, it uses the rolling raw GitHub branch:

```
https://raw.githubusercontent.com/OSGeo/spatialreference.org/gh-pages
```

jsDelivr is the normal traffic endpoint, while raw GitHub provides a separate
delivery path and direct source check. jsDelivr can cache a branch revision for
up to about 12 hours, so this arrangement favors availability and lower fan-out
load over immediate visibility of every upstream commit.

The CRS catalog remains an external runtime dependency; it is not bundled in
the proj4sedona JAR.

```java
import org.datasyslab.proj4sedona.defs.UrlCRSProvider;

// The default OSGeo jsDelivr provider and GitHub fallback are registered automatically.
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
HTTP errors, or an open circuit. By default, a reachable primary that returns
HTTP 404 is authoritative, so an older fallback cannot resurrect a CRS removed
from the current catalog. The not-found policy can instead advance to the next
endpoint. If every endpoint is non-authoritative and returns 404, the result is
not-found; if an earlier endpoint had a hard failure, that failure is propagated
after the endpoint list is exhausted. An interrupted lookup stops immediately
rather than starting another request.

For mirrors that may lag their source, configure a non-authoritative primary
miss explicitly:

```java
UrlCRSProvider provider = UrlCRSProvider.builder("cdn-first")
    .baseUrl("https://cdn.example.com/crs")
    .primaryNotFoundPolicy(UrlCRSProvider.NotFoundPolicy.TRY_NEXT_ENDPOINT)
    .fallbackFetcher(
        mirror,
        UrlCRSProvider.NotFoundPolicy.AUTHORITATIVE)
    .build();
```

The built-in provider uses this policy. A jsDelivr 404 advances to raw GitHub
for confirmation. A GitHub 404 is authoritative, even if jsDelivr had a hard
failure. If jsDelivr returns 404 but GitHub has a hard failure, the lookup
propagates that failure instead of incorrectly reporting the code as missing.

Configure each fallback explicitly. Its headers are not copied from the
primary, so credentials are scoped to the fetcher on which they were
configured. Same-origin redirects are followed. Unsafe redirects, including
cross-origin redirects, are refused and reported as an HTTP error with sanitized
source and target origins; configured endpoint fallback still applies, and
headers are never sent to the refused target. Every endpoint must serve the
format configured on the provider.

The built-in jsDelivr and GitHub fetchers each use the reliability settings in
the example. Concurrent lookups of the same authority and code share one
in-flight HTTP request per endpoint. After three consecutive endpoint
failures—network failures, HTTP 401/403/408/429, or HTTP 5xx—an endpoint's
independent circuit breaker rejects requests locally for 30 seconds before
allowing one probe request.

The eight-second overall deadline applies separately to each endpoint. A
lookup can therefore take up to approximately 16 seconds when both default
endpoints are unhealthy, with at most two attempts per endpoint. An already
open jsDelivr circuit moves to GitHub immediately.

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
| 101 | UrlCRSProvider | Rolling OSGeo catalog through jsDelivr, then raw GitHub |

Manual `Defs.set()` definitions always take precedence over providers.

## Caching

Successful provider results are parsed and cached in memory by normalized
authority code. Repeated lookups such as `EPSG:2154` therefore do not repeat the
HTTP request within the same JVM.

Both built-in rolling endpoints cache HTTP 404 responses for five minutes. A
repeated missing-code lookup therefore avoids repeated downloads during that
window but can discover a newly added upstream code without restarting the JVM.
Custom URL fetchers cache 404 responses for the fetcher's lifetime by default.
Use `.notFoundCacheTtl(Duration.ofMinutes(5))` for an expiring negative cache or
`.cacheNotFound(false)` to disable negative caching.

The cache is process-local and is not persisted to disk. In a Spark deployment,
each executor JVM may fetch a previously unseen CRS once; an executor restart
starts with an empty cache. Successful definitions remain cached in `Defs` for
the JVM lifetime, so repeated transforms using the same CRS do not download the
PROJJSON again. Concurrent first lookups share one in-flight request per
endpoint. HTTP errors and network failures are deliberately not cached as
missing definitions. The circuit breaker bounds repeated failures without
confusing them with 404 responses.

## Error Handling

Only a provider's `null`/not-found result advances to the next registered
provider. After a URL provider exhausts its configured endpoint fallbacks,
non-404 HTTP responses and network failures abort the provider chain.

Use `Defs.getOrThrow` when an unresolved code should produce a
`CRSFetchException` with a `Reason`:

```java
import org.datasyslab.proj4sedona.defs.CRSFetchException;
import org.datasyslab.proj4sedona.defs.Defs;

try {
    Defs.getOrThrow("EPSG:999999");
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

`Defs.get` returns `null` for an unresolved code. Constructors such as
`new Proj("EPSG:999999")` convert that missing definition to an
`IllegalArgumentException`; endpoint failures still propagate as
`CRSFetchException`.

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
