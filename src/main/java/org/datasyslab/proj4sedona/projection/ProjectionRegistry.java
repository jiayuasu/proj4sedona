package org.datasyslab.proj4sedona.projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Registry of available projection implementations.
 * Mirrors: lib/projections.js
 * 
 * <p>This class maintains a global registry of projection implementations that can be
 * looked up by name. Projections register themselves with multiple aliases (e.g.,
 * "merc", "Mercator", "mercator") to support various naming conventions.</p>
 * 
 * <p>Usage:</p>
 * <pre>
 * // Initialize the registry (called once at startup)
 * ProjectionRegistry.start();
 * 
 * // Get a projection by name
 * Projection merc = ProjectionRegistry.get("merc");
 * </pre>
 * 
 * <p>The registry uses a supplier pattern to create fresh projection instances,
 * ensuring thread safety and avoiding state sharing between projections.</p>
 */
public final class ProjectionRegistry {

    /** Pattern for normalizing projection names (removes dashes, parentheses, whitespace) */
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[-()\\s]+");
    
    /** Store of projection suppliers, indexed by registration order */
    private static final List<Supplier<Projection>> projStore = new ArrayList<>();
    
    /** Map from projection name (lowercase) to index in projStore */
    private static final Map<String, Integer> names = new HashMap<>();

    /**
     * Declared valid PROJ short codes per projection index (first entry is the
     * preferred code). Only built-in projections registered with explicit codes
     * appear here; custom projections registered via {@link #add(Supplier)} do not.
     */
    private static final Map<Integer, List<String>> codesByIndex = new HashMap<>();

    /** All declared valid PROJ short codes (lower-case), across every projection. */
    private static final Set<String> validCodes = new HashSet<>();
    
    /** Flag indicating whether the registry has been initialized */
    private static boolean started = false;

    private ProjectionRegistry() {
        // Utility class - prevent instantiation
    }

    /**
     * Register a projection implementation.
     * 
     * <p>The projection's {@link Projection#getNames()} method is called to get
     * all aliases under which the projection should be registered.</p>
     * 
     * @param projSupplier Supplier that creates new instances of the projection
     */
    public static void add(Supplier<Projection> projSupplier) {
        registerProjection(projSupplier);
    }

    /**
     * Register a built-in projection along with its valid PROJ short codes.
     *
     * <p>The first code is the projection's <em>preferred</em> code — the canonical
     * {@code +proj=} value used when a non-code alias (a WKT/GeoTools method name)
     * is resolved back to this projection. Every listed code is a valid external
     * PROJ token and is returned as its canonical (lower-case) code when supplied
     * directly. External
     * validity cannot be inferred from the alias set (e.g. the registered typo
     * alias {@code gstmerg} re-parses internally but PROJ rejects it), so it is
     * declared here rather than guessed.</p>
     *
     * @param projCodes   valid PROJ short codes, preferred first
     * @param projSupplier supplier that creates new instances of the projection
     */
    public static void add(List<String> projCodes, Supplier<Projection> projSupplier) {
        int index = registerProjection(projSupplier);
        if (index < 0 || projCodes == null || projCodes.isEmpty()) {
            return;
        }
        codesByIndex.put(index, new ArrayList<>(projCodes));
        for (String code : projCodes) {
            String key = code.toLowerCase();
            names.putIfAbsent(key, index);
            validCodes.add(key);
        }
    }

    private static int registerProjection(Supplier<Projection> projSupplier) {
        Projection sample = projSupplier.get();
        String[] projNames = sample.getNames();
        if (projNames == null || projNames.length == 0) {
            return -1;
        }
        int index = projStore.size();
        projStore.add(projSupplier);
        for (String name : projNames) {
            names.put(name.toLowerCase(), index);
        }
        return index;
    }

    /**
     * Resolve a projection name to a canonical PROJ short code (see
     * {@link #add(List, Supplier)}). Starts the registry if needed. An input that is
     * already a declared valid code is preserved; any other registered alias resolves
     * to its projection's preferred code; unknown or custom names (no declared codes)
     * return null, so callers can keep the original spelling.
     */
    public static String resolveProjCode(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        start();
        String lower = name.toLowerCase();
        if (validCodes.contains(lower)) {
            return lower;
        }
        Integer index = names.get(lower);
        if (index == null) {
            index = names.get(getNormalizedProjName(lower));
        }
        if (index != null) {
            List<String> codes = codesByIndex.get(index);
            if (codes != null && !codes.isEmpty()) {
                return codes.get(0);
            }
        }
        return null;
    }

    /** Whether {@code name} is a declared valid PROJ short code. Starts the registry. */
    public static boolean isValidProjCode(String name) {
        if (name == null) {
            return false;
        }
        start();
        return validCodes.contains(name.toLowerCase());
    }

    /**
     * Get a projection by name.
     * 
     * <p>Lookup is case-insensitive. If direct lookup fails, the name is normalized
     * (removing dashes, parentheses, and whitespace) and tried again.</p>
     * 
     * @param name The projection name (e.g., "merc", "longlat", "utm", "Transverse_Mercator")
     * @return A new instance of the projection, or null if not found
     */
    public static Projection get(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Try direct lookup (case-insensitive)
        String n = name.toLowerCase();
        Integer index = names.get(n);
        if (index != null) {
            return projStore.get(index).get();
        }

        // Try normalized name
        String normalized = getNormalizedProjName(n);
        index = names.get(normalized);
        if (index != null) {
            return projStore.get(index).get();
        }

        return null;
    }

    /**
     * Normalize a projection name by replacing special characters with underscores.
     * Mirrors: lib/projections.js getNormalizedProjName()
     * 
     * <p>Example: "Lambert Conformal Conic" becomes "Lambert_Conformal_Conic"</p>
     * 
     * @param name The projection name to normalize
     * @return Normalized name with underscores
     */
    public static String getNormalizedProjName(String name) {
        return NORMALIZE_PATTERN.matcher(name).replaceAll(" ").trim().replace(" ", "_");
    }

    /**
     * Initialize the registry with built-in projections.
     * 
     * <p>This method is thread-safe and idempotent - calling it multiple times
     * has no effect after the first call.</p>
     * 
     * <p>Registered projections:</p>
     * <ul>
     *   <li>LongLat - Geographic (lat/lon) identity projection</li>
     *   <li>Mercator - Standard Mercator projection</li>
     *   <li>ExtendedTransverseMercator - Accurate Transverse Mercator (etmerc/tmerc)</li>
     *   <li>UTM - Universal Transverse Mercator</li>
     *   <li>LambertConformalConic - Lambert Conformal Conic (lcc)</li>
     *   <li>AlbersEqualArea - Albers Equal Area Conic (aea)</li>
     *   <li>EquidistantConic - Equidistant Conic (eqdc)</li>
     *   <li>Stereographic - Polar Stereographic (stere)</li>
     *   <li>LambertAzimuthalEqualArea - Lambert Azimuthal Equal Area (laea)</li>
     *   <li>AzimuthalEquidistant - Azimuthal Equidistant (aeqd)</li>
     *   <li>Sinusoidal - Sinusoidal (sinu)</li>
     *   <li>Mollweide - Mollweide (moll)</li>
     *   <li>Robinson - Robinson (robin)</li>
     *   <li>EquidistantCylindrical - Equidistant Cylindrical / Plate Carrée (eqc)</li>
     *   <li>CylindricalEqualArea - Cylindrical Equal Area (cea)</li>
     * </ul>
     */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        
        // Identity / Geographic
        add(List.of("longlat"), LongLat::new);
        
        // Cylindrical projections
        add(List.of("merc"), Mercator::new);
        add(List.of("tmerc", "etmerc"), ExtendedTransverseMercator::new);
        add(List.of("utm"), UTM::new);
        add(List.of("eqc"), EquidistantCylindrical::new);
        add(List.of("cea"), CylindricalEqualArea::new);
        add(List.of("somerc"), SwissObliqueMercator::new);
        add(List.of("omerc"), ObliqueMercator::new);
        add(List.of("cass"), CassiniSoldner::new);
        add(List.of("mill"), MillerCylindrical::new);
        add(List.of("gstmerc"), GaussSchreiberTransverseMercator::new);

        // Conic projections
        add(List.of("lcc"), LambertConformalConic::new);
        add(List.of("aea"), AlbersEqualArea::new);
        add(List.of("eqdc"), EquidistantConic::new);
        add(List.of("poly"), Polyconic::new);
        add(List.of("krovak"), Krovak::new);
        add(List.of("bonne"), Bonne::new);

        // Azimuthal projections
        add(List.of("stere"), Stereographic::new);
        add(List.of("sterea"), StereographicAlternative::new);
        add(List.of("laea"), LambertAzimuthalEqualArea::new);
        add(List.of("aeqd"), AzimuthalEquidistant::new);
        add(List.of("gnom"), Gnomonic::new);
        add(List.of("ortho"), Orthographic::new);
        add(List.of("tpers"), TiltedPerspective::new);

        // Pseudocylindrical projections
        add(List.of("sinu"), Sinusoidal::new);
        add(List.of("moll"), Mollweide::new);
        add(List.of("robin"), Robinson::new);
        add(List.of("vandg"), VanDerGrinten::new);
        add(List.of("eqearth"), EqualEarth::new);
        add(List.of("eck6"), EckertVI::new);

        // Miscellaneous projections
        add(List.of("geos"), Geostationary::new);
        add(List.of("nzmg"), NewZealandMapGrid::new);
        add(List.of("geocent"), Geocentric::new);
        add(List.of("qsc"), QuadrilateralizedSphericalCube::new);
        add(List.of("ob_tran"), GeneralObliqueTransformation::new);
    }

    /**
     * Check if the registry has been initialized.
     * 
     * @return true if {@link #start()} has been called
     */
    public static boolean isStarted() {
        return started;
    }

    /**
     * Reset the registry to its initial empty state.
     * 
     * <p><strong>Warning:</strong> This is primarily intended for testing.
     * Do not call in production code.</p>
     */
    public static synchronized void reset() {
        projStore.clear();
        names.clear();
        codesByIndex.clear();
        validCodes.clear();
        started = false;
    }
}
