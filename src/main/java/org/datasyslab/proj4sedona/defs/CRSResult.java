package org.datasyslab.proj4sedona.defs;

/**
 * Result returned by a {@link CRSProvider} when it successfully resolves a CRS code.
 *
 * <p>Wraps the raw CRS definition string together with a {@link Format} hint that tells
 * the caller which parser to use. Use the static factory methods to create instances:</p>
 * <pre>
 * CRSResult.proj4("+proj=longlat +datum=WGS84 +units=degrees");
 * CRSResult.projJson("{\"type\":\"GeographicCRS\", ...}");
 * CRSResult.wkt1("GEOGCS[\"WGS 84\", ...]");
 * CRSResult.wkt2("GEOGCRS[\"WGS 84\", ...]");
 * </pre>
 */
public final class CRSResult {

    /**
     * The format of the CRS definition string.
     */
    public enum Format {
        /** PROJ.4 string (e.g. {@code +proj=longlat +datum=WGS84}) */
        PROJ4,
        /** PROJJSON (a JSON object following the PROJJSON schema) */
        PROJJSON,
        /** OGC Well-Known Text 1 (e.g. {@code GEOGCS["WGS 84", ...]}) */
        WKT1,
        /** ISO 19162 Well-Known Text 2 (e.g. {@code GEOGCRS["WGS 84", ...]}) */
        WKT2
    }

    private final String definition;
    private final Format format;

    private CRSResult(String definition, Format format) {
        if (definition == null || definition.isEmpty()) {
            throw new IllegalArgumentException("CRS definition must not be null or empty");
        }
        this.definition = definition;
        this.format = format;
    }

    /**
     * Create a result containing a PROJ.4 definition string.
     *
     * @param proj4String the PROJ.4 string (e.g. {@code +proj=longlat +datum=WGS84})
     * @return a new {@code CRSResult}
     */
    public static CRSResult proj4(String proj4String) {
        return new CRSResult(proj4String, Format.PROJ4);
    }

    /**
     * Create a result containing a PROJJSON definition string.
     *
     * @param json the PROJJSON string
     * @return a new {@code CRSResult}
     */
    public static CRSResult projJson(String json) {
        return new CRSResult(json, Format.PROJJSON);
    }

    /**
     * Create a result containing a WKT1 definition string.
     *
     * @param wkt the WKT1 string (e.g. {@code GEOGCS["WGS 84", ...]})
     * @return a new {@code CRSResult}
     */
    public static CRSResult wkt1(String wkt) {
        return new CRSResult(wkt, Format.WKT1);
    }

    /**
     * Create a result containing a WKT2 definition string.
     *
     * @param wkt the WKT2 string (e.g. {@code GEOGCRS["WGS 84", ...]})
     * @return a new {@code CRSResult}
     */
    public static CRSResult wkt2(String wkt) {
        return new CRSResult(wkt, Format.WKT2);
    }

    /**
     * Get the raw CRS definition string.
     *
     * @return the definition string (never null or empty)
     */
    public String getDefinition() {
        return definition;
    }

    /**
     * Get the format of the definition string.
     *
     * @return the format
     */
    public Format getFormat() {
        return format;
    }

    @Override
    public String toString() {
        return "CRSResult{format=" + format + ", definition='" +
                (definition.length() > 60 ? definition.substring(0, 60) + "..." : definition) + "'}";
    }
}
