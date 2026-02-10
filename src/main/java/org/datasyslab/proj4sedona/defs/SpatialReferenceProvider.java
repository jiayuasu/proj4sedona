package org.datasyslab.proj4sedona.defs;

import java.util.Locale;

/**
 * CRS provider that fetches PROJJSON definitions from
 * <a href="https://spatialreference.org">spatialreference.org</a>.
 *
 * <p>This provider delegates to {@link SpatialReferenceFetcher} for the actual HTTP
 * request, retry logic, and negative caching. It translates the {@link SpatialReferenceFetcher.FetchResult}
 * into the {@link CRSProvider} contract:</p>
 * <ul>
 *   <li>{@code SUCCESS} → returns {@link CRSResult#projJson(String)}</li>
 *   <li>{@code NOT_FOUND} → returns {@code null} (next provider in chain can try)</li>
 *   <li>{@code NETWORK_ERROR} → throws {@link CRSFetchException}</li>
 * </ul>
 *
 * <p>Registered by default at priority 101 via {@link Defs#globals()}.</p>
 *
 * @see SpatialReferenceFetcher
 */
public final class SpatialReferenceProvider implements CRSProvider {

    @Override
    public String getName() {
        return "spatialreference.org";
    }

    /**
     * Fetch a CRS definition from spatialreference.org.
     *
     * @param authority the authority name, lower-cased (e.g. {@code "epsg"}, {@code "esri"})
     * @param code      the code (e.g. {@code "2154"}, {@code "102001"})
     * @return a {@link CRSResult} with PROJJSON on success, or {@code null} if the code
     *         does not exist on the server
     * @throws CRSFetchException if a network error occurs after exhausting retries
     */
    @Override
    public CRSResult resolve(String authority, String code) {
        String fullCode = authority.toUpperCase(Locale.ROOT) + ":" + code;

        SpatialReferenceFetcher.FetchResult result =
                SpatialReferenceFetcher.fetchProjJson(authority, code);

        switch (result.getStatus()) {
            case SUCCESS:
                return CRSResult.projJson(result.getProjJson());

            case NOT_FOUND:
                return null;

            case NETWORK_ERROR:
                throw new CRSFetchException(fullCode, CRSFetchException.Reason.NETWORK_ERROR,
                        "Failed to fetch CRS definition for " + fullCode + " after " +
                                result.getAttemptCount() + " attempts",
                        result.getLastException());

            default:
                throw new IllegalStateException(
                        "Unknown fetch status " + result.getStatus() + " for " + fullCode);
        }
    }
}
