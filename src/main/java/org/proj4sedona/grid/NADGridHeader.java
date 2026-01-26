package org.proj4sedona.grid;

/**
 * Header information for a NAD grid file.
 */
public class NADGridHeader {
    private int nFields;
    private int nSubgridFields;
    private int nSubgrids;
    private String shiftType;
    private double fromSemiMajorAxis;
    private double fromSemiMinorAxis;
    private double toSemiMajorAxis;
    private double toSemiMinorAxis;

    public int getnFields() { return nFields; }
    public void setnFields(int nFields) { this.nFields = nFields; }
    public int getnSubgridFields() { return nSubgridFields; }
    public void setnSubgridFields(int nSubgridFields) { this.nSubgridFields = nSubgridFields; }
    public int getnSubgrids() { return nSubgrids; }
    public void setnSubgrids(int nSubgrids) { this.nSubgrids = nSubgrids; }
    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }
    public double getFromSemiMajorAxis() { return fromSemiMajorAxis; }
    public void setFromSemiMajorAxis(double fromSemiMajorAxis) { this.fromSemiMajorAxis = fromSemiMajorAxis; }
    public double getFromSemiMinorAxis() { return fromSemiMinorAxis; }
    public void setFromSemiMinorAxis(double fromSemiMinorAxis) { this.fromSemiMinorAxis = fromSemiMinorAxis; }
    public double getToSemiMajorAxis() { return toSemiMajorAxis; }
    public void setToSemiMajorAxis(double toSemiMajorAxis) { this.toSemiMajorAxis = toSemiMajorAxis; }
    public double getToSemiMinorAxis() { return toSemiMinorAxis; }
    public void setToSemiMinorAxis(double toSemiMinorAxis) { this.toSemiMinorAxis = toSemiMinorAxis; }
}
