package org.proj4.mgrs;

/**
 * MGRS (Military Grid Reference System) coordinate conversion utility.
 * 
 * This class provides methods to convert between WGS84 lat/lng coordinates
 * and MGRS coordinates, ported from the JavaScript mgrs library.
 * 
 * @author proj4sedona team
 */
public class Mgrs {
    
    /**
     * UTM zones are grouped, and assigned to one of a group of 6 sets.
     */
    private static final int NUM_100K_SETS = 6;
    
    /**
     * The column letters (for easting) of the lower left value, per set.
     */
    private static final String SET_ORIGIN_COLUMN_LETTERS = "AJSAJS";
    
    /**
     * The row letters (for northing) of the lower left value, per set.
     */
    private static final String SET_ORIGIN_ROW_LETTERS = "AFAFAF";
    
    private static final int A = 65; // A
    private static final int I = 73; // I
    private static final int O = 79; // O
    private static final int V = 86; // V
    private static final int Z = 90; // Z
    
    /**
     * First eccentricity squared
     */
    private static final double ECC_SQUARED = 0.00669438;
    
    /**
     * Scale factor along the central meridian
     */
    private static final double SCALE_FACTOR = 0.9996;
    
    /**
     * Semimajor axis (half the width of the earth) in meters
     */
    private static final double SEMI_MAJOR_AXIS = 6378137;
    
    /**
     * The easting of the central meridian of each UTM zone
     */
    private static final double EASTING_OFFSET = 500000;
    
    /**
     * The northing of the equator for southern hemisphere locations (in UTM)
     */
    private static final double NORTHING_OFFSET = 10000000;
    
    /**
     * UTM zone width in degrees
     */
    private static final double UTM_ZONE_WIDTH = 6;
    
    /**
     * Half the width of a UTM zone in degrees
     */
    private static final double HALF_UTM_ZONE_WIDTH = UTM_ZONE_WIDTH / 2;
    
    /**
     * Convert lat/lon to MGRS.
     *
     * @param lon longitude in degrees
     * @param lat latitude in degrees
     * @param accuracy accuracy in digits (5 for 1 m, 4 for 10 m, 3 for 100 m, 2 for 1 km, 1 for 10 km or 0 for 100 km). Default is 5.
     * @return the MGRS string for the given location and accuracy.
     * @throws IllegalArgumentException if coordinates are invalid
     */
    public static String forward(double lon, double lat, int accuracy) {
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid longitude: " + lon);
        }
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }
        if (lat < -80 || lat > 84) {
            throw new IllegalArgumentException("Latitude " + lat + " is outside MGRS limits (80°S to 84°N)");
        }
        
        UtmCoordinate utm = llToUtm(lat, lon);
        return encode(utm, accuracy);
    }
    
    /**
     * Convert lat/lon to MGRS with default accuracy (5 digits = 1 meter).
     *
     * @param lon longitude in degrees
     * @param lat latitude in degrees
     * @return the MGRS string for the given location
     * @throws IllegalArgumentException if coordinates are invalid
     */
    public static String forward(double lon, double lat) {
        return forward(lon, lat, 5);
    }
    
    /**
     * Convert MGRS to lat/lon bounding box.
     *
     * @param mgrs MGRS string
     * @return array with [left, bottom, right, top] values in WGS84 degrees
     * @throws IllegalArgumentException if MGRS string is invalid
     */
    public static double[] inverse(String mgrs) {
        UtmCoordinate utm = decode(mgrs.toUpperCase());
        LatLonBbox bbox = utmToLl(utm);
        
        if (bbox.isPoint()) {
            return new double[]{bbox.lon, bbox.lat, bbox.lon, bbox.lat};
        }
        return new double[]{bbox.left, bbox.bottom, bbox.right, bbox.top};
    }
    
    /**
     * Convert MGRS to lat/lon point (center of bounding box).
     *
     * @param mgrs MGRS string
     * @return array with [longitude, latitude] in degrees
     * @throws IllegalArgumentException if MGRS string is invalid
     */
    public static double[] toPoint(String mgrs) {
        if (mgrs == null || mgrs.trim().isEmpty()) {
            throw new IllegalArgumentException("MGRS string cannot be empty");
        }
        
        UtmCoordinate utm = decode(mgrs.toUpperCase());
        LatLonBbox bbox = utmToLl(utm);
        
        if (bbox.isPoint()) {
            return new double[]{bbox.lon, bbox.lat};
        }
        return new double[]{(bbox.left + bbox.right) / 2, (bbox.top + bbox.bottom) / 2};
    }
    
    /**
     * Conversion from degrees to radians.
     */
    private static double degToRad(double deg) {
        return deg * (Math.PI / 180);
    }
    
    /**
     * Conversion from radians to degrees.
     */
    private static double radToDeg(double rad) {
        return 180 * (rad / Math.PI);
    }
    
    /**
     * Converts a set of Longitude and Latitude coordinates to UTM using the WGS84 ellipsoid.
     */
    private static UtmCoordinate llToUtm(double lat, double lon) {
        double a = SEMI_MAJOR_AXIS;
        double latRad = degToRad(lat);
        double lonRad = degToRad(lon);
        
        int zoneNumber = (int) Math.floor((lon + 180) / 6) + 1;
        
        // Make sure the longitude 180 is in Zone 60
        if (lon == 180) {
            zoneNumber = 60;
        }
        
        // Special zone for Norway
        if (lat >= 56 && lat < 64 && lon >= 3 && lon < 12) {
            zoneNumber = 32;
        }
        
        // Special zones for Svalbard
        if (lat >= 72 && lat < 84) {
            if (lon >= 0 && lon < 9) {
                zoneNumber = 31;
            } else if (lon >= 9 && lon < 21) {
                zoneNumber = 33;
            } else if (lon >= 21 && lon < 33) {
                zoneNumber = 35;
            } else if (lon >= 33 && lon < 42) {
                zoneNumber = 37;
            }
        }
        
        // +HALF_UTM_ZONE_WIDTH puts origin in middle of zone
        double longOrigin = (zoneNumber - 1) * UTM_ZONE_WIDTH - 180 + HALF_UTM_ZONE_WIDTH;
        double longOriginRad = degToRad(longOrigin);
        
        double eccPrimeSquared = ECC_SQUARED / (1 - ECC_SQUARED);
        
        double N = a / Math.sqrt(1 - ECC_SQUARED * Math.sin(latRad) * Math.sin(latRad));
        double T = Math.tan(latRad) * Math.tan(latRad);
        double C = eccPrimeSquared * Math.cos(latRad) * Math.cos(latRad);
        double A = Math.cos(latRad) * (lonRad - longOriginRad);
        
        double M = a * ((1 - ECC_SQUARED / 4 - 3 * ECC_SQUARED * ECC_SQUARED / 64 - 5 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 256) * latRad 
                - (3 * ECC_SQUARED / 8 + 3 * ECC_SQUARED * ECC_SQUARED / 32 + 45 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 1024) * Math.sin(2 * latRad) 
                + (15 * ECC_SQUARED * ECC_SQUARED / 256 + 45 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 1024) * Math.sin(4 * latRad) 
                - (35 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 3072) * Math.sin(6 * latRad));
        
        double utmEasting = SCALE_FACTOR * N * (A + (1 - T + C) * A * A * A / 6 
                + (5 - 18 * T + T * T + 72 * C - 58 * eccPrimeSquared) * A * A * A * A * A / 120) + EASTING_OFFSET;
        
        double utmNorthing = SCALE_FACTOR * (M + N * Math.tan(latRad) * (A * A / 2 
                + (5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24 
                + (61 - 58 * T + T * T + 600 * C - 330 * eccPrimeSquared) * A * A * A * A * A * A / 720));
        
        if (lat < 0) {
            utmNorthing += NORTHING_OFFSET;
        }
        
        return new UtmCoordinate(
            (int) Math.floor(utmNorthing),
            (int) Math.floor(utmEasting),
            zoneNumber,
            getLetterDesignator(lat)
        );
    }
    
    /**
     * Converts UTM coordinates to lat/lon, using the WGS84 ellipsoid.
     */
    private static LatLonBbox utmToLl(UtmCoordinate utm) {
        double utmNorthing = utm.northing;
        double utmEasting = utm.easting;
        char zoneLetter = utm.zoneLetter;
        int zoneNumber = utm.zoneNumber;
        
        // check the ZoneNumber is valid
        if (zoneNumber < 0 || zoneNumber > 60) {
            return null;
        }
        
        double a = SEMI_MAJOR_AXIS;
        double e1 = (1 - Math.sqrt(1 - ECC_SQUARED)) / (1 + Math.sqrt(1 - ECC_SQUARED));
        
        // remove 500,000 meter offset for longitude
        double x = utmEasting - EASTING_OFFSET;
        double y = utmNorthing;
        
        // We must know somehow if we are in the Northern or Southern hemisphere
        if (zoneLetter < 'N') {
            y -= NORTHING_OFFSET; // remove offset used for southern hemisphere
        }
        
        // +HALF_UTM_ZONE_WIDTH puts origin in middle of zone
        double longOrigin = (zoneNumber - 1) * UTM_ZONE_WIDTH - 180 + HALF_UTM_ZONE_WIDTH;
        
        double eccPrimeSquared = ECC_SQUARED / (1 - ECC_SQUARED);
        
        double M = y / SCALE_FACTOR;
        double mu = M / (a * (1 - ECC_SQUARED / 4 - 3 * ECC_SQUARED * ECC_SQUARED / 64 - 5 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 256));
        
        double phi1Rad = mu + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * Math.sin(2 * mu) 
                + (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32) * Math.sin(4 * mu) 
                + (151 * e1 * e1 * e1 / 96) * Math.sin(6 * mu);
        
        double N1 = a / Math.sqrt(1 - ECC_SQUARED * Math.sin(phi1Rad) * Math.sin(phi1Rad));
        double T1 = Math.tan(phi1Rad) * Math.tan(phi1Rad);
        double C1 = eccPrimeSquared * Math.cos(phi1Rad) * Math.cos(phi1Rad);
        double R1 = a * (1 - ECC_SQUARED) / Math.pow(1 - ECC_SQUARED * Math.sin(phi1Rad) * Math.sin(phi1Rad), 1.5);
        double D = x / (N1 * SCALE_FACTOR);
        
        double lat = phi1Rad - (N1 * Math.tan(phi1Rad) / R1) * (D * D / 2 
                - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * eccPrimeSquared) * D * D * D * D / 24 
                + (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * eccPrimeSquared - 3 * C1 * C1) * D * D * D * D * D * D / 720);
        lat = radToDeg(lat);
        
        double lon = (D - (1 + 2 * T1 + C1) * D * D * D / 6 
                + (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * eccPrimeSquared + 24 * T1 * T1) * D * D * D * D * D / 120) / Math.cos(phi1Rad);
        lon = longOrigin + radToDeg(lon);
        
        if (utm.accuracy > 0) {
            UtmCoordinate topRight = new UtmCoordinate(
                utm.northing + utm.accuracy,
                utm.easting + utm.accuracy,
                utm.zoneNumber,
                utm.zoneLetter
            );
            LatLonBbox topRightBbox = utmToLl(topRight);
            return new LatLonBbox(topRightBbox.lat, topRightBbox.lon, lat, lon);
        } else {
            return new LatLonBbox(lat, lon);
        }
    }
    
    /**
     * Calculates the MGRS letter designator for the given latitude.
     */
    public static char getLetterDesignator(double latitude) {
        if (latitude <= 84 && latitude >= 72) {
            // the X band is 12 degrees high
            return 'X';
        } else if (latitude < 72 && latitude >= -80) {
            // Latitude bands are lettered C through X, excluding I and O
            String bandLetters = "CDEFGHJKLMNPQRSTUVWX";
            double bandHeight = 8;
            double minLatitude = -80;
            int index = (int) Math.floor((latitude - minLatitude) / bandHeight);
            return bandLetters.charAt(index);
        } else if (latitude > 84 || latitude < -80) {
            // This is here as an error flag to show that the Latitude is outside MGRS limits
            return 'Z';
        }
        return 'Z'; // fallback
    }
    
    /**
     * Encodes a UTM location as MGRS string.
     */
    private static String encode(UtmCoordinate utm, int accuracy) {
        // prepend with leading zeroes
        String seasting = "00000" + utm.easting;
        String snorthing = "00000" + utm.northing;
        
        return utm.zoneNumber + String.valueOf(utm.zoneLetter) + 
               get100kId(utm.easting, utm.northing, utm.zoneNumber) + 
               seasting.substring(seasting.length() - 5, seasting.length() - 5 + accuracy) + 
               snorthing.substring(snorthing.length() - 5, snorthing.length() - 5 + accuracy);
    }
    
    /**
     * Get the two letter 100k designator for a given UTM easting, northing and zone number value.
     */
    private static String get100kId(int easting, int northing, int zoneNumber) {
        int setParm = get100kSetForZone(zoneNumber);
        int setColumn = easting / 100000;
        int setRow = (northing / 100000) % 20;
        return getLetter100kId(setColumn, setRow, setParm);
    }
    
    /**
     * Given a UTM zone number, figure out the MGRS 100K set it is in.
     */
    private static int get100kSetForZone(int i) {
        int setParm = i % NUM_100K_SETS;
        if (setParm == 0) {
            setParm = NUM_100K_SETS;
        }
        return setParm;
    }
    
    /**
     * Get the two-letter MGRS 100k designator given information translated from the UTM northing, easting and zone number.
     */
    private static String getLetter100kId(int column, int row, int parm) {
        // colOrigin and rowOrigin are the letters at the origin of the set
        int index = parm - 1;
        int colOrigin = SET_ORIGIN_COLUMN_LETTERS.charAt(index);
        int rowOrigin = SET_ORIGIN_ROW_LETTERS.charAt(index);
        
        // colInt and rowInt are the letters to build to return
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
        
        if (((rowInt == I) || ((rowOrigin < I) && (rowInt > I))) || (((rowInt > I) || (rowOrigin < I)) && rollover)) {
            rowInt++;
        }
        
        if (((rowInt == O) || ((rowOrigin < O) && (rowInt > O))) || (((rowInt > O) || (rowOrigin < O)) && rollover)) {
            rowInt++;
            if (rowInt == I) {
                rowInt++;
            }
        }
        
        if (rowInt > V) {
            rowInt = rowInt - V + A - 1;
        }
        
        return String.valueOf((char) colInt) + String.valueOf((char) rowInt);
    }
    
    /**
     * Decode the UTM parameters from a MGRS string.
     */
    private static UtmCoordinate decode(String mgrsString) {
        if (mgrsString == null || mgrsString.length() == 0) {
            throw new IllegalArgumentException("MGRS string cannot be empty");
        }
        
        // remove any spaces in MGRS String
        mgrsString = mgrsString.replaceAll(" ", "");
        
        int length = mgrsString.length();
        String sb = "";
        char testChar;
        int i = 0;
        
        // get Zone number
        while (!Character.isLetter(testChar = mgrsString.charAt(i))) {
            if (i >= 2) {
                throw new IllegalArgumentException("MGRS bad conversion from: " + mgrsString);
            }
            sb += testChar;
            i++;
        }
        
        int zoneNumber = Integer.parseInt(sb);
        
        if (i == 0 || i + 3 > length) {
            // A good MGRS string has to be 4-5 digits long, ##AAA/#AAA at least.
            throw new IllegalArgumentException("MGRS bad conversion from " + mgrsString);
        }
        
        char zoneLetter = mgrsString.charAt(i++);
        
        // Should we check the zone letter here? Why not.
        if (zoneLetter <= 'A' || zoneLetter == 'B' || zoneLetter == 'Y' || zoneLetter >= 'Z' || zoneLetter == 'I' || zoneLetter == 'O') {
            throw new IllegalArgumentException("MGRS zone letter " + zoneLetter + " not handled: " + mgrsString);
        }
        
        String hunK = mgrsString.substring(i, i += 2);
        
        int set = get100kSetForZone(zoneNumber);
        
        int east100k = getEastingFromChar(hunK.charAt(0), set);
        int north100k = getNorthingFromChar(hunK.charAt(1), set);
        
        // We have a bug where the northing may be 2000000 too low.
        while (north100k < getMinNorthing(zoneLetter)) {
            north100k += 2000000;
        }
        
        // calculate the char index for easting/northing separator
        int remainder = length - i;
        
        if (remainder % 2 != 0) {
            throw new IllegalArgumentException("MGRS has to have an even number of digits after the zone letter and two 100km letters - front half for easting meters, second half for northing meters " + mgrsString);
        }
        
        int sep = remainder / 2;
        
        int sepEasting = 0;
        int sepNorthing = 0;
        double accuracyBonus = 0;
        
        if (sep > 0) {
            accuracyBonus = 100000 / Math.pow(10, sep);
            String sepEastingString = mgrsString.substring(i, i + sep);
            sepEasting = (int) (Double.parseDouble(sepEastingString) * accuracyBonus);
            String sepNorthingString = mgrsString.substring(i + sep);
            sepNorthing = (int) (Double.parseDouble(sepNorthingString) * accuracyBonus);
        }
        
        int easting = sepEasting + east100k;
        int northing = sepNorthing + north100k;
        
        return new UtmCoordinate(northing, easting, zoneNumber, zoneLetter, (int) accuracyBonus);
    }
    
    /**
     * Given the first letter from a two-letter MGRS 100k zone, and given the MGRS table set for the zone number, figure out the easting value.
     */
    private static int getEastingFromChar(char e, int set) {
        // colOrigin is the letter at the origin of the set for the column
        int curCol = SET_ORIGIN_COLUMN_LETTERS.charAt(set - 1);
        int eastingValue = 100000;
        boolean rewindMarker = false;
        
        while (curCol != e) {
            curCol++;
            if (curCol == I) {
                curCol++;
            }
            if (curCol == O) {
                curCol++;
            }
            if (curCol > Z) {
                if (rewindMarker) {
                    throw new IllegalArgumentException("Bad character: " + e);
                }
                curCol = A;
                rewindMarker = true;
            }
            eastingValue += 100000;
        }
        
        return eastingValue;
    }
    
    /**
     * Given the second letter from a two-letter MGRS 100k zone, and given the MGRS table set for the zone number, figure out the northing value.
     */
    private static int getNorthingFromChar(char n, int set) {
        if (n > 'V') {
            throw new IllegalArgumentException("MGRS given invalid Northing " + n);
        }
        
        // rowOrigin is the letter at the origin of the set for the column
        int curRow = SET_ORIGIN_ROW_LETTERS.charAt(set - 1);
        int northingValue = 0;
        boolean rewindMarker = false;
        
        while (curRow != n) {
            curRow++;
            if (curRow == I) {
                curRow++;
            }
            if (curRow == O) {
                curRow++;
            }
            // fixing a bug making whole application hang in this loop when 'n' is a wrong character
            if (curRow > V) {
                if (rewindMarker) { // making sure that this loop ends
                    throw new IllegalArgumentException("Bad character: " + n);
                }
                curRow = A;
                rewindMarker = true;
            }
            northingValue += 100000;
        }
        
        return northingValue;
    }
    
    /**
     * The function getMinNorthing returns the minimum northing value of a MGRS zone.
     * Ported from Geotrans' c Latitude_Band_Value structure table.
     */
    private static int getMinNorthing(char zoneLetter) {
        int northing;
        switch (zoneLetter) {
            case 'C': northing = 1100000; break;
            case 'D': northing = 2000000; break;
            case 'E': northing = 2800000; break;
            case 'F': northing = 3700000; break;
            case 'G': northing = 4600000; break;
            case 'H': northing = 5500000; break;
            case 'J': northing = 6400000; break;
            case 'K': northing = 7300000; break;
            case 'L': northing = 8200000; break;
            case 'M': northing = 9100000; break;
            case 'N': northing = 0; break;
            case 'P': northing = 800000; break;
            case 'Q': northing = 1700000; break;
            case 'R': northing = 2600000; break;
            case 'S': northing = 3500000; break;
            case 'T': northing = 4400000; break;
            case 'U': northing = 5300000; break;
            case 'V': northing = 6200000; break;
            case 'W': northing = 7000000; break;
            case 'X': northing = 7900000; break;
            default:
                throw new IllegalArgumentException("Invalid zone letter: " + zoneLetter);
        }
        return northing;
    }
    
    /**
     * UTM coordinate representation
     */
    private static class UtmCoordinate {
        final int northing;
        final int easting;
        final int zoneNumber;
        final char zoneLetter;
        final int accuracy;
        
        UtmCoordinate(int northing, int easting, int zoneNumber, char zoneLetter) {
            this(northing, easting, zoneNumber, zoneLetter, 0);
        }
        
        UtmCoordinate(int northing, int easting, int zoneNumber, char zoneLetter, int accuracy) {
            this.northing = northing;
            this.easting = easting;
            this.zoneNumber = zoneNumber;
            this.zoneLetter = zoneLetter;
            this.accuracy = accuracy;
        }
    }
    
    /**
     * Latitude/Longitude bounding box representation
     */
    private static class LatLonBbox {
        final double lat, lon;
        final double left, bottom, right, top;
        final boolean isPoint;
        
        LatLonBbox(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
            this.left = this.bottom = this.right = this.top = 0;
            this.isPoint = true;
        }
        
        LatLonBbox(double top, double right, double bottom, double left) {
            this.lat = this.lon = 0;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.left = left;
            this.isPoint = false;
        }
        
        boolean isPoint() {
            return isPoint;
        }
    }
}
