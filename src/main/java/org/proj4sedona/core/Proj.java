package org.proj4sedona.core;

import com.google.gson.Gson;
import org.proj4sedona.constants.Datum;
import org.proj4sedona.constants.Values;
import org.proj4sedona.defs.Defs;
import org.proj4sedona.parser.ProjString;
import org.proj4sedona.parser.WktParser;
import org.proj4sedona.projection.Projection;
import org.proj4sedona.projection.ProjectionParams;
import org.proj4sedona.projection.ProjectionRegistry;

import java.util.Map;

/**
 * Main projection class that initializes and manages coordinate system transformations.
 * Mirrors: lib/Proj.js
 * 
 * This class:
 * 1. Parses the SRS code (PROJ string, etc.)
 * 2. Looks up datum definitions
 * 3. Derives ellipsoid and eccentricity constants
 * 4. Initializes the projection implementation
 */
public class Proj {

    private final ProjectionParams params;
    private final Projection projection;

    /**
     * Create a projection from an SRS code (PROJ string).
     * 
     * @param srsCode The SRS code (e.g., "+proj=longlat +datum=WGS84")
     * @throws IllegalArgumentException if the projection cannot be parsed or is not supported
     */
    public Proj(String srsCode) {
        // Ensure registry is initialized
        ProjectionRegistry.start();

        // Parse the SRS code
        ProjectionDef def = parseCode(srsCode);
        if (def == null || def.getProjName() == null) {
            throw new IllegalArgumentException("Could not parse SRS code: " + srsCode);
        }

        // Get the projection implementation
        projection = ProjectionRegistry.get(def.getProjName());
        if (projection == null) {
            throw new IllegalArgumentException("Unknown projection: " + def.getProjName());
        }

        // Process datum definition
        processDatumDef(def);

        // Set defaults
        if (def.getK0() == null) def.setK0(1.0);
        if (def.getAxis() == null) def.setAxis("enu");
        if (def.getEllps() == null) def.setEllps("wgs84");
        if (def.getLat1() == null && def.getLat0() != null) {
            def.setLat1(def.getLat0()); // Lambert 1SP needs this
        }

        // Derive sphere constants
        DeriveConstants.SphereResult sphere = DeriveConstants.sphere(
            def.getA(), def.getB(), def.getRf(), def.getEllps(), def.getSphere()
        );

        // Derive eccentricity
        DeriveConstants.EccentricityResult ecc = DeriveConstants.eccentricity(
            sphere.a, sphere.b, sphere.rf, def.getRA()
        );

        // Create datum object
        DatumParams datum = createDatum(def, sphere, ecc);

        // Build projection parameters
        params = buildParams(def, sphere, ecc, datum);

        // Initialize the projection
        projection.init(params);
    }

    /**
     * Parse the SRS code into a ProjectionDef.
     * Supports PROJ strings, WKT1, WKT2, PROJJSON formats, and Defs lookups.
     * 
     * <p>Format detection (all near O(1) cost):</p>
     * <ul>
     *   <li>PROJ string: starts with '+'</li>
     *   <li>PROJJSON: starts with '{'</li>
     *   <li>WKT1/WKT2: contains '['</li>
     *   <li>EPSG codes: looked up in Defs registry</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private ProjectionDef parseCode(String srsCode) {
        if (srsCode == null || srsCode.isEmpty()) {
            return null;
        }

        String trimmed = srsCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        char firstChar = trimmed.charAt(0);

        // Check if it's a PROJ string (starts with +)
        if (firstChar == '+') {
            return ProjString.parse(srsCode);
        }

        // Check if it's a PROJJSON string (starts with {)
        if (firstChar == '{') {
            try {
                Gson gson = new Gson();
                Map<String, Object> json = gson.fromJson(srsCode, Map.class);
                return WktParser.parse(json);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid PROJJSON: " + e.getMessage(), e);
            }
        }

        // Check if it's WKT format (contains '[' and doesn't start with '+')
        if (WktParser.isWkt(srsCode)) {
            return WktParser.parse(srsCode);
        }

        // Try looking up in the Defs registry (for EPSG codes, aliases, etc.)
        ProjectionDef defFromRegistry = Defs.get(srsCode);
        if (defFromRegistry != null) {
            return defFromRegistry;
        }

        // Try parsing as PROJ string anyway (for simple strings without +)
        try {
            return ProjString.parse(srsCode);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Process datum definition lookup.
     * If datumCode is set, look up the datum and fill in missing parameters.
     * Mirrors: lib/Proj.js lines 54-61
     */
    private void processDatumDef(ProjectionDef def) {
        if (def.getDatumCode() == null || "none".equalsIgnoreCase(def.getDatumCode())) {
            return;
        }

        // Look up datum by code
        Datum datumDef = Datum.get(def.getDatumCode());
        if (datumDef != null) {
            // Fill in datum_params if not already set
            if (def.getDatumParams() == null) {
                double[] towgs84 = datumDef.getTowgs84Array();
                if (towgs84 != null) {
                    def.setDatumParams(towgs84);
                }
            }

            // Fill in ellipsoid if not already set
            if (def.getEllps() == null && datumDef.getEllipse() != null) {
                def.setEllps(datumDef.getEllipse());
            }

            // Fill in datum name
            if (def.getDatumName() == null) {
                def.setDatumName(datumDef.getDatumName() != null ? 
                    datumDef.getDatumName() : def.getDatumCode());
            }

            // Handle nadgrids
            if (def.getNadgrids() == null && datumDef.getNadgrids() != null) {
                def.setNadgrids(datumDef.getNadgrids());
            }
        }
    }

    /**
     * Create datum parameters object.
     */
    private DatumParams createDatum(ProjectionDef def, 
                                     DeriveConstants.SphereResult sphere,
                                     DeriveConstants.EccentricityResult ecc) {
        // TODO: Handle nadgrids lookup in Phase 14
        return new DatumParams(
            def.getDatumCode(),
            def.getDatumParams(),
            sphere.a,
            sphere.b,
            ecc.es,
            ecc.ep2,
            null  // nadgrids will be added later
        );
    }

    /**
     * Build projection parameters from definition and derived constants.
     */
    private ProjectionParams buildParams(ProjectionDef def,
                                          DeriveConstants.SphereResult sphere,
                                          DeriveConstants.EccentricityResult ecc,
                                          DatumParams datum) {
        ProjectionParams p = new ProjectionParams();

        // Ellipsoid (derived)
        p.a = sphere.a;
        p.b = sphere.b;
        p.rf = sphere.rf;
        p.sphere = sphere.sphere;

        // Eccentricity (derived)
        p.es = ecc.es;
        p.e = ecc.e;
        p.ep2 = ecc.ep2;

        // Datum
        p.datum = datum;

        // Projection parameters (from definition)
        p.projName = def.getProjName();
        p.lat0 = def.getLat0();
        p.lat1 = def.getLat1();
        p.lat2 = def.getLat2();
        p.latTs = def.getLatTs();
        p.long0 = def.getLong0();
        p.long1 = def.getLong1();
        p.long2 = def.getLong2();
        p.alpha = def.getAlpha();
        p.longc = def.getLongc();
        p.rectifiedGridAngle = def.getRectifiedGridAngle();

        // Scale and offsets
        p.k0 = def.getK0() != null ? def.getK0() : 1.0;
        p.x0 = def.getX0() != null ? def.getX0() : 0.0;
        p.y0 = def.getY0() != null ? def.getY0() : 0.0;

        // Units
        p.toMeter = def.getToMeter();
        p.units = def.getUnits();
        p.fromGreenwich = def.getFromGreenwich();
        p.axis = def.getAxis() != null ? def.getAxis() : "enu";

        // UTM
        p.zone = def.getZone();
        p.utmSouth = def.getUtmSouth();

        // Flags
        p.rA = def.getRA();
        p.approx = def.getApprox();
        p.over = def.getOver();

        // Original
        p.srsCode = def.getSrsCode();
        p.datumCode = def.getDatumCode();

        return p;
    }

    /**
     * Forward projection: geodetic to projected coordinates.
     * 
     * @param p Point with x=longitude, y=latitude (in radians)
     * @return Point with x=easting, y=northing
     */
    public Point forward(Point p) {
        return projection.forward(p);
    }

    /**
     * Inverse projection: projected to geodetic coordinates.
     * 
     * @param p Point with x=easting, y=northing
     * @return Point with x=longitude, y=latitude (in radians)
     */
    public Point inverse(Point p) {
        return projection.inverse(p);
    }

    /**
     * Get the projection parameters.
     */
    public ProjectionParams getParams() {
        return params;
    }

    /**
     * Get the underlying projection implementation.
     */
    public Projection getProjection() {
        return projection;
    }

    /**
     * Get the semi-major axis.
     */
    public double getA() {
        return params.a;
    }

    /**
     * Get the semi-minor axis.
     */
    public double getB() {
        return params.b;
    }

    /**
     * Get the eccentricity squared.
     */
    public double getEs() {
        return params.es;
    }

    /**
     * Get the datum parameters.
     */
    public DatumParams getDatum() {
        return params.datum;
    }

    /**
     * Check if this is a sphere (a == b).
     */
    public boolean isSphere() {
        return params.sphere;
    }
}
