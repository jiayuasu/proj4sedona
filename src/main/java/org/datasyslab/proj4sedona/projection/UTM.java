package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Universal Transverse Mercator (UTM) projection implementation.
 * Mirrors: lib/projections/utm.js
 * 
 * <p>UTM is a global map projection system that divides the world into 60 zones,
 * each 6 degrees of longitude wide. It is based on the Transverse Mercator projection
 * with specific parameters for each zone.</p>
 * 
 * <p>Zone characteristics:</p>
 * <ul>
 *   <li>Central meridian: calculated from zone number</li>
 *   <li>Scale factor (k0): 0.9996</li>
 *   <li>False easting: 500,000 meters</li>
 *   <li>False northing: 0 (northern hemisphere) or 10,000,000 (southern hemisphere)</li>
 * </ul>
 * 
 * <p>PROJ string examples:</p>
 * <pre>
 * +proj=utm +zone=32 +datum=WGS84           // UTM zone 32N
 * +proj=utm +zone=56 +south +datum=WGS84    // UTM zone 56S (Sydney area)
 * </pre>
 * 
 * <p>This implementation delegates to {@link ExtendedTransverseMercator} for the
 * actual projection math, after setting up the UTM-specific parameters.</p>
 */
public class UTM implements Projection {

    private static final String[] NAMES = {
        "Universal Transverse Mercator System", 
        "Universal_Transverse_Mercator", 
        "utm"
    };

    /** Delegate to etmerc for actual projection calculations */
    private final ExtendedTransverseMercator etmerc = new ExtendedTransverseMercator();

    @Override
    public String[] getNames() {
        return NAMES;
    }

    /**
     * Initialize the UTM projection.
     * 
     * <p>Sets up UTM-specific parameters based on the zone number:</p>
     * <ul>
     *   <li>lat0 = 0 (equator)</li>
     *   <li>long0 = (zone * 6 - 183)° (central meridian)</li>
     *   <li>k0 = 0.9996 (UTM scale factor)</li>
     *   <li>x0 = 500,000 m (false easting)</li>
     *   <li>y0 = 0 or 10,000,000 m (false northing for N/S hemisphere)</li>
     * </ul>
     * 
     * @param params Projection parameters (must include zone or long0)
     * @throws IllegalArgumentException if zone cannot be determined
     */
    @Override
    public void init(ProjectionParams params) {
        // Determine zone from explicit zone parameter or from longitude
        int zone = ProjMath.adjustZone(params.zone, params.getLong0());
        if (zone == 0) {
            throw new IllegalArgumentException("Unknown UTM zone");
        }

        // Set UTM-specific parameters
        params.lat0 = 0.0;
        params.long0 = ((6 * Math.abs(zone)) - 183) * Values.D2R;
        params.x0 = 500000.0;
        params.y0 = Boolean.TRUE.equals(params.utmSouth) ? 10000000.0 : 0.0;
        params.k0 = 0.9996;

        // Initialize the underlying etmerc projection
        etmerc.init(params);
    }

    /**
     * Forward projection: geographic (lon/lat in radians) to UTM (easting/northing in meters).
     */
    @Override
    public Point forward(Point p) {
        return etmerc.forward(p);
    }

    /**
     * Inverse projection: UTM (easting/northing in meters) to geographic (lon/lat in radians).
     */
    @Override
    public Point inverse(Point p) {
        return etmerc.inverse(p);
    }
}
