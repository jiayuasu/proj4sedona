package org.proj4sedona.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference ellipsoid definitions for geodetic calculations.
 * Mirrors: lib/constants/Ellipsoid.js
 * 
 * <p>An ellipsoid (or spheroid) is a mathematical model of the Earth's shape.
 * The Earth is not a perfect sphere - it's slightly flattened at the poles
 * due to rotation. Different ellipsoids have been defined over time to best
 * fit different regions of the Earth.</p>
 * 
 * <p>Key parameters:</p>
 * <ul>
 *   <li><b>a</b> - Semi-major axis (equatorial radius) in meters</li>
 *   <li><b>b</b> - Semi-minor axis (polar radius) in meters</li>
 *   <li><b>rf</b> - Inverse flattening: rf = a / (a - b)</li>
 * </ul>
 * 
 * <p>The flattening (f) describes how much the ellipsoid deviates from a sphere:
 * f = (a - b) / a. The inverse flattening rf = 1/f is typically used because
 * it's a larger, more convenient number.</p>
 * 
 * <p>Common ellipsoids:</p>
 * <ul>
 *   <li><b>WGS84</b> - Used by GPS, the current global standard</li>
 *   <li><b>GRS80</b> - Very similar to WGS84, used by NAD83</li>
 *   <li><b>Clarke 1866</b> - Used by NAD27 (North American Datum 1927)</li>
 *   <li><b>Bessel 1841</b> - Used in Central Europe, Japan</li>
 *   <li><b>Airy 1830</b> - Used in Great Britain</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>
 * Ellipsoid wgs84 = Ellipsoid.get("WGS84");
 * double semiMajor = wgs84.getA();  // 6378137.0 meters
 * double semiMinor = wgs84.getB();  // 6356752.3142... meters
 * </pre>
 */
public final class Ellipsoid {

    /** Registry of all known ellipsoids, keyed by lowercase code */
    private static final Map<String, Ellipsoid> ELLIPSOIDS = new HashMap<>();

    // All ellipsoids from proj4js Ellipsoid.js
    public static final Ellipsoid MERIT = register("MERIT", 6378137, 298.257, "MERIT 1983");
    public static final Ellipsoid SGS85 = register("SGS85", 6378136, 298.257, "Soviet Geodetic System 85");
    /** GRS 1980 - used by NAD83, nearly identical to WGS84 */
    public static final Ellipsoid GRS80 = register("GRS80", 6378137, 298.257222101, "GRS 1980(IUGG, 1980)");
    public static final Ellipsoid IAU76 = register("IAU76", 6378140, 298.257, "IAU 1976");
    /** Airy 1830 - used by OSGB36 (Great Britain) */
    public static final Ellipsoid AIRY = registerWithB("airy", 6377563.396, 6356256.91, "Airy 1830");
    public static final Ellipsoid APL4 = register("APL4", 6378137, 298.25, "Appl. Physics. 1965");
    public static final Ellipsoid NWL9D = register("NWL9D", 6378145, 298.25, "Naval Weapons Lab., 1965");
    /** Modified Airy - used by Ireland 1965 */
    public static final Ellipsoid MOD_AIRY = registerWithB("mod_airy", 6377340.189, 6356034.446, "Modified Airy");
    public static final Ellipsoid ANDRAE = register("andrae", 6377104.43, 300, "Andrae 1876 (Den., Iclnd.)");
    public static final Ellipsoid AUST_SA = register("aust_SA", 6378160, 298.25, "Australian Natl & S. Amer. 1969");
    public static final Ellipsoid GRS67 = register("GRS67", 6378160, 298.247167427, "GRS 67(IUGG 1967)");
    /** Bessel 1841 - used extensively in Central Europe and Japan */
    public static final Ellipsoid BESSEL = register("bessel", 6377397.155, 299.1528128, "Bessel 1841");
    public static final Ellipsoid BESS_NAM = register("bess_nam", 6377483.865, 299.1528128, "Bessel 1841 (Namibia)");
    /** Clarke 1866 - used by NAD27 (North America) */
    public static final Ellipsoid CLRK66 = registerWithB("clrk66", 6378206.4, 6356583.8, "Clarke 1866");
    /** Clarke 1880 modified */
    public static final Ellipsoid CLRK80 = register("clrk80", 6378249.145, 293.4663, "Clarke 1880 mod.");
    public static final Ellipsoid CLRK80IGN = registerWithBAndRf("clrk80ign", 6378249.2, 6356515, 293.4660213, "Clarke 1880 (IGN)");
    public static final Ellipsoid CLRK58 = register("clrk58", 6378293.645208759, 294.2606763692654, "Clarke 1858");
    public static final Ellipsoid CPM = register("CPM", 6375738.7, 334.29, "Comm. des Poids et Mesures 1799");
    public static final Ellipsoid DELMBR = register("delmbr", 6376428, 311.5, "Delambre 1810 (Belgium)");
    public static final Ellipsoid ENGELIS = register("engelis", 6378136.05, 298.2566, "Engelis 1985");
    public static final Ellipsoid EVRST30 = register("evrst30", 6377276.345, 300.8017, "Everest 1830");
    public static final Ellipsoid EVRST48 = register("evrst48", 6377304.063, 300.8017, "Everest 1948");
    public static final Ellipsoid EVRST56 = register("evrst56", 6377301.243, 300.8017, "Everest 1956");
    public static final Ellipsoid EVRST69 = register("evrst69", 6377295.664, 300.8017, "Everest 1969");
    public static final Ellipsoid EVRSTSS = register("evrstSS", 6377298.556, 300.8017, "Everest (Sabah & Sarawak)");
    public static final Ellipsoid FSCHR60 = register("fschr60", 6378166, 298.3, "Fischer (Mercury Datum) 1960");
    public static final Ellipsoid FSCHR60M = register("fschr60m", 6378155, 298.3, "Fischer 1960");
    public static final Ellipsoid FSCHR68 = register("fschr68", 6378150, 298.3, "Fischer 1968");
    public static final Ellipsoid HELMERT = register("helmert", 6378200, 298.3, "Helmert 1906");
    public static final Ellipsoid HOUGH = register("hough", 6378270, 297, "Hough");
    /** International 1909 (Hayford) - basis for many local datums */
    public static final Ellipsoid INTL = register("intl", 6378388, 297, "International 1909 (Hayford)");
    public static final Ellipsoid KAULA = register("kaula", 6378163, 298.24, "Kaula 1961");
    public static final Ellipsoid LERCH = register("lerch", 6378139, 298.257, "Lerch 1979");
    public static final Ellipsoid MPRTS = register("mprts", 6397300, 191, "Maupertius 1738");
    public static final Ellipsoid NEW_INTL = registerWithB("new_intl", 6378157.5, 6356772.2, "New International 1967");
    public static final Ellipsoid PLESSIS = registerWithB("plessis", 6376523, 6355863, "Plessis 1817 (France)");
    /** Krassovsky 1942 - used in Russia and Eastern Europe */
    public static final Ellipsoid KRASS = register("krass", 6378245, 298.3, "Krassovsky, 1942");
    public static final Ellipsoid SEASIA = registerWithB("SEasia", 6378155, 6356773.3205, "Southeast Asia");
    public static final Ellipsoid WALBECK = registerWithB("walbeck", 6376896, 6355834.8467, "Walbeck");
    public static final Ellipsoid WGS60 = register("WGS60", 6378165, 298.3, "WGS 60");
    public static final Ellipsoid WGS66 = register("WGS66", 6378145, 298.25, "WGS 66");
    public static final Ellipsoid WGS7 = register("WGS7", 6378135, 298.26, "WGS 72");
    /** WGS84 - the GPS reference ellipsoid, current global standard */
    public static final Ellipsoid WGS84 = register("WGS84", 6378137, 298.257223563, "WGS 84");
    /** Perfect sphere - used for some projections and approximate calculations */
    public static final Ellipsoid SPHERE = registerWithB("sphere", 6370997, 6370997, "Normal Sphere (r=6370997)");

    private final String code;
    private final double a;           // Semi-major axis (equatorial radius) in meters
    private final double b;           // Semi-minor axis (polar radius) in meters
    private final double rf;          // Inverse flattening
    private final String ellipseName;

    private Ellipsoid(String code, double a, double b, double rf, String ellipseName) {
        this.code = code;
        this.a = a;
        this.b = b;
        this.rf = rf;
        this.ellipseName = ellipseName;
    }

    /**
     * Register ellipsoid with semi-major axis and inverse flattening.
     * Semi-minor axis is calculated: b = a * (1 - 1/rf)
     */
    private static Ellipsoid register(String code, double a, double rf, String name) {
        double b = (1.0 - 1.0 / rf) * a;
        Ellipsoid e = new Ellipsoid(code, a, b, rf, name);
        ELLIPSOIDS.put(code.toLowerCase(), e);
        return e;
    }

    /**
     * Register ellipsoid with semi-major and semi-minor axes.
     * Inverse flattening is calculated: rf = a / (a - b)
     */
    private static Ellipsoid registerWithB(String code, double a, double b, String name) {
        double rf = a / (a - b);
        Ellipsoid e = new Ellipsoid(code, a, b, rf, name);
        ELLIPSOIDS.put(code.toLowerCase(), e);
        return e;
    }

    /**
     * Register ellipsoid with all parameters explicitly specified.
     * Used when values don't compute exactly due to historical definitions.
     */
    private static Ellipsoid registerWithBAndRf(String code, double a, double b, double rf, String name) {
        Ellipsoid e = new Ellipsoid(code, a, b, rf, name);
        ELLIPSOIDS.put(code.toLowerCase(), e);
        return e;
    }

    /**
     * Get ellipsoid by code (case-insensitive).
     *
     * @param code The ellipsoid code (e.g., "WGS84", "bessel", "clrk66")
     * @return The Ellipsoid object, or null if not found
     */
    public static Ellipsoid get(String code) {
        if (code == null) {
            return null;
        }
        return ELLIPSOIDS.get(code.toLowerCase());
    }

    /** @return The ellipsoid code (e.g., "WGS84") */
    public String getCode() {
        return code;
    }

    /** @return Semi-major axis (equatorial radius) in meters */
    public double getA() {
        return a;
    }

    /** @return Semi-minor axis (polar radius) in meters */
    public double getB() {
        return b;
    }

    /** @return Inverse flattening: rf = a / (a - b) */
    public double getRf() {
        return rf;
    }

    /** @return The full ellipsoid name */
    public String getEllipseName() {
        return ellipseName;
    }

    @Override
    public String toString() {
        return "Ellipsoid{" +
                "code='" + code + '\'' +
                ", a=" + a +
                ", b=" + b +
                ", rf=" + rf +
                ", name='" + ellipseName + '\'' +
                '}';
    }
}
