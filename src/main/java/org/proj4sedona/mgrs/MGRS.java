package org.proj4sedona.mgrs;

/**
 * Military Grid Reference System (MGRS) coordinate converter.
 * Mirrors: node_modules/mgrs/mgrs.js
 * 
 * MGRS is a geocoordinate standard used by NATO militaries for locating points
 * on Earth. It is derived from the UTM system, but uses a different notation.
 * 
 * An MGRS coordinate consists of:
 * - Zone number (1-60)
 * - Zone letter (C-X, excluding I and O)
 * - 100km grid square (two letters)
 * - Easting and northing within the grid square (variable precision)
 * 
 * Example: "33UUP0500011950" represents a point in UTM zone 33U
 * 
 * Usage:
 * <pre>
 * // Convert lat/lon to MGRS
 * String mgrs = MGRS.forward(new double[]{-77.0, 38.9}, 5);
 * // Returns: "18SUJ2338308450"
 * 
 * // Convert MGRS to lat/lon
 * double[] point = MGRS.toPoint("18SUJ2338308450");
 * // Returns: [-77.0, 38.9]
 * 
 * // Get bounding box for MGRS reference
 * double[] bbox = MGRS.inverse("18SUJ23384");
 * // Returns: [left, bottom, right, top]
 * </pre>
 */
public final class MGRS {

    // ========== Constants ==========

    /** Number of 100K sets used in MGRS */
    private static final int NUM_100K_SETS = 6;

    /** Column letters at the origin of each set (for easting) */
    private static final String SET_ORIGIN_COLUMN_LETTERS = "AJSAJS";

    /** Row letters at the origin of each set (for northing) */
    private static final String SET_ORIGIN_ROW_LETTERS = "AFAFAF";

    /** ASCII code for 'A' */
    private static final int A = 65;

    /** ASCII code for 'I' (excluded from MGRS) */
    private static final int I = 73;

    /** ASCII code for 'O' (excluded from MGRS) */
    private static final int O = 79;

    /** ASCII code for 'V' */
    private static final int V = 86;

    /** ASCII code for 'Z' */
    private static final int Z = 90;

    /** WGS84 semi-major axis */
    private static final double WGS84_A = 6378137.0;

    /** WGS84 eccentricity squared */
    private static final double WGS84_ECC_SQUARED = 0.00669438;

    /** UTM scale factor */
    private static final double K0 = 0.9996;

    /** Degrees to radians conversion factor */
    private static final double DEG_TO_RAD = Math.PI / 180.0;

    /** Radians to degrees conversion factor */
    private static final double RAD_TO_DEG = 180.0 / Math.PI;

    private MGRS() {
        // Utility class
    }

    // ========== Public API ==========

    /**
     * Convert latitude/longitude to MGRS string.
     * 
     * @param lonLat Array with [longitude, latitude] in degrees (WGS84)
     * @param accuracy Accuracy in digits (1-5): 5=1m, 4=10m, 3=100m, 2=1km, 1=10km
     * @return MGRS string for the given location
     * @throws IllegalArgumentException if coordinates are outside MGRS bounds
     */
    public static String forward(double[] lonLat, int accuracy) {
        if (accuracy < 1 || accuracy > 5) {
            accuracy = 5; // Default to 1m accuracy
        }
        UTMCoordinate utm = llToUTM(lonLat[1], lonLat[0]);
        return encode(utm, accuracy);
    }

    /**
     * Convert latitude/longitude to MGRS string with default 1m accuracy.
     * 
     * @param lonLat Array with [longitude, latitude] in degrees (WGS84)
     * @return MGRS string for the given location
     */
    public static String forward(double[] lonLat) {
        return forward(lonLat, 5);
    }

    /**
     * Convert MGRS string to bounding box.
     * 
     * @param mgrs MGRS string
     * @return Array with [left, bottom, right, top] in degrees (WGS84)
     * @throws IllegalArgumentException if MGRS string is invalid
     */
    public static double[] inverse(String mgrs) {
        UTMCoordinate utm = decode(mgrs.toUpperCase());
        LatLonResult result = utmToLL(utm);
        
        if (result.isPoint) {
            return new double[]{result.lon, result.lat, result.lon, result.lat};
        }
        return new double[]{result.left, result.bottom, result.right, result.top};
    }

    /**
     * Convert MGRS string to center point.
     * 
     * @param mgrs MGRS string
     * @return Array with [longitude, latitude] in degrees (WGS84)
     * @throws IllegalArgumentException if MGRS string is invalid
     */
    public static double[] toPoint(String mgrs) {
        UTMCoordinate utm = decode(mgrs.toUpperCase());
        LatLonResult result = utmToLL(utm);
        
        if (result.isPoint) {
            return new double[]{result.lon, result.lat};
        }
        return new double[]{
            (result.left + result.right) / 2,
            (result.top + result.bottom) / 2
        };
    }

    // ========== UTM Conversion ==========

    /**
     * Convert lat/lon to UTM coordinates using WGS84.
     */
    private static UTMCoordinate llToUTM(double lat, double lon) {
        double latRad = lat * DEG_TO_RAD;
        double lonRad = lon * DEG_TO_RAD;

        // Calculate zone number
        int zoneNumber = (int) Math.floor((lon + 180) / 6) + 1;

        // Special case for longitude 180
        if (lon == 180) {
            zoneNumber = 60;
        }

        // Special zone for Norway
        if (lat >= 56.0 && lat < 64.0 && lon >= 3.0 && lon < 12.0) {
            zoneNumber = 32;
        }

        // Special zones for Svalbard
        if (lat >= 72.0 && lat < 84.0) {
            if (lon >= 0.0 && lon < 9.0) {
                zoneNumber = 31;
            } else if (lon >= 9.0 && lon < 21.0) {
                zoneNumber = 33;
            } else if (lon >= 21.0 && lon < 33.0) {
                zoneNumber = 35;
            } else if (lon >= 33.0 && lon < 42.0) {
                zoneNumber = 37;
            }
        }

        double longOrigin = (zoneNumber - 1) * 6 - 180 + 3;
        double longOriginRad = longOrigin * DEG_TO_RAD;

        double eccPrimeSquared = WGS84_ECC_SQUARED / (1 - WGS84_ECC_SQUARED);

        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);
        double tanLat = Math.tan(latRad);

        double N = WGS84_A / Math.sqrt(1 - WGS84_ECC_SQUARED * sinLat * sinLat);
        double T = tanLat * tanLat;
        double C = eccPrimeSquared * cosLat * cosLat;
        double A = cosLat * (lonRad - longOriginRad);

        double M = WGS84_A * (
            (1 - WGS84_ECC_SQUARED / 4 - 3 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 64 
                - 5 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 256) * latRad
            - (3 * WGS84_ECC_SQUARED / 8 + 3 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 32 
                + 45 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 1024) * Math.sin(2 * latRad)
            + (15 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 256 
                + 45 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 1024) * Math.sin(4 * latRad)
            - (35 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 3072) * Math.sin(6 * latRad)
        );

        double easting = K0 * N * (A + (1 - T + C) * A * A * A / 6.0 
            + (5 - 18 * T + T * T + 72 * C - 58 * eccPrimeSquared) * A * A * A * A * A / 120.0) 
            + 500000.0;

        double northing = K0 * (M + N * tanLat * (A * A / 2 
            + (5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24.0 
            + (61 - 58 * T + T * T + 600 * C - 330 * eccPrimeSquared) * A * A * A * A * A * A / 720.0));

        if (lat < 0.0) {
            northing += 10000000.0; // Southern hemisphere offset
        }

        return new UTMCoordinate(
            Math.round(northing),
            Math.round(easting),
            zoneNumber,
            getLetterDesignator(lat),
            0 // No accuracy for forward conversion
        );
    }

    /**
     * Convert UTM coordinates to lat/lon using WGS84.
     */
    private static LatLonResult utmToLL(UTMCoordinate utm) {
        if (utm.zoneNumber < 0 || utm.zoneNumber > 60) {
            throw new IllegalArgumentException("Invalid UTM zone number: " + utm.zoneNumber);
        }

        double eccPrimeSquared = WGS84_ECC_SQUARED / (1 - WGS84_ECC_SQUARED);
        double e1 = (1 - Math.sqrt(1 - WGS84_ECC_SQUARED)) / (1 + Math.sqrt(1 - WGS84_ECC_SQUARED));

        double x = utm.easting - 500000.0;
        double y = utm.northing;

        // Southern hemisphere adjustment
        if (utm.zoneLetter < 'N') {
            y -= 10000000.0;
        }

        double longOrigin = (utm.zoneNumber - 1) * 6 - 180 + 3;

        double M = y / K0;
        double mu = M / (WGS84_A * (1 - WGS84_ECC_SQUARED / 4 
            - 3 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 64 
            - 5 * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED * WGS84_ECC_SQUARED / 256));

        double phi1Rad = mu 
            + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * Math.sin(2 * mu) 
            + (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32) * Math.sin(4 * mu) 
            + (151 * e1 * e1 * e1 / 96) * Math.sin(6 * mu);

        double sinPhi1 = Math.sin(phi1Rad);
        double cosPhi1 = Math.cos(phi1Rad);
        double tanPhi1 = Math.tan(phi1Rad);

        double N1 = WGS84_A / Math.sqrt(1 - WGS84_ECC_SQUARED * sinPhi1 * sinPhi1);
        double T1 = tanPhi1 * tanPhi1;
        double C1 = eccPrimeSquared * cosPhi1 * cosPhi1;
        double R1 = WGS84_A * (1 - WGS84_ECC_SQUARED) 
            / Math.pow(1 - WGS84_ECC_SQUARED * sinPhi1 * sinPhi1, 1.5);
        double D = x / (N1 * K0);

        double lat = phi1Rad - (N1 * tanPhi1 / R1) * (
            D * D / 2 
            - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * eccPrimeSquared) * D * D * D * D / 24 
            + (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * eccPrimeSquared - 3 * C1 * C1) 
                * D * D * D * D * D * D / 720
        );
        lat = lat * RAD_TO_DEG;

        double lon = (D - (1 + 2 * T1 + C1) * D * D * D / 6 
            + (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * eccPrimeSquared + 24 * T1 * T1) 
                * D * D * D * D * D / 120) / cosPhi1;
        lon = longOrigin + lon * RAD_TO_DEG;

        if (utm.accuracy > 0) {
            // Return bounding box
            LatLonResult topRight = utmToLL(new UTMCoordinate(
                utm.northing + utm.accuracy,
                utm.easting + utm.accuracy,
                utm.zoneNumber,
                utm.zoneLetter,
                0
            ));
            return new LatLonResult(lat, lon, topRight.lat, topRight.lon);
        }

        return new LatLonResult(lat, lon);
    }

    // ========== MGRS Encoding/Decoding ==========

    /**
     * Encode UTM coordinates as MGRS string.
     */
    private static String encode(UTMCoordinate utm, int accuracy) {
        String seasting = String.format("%05d", (int) utm.easting);
        String snorthing = String.format("%05d", (int) utm.northing);

        return String.valueOf(utm.zoneNumber) 
            + utm.zoneLetter 
            + get100kID((int) utm.easting, (int) utm.northing, utm.zoneNumber)
            + seasting.substring(seasting.length() - 5, seasting.length() - 5 + accuracy)
            + snorthing.substring(snorthing.length() - 5, snorthing.length() - 5 + accuracy);
    }

    /**
     * Decode MGRS string to UTM coordinates.
     */
    private static UTMCoordinate decode(String mgrsString) {
        if (mgrsString == null || mgrsString.isEmpty()) {
            throw new IllegalArgumentException("MGRS string cannot be empty");
        }

        int length = mgrsString.length();
        StringBuilder sb = new StringBuilder();
        int i = 0;

        // Parse zone number
        while (i < length && !Character.isLetter(mgrsString.charAt(i))) {
            if (i >= 2) {
                throw new IllegalArgumentException("Invalid MGRS string: " + mgrsString);
            }
            sb.append(mgrsString.charAt(i));
            i++;
        }

        int zoneNumber = Integer.parseInt(sb.toString());

        if (i == 0 || i + 3 > length) {
            throw new IllegalArgumentException("Invalid MGRS string: " + mgrsString);
        }

        char zoneLetter = mgrsString.charAt(i++);

        // Validate zone letter
        if (zoneLetter <= 'A' || zoneLetter == 'B' || zoneLetter == 'Y' || zoneLetter >= 'Z' 
            || zoneLetter == 'I' || zoneLetter == 'O') {
            throw new IllegalArgumentException("Invalid zone letter '" + zoneLetter + "' in: " + mgrsString);
        }

        // Parse 100km grid square
        String hunK = mgrsString.substring(i, i + 2);
        i += 2;

        int set = get100kSetForZone(zoneNumber);
        double east100k = getEastingFromChar(hunK.charAt(0), set);
        double north100k = getNorthingFromChar(hunK.charAt(1), set);

        // Adjust northing for zone letter
        while (north100k < getMinNorthing(zoneLetter)) {
            north100k += 2000000;
        }

        // Parse easting/northing digits
        int remainder = length - i;
        if (remainder % 2 != 0) {
            throw new IllegalArgumentException(
                "MGRS string must have even number of digits after zone and grid square: " + mgrsString);
        }

        int sep = remainder / 2;
        double sepEasting = 0.0;
        double sepNorthing = 0.0;
        double accuracyBonus = 0;

        if (sep > 0) {
            accuracyBonus = 100000.0 / Math.pow(10, sep);
            String sepEastingString = mgrsString.substring(i, i + sep);
            String sepNorthingString = mgrsString.substring(i + sep);
            sepEasting = Double.parseDouble(sepEastingString) * accuracyBonus;
            sepNorthing = Double.parseDouble(sepNorthingString) * accuracyBonus;
        }

        double easting = sepEasting + east100k;
        double northing = sepNorthing + north100k;

        return new UTMCoordinate(northing, easting, zoneNumber, zoneLetter, accuracyBonus);
    }

    // ========== Helper Methods ==========

    /**
     * Get the MGRS latitude band letter for a given latitude.
     */
    private static char getLetterDesignator(double lat) {
        if (lat >= 84 || lat < -80) {
            return 'Z'; // Outside MGRS bounds
        }

        if (lat >= 72) return 'X';
        if (lat >= 64) return 'W';
        if (lat >= 56) return 'V';
        if (lat >= 48) return 'U';
        if (lat >= 40) return 'T';
        if (lat >= 32) return 'S';
        if (lat >= 24) return 'R';
        if (lat >= 16) return 'Q';
        if (lat >= 8) return 'P';
        if (lat >= 0) return 'N';
        if (lat >= -8) return 'M';
        if (lat >= -16) return 'L';
        if (lat >= -24) return 'K';
        if (lat >= -32) return 'J';
        if (lat >= -40) return 'H';
        if (lat >= -48) return 'G';
        if (lat >= -56) return 'F';
        if (lat >= -64) return 'E';
        if (lat >= -72) return 'D';
        return 'C';
    }

    /**
     * Get the two-letter 100km grid square ID.
     */
    private static String get100kID(int easting, int northing, int zoneNumber) {
        int setParm = get100kSetForZone(zoneNumber);
        int setColumn = easting / 100000;
        int setRow = (northing / 100000) % 20;
        return getLetter100kID(setColumn, setRow, setParm);
    }

    /**
     * Get the 100k set number for a UTM zone.
     */
    private static int get100kSetForZone(int zoneNumber) {
        int setParm = zoneNumber % NUM_100K_SETS;
        if (setParm == 0) {
            setParm = NUM_100K_SETS;
        }
        return setParm;
    }

    /**
     * Get the two-letter 100km grid square designator.
     */
    private static String getLetter100kID(int column, int row, int parm) {
        int index = parm - 1;
        int colOrigin = SET_ORIGIN_COLUMN_LETTERS.charAt(index);
        int rowOrigin = SET_ORIGIN_ROW_LETTERS.charAt(index);

        int colInt = colOrigin + column - 1;
        int rowInt = rowOrigin + row;
        boolean rollover = false;

        if (colInt > Z) {
            colInt = colInt - Z + A - 1;
            rollover = true;
        }

        if (colInt == I || (colOrigin < I && colInt > I) || ((colInt > I || colOrigin < I) && rollover)) {
            colInt++;
        }

        if (colInt == O || (colOrigin < O && colInt > O) || ((colInt > O || colOrigin < O) && rollover)) {
            colInt++;
            if (colInt == I) {
                colInt++;
            }
        }

        if (colInt > Z) {
            colInt = colInt - Z + A - 1;
        }

        if (rowInt > V) {
            rowInt = rowInt - V + A - 1;
            rollover = true;
        } else {
            rollover = false;
        }

        if (rowInt == I || (rowOrigin < I && rowInt > I) || ((rowInt > I || rowOrigin < I) && rollover)) {
            rowInt++;
        }

        if (rowInt == O || (rowOrigin < O && rowInt > O) || ((rowInt > O || rowOrigin < O) && rollover)) {
            rowInt++;
            if (rowInt == I) {
                rowInt++;
            }
        }

        if (rowInt > V) {
            rowInt = rowInt - V + A - 1;
        }

        return String.valueOf((char) colInt) + (char) rowInt;
    }

    /**
     * Get easting value from 100km grid column letter.
     */
    private static double getEastingFromChar(char e, int set) {
        int curCol = SET_ORIGIN_COLUMN_LETTERS.charAt(set - 1);
        double eastingValue = 100000.0;
        boolean rewindMarker = false;

        while (curCol != e) {
            curCol++;
            if (curCol == I) curCol++;
            if (curCol == O) curCol++;
            if (curCol > Z) {
                if (rewindMarker) {
                    throw new IllegalArgumentException("Invalid easting character: " + e);
                }
                curCol = A;
                rewindMarker = true;
            }
            eastingValue += 100000.0;
        }

        return eastingValue;
    }

    /**
     * Get northing value from 100km grid row letter.
     */
    private static double getNorthingFromChar(char n, int set) {
        if (n > 'V') {
            throw new IllegalArgumentException("Invalid northing character: " + n);
        }

        int curRow = SET_ORIGIN_ROW_LETTERS.charAt(set - 1);
        double northingValue = 0.0;
        boolean rewindMarker = false;

        while (curRow != n) {
            curRow++;
            if (curRow == I) curRow++;
            if (curRow == O) curRow++;
            if (curRow > V) {
                if (rewindMarker) {
                    throw new IllegalArgumentException("Invalid northing character: " + n);
                }
                curRow = A;
                rewindMarker = true;
            }
            northingValue += 100000.0;
        }

        return northingValue;
    }

    /**
     * Get minimum northing value for a zone letter.
     */
    private static double getMinNorthing(char zoneLetter) {
        switch (zoneLetter) {
            case 'C': return 1100000.0;
            case 'D': return 2000000.0;
            case 'E': return 2800000.0;
            case 'F': return 3700000.0;
            case 'G': return 4600000.0;
            case 'H': return 5500000.0;
            case 'J': return 6400000.0;
            case 'K': return 7300000.0;
            case 'L': return 8200000.0;
            case 'M': return 9100000.0;
            case 'N': return 0.0;
            case 'P': return 800000.0;
            case 'Q': return 1700000.0;
            case 'R': return 2600000.0;
            case 'S': return 3500000.0;
            case 'T': return 4400000.0;
            case 'U': return 5300000.0;
            case 'V': return 6200000.0;
            case 'W': return 7000000.0;
            case 'X': return 7900000.0;
            default:
                throw new IllegalArgumentException("Invalid zone letter: " + zoneLetter);
        }
    }

    // ========== Internal Data Classes ==========

    /**
     * Internal class representing UTM coordinates.
     */
    private static class UTMCoordinate {
        final double northing;
        final double easting;
        final int zoneNumber;
        final char zoneLetter;
        final double accuracy;

        UTMCoordinate(double northing, double easting, int zoneNumber, char zoneLetter, double accuracy) {
            this.northing = northing;
            this.easting = easting;
            this.zoneNumber = zoneNumber;
            this.zoneLetter = zoneLetter;
            this.accuracy = accuracy;
        }
    }

    /**
     * Internal class representing lat/lon result.
     */
    private static class LatLonResult {
        final double lat;
        final double lon;
        final double top;
        final double right;
        final double bottom;
        final double left;
        final boolean isPoint;

        // Point result
        LatLonResult(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
            this.top = 0;
            this.right = 0;
            this.bottom = 0;
            this.left = 0;
            this.isPoint = true;
        }

        // Bounding box result
        LatLonResult(double bottom, double left, double top, double right) {
            this.lat = 0;
            this.lon = 0;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.left = left;
            this.isPoint = false;
        }
    }
}
