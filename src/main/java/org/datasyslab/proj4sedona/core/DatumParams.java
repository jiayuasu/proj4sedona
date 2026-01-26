package org.datasyslab.proj4sedona.core;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.grid.NadgridInfo;

import java.util.List;

/**
 * Datum parameters for coordinate transformations.
 * Mirrors: lib/datum.js datum function output
 * 
 * Contains the datum type and transformation parameters needed
 * for datum shifts between coordinate systems.
 */
public class DatumParams {

    private int datumType;         // PJD_3PARAM, PJD_7PARAM, PJD_GRIDSHIFT, PJD_WGS84, PJD_NODATUM
    private double[] datumParams;  // 3 or 7 transformation parameters
    private double a;              // Semi-major axis of the ellipsoid
    private double b;              // Semi-minor axis of the ellipsoid
    private double es;             // Eccentricity squared
    private double ep2;            // Second eccentricity squared
    private List<NadgridInfo> grids;  // NAD grid info objects (for PJD_GRIDSHIFT)
    private String nadgrids;       // NAD grids string (e.g., "@conus,null")

    /**
     * Create datum parameters.
     * Mirrors: lib/datum.js datum function
     * 
     * @param datumCode The datum code (e.g., "wgs84", "nad83")
     * @param datumParamsArray The towgs84 parameters (can be null)
     * @param a Semi-major axis
     * @param b Semi-minor axis
     * @param es Eccentricity squared
     * @param ep2 Second eccentricity squared
     * @param nadgrids NAD grid objects (can be null)
     */
    public DatumParams(String datumCode, double[] datumParamsArray, double a, double b, 
                       double es, double ep2, List<Object> nadgrids) {
        this.a = a;
        this.b = b;
        this.es = es;
        this.ep2 = ep2;

        // Determine datum type based on inputs
        // Mirrors logic from lib/datum.js
        if (datumCode == null || "none".equalsIgnoreCase(datumCode)) {
            this.datumType = Values.PJD_NODATUM;
        } else {
            this.datumType = Values.PJD_WGS84;
        }

        if (datumParamsArray != null) {
            this.datumParams = datumParamsArray.clone();
            
            // Check if any translation params are non-zero
            if (datumParamsArray.length >= 3 && 
                (datumParamsArray[0] != 0 || datumParamsArray[1] != 0 || datumParamsArray[2] != 0)) {
                this.datumType = Values.PJD_3PARAM;
            }
            
            // Check if rotation/scale params are present and non-zero
            if (datumParamsArray.length > 3) {
                if (datumParamsArray[3] != 0 || datumParamsArray[4] != 0 || 
                    datumParamsArray[5] != 0 || datumParamsArray[6] != 0) {
                    this.datumType = Values.PJD_7PARAM;
                    
                    // Convert rotation parameters from arc-seconds to radians
                    // and scale from ppm to multiplier
                    // Mirrors: lib/datum.js lines 18-23
                    this.datumParams[3] *= Values.SEC_TO_RAD;
                    this.datumParams[4] *= Values.SEC_TO_RAD;
                    this.datumParams[5] *= Values.SEC_TO_RAD;
                    this.datumParams[6] = (this.datumParams[6] / 1000000.0) + 1.0;
                }
            }
        }

        if (nadgrids != null && !nadgrids.isEmpty()) {
            this.datumType = Values.PJD_GRIDSHIFT;
            @SuppressWarnings("unchecked")
            List<NadgridInfo> gridList = (List<NadgridInfo>) (List<?>) nadgrids;
            this.grids = gridList;
        }
    }

    /**
     * Factory method to create DatumParams with nadgrids string.
     */
    public static DatumParams withNadgrids(String datumCode, double[] datumParamsArray, 
                                            double a, double b, double es, double ep2, 
                                            String nadgridsStr) {
        DatumParams params = new DatumParams(datumCode, datumParamsArray, a, b, es, ep2, (List<Object>) null);
        if (nadgridsStr != null && !nadgridsStr.isEmpty()) {
            params.nadgrids = nadgridsStr;
            params.datumType = Values.PJD_GRIDSHIFT;
        }
        return params;
    }

    /**
     * Simple constructor for WGS84-equivalent datum.
     */
    public DatumParams(double a, double b, double es, double ep2) {
        this.datumType = Values.PJD_WGS84;
        this.a = a;
        this.b = b;
        this.es = es;
        this.ep2 = ep2;
    }

    /**
     * Check if this is a 3-parameter datum.
     */
    public boolean is3Param() {
        return datumType == Values.PJD_3PARAM;
    }

    /**
     * Check if this is a 7-parameter datum.
     */
    public boolean is7Param() {
        return datumType == Values.PJD_7PARAM;
    }

    /**
     * Check if this is a grid shift datum.
     */
    public boolean isGridShift() {
        return datumType == Values.PJD_GRIDSHIFT;
    }

    /**
     * Check if this is WGS84 or equivalent (no transform needed).
     */
    public boolean isWgs84() {
        return datumType == Values.PJD_WGS84;
    }

    /**
     * Check if datum transform should be skipped.
     */
    public boolean isNoDatum() {
        return datumType == Values.PJD_NODATUM;
    }

    // Getters

    public int getDatumType() { return datumType; }
    public double[] getDatumParams() { return datumParams; }
    public double getA() { return a; }
    public double getB() { return b; }
    public double getEs() { return es; }
    public double getEp2() { return ep2; }
    public List<NadgridInfo> getGrids() { return grids; }
    public String getNadgrids() { return nadgrids; }

    // Setters (for special cases)

    public void setDatumType(int datumType) { this.datumType = datumType; }
    public void setA(double a) { this.a = a; }
    public void setB(double b) { this.b = b; }
    public void setEs(double es) { this.es = es; }
    public void setEp2(double ep2) { this.ep2 = ep2; }
    public void setGrids(List<NadgridInfo> grids) { this.grids = grids; }
    public void setNadgrids(String nadgrids) { this.nadgrids = nadgrids; }

    @Override
    public String toString() {
        String typeStr;
        switch (datumType) {
            case Values.PJD_3PARAM: typeStr = "3PARAM"; break;
            case Values.PJD_7PARAM: typeStr = "7PARAM"; break;
            case Values.PJD_GRIDSHIFT: typeStr = "GRIDSHIFT"; break;
            case Values.PJD_WGS84: typeStr = "WGS84"; break;
            case Values.PJD_NODATUM: typeStr = "NODATUM"; break;
            default: typeStr = "UNKNOWN"; break;
        }
        return "DatumParams{" +
                "type=" + typeStr +
                ", a=" + a +
                ", b=" + b +
                ", es=" + es +
                '}';
    }
}
