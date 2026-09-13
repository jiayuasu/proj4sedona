package org.datasyslab.proj4sedona.constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Esri object names mapped to the authority codes they denote.
 *
 * <p>Esri-style WKT names its objects differently from EPSG: datums carry a {@code D_} prefix
 * ({@code D_North_American_1983}), geographic systems a {@code GCS_} prefix, and projected
 * systems use Esri's own names ({@code NAD_1983_UTM_Zone_19N},
 * {@code NAD_1983_StatePlane_California_V_FIPS_0405_Feet}). Esri publishes the contents of its
 * projection engine database, where every object carries its WKID (the EPSG code wherever one
 * exists, an Esri code otherwise). The index behind this class is generated from that
 * publication by {@code scripts/sync-esri-aliases.py} and bundled as a resource.</p>
 *
 * <p>The index carries names and codes only, never coordinate system parameters: the
 * definition behind a code is resolved through the library's providers when it is needed.
 * The index is loaded lazily on first use and held for the lifetime of the JVM. Lookups are
 * case-insensitive.</p>
 */
public final class EsriAliases {

    /** The kind of object an alias denotes. */
    public enum Kind {
        GEODETIC_DATUM,
        GEODETIC_CRS,
        PROJECTED_CRS,
        ELLIPSOID
    }

    /** One Esri name and the object it denotes. */
    public static final class Alias {
        private final Kind kind;
        private final String name;
        private final String code;
        private final String baseCrs;
        private final boolean deprecated;

        Alias(Kind kind, String name, String code, String baseCrs, boolean deprecated) {
            this.kind = kind;
            this.name = name;
            this.code = code;
            this.baseCrs = baseCrs;
            this.deprecated = deprecated;
        }

        public Kind getKind() { return kind; }

        /** The Esri name exactly as Esri spells it. */
        public String getName() { return name; }

        /** The object denoted, as {@code AUTHORITY:code} ({@code EPSG:26919}, {@code ESRI:102001}). */
        public String getCode() { return code; }

        /**
         * The geographic CRS of the object's datum, as {@code AUTHORITY:code}: the base CRS of
         * a projected CRS, a geographic CRS itself, the lowest current geographic CRS on a
         * datum. {@code null} for ellipsoids and for datums with no such CRS.
         */
        public String getBaseCrs() { return baseCrs; }

        /** Whether Esri marks the object deprecated. */
        public boolean isDeprecated() { return deprecated; }

        @Override
        public String toString() {
            return kind + " " + name + " -> " + code + (baseCrs != null ? " (" + baseCrs + ")" : "");
        }
    }

    private static final String RESOURCE =
        "/org/datasyslab/proj4sedona/constants/esri-aliases.tsv";

    private static final class Tables {
        final Map<String, Alias> crs;
        final Map<String, Alias> datums;
        final Map<String, Alias> ellipsoids;
        final int size;

        Tables(Map<String, Alias> crs, Map<String, Alias> datums, Map<String, Alias> ellipsoids) {
            this.crs = Collections.unmodifiableMap(crs);
            this.datums = Collections.unmodifiableMap(datums);
            this.ellipsoids = Collections.unmodifiableMap(ellipsoids);
            this.size = crs.size() + datums.size() + ellipsoids.size();
        }
    }

    private static volatile Tables tables;

    private EsriAliases() {
    }

    /** Resolve an Esri geographic or projected CRS name, or {@code null}. */
    public static Alias crs(String esriName) {
        String key = key(esriName);
        return key == null ? null : tables().crs.get(key);
    }

    /** Resolve an Esri datum name such as {@code D_North_American_1983}, or {@code null}. */
    public static Alias datum(String esriName) {
        String key = key(esriName);
        return key == null ? null : tables().datums.get(key);
    }

    /** Resolve an Esri ellipsoid name such as {@code GRS_1980}, or {@code null}. */
    public static Alias ellipsoid(String esriName) {
        String key = key(esriName);
        return key == null ? null : tables().ellipsoids.get(key);
    }

    /** Number of names in the index. */
    public static int size() {
        return tables().size;
    }

    private static String key(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        return key.isEmpty() ? null : key;
    }

    private static Tables tables() {
        Tables loaded = tables;
        if (loaded == null) {
            synchronized (EsriAliases.class) {
                loaded = tables;
                if (loaded == null) {
                    loaded = load();
                    tables = loaded;
                }
            }
        }
        return loaded;
    }

    private static Tables load() {
        Map<String, Alias> crs = new HashMap<>(16384);
        Map<String, Alias> datums = new HashMap<>(2048);
        Map<String, Alias> ellipsoids = new HashMap<>(512);
        InputStream input = EsriAliases.class.getResourceAsStream(RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Missing resource " + RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 5) {
                    throw new IllegalStateException("Malformed alias row: " + line);
                }
                Kind kind = kindOf(fields[0]);
                String baseCrs = fields[3].isEmpty() ? null : fields[3];
                Alias alias = new Alias(kind, fields[1], fields[2], baseCrs, "1".equals(fields[4]));
                switch (kind) {
                    case GEODETIC_DATUM:
                        datums.put(key(alias.name), alias);
                        break;
                    case ELLIPSOID:
                        ellipsoids.put(key(alias.name), alias);
                        break;
                    default:
                        crs.put(key(alias.name), alias);
                        break;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
        return new Tables(crs, datums, ellipsoids);
    }

    private static Kind kindOf(String table) {
        switch (table) {
            case "geodetic_datum":
                return Kind.GEODETIC_DATUM;
            case "geodetic_crs":
                return Kind.GEODETIC_CRS;
            case "projected_crs":
                return Kind.PROJECTED_CRS;
            case "ellipsoid":
                return Kind.ELLIPSOID;
            default:
                throw new IllegalStateException("Unknown alias table: " + table);
        }
    }
}
