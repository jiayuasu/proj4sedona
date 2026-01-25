package org.proj4sedona.core;

import org.proj4sedona.constants.Datum;
import org.proj4sedona.constants.Values;
import org.proj4sedona.parser.ProjString;
import org.proj4sedona.defs.Defs;
import org.proj4sedona.projection.Projection;
import org.proj4sedona.projection.ProjectionParams;
import org.proj4sedona.projection.ProjectionRegistry;

/**
 * Main projection class that initializes and manages coordinate system transformations.
 * Mirrors: lib/Proj.js
 * 
 * <p>This class:</p>
 * <ol>
 *   <li>Parses the SRS code (PROJ string, EPSG code, etc.)</li>
 *   <li>Looks up from the definition registry if applicable</li>
 *   <li>Looks up datum definitions</li>
 *   <li>Derives ellipsoid and eccentricity constants</li>
 *   <li>Initializes the projection implementation</li>
 * </ol>
 * 
 * <p>Supported input formats:</p>
 * <ul>
 *   <li>PROJ strings: "+proj=longlat +datum=WGS84"</li>
 *   <li>EPSG codes: "EPSG:4326", "EPSG:3857"</li>
 *   <li>Aliases: "WGS84", "GOOGLE"</li>
 * </ul>
 */
public class Proj {

    private final ProjectionParams params;
    private final Projection projection;

    /**
     * Create a projection from an SRS code.
     * 
     * @param srsCode The SRS code (PROJ string, EPSG code, or alias)
     * @throws IllegalArgumentException if the projection cannot be parsed or is not supported
     */
    public Proj(String srsCode) {
        // Ensure registries are initialized
        ProjectionRegistry.start();
        if (!Defs.isGlobalsInitialized()) {
            Defs.globals();
        }

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
     * Mirrors: lib/parseCode.js
     * 
     * <p>Resolution order:</p>
     * <ol>
     *   <li>PROJ string (starts with "+")</li>
     *   <li>Definition registry lookup (EPSG codes, aliases)</li>
     *   <li>WKT parsing (Phase 13 - TODO)</li>
     * </ol>
     * 
     * @param srsCode The input SRS code
     * @return The parsed ProjectionDef, or null if parsing fails
     */
    private ProjectionDef parseCode(String srsCode) {
        if (srsCode == null || srsCode.isEmpty()) {
            return null;
        }

        // Check if it's a PROJ string (starts with +)
        if (srsCode.charAt(0) == '+') {
            ProjectionDef def = ProjString.parse(srsCode);
            if (def.getSrsCode() == null) {
                def.setSrsCode(srsCode);
            }
            return def;
        }

        // Check the definition registry (EPSG codes, aliases like WGS84, GOOGLE)
        ProjectionDef def = Defs.get(srsCode);
        if (def != null) {
            // Return the cached definition - it's already parsed
            return def;
        }

        // TODO: Add WKT parsing in Phase 13
        // Check for WKT format: contains '[' and doesn't start with '+'
        // if (srsCode.indexOf('[') != -1) {
        //     return WktParser.parse(srsCode);
        // }

        // Last resort: try parsing as PROJ string anyway
        try {
            ProjectionDef parsed = ProjString.parse(srsCode);
            if (parsed.getSrsCode() == null) {
                parsed.setSrsCode(srsCode);
            }
            return parsed;
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
