package org.proj4sedona.datum;

import org.proj4sedona.constants.Values;
import org.proj4sedona.core.DatumParams;
import org.proj4sedona.core.Point;
import org.proj4sedona.grid.GridInterpolator;
import org.proj4sedona.grid.GridData;
import org.proj4sedona.grid.GridLoader;
import org.proj4sedona.grid.NadgridInfo;
import org.proj4sedona.grid.Subgrid;

import java.util.ArrayList;
import java.util.List;

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
     * Mirrors: lib/datum_transform.js applyGridShift
     * 
     * @param datum Datum with grid shift information
     * @param inverse If true, apply inverse grid shift
     * @param point Point to transform (modified in place)
     * @return 0 on success, -1 on failure
     */
    private static int applyGridShift(DatumParams datum, boolean inverse, Point point) {
        // Get grid information from nadgrids string
        List<NadgridInfo> grids = datum.getGrids();
        
        // If no grids from DatumParams, try to parse from nadgrids string
        if ((grids == null || grids.isEmpty()) && datum.getNadgrids() != null) {
            grids = GridLoader.getNadgrids(datum.getNadgrids());
        }
        
        if (grids == null || grids.isEmpty()) {
            System.err.println("Grid shift grids not found");
            return -1;
        }

        // Note: proj4js uses negative longitude internally for grid operations
        double inputX = -point.x;
        double inputY = point.y;
        
        double outputX = Double.NaN;
        double outputY = Double.NaN;
        List<String> attemptedGrids = new ArrayList<>();

        // Iterate through grids to find one containing the point
        outer:
        for (NadgridInfo gridInfo : grids) {
            attemptedGrids.add(gridInfo.getName());
            
            // Handle "null" grid - pass through unchanged
            if (gridInfo.isNull()) {
                outputX = inputX;
                outputY = inputY;
                break;
            }
            
            // Check if grid is loaded
            GridData grid = gridInfo.getGrid();
            if (grid == null) {
                if (gridInfo.isMandatory()) {
                    System.err.println("Unable to find mandatory grid '" + gridInfo.getName() + "'");
                    return -1;
                }
                continue;
            }
            
            // Search subgrids for one containing the point
            for (Subgrid subgrid : grid.getSubgrids()) {
                // Check if point is within subgrid bounds
                if (!subgrid.contains(inputX, inputY)) {
                    continue;
                }
                
                // Apply shift
                double[] result;
                if (inverse) {
                    result = GridInterpolator.applyInverse(inputX, inputY, subgrid);
                } else {
                    result = GridInterpolator.applyForward(inputX, inputY, subgrid);
                }
                
                if (!Double.isNaN(result[0])) {
                    outputX = result[0];
                    outputY = result[1];
                    break outer;
                }
            }
        }

        // Check if we found a valid shift
        if (Double.isNaN(outputX)) {
            System.err.println("Failed to find a grid shift table for location '" +
                    (-inputX * Values.R2D) + " " + (inputY * Values.R2D) +
                    "' tried: '" + attemptedGrids + "'");
            return -1;
        }

        // Update point (convert back from negative longitude)
        point.x = -outputX;
        point.y = outputY;
        
        return 0;
    }
}
