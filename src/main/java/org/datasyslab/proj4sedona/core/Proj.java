package org.datasyslab.proj4sedona.core;

import com.google.gson.Gson;
import org.datasyslab.proj4sedona.constants.Datum;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.defs.Defs;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.parser.ProjString;
import org.datasyslab.proj4sedona.parser.WktParser;
import org.datasyslab.proj4sedona.projection.Projection;
import org.datasyslab.proj4sedona.projection.ProjectionParams;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;

import java.util.Map;
import java.util.Set;

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

    // Known web mercator EPSG codes whose WKT2/PROJJSON definitions are
    // traditionally wrong (they declare the WGS84 ellipsoid instead of a sphere).
    // When we detect one of these after parsing, we replace it with the correct
    // hard-coded definition.  Mirrors: proj4js lib/parseCode.js checkMercator()
    private static final Set<String> WEB_MERCATOR_CODES = Set.of("3857", "900913", "3785", "102113");

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

        // Preserve the original authority:code in srsCode if input looks like one
        // (e.g., "EPSG:3857", "IAU:49900") and parsing didn't already set it
        if (def.getSrsCode() == null && srsCode != null) {
            String trimmed = srsCode.trim();
            if (trimmed.matches("(?i)[A-Z]+:\\d+")) {
                def.setSrsCode(trimmed.toUpperCase());
            }
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

        ProjectionDef out = null;
        char firstChar = trimmed.charAt(0);

        // Check if it's a PROJ string (starts with +)
        if (firstChar == '+') {
            out = ProjString.parse(trimmed);
        }
        // Check if it's a PROJJSON string (starts with {)
        else if (firstChar == '{') {
            try {
                Gson gson = new Gson();
                Map<String, Object> json = gson.fromJson(trimmed, Map.class);
                out = WktParser.parse(json);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid PROJJSON: " + e.getMessage(), e);
            }
        }
        // Check if it's WKT format (contains '[' and doesn't start with '+')
        else if (WktParser.isWkt(trimmed)) {
            out = WktParser.parse(trimmed);
        } else {
            // Try looking up in the Defs registry (for EPSG codes, aliases, etc.)
            out = Defs.get(trimmed);

            // Try parsing as PROJ string anyway (for simple strings without +)
            if (out == null) {
                try {
                    out = ProjString.parse(trimmed);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        // Check for special Web Mercator case in all code paths.
        // Web Mercator definitions are commonly malformed (WGS84 ellipsoid
        // instead of sphere), so always replace with the correct hard-coded
        // definition. Mirrors: proj4js PR #553.
        return checkWebMercator(out);
    }

    /**
     * If the parsed definition matches a known web mercator EPSG code,
     * return the hard-coded (correct, sphere-based) definition instead.
     * Web mercator WKT2/PROJJSON definitions are traditionally wrong because
     * they declare the WGS84 ellipsoid (a != b) instead of a sphere; the
     * hard-coded replacement uses a sphere with a == b == 6378137.
     * Mirrors: proj4js PR #546, #553 / lib/parseCode.js checkMercator()
     */
    private static ProjectionDef checkWebMercator(ProjectionDef def) {
        if (def == null) {
            return null;
        }
        String title = def.getTitle();
        if (title != null) {
            String lower = title.toLowerCase();
            if (lower.startsWith("epsg:") && WEB_MERCATOR_CODES.contains(lower.substring(5))) {
                ProjectionDef hardcoded = Defs.get("EPSG:3857");
                if (hardcoded != null) {
                    return hardcoded;
                }
            }
        }
        return def;
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
        // Check if nadgrids is specified
        String nadgrids = def.getNadgrids();
        if (nadgrids != null && !nadgrids.isEmpty()) {
            // Use factory method that handles nadgrids string
            return DatumParams.withNadgrids(
                def.getDatumCode(),
                def.getDatumParams(),
                sphere.a,
                sphere.b,
                ecc.es,
                ecc.ep2,
                nadgrids
            );
        }
        
        // Standard datum without grid shift
        return new DatumParams(
            def.getDatumCode(),
            def.getDatumParams(),
            sphere.a,
            sphere.b,
            ecc.es,
            ecc.ep2,
            null
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
        p.noUoff = def.getNoUoff();
        p.noRot = def.getNoRot();
        p.h = def.getH();
        p.tilt = def.getTilt();
        p.azi = def.getAzi();
        p.longWrap = def.getLongWrap();
        p.sweep = def.getSweep();

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

    // ==================== Export Methods ====================

    /**
     * Export this CRS to PROJ string format.
     * 
     * @return PROJ string (e.g., "+proj=longlat +datum=WGS84 +no_defs")
     */
    public String toProjString() {
        return CRSSerializer.toProjString(this);
    }

    /**
     * Export this CRS to OGC WKT 1 format.
     * 
     * @return WKT1 string
     */
    public String toWkt1() {
        return CRSSerializer.toWkt1(this);
    }

    /**
     * Export this CRS to ISO 19162 WKT 2 format.
     * 
     * @return WKT2 string
     */
    public String toWkt2() {
        return CRSSerializer.toWkt2(this);
    }

    /**
     * Export this CRS to PROJJSON format.
     * 
     * @return PROJJSON string (pretty-printed)
     */
    public String toProjJson() {
        return CRSSerializer.toProjJson(this);
    }

    /**
     * Export this CRS to PROJJSON format.
     * 
     * @param prettyPrint Whether to pretty-print the JSON
     * @return PROJJSON string
     */
    public String toProjJson(boolean prettyPrint) {
        return CRSSerializer.toProjJson(this, prettyPrint);
    }

    /**
     * Attempt to identify the EPSG code for this CRS.
     * 
     * <p>Returns "EPSG:code" only when the authority is EPSG. For non-EPSG authorities
     * (e.g., IAU), returns null. Use {@link #toAuthority()} for general authority lookup.</p>
     * 
     * @return EPSG code (e.g., "EPSG:4326") or null if not found or non-EPSG authority
     */
    public String toEpsgCode() {
        return CRSSerializer.toEpsgCode(this);
    }

    /**
     * Attempt to identify the authority and code for this CRS.
     * 
     * <p>Returns a String array {"authority", "code"} for any recognized authority
     * (e.g., {"EPSG", "4326"} or {"IAU", "49900"}). Returns null if not found.</p>
     * 
     * <p>Identification strategy:</p>
     * <ol>
     *   <li>Check if the input already declared an authority:code (e.g., from PROJJSON id field)</li>
     *   <li>Try datum name lookup against well-known datum names</li>
     *   <li>Fall back to parameter matching against known EPSG definitions</li>
     * </ol>
     * 
     * <p>Similar to pyproj's {@code CRS.to_authority()} method.</p>
     * 
     * @return String array {"authority", "code"} or null if not found
     */
    public String[] toAuthority() {
        return CRSSerializer.toAuthority(this);
    }

    @Override
    public String toString() {
        return toProjString();
    }
}
