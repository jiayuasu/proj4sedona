package org.proj4sedona.mgrs;

/**
 * Universal Polar Stereographic (UPS) coordinate converter.
 * 
 * UPS is used for polar regions outside the UTM/MGRS coverage area:
 * &lt;ul&gt;
 * &lt;li&gt;North polar region: latitude &gt; 84°N&lt;/li&gt;
 * &lt;li&gt;South polar region: latitude &lt; -80°&lt;/li&gt;
 * &lt;/ul&gt;
 * 
 * UPS coordinates consist of:
 * - Zone designator: 'A' or 'B' for south pole, 'Y' or 'Z' for north pole
 * - Easting and Northing in meters (false origin at pole: 2,000,000m)
 * 
 * Usage:
 * <pre>
 * // Convert lat/lon to UPS
 * UPSCoordinate ups = UPS.fromLatLon(85.0, 45.0);
 * // ups.zone = 'Z', ups.easting = ..., ups.northing = ...
 * 
 * // Convert UPS to lat/lon
 * double[] latLon = UPS.toLatLon('Z', 2100000, 2000000);
 * </pre>
 */
public final class UPS {

    // WGS84 ellipsoid parameters
    private static final double WGS84_A = 6378137.0;           // Semi-major axis
    private static final double WGS84_F = 1.0 / 298.257223563; // Flattening
    private static final double WGS84_E = Math.sqrt(2 * WGS84_F - WGS84_F * WGS84_F); // Eccentricity

    // UPS constants
    private static final double K0 = 0.994;                    // Scale factor at pole
    private static final double FALSE_EASTING = 2000000.0;     // False easting (meters)
    private static final double FALSE_NORTHING = 2000000.0;    // False northing (meters)

    // Latitude bounds for UPS zones
    private static final double NORTH_LIMIT = 84.0;  // UTM ends at 84°N
    private static final double SOUTH_LIMIT = -80.0; // UTM ends at 80°S

    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;

    private UPS() {
        // Utility class
    }

    /**
     * Check if a latitude is in the UPS north zone (&gt;84°N).
     *
     * @param lat latitude in degrees
     * @return true if latitude is in the north polar zone
     */
    public static boolean isNorthPolar(double lat) {
        return lat > NORTH_LIMIT;
    }

    /**
     * Check if a latitude is in the UPS south zone (&lt;-80°).
     *
     * @param lat latitude in degrees
     * @return true if latitude is in the south polar zone
     */
    public static boolean isSouthPolar(double lat) {
        return lat < SOUTH_LIMIT;
    }

    /**
     * Check if a latitude is in a UPS zone (outside UTM coverage).
     *
     * @param lat latitude in degrees
     * @return true if latitude is in a UPS zone
     */
    public static boolean isUPS(double lat) {
        return isNorthPolar(lat) || isSouthPolar(lat);
    }

    /**
     * Convert latitude/longitude to UPS coordinates.
     * 
     * @param lat Latitude in degrees (-90 to 90)
     * @param lon Longitude in degrees (-180 to 180)
     * @return UPS coordinate with zone, easting, and northing
     * @throws IllegalArgumentException if latitude is within UTM bounds
     */
    public static UPSCoordinate fromLatLon(double lat, double lon) {
        if (!isUPS(lat)) {
            throw new IllegalArgumentException(
                "Latitude " + lat + " is within UTM bounds. Use UTM/MGRS instead.");
        }

        boolean isNorth = lat > 0;
        double latRad = Math.abs(lat) * DEG_TO_RAD;
        double lonRad = lon * DEG_TO_RAD;

        // Calculate conformal latitude
        double sinLat = Math.sin(latRad);
        double t = Math.tan(Math.PI / 4 - latRad / 2) / 
                   Math.pow((1 - WGS84_E * sinLat) / (1 + WGS84_E * sinLat), WGS84_E / 2);

        // Calculate radius
        double rho = 2 * WGS84_A * K0 * t / 
                     Math.sqrt(Math.pow(1 + WGS84_E, 1 + WGS84_E) * Math.pow(1 - WGS84_E, 1 - WGS84_E));

        // Calculate UPS coordinates
        double easting, northing;
        char zone;

        if (isNorth) {
            easting = FALSE_EASTING + rho * Math.sin(lonRad);
            northing = FALSE_NORTHING - rho * Math.cos(lonRad);
            zone = (lon < 0) ? 'Y' : 'Z';
        } else {
            easting = FALSE_EASTING + rho * Math.sin(lonRad);
            northing = FALSE_NORTHING + rho * Math.cos(lonRad);
            zone = (lon < 0) ? 'A' : 'B';
        }

        return new UPSCoordinate(zone, easting, northing);
    }

    /**
     * Convert UPS coordinates to latitude/longitude.
     * 
     * @param zone UPS zone ('A', 'B' for south, 'Y', 'Z' for north)
     * @param easting Easting in meters
     * @param northing Northing in meters
     * @return Array with [latitude, longitude] in degrees
     * @throws IllegalArgumentException if zone is invalid
     */
    public static double[] toLatLon(char zone, double easting, double northing) {
        boolean isNorth;
        switch (zone) {
            case 'A':
            case 'B':
                isNorth = false;
                break;
            case 'Y':
            case 'Z':
                isNorth = true;
                break;
            default:
                throw new IllegalArgumentException("Invalid UPS zone: " + zone);
        }

        // Adjust coordinates relative to false origin
        double x = easting - FALSE_EASTING;
        double y;
        if (isNorth) {
            y = FALSE_NORTHING - northing;
        } else {
            y = northing - FALSE_NORTHING;
        }

        // Calculate radius from pole
        double rho = Math.sqrt(x * x + y * y);

        // Handle pole case
        if (rho < 1e-10) {
            return new double[]{isNorth ? 90.0 : -90.0, 0.0};
        }

        // Calculate longitude
        double lon = Math.atan2(x, y) * RAD_TO_DEG;

        // Calculate latitude using iterative method
        double t = rho * Math.sqrt(Math.pow(1 + WGS84_E, 1 + WGS84_E) * Math.pow(1 - WGS84_E, 1 - WGS84_E)) 
                   / (2 * WGS84_A * K0);

        double lat = Math.PI / 2 - 2 * Math.atan(t);

        // Iterate to refine latitude
        for (int i = 0; i < 10; i++) {
            double sinLat = Math.sin(lat);
            double newLat = Math.PI / 2 - 2 * Math.atan(t * 
                Math.pow((1 - WGS84_E * sinLat) / (1 + WGS84_E * sinLat), WGS84_E / 2));
            
            if (Math.abs(newLat - lat) < 1e-12) {
                break;
            }
            lat = newLat;
        }

        lat = lat * RAD_TO_DEG;
        if (!isNorth) {
            lat = -lat;
        }

        return new double[]{lat, lon};
    }

    /**
     * Convert UPS coordinate to lat/lon.
     * 
     * @param ups UPS coordinate
     * @return Array with [latitude, longitude] in degrees
     */
    public static double[] toLatLon(UPSCoordinate ups) {
        return toLatLon(ups.zone, ups.easting, ups.northing);
    }

    /**
     * Get the UPS zone for a latitude/longitude.
     * 
     * @param lat Latitude in degrees
     * @param lon Longitude in degrees
     * @return UPS zone character ('A', 'B', 'Y', or 'Z')
     * @throws IllegalArgumentException if latitude is within UTM bounds
     */
    public static char getZone(double lat, double lon) {
        if (!isUPS(lat)) {
            throw new IllegalArgumentException(
                "Latitude " + lat + " is within UTM bounds. Use UTM/MGRS instead.");
        }

        if (lat > 0) {
            return (lon < 0) ? 'Y' : 'Z';
        } else {
            return (lon < 0) ? 'A' : 'B';
        }
    }

    /**
     * Represents a UPS coordinate.
     */
    public static class UPSCoordinate {
        /** Zone designator: 'A', 'B' (south), 'Y', 'Z' (north) */
        public final char zone;
        
        /** Easting in meters (false origin: 2,000,000m) */
        public final double easting;
        
        /** Northing in meters (false origin: 2,000,000m) */
        public final double northing;

        public UPSCoordinate(char zone, double easting, double northing) {
            this.zone = zone;
            this.easting = easting;
            this.northing = northing;
        }

        @Override
        public String toString() {
            return String.format("%c %.2f %.2f", zone, easting, northing);
        }

        /**
         * Format as MGRS-style string for polar regions.
         * 
         * @param accuracy Accuracy in digits (1-5)
         * @return MGRS-style string (e.g., "ZAH1234512345")
         */
        public String toMGRSString(int accuracy) {
            // Get 100km grid square
            String grid = get100kGridSquare(zone, easting, northing);
            
            // Format easting/northing with specified accuracy
            int e = (int) Math.round(easting) % 100000;
            int n = (int) Math.round(northing) % 100000;
            
            String eStr = String.format("%05d", e);
            String nStr = String.format("%05d", n);
            
            return String.valueOf(zone) + grid + 
                   eStr.substring(0, accuracy) + 
                   nStr.substring(0, accuracy);
        }

        /**
         * Get the 100km grid square letters for UPS coordinates.
         */
        private static String get100kGridSquare(char zone, double easting, double northing) {
            // UPS 100km grid square calculation
            // North zones (Y, Z) and South zones (A, B) have different grid patterns
            int col = (int) Math.floor(easting / 100000);
            int row = (int) Math.floor(northing / 100000);

            // Column letters: A-Z excluding I and O
            // Row letters vary by hemisphere
            String colLetters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
            String rowLetters;
            
            if (zone == 'Y' || zone == 'Z') {
                // North pole
                rowLetters = "ABCDEFGHJKLMNP";
            } else {
                // South pole
                rowLetters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
            }

            char colLetter = colLetters.charAt(col % colLetters.length());
            char rowLetter = rowLetters.charAt(row % rowLetters.length());

            return String.valueOf(colLetter) + rowLetter;
        }
    }
}
