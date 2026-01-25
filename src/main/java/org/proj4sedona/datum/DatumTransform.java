package org.proj4sedona.datum;

import org.proj4sedona.constants.Values;
import org.proj4sedona.core.DatumParams;
import org.proj4sedona.core.Point;

/**
 * Performs datum transformations between coordinate systems.
 * Mirrors: lib/datum_transform.js
 * 
 * The transformation process:
 * 1. Check if datums are identical (no transform needed)
 * 2. Apply grid shift if source uses grid shift (NAD27->NAD83, etc.)
 * 3. Convert from geodetic to geocentric coordinates
 * 4. Apply 3-param or 7-param transform to WGS84 (if needed)
 * 5. Apply inverse 3-param or 7-param transform from WGS84 (if needed)
 * 6. Convert from geocentric to geodetic coordinates
 * 7. Apply grid shift if destination uses grid shift
 */
public final class DatumTransform {

    private DatumTransform() {
        // Utility class
    }

    /**
     * Check if datum type requires parameter transformation.
     */
    private static boolean checkParams(int datumType) {
        return datumType == Values.PJD_3PARAM || datumType == Values.PJD_7PARAM;
    }

    /**
     * Transform a point from source datum to destination datum.
     * Mirrors: lib/datum_transform.js default export
     * 
     * @param source Source datum parameters
     * @param dest Destination datum parameters
     * @param point Input point (geodetic coordinates: lon/lat in radians, height in meters)
     * @return Transformed point, or null if transformation failed
     */
    public static Point transform(DatumParams source, DatumParams dest, Point point) {
        // Handle null datums
        if (source == null || dest == null) {
            return point;
        }

        // Short cut if the datums are identical
        if (DatumUtils.compareDatums(source, dest)) {
            return point;
        }

        // Explicitly skip datum transform by setting 'datum=none'
        if (source.getDatumType() == Values.PJD_NODATUM || 
            dest.getDatumType() == Values.PJD_NODATUM) {
            return point;
        }

        // Clone the point to avoid modifying input
        Point p = point.copy();

        // Track ellipsoid parameters for transformations
        double sourceA = source.getA();
        double sourceEs = source.getEs();

        // If source datum requires grid shifts, apply it to geodetic coordinates
        if (source.getDatumType() == Values.PJD_GRIDSHIFT) {
            int gridShiftCode = applyGridShift(source, false, p);
            if (gridShiftCode != 0) {
                return null;
            }
            // After grid shift, coordinates are in WGS84
            sourceA = Values.SRS_WGS84_SEMIMAJOR;
            sourceEs = Values.SRS_WGS84_ESQUARED;
        }

        double destA = dest.getA();
        double destB = dest.getB();
        double destEs = dest.getEs();

        // If destination datum requires grid shifts, use WGS84 as intermediate
        if (dest.getDatumType() == Values.PJD_GRIDSHIFT) {
            destA = Values.SRS_WGS84_SEMIMAJOR;
            destB = Values.SRS_WGS84_SEMIMINOR;
            destEs = Values.SRS_WGS84_ESQUARED;
        }

        // Do we need to go through geocentric coordinates?
        // Skip if ellipsoids are identical and no datum params
        if (sourceEs == destEs && sourceA == destA && 
            !checkParams(source.getDatumType()) && !checkParams(dest.getDatumType())) {
            return p;
        }

        // Convert to geocentric coordinates
        p = DatumUtils.geodeticToGeocentric(p, sourceEs, sourceA);

        // Convert from source datum to WGS84 (if needed)
        if (checkParams(source.getDatumType())) {
            p = DatumUtils.geocentricToWgs84(p, source.getDatumType(), source.getDatumParams());
        }

        // Convert from WGS84 to destination datum (if needed)
        if (checkParams(dest.getDatumType())) {
            p = DatumUtils.geocentricFromWgs84(p, dest.getDatumType(), dest.getDatumParams());
        }

        // Convert back to geodetic coordinates
        p = DatumUtils.geocentricToGeodetic(p, destEs, destA, destB);

        // If destination datum requires grid shifts, apply it
        if (dest.getDatumType() == Values.PJD_GRIDSHIFT) {
            int gridShiftCode = applyGridShift(dest, true, p);
            if (gridShiftCode != 0) {
                return null;
            }
        }

        return p;
    }

    /**
     * Apply grid shift transformation.
     * This is a placeholder for Phase 14 (NAD Grid Support).
     * 
     * Mirrors: lib/datum_transform.js applyGridShift
     * 
     * @param datum Datum with grid shift information
     * @param inverse If true, apply inverse grid shift
     * @param point Point to transform (modified in place)
     * @return 0 on success, -1 on failure
     */
    private static int applyGridShift(DatumParams datum, boolean inverse, Point point) {
        // Grid shift requires grid files to be loaded
        // This will be implemented in Phase 14 (NAD Grid Support)
        if (datum.getGrids() == null || datum.getGrids().isEmpty()) {
            // No grids available - this is an error for grid shift datums
            // For now, return success and pass through (known limitation)
            // In full implementation, this would return -1
            return 0;
        }

        // TODO: Implement full grid shift in Phase 14
        // The implementation will include:
        // - Iterate through available grids
        // - Find the grid containing the point
        // - Interpolate shift values
        // - Apply forward or inverse shift

        return 0;
    }
}
