package org.datasyslab.proj4sedona.core;

/**
 * Projection definition - holds all parsed parameters for a coordinate system.
 * Mirrors: ProjectionDefinition type in lib/defs.js
 * 
 * This class stores the raw parsed values from PROJ strings, WKT, etc.
 * before they are processed into a full Projection object.
 */
public class ProjectionDef {

    // Projection identification
    private String title;
    private String projName;       // proj: merc, longlat, utm, lcc, etc.
    private String srsCode;        // Original definition string

    // Ellipsoid parameters
    private String ellps;          // Ellipsoid name: WGS84, GRS80, etc.
    private Double a;              // Semi-major axis
    private Double b;              // Semi-minor axis
    private Double rf;             // Inverse flattening
    private Boolean sphere;        // True if using a sphere

    // Datum parameters
    private String datumCode;      // Datum name: wgs84, nad83, etc.
    private String datumName;
    private double[] datumParams;  // towgs84 parameters (3 or 7 values)
    private String nadgrids;       // NAD grid files

    // Projection parameters (angles in radians after parsing)
    private Double lat0;           // lat_0: Latitude of origin
    private Double lat1;           // lat_1: First standard parallel
    private Double lat2;           // lat_2: Second standard parallel
    private Double latTs;          // lat_ts: Latitude of true scale
    private Double long0;          // lon_0: Central meridian
    private Double long1;          // lon_1
    private Double long2;          // lon_2
    private Double alpha;          // alpha: Azimuth
    private Double longc;          // longc: Longitude of center
    private Double rectifiedGridAngle;  // gamma: Rectified grid angle

    // Scale and offsets
    private Double k0;             // k_0 or k: Scale factor (default 1.0)
    private Double x0;             // x_0: False easting (default 0)
    private Double y0;             // y_0: False northing (default 0)

    // Units and coordinate handling
    private Double toMeter;        // Conversion factor to meters
    private String units;          // Unit name: m, ft, us-ft, etc.
    private Double fromGreenwich;  // Prime meridian offset in radians
    private String axis;           // Axis order: enu, neu, etc. (default "enu")

    // UTM specific
    private Integer zone;          // UTM zone number
    private Boolean utmSouth;      // True for southern hemisphere UTM

    // Other flags
    private Boolean rA;            // R_A: Use authalic radius
    private Boolean approx;        // Use approximate algorithms
    private Boolean over;          // Allow longitude wrapping

    // Datum object (populated after parsing)
    private DatumParams datum;

    public ProjectionDef() {
        // Default values matching proj4js
        this.k0 = 1.0;
        this.x0 = 0.0;
        this.y0 = 0.0;
        this.axis = "enu";
    }

    // Getters and setters

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getProjName() { return projName; }
    public void setProjName(String projName) { this.projName = projName; }

    public String getSrsCode() { return srsCode; }
    public void setSrsCode(String srsCode) { this.srsCode = srsCode; }

    public String getEllps() { return ellps; }
    public void setEllps(String ellps) { this.ellps = ellps; }

    public Double getA() { return a; }
    public void setA(Double a) { this.a = a; }

    public Double getB() { return b; }
    public void setB(Double b) { this.b = b; }

    public Double getRf() { return rf; }
    public void setRf(Double rf) { this.rf = rf; }

    public Boolean getSphere() { return sphere; }
    public void setSphere(Boolean sphere) { this.sphere = sphere; }

    public String getDatumCode() { return datumCode; }
    public void setDatumCode(String datumCode) { this.datumCode = datumCode; }

    public String getDatumName() { return datumName; }
    public void setDatumName(String datumName) { this.datumName = datumName; }

    public double[] getDatumParams() { return datumParams; }
    public void setDatumParams(double[] datumParams) { this.datumParams = datumParams; }

    public String getNadgrids() { return nadgrids; }
    public void setNadgrids(String nadgrids) { this.nadgrids = nadgrids; }

    public Double getLat0() { return lat0; }
    public void setLat0(Double lat0) { this.lat0 = lat0; }

    public Double getLat1() { return lat1; }
    public void setLat1(Double lat1) { this.lat1 = lat1; }

    public Double getLat2() { return lat2; }
    public void setLat2(Double lat2) { this.lat2 = lat2; }

    public Double getLatTs() { return latTs; }
    public void setLatTs(Double latTs) { this.latTs = latTs; }

    public Double getLong0() { return long0; }
    public void setLong0(Double long0) { this.long0 = long0; }

    public Double getLong1() { return long1; }
    public void setLong1(Double long1) { this.long1 = long1; }

    public Double getLong2() { return long2; }
    public void setLong2(Double long2) { this.long2 = long2; }

    public Double getAlpha() { return alpha; }
    public void setAlpha(Double alpha) { this.alpha = alpha; }

    public Double getLongc() { return longc; }
    public void setLongc(Double longc) { this.longc = longc; }

    public Double getRectifiedGridAngle() { return rectifiedGridAngle; }
    public void setRectifiedGridAngle(Double rectifiedGridAngle) { this.rectifiedGridAngle = rectifiedGridAngle; }

    public Double getK0() { return k0; }
    public void setK0(Double k0) { this.k0 = k0; }

    public Double getX0() { return x0; }
    public void setX0(Double x0) { this.x0 = x0; }

    public Double getY0() { return y0; }
    public void setY0(Double y0) { this.y0 = y0; }

    public Double getToMeter() { return toMeter; }
    public void setToMeter(Double toMeter) { this.toMeter = toMeter; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public Double getFromGreenwich() { return fromGreenwich; }
    public void setFromGreenwich(Double fromGreenwich) { this.fromGreenwich = fromGreenwich; }

    public String getAxis() { return axis; }
    public void setAxis(String axis) { this.axis = axis; }

    public Integer getZone() { return zone; }
    public void setZone(Integer zone) { this.zone = zone; }

    public Boolean getUtmSouth() { return utmSouth; }
    public void setUtmSouth(Boolean utmSouth) { this.utmSouth = utmSouth; }

    public Boolean getRA() { return rA; }
    public void setRA(Boolean rA) { this.rA = rA; }

    public Boolean getApprox() { return approx; }
    public void setApprox(Boolean approx) { this.approx = approx; }

    public Boolean getOver() { return over; }
    public void setOver(Boolean over) { this.over = over; }

    public DatumParams getDatum() { return datum; }
    public void setDatum(DatumParams datum) { this.datum = datum; }

    @Override
    public String toString() {
        return "ProjectionDef{" +
                "projName='" + projName + '\'' +
                ", ellps='" + ellps + '\'' +
                ", datumCode='" + datumCode + '\'' +
                '}';
    }
}
