package org.datasyslab.proj4sedona.constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Datum definitions with towgs84 transformation parameters.
 * Mirrors: lib/constants/Datum.js
 * 
 * <p>A geodetic datum defines the size and shape of the Earth (via an ellipsoid) and the
 * origin and orientation of the coordinate system. To transform coordinates between different
 * datums, transformation parameters (towgs84) are used.</p>
 * 
 * <p>Transformation types:</p>
 * <ul>
 *   <li><b>3-parameter</b>: Translation only (dx, dy, dz in meters)</li>
 *   <li><b>7-parameter</b>: Translation + rotation + scale (Helmert transformation)
 *       Format: dx, dy, dz, rx, ry, rz, s (rotations in arc-seconds, scale in ppm)</li>
 *   <li><b>NAD grids</b>: Grid-based shifts (e.g., NTv2 format) for high-accuracy transforms</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>
 * Datum wgs84 = Datum.get("wgs84");
 * double[] params = wgs84.getTowgs84Array();  // Returns [0, 0, 0]
 * </pre>
 * 
 * <p>Common datums:</p>
 * <ul>
 *   <li>WGS84 - World Geodetic System 1984 (GPS datum)</li>
 *   <li>NAD83 - North American Datum 1983</li>
 *   <li>NAD27 - North American Datum 1927 (uses grid shifts)</li>
 *   <li>OSGB36 - Ordnance Survey Great Britain 1936</li>
 * </ul>
 */
public final class Datum {

    /** Registry of all known datums, keyed by lowercase code */
    private static final Map<String, Datum> DATUMS = new HashMap<>();
    /** Fuzzy lookup index matching proj4js's match.js normalization. */
    private static final Map<String, Datum> NORMALIZED_DATUMS = new HashMap<>();
    private static final String GENERATED_DATUM_RESOURCE =
            "/org/datasyslab/proj4sedona/constants/proj4js-datums.tsv";

    // Core datums from proj4js
    /** World Geodetic System 1984 - the GPS reference datum */
    public static final Datum WGS84 = register("wgs84", "0,0,0", "WGS84", "WGS84");
    /** Swiss datum (CH1903) */
    public static final Datum CH1903 = register("ch1903", "674.374,15.056,405.346", "bessel", "swiss");
    /** Greek Geodetic Reference System 1987 */
    public static final Datum GGRS87 = register("ggrs87", "-199.87,74.79,246.62", "GRS80", "Greek_Geodetic_Reference_System_1987");
    /** North American Datum 1983 - essentially equivalent to WGS84 */
    public static final Datum NAD83 = register("nad83", "0,0,0", "GRS80", "North_American_Datum_1983");
    /** North American Datum 1927 - uses grid shift files for accuracy */
    public static final Datum NAD27 = registerWithNadgrids("nad27", "@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat", "clrk66", "North_American_Datum_1927");
    /** German DHDN datum (Potsdam) */
    public static final Datum POTSDAM = register("potsdam", "598.1,73.7,418.2,0.202,0.045,-2.455,6.7", "bessel", "Potsdam Rauenberg 1950 DHDN");
    /** Carthage datum (Tunisia) */
    public static final Datum CARTHAGE = register("carthage", "-263.0,6.0,431.0", "clrk80ign", "Carthage 1934 Tunisia");
    /** Austrian MGI datum */
    public static final Datum HERMANNSKOGEL = register("hermannskogel", "577.326,90.129,463.919,5.137,1.474,5.297,2.4232", "bessel", "Hermannskogel");
    /** Austrian Military Geographic Institute datum */
    public static final Datum MGI = register("mgi", "577.326,90.129,463.919,5.137,1.474,5.297,2.4232", "bessel", "Militar-Geographische Institut");
    /** Northern Ireland datum */
    public static final Datum OSNI52 = register("osni52", "482.530,-130.596,564.557,-1.042,-0.214,-0.631,8.15", "airy", "Irish National");
    /** Ireland 1965 datum */
    public static final Datum IRE65 = register("ire65", "482.530,-130.596,564.557,-1.042,-0.214,-0.631,8.15", "mod_airy", "Ireland 1965");
    /** Rassadiran datum */
    public static final Datum RASSADIRAN = register("rassadiran", "-133.63,-157.5,-158.62", "intl", "Rassadiran");
    /** New Zealand Geodetic Datum 1949 */
    public static final Datum NZGD49 = register("nzgd49", "59.47,-5.04,187.44,0.47,-0.1,1.024,-4.5993", "intl", "New Zealand Geodetic Datum 1949");
    /** Ordnance Survey Great Britain 1936 */
    public static final Datum OSGB36 = register("osgb36", "446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894", "airy", "Ordnance Survey of Great Britain 1936");
    /** Czech S-JTSK datum */
    public static final Datum S_JTSK = register("s_jtsk", "589,76,480", "bessel", "S-JTSK (Ferro)");
    /** Beduaram datum */
    public static final Datum BEDUARAM = register("beduaram", "-106,-87,188", "clrk80", "Beduaram");
    /** Indonesian datum */
    public static final Datum GUNUNG_SEGARA = register("gunung_segara", "-403,684,41", "bessel", "Gunung Segara Jakarta");
    /** Belgian RNB72 datum */
    public static final Datum RNB72 = register("rnb72", "106.869,-52.2978,103.724,-0.33657,0.456955,-1.84218,1", "intl", "Reseau National Belge 1972");

    // Additional EPSG datums (selected important ones)
    public static final Datum EPSG_4314 = register("EPSG_4314", "597.1,71.4,412.1,0.894,0.068,-1.563,7.58", null, null);
    public static final Datum EPSG_4267 = register("EPSG_4267", "-8.0,160.0,176.0", null, null);
    public static final Datum EPSG_4269 = register("EPSG_4269", "0,0,0", null, null);
    public static final Datum EPSG_4230 = register("EPSG_4230", "-68.863,-134.888,-111.49,-0.53,-0.14,0.57,-3.4", null, null);

    // Datums added / corrected from proj4js v2.20.4 (PR #551).
    // Rotation values were corrected from microradians to arcseconds.
    /** Amersfoort datum (Netherlands) — used by EPSG:28992 (RD New) */
    public static final Datum EPSG_4289 = register("EPSG_4289",
            "565.7381,50.4018,465.2904,-0.395026,0.330772,-1.876073,4.07244", "bessel", null);
    /** GDA94 (Geocentric Datum of Australia 1994) */
    public static final Datum EPSG_4283 = register("EPSG_4283",
            "0.06155,-0.01087,-0.04019,0.039492,0.032722,0.032898,-0.009994", "GRS80", null);
    /** NAD83(CSRS) — Canadian Spatial Reference System */
    public static final Datum EPSG_4617 = register("EPSG_4617",
            "-0.991,1.9072,0.5129,0.02579,0.00965,0.01166,0", "GRS80", null);
    /** S-JTSK/05 (Ferro) / Modified Krovak — used by EPSG:5514 (Czech Republic) */
    public static final Datum EPSG_8351 = register("EPSG_8351",
            "485.021,169.465,483.839,7.786342,4.397554,4.102655,0", "bessel", null);

    // ISO 19162 / PROJJSON / WKT2 datum-name aliases.
    // These map the verbose names found in WKT2 and PROJJSON documents to the
    // short PROJ codes already registered above.
    static {
        loadGeneratedDatums();

        registerAlias("World Geodetic System 1984", WGS84);
        registerAlias("World Geodetic System 1984 ensemble", WGS84);
        registerAlias("WGS 84", WGS84);
        registerAlias("North American Datum 1983", NAD83);
        registerAlias("North American Datum of 1983", NAD83);
        registerAlias("North American Datum 1927", NAD27);
        registerAlias("North American Datum of 1927", NAD27);
        registerAlias("Ordnance Survey of Great Britain 1936", OSGB36);
        registerAlias("OSGB 1936", OSGB36);
        // Aliases for newly-added datums (PR #551 corrections)
        registerAlias("Amersfoort", EPSG_4289);
        registerAlias("Geocentric Datum of Australia 1994", EPSG_4283);
        registerAlias("GDA94", EPSG_4283);
        registerAlias("NAD83 Canadian Spatial Reference System", EPSG_4617);
        registerAlias("NAD83(CSRS)", EPSG_4617);
        registerAlias("System of the Unified Trigonometrical Cadastral Network [JTSK03]", EPSG_8351);
    }

    private final String code;
    private final String towgs84;
    private final String ellipse;
    private final String datumName;
    private final String nadgrids;

    private Datum(String code, String towgs84, String ellipse, String datumName, String nadgrids) {
        this.code = code;
        this.towgs84 = towgs84;
        this.ellipse = ellipse;
        this.datumName = datumName;
        this.nadgrids = nadgrids;
    }

    /**
     * Register a datum with towgs84 transformation parameters.
     */
    private static Datum register(String code, String towgs84, String ellipse, String datumName) {
        return register(code, towgs84, ellipse, datumName, null);
    }

    private static Datum register(
            String code, String towgs84, String ellipse, String datumName, String nadgrids) {
        Datum datum = new Datum(code, towgs84, ellipse, datumName, nadgrids);
        registerLookup(code, datum);
        if (datumName != null) {
            registerLookup(datumName, datum);
        }
        return datum;
    }

    /**
     * Register an alias name that maps to an existing datum.
     */
    private static void registerAlias(String alias, Datum datum) {
        registerLookup(alias, datum);
    }

    private static void registerLookup(String lookup, Datum datum) {
        Datum exact = DATUMS.putIfAbsent(lower(lookup), datum);
        if (exact != null && exact != datum) {
            throw new IllegalStateException("Conflicting datum lookup key: " + lookup);
        }

        Datum normalized = NORMALIZED_DATUMS.putIfAbsent(normalizeKey(lookup), datum);
        if (normalized != null && normalized != datum) {
            throw new IllegalStateException("Conflicting normalized datum lookup key: " + lookup);
        }
    }

    /**
     * Register a datum that uses NAD grid shift files instead of towgs84 parameters.
     */
    private static Datum registerWithNadgrids(String code, String nadgrids, String ellipse, String datumName) {
        return register(code, null, ellipse, datumName, nadgrids);
    }

    /**
     * Load the generated proj4js snapshot after the public legacy constants have
     * initialized. Existing constants remain the canonical Java objects; the
     * generated data supplies every datum that was not historically exposed as a
     * public field.
     */
    private static void loadGeneratedDatums() {
        InputStream input = Datum.class.getResourceAsStream(GENERATED_DATUM_RESOURCE);
        if (input == null) {
            throw new IllegalStateException(
                    "Missing generated datum registry: " + GENERATED_DATUM_RESOURCE);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            boolean sawHeader = false;
            Set<String> generatedCodes = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!sawHeader) {
                    if (!"code\ttowgs84\tellipse\tdatumName\tnadgrids".equals(line)) {
                        throw malformedGeneratedRegistry(lineNumber, "unexpected header");
                    }
                    sawHeader = true;
                    continue;
                }

                String[] fields = line.split("\t", -1);
                if (fields.length != 5) {
                    throw malformedGeneratedRegistry(lineNumber, "expected five tab-separated fields");
                }
                for (String field : fields) {
                    if (field.isEmpty()) {
                        throw malformedGeneratedRegistry(
                                lineNumber, "empty field; use \\N for null");
                    }
                }
                if (!generatedCodes.add(lower(fields[0]))) {
                    throw malformedGeneratedRegistry(lineNumber, "duplicate datum code " + fields[0]);
                }
                mergeGeneratedDatum(
                        fields[0],
                        nullMarkerToNull(fields[1]),
                        nullMarkerToNull(fields[2]),
                        nullMarkerToNull(fields[3]),
                        nullMarkerToNull(fields[4]),
                        lineNumber);
            }
            if (!sawHeader) {
                throw malformedGeneratedRegistry(lineNumber, "missing header");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load generated datum registry", e);
        }
    }

    private static void mergeGeneratedDatum(
            String code,
            String towgs84,
            String ellipse,
            String datumName,
            String nadgrids,
            int lineNumber) {
        Datum existing = DATUMS.get(lower(code));
        if (existing == null) {
            register(code, towgs84, ellipse, datumName, nadgrids);
            return;
        }
        if (!lower(existing.code).equals(lower(code))) {
            throw malformedGeneratedRegistry(
                    lineNumber,
                    code + " collides with lookup alias for " + existing.code);
        }

        // Public constants predate the generated registry. Require every upstream
        // value to agree, while retaining Java-only descriptive metadata on four
        // EPSG constants for source and binary compatibility.
        requireGeneratedMatch(code, "towgs84", towgs84, existing.towgs84, lineNumber, true);
        requireGeneratedMatch(code, "nadgrids", nadgrids, existing.nadgrids, lineNumber, true);
        requireGeneratedMatch(code, "ellipse", ellipse, existing.ellipse, lineNumber, false);
        requireGeneratedMatch(code, "datumName", datumName, existing.datumName, lineNumber, false);
    }

    private static void requireGeneratedMatch(
            String code,
            String field,
            String upstream,
            String existing,
            int lineNumber,
            boolean requireNullEquality) {
        if ((requireNullEquality || upstream != null) && !Objects.equals(upstream, existing)) {
            throw malformedGeneratedRegistry(
                    lineNumber,
                    code + " " + field + " differs from its public constant");
        }
    }

    private static IllegalStateException malformedGeneratedRegistry(int lineNumber, String message) {
        return new IllegalStateException(
                "Malformed " + GENERATED_DATUM_RESOURCE + " at line " + lineNumber + ": " + message);
    }

    private static String nullMarkerToNull(String value) {
        return "\\N".equals(value) ? null : value;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Get datum by code or name (case-insensitive).
     *
     * @param code The datum code (e.g., "wgs84") or name (e.g., "WGS84")
     * @return The Datum object, or null if not found
     */
    public static Datum get(String code) {
        if (code == null) {
            return null;
        }
        Datum direct = DATUMS.get(lower(code));
        if (direct != null) {
            return direct;
        }
        // Fuzzy fallback, as proj4js's match.js applies to its datum table:
        // whitespace, underscores, hyphens, slashes and parentheses are ignored,
        // so +datum=s-jtsk resolves to s_jtsk.
        return NORMALIZED_DATUMS.get(normalizeKey(code));
    }

    private static String normalizeKey(String key) {
        return lower(key).replaceAll("[\\s_\\-/()]", "");
    }

    /** @return The datum code (e.g., "wgs84") */
    public String getCode() {
        return code;
    }

    /** @return The towgs84 parameters as a comma-separated string, or null for grid-based datums */
    public String getTowgs84() {
        return towgs84;
    }

    /** @return The associated ellipsoid code (e.g., "WGS84", "bessel") */
    public String getEllipse() {
        return ellipse;
    }

    /** @return The full datum name */
    public String getDatumName() {
        return datumName;
    }

    /** @return The NAD grid file list (comma-separated), or null for towgs84-based datums */
    public String getNadgrids() {
        return nadgrids;
    }

    /**
     * Parse towgs84 string into a double array.
     * 
     * @return Array of 3 or 7 parameters, or null if no towgs84 parameters defined
     */
    public double[] getTowgs84Array() {
        if (towgs84 == null || towgs84.isEmpty()) {
            return null;
        }
        String[] parts = towgs84.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }

    @Override
    public String toString() {
        return "Datum{" +
                "code='" + code + '\'' +
                ", towgs84='" + towgs84 + '\'' +
                ", ellipse='" + ellipse + '\'' +
                ", datumName='" + datumName + '\'' +
                '}';
    }
}
