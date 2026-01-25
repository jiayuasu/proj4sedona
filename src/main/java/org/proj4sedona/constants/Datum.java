package org.proj4sedona.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Datum definitions with towgs84 parameters.
 * Mirrors: lib/constants/Datum.js
 */
public final class Datum {

    private static final Map<String, Datum> DATUMS = new HashMap<>();

    // Core datums from proj4js
    public static final Datum WGS84 = register("wgs84", "0,0,0", "WGS84", "WGS84");
    public static final Datum CH1903 = register("ch1903", "674.374,15.056,405.346", "bessel", "swiss");
    public static final Datum GGRS87 = register("ggrs87", "-199.87,74.79,246.62", "GRS80", "Greek_Geodetic_Reference_System_1987");
    public static final Datum NAD83 = register("nad83", "0,0,0", "GRS80", "North_American_Datum_1983");
    public static final Datum NAD27 = registerWithNadgrids("nad27", "@conus,@alaska,@ntv2_0.gsb,@ntv1_can.dat", "clrk66", "North_American_Datum_1927");
    public static final Datum POTSDAM = register("potsdam", "598.1,73.7,418.2,0.202,0.045,-2.455,6.7", "bessel", "Potsdam Rauenberg 1950 DHDN");
    public static final Datum CARTHAGE = register("carthage", "-263.0,6.0,431.0", "clark80", "Carthage 1934 Tunisia");
    public static final Datum HERMANNSKOGEL = register("hermannskogel", "577.326,90.129,463.919,5.137,1.474,5.297,2.4232", "bessel", "Hermannskogel");
    public static final Datum MGI = register("mgi", "577.326,90.129,463.919,5.137,1.474,5.297,2.4232", "bessel", "Militar-Geographische Institut");
    public static final Datum OSNI52 = register("osni52", "482.530,-130.596,564.557,-1.042,-0.214,-0.631,8.15", "airy", "Irish National");
    public static final Datum IRE65 = register("ire65", "482.530,-130.596,564.557,-1.042,-0.214,-0.631,8.15", "mod_airy", "Ireland 1965");
    public static final Datum RASSADIRAN = register("rassadiran", "-133.63,-157.5,-158.62", "intl", "Rassadiran");
    public static final Datum NZGD49 = register("nzgd49", "59.47,-5.04,187.44,0.47,-0.1,1.024,-4.5993", "intl", "New Zealand Geodetic Datum 1949");
    public static final Datum OSGB36 = register("osgb36", "446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894", "airy", "Ordnance Survey of Great Britain 1936");
    public static final Datum S_JTSK = register("s_jtsk", "589,76,480", "bessel", "S-JTSK (Ferro)");
    public static final Datum BEDUARAM = register("beduaram", "-106,-87,188", "clrk80", "Beduaram");
    public static final Datum GUNUNG_SEGARA = register("gunung_segara", "-403,684,41", "bessel", "Gunung Segara Jakarta");
    public static final Datum RNB72 = register("rnb72", "106.869,-52.2978,103.724,-0.33657,0.456955,-1.84218,1", "intl", "Reseau National Belge 1972");

    // Additional EPSG datums (selected important ones - the full list has 200+ entries)
    public static final Datum EPSG_4314 = register("EPSG_4314", "597.1,71.4,412.1,0.894,0.068,-1.563,7.58", null, null);
    public static final Datum EPSG_4267 = register("EPSG_4267", "-8.0,160.0,176.0", null, null);
    public static final Datum EPSG_4269 = register("EPSG_4269", "0,0,0", null, null);
    public static final Datum EPSG_4230 = register("EPSG_4230", "-68.863,-134.888,-111.49,-0.53,-0.14,0.57,-3.4", null, null);

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

    private static Datum register(String code, String towgs84, String ellipse, String datumName) {
        Datum d = new Datum(code, towgs84, ellipse, datumName, null);
        DATUMS.put(code.toLowerCase(), d);
        if (datumName != null) {
            DATUMS.put(datumName.toLowerCase(), d);
        }
        return d;
    }

    private static Datum registerWithNadgrids(String code, String nadgrids, String ellipse, String datumName) {
        Datum d = new Datum(code, null, ellipse, datumName, nadgrids);
        DATUMS.put(code.toLowerCase(), d);
        if (datumName != null) {
            DATUMS.put(datumName.toLowerCase(), d);
        }
        return d;
    }

    /**
     * Get datum by code (case-insensitive).
     */
    public static Datum get(String code) {
        if (code == null) {
            return null;
        }
        return DATUMS.get(code.toLowerCase());
    }

    public String getCode() {
        return code;
    }

    public String getTowgs84() {
        return towgs84;
    }

    public String getEllipse() {
        return ellipse;
    }

    public String getDatumName() {
        return datumName;
    }

    public String getNadgrids() {
        return nadgrids;
    }

    /**
     * Parse towgs84 string into double array.
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
