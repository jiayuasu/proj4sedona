package org.datasyslab.proj4sedona.defs;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CRS provider for commonly-used, hardcoded projection definitions.
 *
 * <p>This provider contains PROJ.4 strings for the most frequently used EPSG codes
 * so they can be resolved instantly without network access:</p>
 * <ul>
 *   <li>EPSG:4326 — WGS 84 (lon/lat)</li>
 *   <li>EPSG:4269 — NAD83 (lon/lat)</li>
 *   <li>EPSG:3857 — WGS 84 / Pseudo-Mercator</li>
 *   <li>EPSG:326xx — UTM zones 1–60 North (WGS 84)</li>
 *   <li>EPSG:327xx — UTM zones 1–60 South (WGS 84)</li>
 *   <li>EPSG:5041 — WGS 84 / UPS North</li>
 *   <li>EPSG:5042 — WGS 84 / UPS South</li>
 *   <li>Aliases: WGS84, GOOGLE, EPSG:3785, EPSG:900913, EPSG:102113</li>
 * </ul>
 *
 * <p>Registered by default at priority 100 via {@link Defs#globals()}.</p>
 */
public final class BuiltInCRSProvider implements CRSProvider {

    /** Map from normalized authority:code to PROJ.4 string */
    private final Map<String, String> definitions;

    /**
     * Creates a new provider and populates it with all built-in definitions.
     */
    public BuiltInCRSProvider() {
        definitions = new HashMap<>();
        populate();
    }

    @Override
    public String getName() {
        return "built-in";
    }

    /**
     * Resolve a CRS code against the built-in definitions.
     *
     * @param authority the authority name, lower-cased (e.g. {@code "epsg"})
     * @param code      the code (e.g. {@code "4326"})
     * @return a {@link CRSResult} with format {@link CRSResult.Format#PROJ4}, or
     *         {@code null} if the code is not in the built-in set
     */
    @Override
    public CRSResult resolve(String authority, String code) {
        // Reconstruct the normalized key (uppercase authority)
        String key = authority.toUpperCase(Locale.ROOT) + ":" + code;
        String proj4 = definitions.get(key);
        return proj4 != null ? CRSResult.proj4(proj4) : null;
    }

    // ========== Population ==========

    private void populate() {
        // WGS84 - Geographic CRS
        put("EPSG:4326", "+title=WGS 84 (long/lat) +proj=longlat +ellps=WGS84 +datum=WGS84 +units=degrees");

        // NAD83 - North American Datum 1983
        put("EPSG:4269", "+title=NAD83 (long/lat) +proj=longlat +a=6378137.0 +b=6356752.31414036 +ellps=GRS80 +datum=NAD83 +units=degrees");

        // Web Mercator (Pseudo-Mercator)
        put("EPSG:3857", "+title=WGS 84 / Pseudo-Mercator +proj=merc +a=6378137 +b=6378137 +lat_ts=0.0 +lon_0=0.0 +x_0=0.0 +y_0=0 +k=1.0 +units=m +nadgrids=@null +no_defs");

        // UTM zones WGS84 (1–60, North and South)
        for (int zone = 1; zone <= 60; zone++) {
            put("EPSG:" + (32600 + zone),
                    "+proj=utm +zone=" + zone + " +datum=WGS84 +units=m");
            put("EPSG:" + (32700 + zone),
                    "+proj=utm +zone=" + zone + " +south +datum=WGS84 +units=m");
        }

        // UPS North
        put("EPSG:5041", "+title=WGS 84 / UPS North (E,N) +proj=stere +lat_0=90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m");

        // UPS South
        put("EPSG:5042", "+title=WGS 84 / UPS South (E,N) +proj=stere +lat_0=-90 +lon_0=0 +k=0.994 +x_0=2000000 +y_0=2000000 +datum=WGS84 +units=m");

        // Common aliases — point to the same proj4 strings
        putAlias("WGS84",         "EPSG:4326");
        putAlias("EPSG:3785",     "EPSG:3857");
        putAlias("GOOGLE",        "EPSG:3857");
        putAlias("EPSG:900913",   "EPSG:3857");
        putAlias("EPSG:102113",   "EPSG:3857");
    }

    private void put(String key, String proj4) {
        definitions.put(key, proj4);
    }

    private void putAlias(String alias, String target) {
        String proj4 = definitions.get(target);
        if (proj4 != null) {
            definitions.put(alias, proj4);
        }
    }
}
