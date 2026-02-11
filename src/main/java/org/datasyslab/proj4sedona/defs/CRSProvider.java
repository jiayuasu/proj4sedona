package org.datasyslab.proj4sedona.defs;

/**
 * Service provider interface for resolving CRS (Coordinate Reference System) definitions
 * by authority and code (e.g. {@code "EPSG"} / {@code "4326"}).
 *
 * <p>Implementations are registered with {@link Defs#registerProvider(CRSProvider, int)}
 * and are consulted in priority order (lower priority value = tried first) when
 * {@link Defs#get(String)} encounters an authority:code that is not already cached.</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Return a {@link CRSResult} when the provider can resolve the code.</li>
 *   <li>Return {@code null} when the provider does not know the code, so the next
 *       provider in the chain can try.</li>
 *   <li>Throw {@link CRSFetchException} only on hard errors (network failure,
 *       invalid response) that should abort the chain.</li>
 * </ul>
 *
 * <h3>Example — custom URL provider</h3>
 * <pre>
 * public class MyCRSProvider implements CRSProvider {
 *     public String getName() { return "my-server"; }
 *
 *     public CRSResult resolve(String authority, String code) {
 *         String json = fetchFromMyServer(authority, code);
 *         return json != null ? CRSResult.projJson(json) : null;
 *     }
 * }
 *
 * // Register at priority 50 (tried before the defaults at 100/101)
 * Defs.registerProvider(new MyCRSProvider(), 50);
 * </pre>
 *
 * @see Defs#registerProvider(CRSProvider, int)
 * @see CRSResult
 */
public interface CRSProvider {

    /**
     * A short, unique name identifying this provider (used for registration
     * and removal via {@link Defs#removeProvider(String)}).
     *
     * @return a non-null provider name (e.g. {@code "built-in"}, {@code "spatialreference.org"})
     */
    String getName();

    /**
     * Attempt to resolve a CRS definition for the given authority and code.
     *
     * @param authority the authority name, already lower-cased (e.g. {@code "epsg"}, {@code "esri"})
     * @param code the code within that authority (e.g. {@code "4326"}, {@code "102001"})
     * @return a {@link CRSResult} if this provider can resolve the code, or {@code null}
     *         to let the next provider in the chain try
     * @throws CRSFetchException on hard errors that should abort the entire lookup
     */
    CRSResult resolve(String authority, String code);
}
