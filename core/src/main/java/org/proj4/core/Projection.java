package org.proj4.core;

import org.proj4.constants.Datum;
import org.proj4.constants.Ellipsoid;
import org.proj4.constants.Values;
import org.proj4.constants.PrimeMeridian;
import org.proj4.constants.Units;
import org.proj4.common.MathUtils;
import org.proj4.parse.ProjStringParser;
import org.proj4.wkt.WKTProcessor;
import org.proj4.wkt.WKTParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a map projection with its parameters and transformation methods.
 * This is the Java equivalent of the JavaScript Proj class.
 */
public class Projection {
    
    // Projection parameters
    public String name;
    public String title;
    public String projName;
    public String ellps;
    public String datumCode;
    public String datumName;
    public String axis;
    public String units;
    public String nadgrids;
    
    // Ellipsoid parameters
    public double a;  // Semi-major axis
    public double b;  // Semi-minor axis
    public double rf; // Reciprocal of flattening
    public double es; // Eccentricity squared
    public double e;  // Eccentricity
    public double ep2; // Second eccentricity squared
    public boolean sphere; // Whether using spherical approximation
    
    // Projection parameters
    public double lat0;   // Latitude of origin
    public double lat1;   // First standard parallel
    public double lat2;   // Second standard parallel
    public double lat_ts; // Latitude of true scale
    public double long0;  // Central meridian
    public double long1;  // First longitude
    public double long2;  // Second longitude
    public double alpha;  // Azimuth
    public double longc;  // Longitude of center
    public double x0;     // False easting
    public double y0;     // False northing
    public double k0;     // Scale factor
    public double to_meter; // Unit conversion factor
    public double from_greenwich; // Prime meridian offset
    
    // Datum and transformation
    public Datum.DatumDef datum;
    public String[] datum_params;
    
    // Projection methods (to be set by specific projection implementations)
    public ProjectionMethod forward;
    public ProjectionMethod inverse;
    public ProjectionInitializer init;
    
    // Registry of available projections
    private static final Map<String, ProjectionFactory> PROJECTIONS = new ConcurrentHashMap<>();
    
    /**
     * Functional interface for projection forward/inverse methods.
     */
    @FunctionalInterface
    public interface ProjectionMethod {
        Point transform(Point point);
    }
    
    /**
     * Functional interface for projection initialization.
     */
    @FunctionalInterface
    public interface ProjectionInitializer {
        void initialize(Projection proj);
    }
    
    /**
     * Functional interface for projection factory.
     */
    @FunctionalInterface
    public interface ProjectionFactory {
        Projection create();
    }
    
    /**
     * Creates a new projection instance.
     * @param srsCode the spatial reference system code or definition
     */
    public Projection(String srsCode) {
        this();
        initializeFromCode(srsCode);
    }
    
    /**
     * Creates a new projection instance.
     */
    public Projection() {
        // Set default values
        this.k0 = Values.DEFAULT_K0;
        this.lat0 = Values.DEFAULT_LAT0;
        this.long0 = Values.DEFAULT_LONG0;
        this.x0 = Values.DEFAULT_X0;
        this.y0 = Values.DEFAULT_Y0;
        this.axis = Values.AXIS_ENU;
        this.ellps = "wgs84";
        this.datumCode = "WGS84";
        this.units = "m";
    }
    
    /**
     * Initializes the projection from a spatial reference system code.
     * @param srsCode the SRS code or definition
     */
    private void initializeFromCode(String srsCode) {
        if (srsCode == null || srsCode.trim().isEmpty()) {
            throw new IllegalArgumentException("SRS code cannot be null or empty");
        }
        
        // Handle PROJ strings (start with +)
        if (srsCode.startsWith("+")) {
            initializeFromProjString(srsCode);
        }
        // Handle WKT strings (start with GEOGCS, PROJCS, etc.)
        else if (srsCode.startsWith("GEOGCS") || srsCode.startsWith("PROJCS") || 
                 srsCode.startsWith("GEODCRS") || srsCode.startsWith("PROJCRS")) {
            initializeFromWKT(srsCode);
        }
        // Handle simple cases
        else if ("WGS84".equals(srsCode) || "EPSG:4326".equals(srsCode)) {
            initializeLongLat();
        } else if ("EPSG:3857".equals(srsCode) || "GOOGLE".equals(srsCode)) {
            initializeMercator();
        } else {
            throw new IllegalArgumentException("Unsupported SRS code: " + srsCode);
        }
    }
    
    /**
     * Initializes the projection from a PROJ string.
     * @param projString the PROJ string
     */
    private void initializeFromProjString(String projString) {
        Map<String, Object> def = ProjStringParser.parseToDefinition(projString);
        
        // Set basic properties
        this.projName = (String) def.get("projName");
        this.name = this.projName;
        this.title = (String) def.getOrDefault("title", this.projName);
        this.datumCode = (String) def.getOrDefault("datumCode", "WGS84");
        this.ellps = (String) def.getOrDefault("ellps", "WGS84");
        this.units = (String) def.getOrDefault("units", "m");
        this.axis = (String) def.getOrDefault("axis", Values.AXIS_ENU);
        
        // Set projection parameters
        this.lat0 = (Double) def.getOrDefault("lat0", Values.DEFAULT_LAT0);
        this.lat1 = (Double) def.getOrDefault("lat1", this.lat0);
        this.lat2 = (Double) def.getOrDefault("lat2", this.lat0);
        this.lat_ts = (Double) def.getOrDefault("lat_ts", 0.0);
        this.long0 = (Double) def.getOrDefault("long0", Values.DEFAULT_LONG0);
        this.long1 = (Double) def.getOrDefault("long1", this.long0);
        this.long2 = (Double) def.getOrDefault("long2", this.long0);
        this.alpha = (Double) def.getOrDefault("alpha", 0.0);
        this.longc = (Double) def.getOrDefault("longc", 0.0);
        this.x0 = (Double) def.getOrDefault("x0", Values.DEFAULT_X0);
        this.y0 = (Double) def.getOrDefault("y0", Values.DEFAULT_Y0);
        this.k0 = (Double) def.getOrDefault("k0", Values.DEFAULT_K0);
        
        // Set ellipsoid parameters
        this.a = (Double) def.getOrDefault("a", Double.NaN);
        this.b = (Double) def.getOrDefault("b", Double.NaN);
        this.rf = (Double) def.getOrDefault("rf", Double.NaN);
        
        // If ellipsoid parameters are not provided, get them from the ellipsoid definition
        if (Double.isNaN(this.a) || Double.isNaN(this.b) || Double.isNaN(this.rf)) {
            Ellipsoid.EllipsoidDef ellipsoid = Ellipsoid.get(this.ellps);
            if (ellipsoid != null) {
                if (Double.isNaN(this.a)) {
                    this.a = ellipsoid.a;
                }
                if (Double.isNaN(this.b)) {
                    this.b = ellipsoid.b;
                }
                if (Double.isNaN(this.rf)) {
                    this.rf = ellipsoid.rf;
                }
            }
        }
        
        // Set unit conversion
        this.to_meter = Units.getToMeter(this.units);
        
        // Set prime meridian offset
        if (def.containsKey("from_greenwich")) {
            this.from_greenwich = (Double) def.get("from_greenwich");
        }
        
        // Set datum parameters
        String datumParams = (String) def.get("datum_params");
        if (datumParams != null) {
            this.datum_params = datumParams.split(",");
        }
        
        // Set NADGRIDS
        this.nadgrids = (String) def.get("nadgrids");
        
        // Calculate derived parameters
        calculateDerivedParameters();
        
        // Set datum
        this.datum = Datum.get(this.datumCode);
        if (this.datum == null) {
            this.datum = Datum.getWGS84();
        }
        
        // Set transformation methods based on projection type
        initializeProjectionMethods();
    }
    
    /**
     * Initializes the projection from a WKT string.
     * @param wktString the WKT string
     */
    private void initializeFromWKT(String wktString) {
        try {
            Map<String, Object> wktDef = WKTProcessor.process(wktString);
            
            // Set basic properties
            this.name = (String) wktDef.get("name");
            this.title = (String) wktDef.get("title");
            this.projName = (String) wktDef.get("projName");
            this.ellps = (String) wktDef.get("ellps");
            this.datumCode = (String) wktDef.get("datumCode");
            this.axis = (String) wktDef.get("axis");
            this.units = (String) wktDef.get("units");
            this.nadgrids = (String) wktDef.get("nadgrids");
            
            // Set ellipsoid parameters
            if (wktDef.containsKey("a")) {
                this.a = ((Number) wktDef.get("a")).doubleValue();
            }
            if (wktDef.containsKey("rf")) {
                this.rf = ((Number) wktDef.get("rf")).doubleValue();
            }
            
            // Set projection parameters
            if (wktDef.containsKey("lat0")) {
                this.lat0 = ((Number) wktDef.get("lat0")).doubleValue();
            }
            if (wktDef.containsKey("lat1")) {
                this.lat1 = ((Number) wktDef.get("lat1")).doubleValue();
            }
            if (wktDef.containsKey("lat2")) {
                this.lat2 = ((Number) wktDef.get("lat2")).doubleValue();
            }
            if (wktDef.containsKey("long0")) {
                this.long0 = ((Number) wktDef.get("long0")).doubleValue();
            }
            if (wktDef.containsKey("k0")) {
                this.k0 = ((Number) wktDef.get("k0")).doubleValue();
            }
            if (wktDef.containsKey("x0")) {
                this.x0 = ((Number) wktDef.get("x0")).doubleValue();
            }
            if (wktDef.containsKey("y0")) {
                this.y0 = ((Number) wktDef.get("y0")).doubleValue();
            }
            
            // Set unit conversion
            if (wktDef.containsKey("to_meter")) {
                this.to_meter = ((Number) wktDef.get("to_meter")).doubleValue();
            } else {
                this.to_meter = Units.getToMeter(this.units);
            }
            
            // Set datum parameters
            if (wktDef.containsKey("datum_params")) {
                Object datumParams = wktDef.get("datum_params");
                if (datumParams instanceof List) {
                    List<?> params = (List<?>) datumParams;
                    this.datum_params = new String[params.size()];
                    for (int i = 0; i < params.size(); i++) {
                        this.datum_params[i] = params.get(i).toString();
                    }
                }
            }
            
            // Calculate derived parameters
            calculateDerivedParameters();
            
            // Set datum
            this.datum = Datum.get(this.datumCode);
            if (this.datum == null) {
                this.datum = Datum.getWGS84();
            }
            
            // Set transformation methods based on projection type
            initializeProjectionMethods();
            
        } catch (WKTParseException e) {
            throw new IllegalArgumentException("Failed to parse WKT string: " + e.getMessage(), e);
        }
    }
    
    
    /**
     * Initializes projection-specific transformation methods.
     */
    private void initializeProjectionMethods() {
        switch (this.projName) {
            case "longlat":
                this.forward = p -> new Point(p.x, p.y, p.z, p.m);
                this.inverse = p -> new Point(p.x, p.y, p.z, p.m);
                this.init = null;
                break;
            case "merc":
                this.forward = this::mercatorForward;
                this.inverse = this::mercatorInverse;
                this.init = this::mercatorInit;
                break;
            default:
                // For unsupported projections, use identity transformation
                this.forward = p -> new Point(p.x, p.y, p.z, p.m);
                this.inverse = p -> new Point(p.x, p.y, p.z, p.m);
                this.init = null;
                break;
        }
        
        // Initialize the projection if needed
        if (this.init != null) {
            this.init.initialize(this);
        }
    }
    
    /**
     * Initializes as a longitude/latitude projection.
     */
    private void initializeLongLat() {
        this.projName = "longlat";
        this.name = "longlat";
        this.title = "Longitude/Latitude";
        this.ellps = "WGS84";
        this.datumCode = "WGS84";
        this.units = "degrees";
        
        // Set ellipsoid parameters
        Ellipsoid.EllipsoidDef ellipsoid = Ellipsoid.getWGS84();
        this.a = ellipsoid.a;
        this.b = ellipsoid.b;
        this.rf = ellipsoid.rf;
        
        // Calculate derived parameters
        calculateDerivedParameters();
        
        // Set datum
        this.datum = Datum.getWGS84();
        
        // Set transformation methods (identity for longlat)
        this.forward = p -> new Point(p.x, p.y, p.z, p.m); // Identity transformation
        this.inverse = p -> new Point(p.x, p.y, p.z, p.m); // Identity transformation
        this.init = null; // No initialization needed
    }
    
    /**
     * Initializes as a Mercator projection.
     */
    private void initializeMercator() {
        this.projName = "merc";
        this.name = "Mercator";
        this.title = "Mercator";
        this.ellps = "WGS84";
        this.datumCode = "WGS84";
        this.units = "m";
        
        // Set ellipsoid parameters
        Ellipsoid.EllipsoidDef ellipsoid = Ellipsoid.getWGS84();
        this.a = ellipsoid.a;
        this.b = ellipsoid.b;
        this.rf = ellipsoid.rf;
        
        // Calculate derived parameters
        calculateDerivedParameters();
        
        // Set datum
        this.datum = Datum.getWGS84();
        
        // Set transformation methods
        this.forward = this::mercatorForward;
        this.inverse = this::mercatorInverse;
        this.init = this::mercatorInit;
        
        // Initialize the projection
        if (this.init != null) {
            this.init.initialize(this);
        }
    }
    
    /**
     * Calculates derived ellipsoid parameters.
     */
    private void calculateDerivedParameters() {
        if (Double.isNaN(this.b) && !Double.isNaN(this.rf)) {
            // Calculate b from a and rf
            this.b = this.a * (1.0 - 1.0 / this.rf);
        }
        
        if (!Double.isNaN(this.b)) {
            this.es = 1.0 - (this.b * this.b) / (this.a * this.a);
            this.e = Math.sqrt(this.es);
            this.ep2 = (this.a * this.a - this.b * this.b) / (this.b * this.b);
        }
        
        this.sphere = Values.equals(this.a, this.b);
    }
    
    /**
     * Mercator projection initialization.
     */
    private void mercatorInit(Projection proj) {
        if (proj.lat_ts != 0) {
            if (proj.sphere) {
                proj.k0 = Math.cos(proj.lat_ts);
            } else {
                proj.k0 = MathUtils.msfnz(proj.e, Math.sin(proj.lat_ts), Math.cos(proj.lat_ts));
            }
        }
    }
    
    /**
     * Mercator forward transformation.
     */
    private Point mercatorForward(Point p) {
        double lon = p.x;
        double lat = p.y;
        
        // Check for invalid coordinates
        if (lat * Values.R2D > 90 || lat * Values.R2D < -90) {
            return null;
        }
        
        if (Math.abs(Math.abs(lat) - Values.HALF_PI) <= Values.EPSLN) {
            return null; // Cannot project poles
        }
        
        double x, y;
        if (this.sphere) {
            x = this.x0 + this.a * this.k0 * adjustLon(lon - this.long0);
            y = this.y0 + this.a * this.k0 * Math.log(Math.tan(Values.FORTPI + 0.5 * lat));
        } else {
            double sinphi = Math.sin(lat);
            double ts = MathUtils.tsfnz(this.e, lat, sinphi);
            x = this.x0 + this.a * this.k0 * adjustLon(lon - this.long0);
            y = this.y0 - this.a * this.k0 * Math.log(ts);
        }
        
        return new Point(x, y, p.z, p.m);
    }
    
    /**
     * Mercator inverse transformation.
     */
    private Point mercatorInverse(Point p) {
        double x = p.x - this.x0;
        double y = p.y - this.y0;
        
        double lat, lon;
        if (this.sphere) {
            lat = Values.HALF_PI - 2 * Math.atan(Math.exp(-y / (this.a * this.k0)));
        } else {
            double ts = Math.exp(-y / (this.a * this.k0));
            lat = MathUtils.phi2z(this.e, ts);
            if (lat == -9999) {
                return null;
            }
        }
        
        lon = adjustLon(this.long0 + x / (this.a * this.k0));
        
        return new Point(lon, lat, p.z, p.m);
    }
    
    /**
     * Adjusts longitude to valid range.
     */
    private double adjustLon(double lon) {
        return MathUtils.adjustLon(lon);
    }
    
    /**
     * Registers a projection factory.
     * @param name the projection name
     * @param factory the projection factory
     */
    public static void registerProjection(String name, ProjectionFactory factory) {
        PROJECTIONS.put(name, factory);
    }
    
    /**
     * Gets a projection factory by name.
     * @param name the projection name
     * @return the projection factory, or null if not found
     */
    public static ProjectionFactory getProjectionFactory(String name) {
        return PROJECTIONS.get(name);
    }
    
    /**
     * Gets all registered projection names.
     * @return list of projection names
     */
    public static List<String> getProjectionNames() {
        return new ArrayList<>(PROJECTIONS.keySet());
    }
}
