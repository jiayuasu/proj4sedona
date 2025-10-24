/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sedona.proj.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sedona.proj.common.MathUtils;
import org.apache.sedona.proj.constants.Datum;
import org.apache.sedona.proj.constants.Ellipsoid;
import org.apache.sedona.proj.constants.Units;
import org.apache.sedona.proj.constants.Values;
import org.apache.sedona.proj.parse.ProjStringParser;
import org.apache.sedona.proj.wkt.WKTParseException;
import org.apache.sedona.proj.wkt.WKTProcessor;

/**
 * Represents a map projection with its parameters and transformation methods. This is the Java
 * equivalent of the JavaScript Proj class.
 */
public class Projection {

  // Projection parameters
  /** Projection name */
  public String name;

  /** Projection title */
  public String title;

  /** Projection type name */
  public String projName;

  /** Ellipsoid name */
  public String ellps;

  /** Datum code */
  public String datumCode;

  /** Datum name */
  public String datumName;

  /** Axis orientation */
  public String axis;

  /** Units */
  public String units;

  /** NAD grid files */
  public String nadgrids;

  // Ellipsoid parameters
  /** Semi-major axis */
  public double a;

  /** Semi-minor axis */
  public double b;

  /** Reciprocal of flattening */
  public double rf;

  /** Eccentricity squared */
  public double es;

  /** Eccentricity */
  public double e;

  /** Second eccentricity squared */
  public double ep2;

  /** Whether using spherical approximation */
  public boolean sphere;

  // Projection parameters
  /** Latitude of origin */
  public double lat0;

  /** First standard parallel */
  public double lat1;

  /** Second standard parallel */
  public double lat2;

  /** Latitude of true scale */
  public double lat_ts;

  /** Central meridian */
  public double long0;

  /** First longitude */
  public double long1;

  /** Second longitude */
  public double long2;

  /** Azimuth */
  public double alpha;

  /** Longitude of center */
  public double longc;

  /** False easting */
  public double x0;

  /** False northing */
  public double y0;

  /** Scale factor */
  public double k0;

  /** Unit conversion factor */
  public double to_meter;

  /** Prime meridian offset */
  public double from_greenwich;

  // Datum and transformation
  /** Datum definition */
  public Datum.DatumDef datum;

  /** Datum parameters */
  public String[] datum_params;

  // Additional projection parameters
  /** Cone constant for conic projections */
  public double n;

  /** Constant for equal area projections */
  public double c;

  /** Radius at origin latitude */
  public double rho0;

  /** Scale factor for LCC */
  public double f0;

  /** Ellipsoid coefficient e0 */
  public double e0;

  /** Ellipsoid coefficient e1 */
  public double e1;

  /** Ellipsoid coefficient e2 */
  public double e2;

  /** Ellipsoid coefficient e3 */
  public double e3;

  /** Meridian length coefficients */
  public double[] en;

  /** Meridian length at origin */
  public double ml0;

  /** UTM zone */
  public int zone;

  /** UTM southern hemisphere flag */
  public boolean utmSouth;

  /** Albers Equal Area cone constant */
  public double ns0;

  // Hotine Oblique Mercator parameters
  /** No offset flag */
  public boolean noOff;

  /** No rotation flag */
  public boolean noRot;

  /** No U offset flag */
  public boolean noUoff;

  /** Rectified grid angle */
  public double rectifiedGridAngle;

  // Projection methods (to be set by specific projection implementations)
  /** Forward projection method */
  public ProjectionMethod forward;

  /** Inverse projection method */
  public ProjectionMethod inverse;

  /** Projection initializer */
  public ProjectionInitializer init;

  // Projection-specific instances
  /** Hotine Oblique Mercator instance */
  public Object omerc;

  /** Equidistant Conic instance */
  public Object eqdc;

  /** Lambert Azimuthal Equal Area instance */
  public Object laea;

  /** Gnomonic instance */
  public Object gnom;

  /** UTM instance */
  public Object utm;

  /** Albers Equal Area instance */
  public Object aea;

  /** Sinusoidal instance */
  public Object sinu;

  /** Cylindrical Equal Area instance */
  public Object cea;

  // Additional projection-specific parameters
  /** Sine of projection latitude (for azimuthal projections) */
  public double sinP14;

  /** Cosine of projection latitude (for azimuthal projections) */
  public double cosP14;

  /** Infinity distance (for gnomonic projection) */
  public double infinityDist;

  /** RC parameter (for gnomonic projection) */
  public double rc;

  /** K parameter (general scale factor) */
  public double k;

  /** Latitude 1 (for Bonne, etc.) */
  public double phi1;

  /** Cosine of phi1 */
  public double cphi1;

  /** M1 parameter */
  public double m1;

  /** AM1 parameter */
  public double am1;

  /** R parameter (radius) */
  public double R;

  /** Sine of latitude 0 (for some projections) */
  public double sinph0;

  /** Cosine of latitude 0 (for some projections) */
  public double cosph0;

  /** Sine of X0 (for stereographic) */
  public double sinX0;

  /** Cosine of X0 (for stereographic) */
  public double cosX0;

  /** X0 parameter (for stereographic) */
  public double X0;

  /** MS1 parameter (for stereographic) */
  public double ms1;

  /** CON parameter (for stereographic) */
  public double con;

  /** CONS parameter (for stereographic) */
  public double cons;

  /** Sine of lat0 */
  public double sinlat0;

  /** Cosine of lat0 */
  public double coslat0;

  // LAEA-specific parameters
  /** Mode for Lambert Azimuthal Equal Area */
  public int mode;

  /** QP parameter for LAEA */
  public double qp;

  /** MMF parameter for LAEA */
  public double mmf;

  /** APA array for LAEA */
  public double[] apa;

  /** RQ parameter for LAEA */
  public double rq;

  /** DD parameter for LAEA */
  public double dd;

  /** XMF parameter for LAEA */
  public double xmf;

  /** YMF parameter for LAEA */
  public double ymf;

  /** SINB1 parameter for LAEA */
  public double sinb1;

  /** COSB1 parameter for LAEA */
  public double cosb1;

  // Registry of available projections
  private static final Map<String, ProjectionFactory> PROJECTIONS = new ConcurrentHashMap<>();

  /** Functional interface for projection forward/inverse methods. */
  @FunctionalInterface
  public interface ProjectionMethod {
    /**
     * Transforms a point using the projection method.
     *
     * @param point the point to transform
     * @return the transformed point
     */
    Point transform(Point point);
  }

  /** Functional interface for projection initialization. */
  @FunctionalInterface
  public interface ProjectionInitializer {
    /**
     * Initializes the projection with the given parameters.
     *
     * @param proj the projection to initialize
     */
    void initialize(Projection proj);
  }

  /** Functional interface for projection factory. */
  @FunctionalInterface
  public interface ProjectionFactory {
    /**
     * Creates a new projection instance.
     *
     * @return a new projection instance
     */
    Projection create();
  }

  /**
   * Creates a new projection instance.
   *
   * @param srsCode the spatial reference system code or definition
   */
  public Projection(String srsCode) {
    this();
    initializeFromCode(srsCode);
  }

  /** Creates a new projection instance. */
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

    // Initialize ellipsoid parameters to NaN to distinguish between unset and explicitly set to 0
    // NaN allows us to detect missing values and fall back to ellipsoid definitions
    // Validation in calculateDerivedParameters() ensures these are properly set before use
    this.a = Double.NaN;
    this.b = Double.NaN;
    this.rf = Double.NaN;
  }

  /**
   * Initializes the projection from a spatial reference system code.
   *
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
    else if (srsCode.startsWith("GEOGCS")
        || srsCode.startsWith("PROJCS")
        || srsCode.startsWith("GEOGCRS")
        || srsCode.startsWith("GEODCRS")
        || srsCode.startsWith("PROJCRS")) {
      initializeFromWKT(srsCode);
    }
    // Handle EPSG codes
    else if ("WGS84".equals(srsCode) || "EPSG:4326".equals(srsCode)) {
      initializeLongLat();
    } else if ("EPSG:3857".equals(srsCode) || "GOOGLE".equals(srsCode)) {
      initializeMercator();
    } else if ("EPSG:4269".equals(srsCode)) {
      // NAD83 (long/lat)
      initializeFromProjString(
          "+title=NAD83 (long/lat) +proj=longlat +a=6378137.0 +b=6356752.31414036 +ellps=GRS80 +datum=NAD83 +units=degrees");
    } else if (srsCode.startsWith("EPSG:326") || srsCode.startsWith("EPSG:327")) {
      // UTM zones (EPSG:32601-32660 for North, EPSG:32701-32760 for South)
      handleUTMZone(srsCode);
    } else if (srsCode.startsWith("EPSG:")) {
      // Try to fetch from spatialreference.org as fallback
      fetchFromSpatialReference(srsCode);
    } else {
      throw new IllegalArgumentException("Unsupported SRS code: " + srsCode);
    }
  }

  /**
   * Fetches CRS definition from spatialreference.org for unknown EPSG codes. Uses the
   * EpsgDefinitionCache to avoid redundant network calls for the same EPSG code.
   *
   * @param srsCode the EPSG code to fetch
   */
  private void fetchFromSpatialReference(String srsCode) {
    try {
      // Use the dedicated EPSG definition cache
      String projString = org.apache.sedona.proj.cache.EpsgDefinitionCache.getDefinition(srsCode);

      // Initialize the projection from the cached/fetched PROJ string
      initializeFromProjString(projString);

    } catch (java.io.IOException e) {
      throw new IllegalArgumentException(
          "Failed to fetch EPSG code from spatialreference.org: "
              + srsCode
              + " - "
              + e.getMessage());
    }
  }

  /**
   * Handles UTM zone EPSG codes (EPSG:32601-32660 for North, EPSG:32701-32760 for South).
   *
   * @param srsCode the EPSG code for a UTM zone
   */
  private void handleUTMZone(String srsCode) {
    try {
      int epsgCode = Integer.parseInt(srsCode.substring(5)); // Extract number after "EPSG:"

      int zone;
      boolean south = false;

      if (epsgCode >= 32601 && epsgCode <= 32660) {
        // Northern hemisphere
        zone = epsgCode - 32600;
        south = false;
      } else if (epsgCode >= 32701 && epsgCode <= 32760) {
        // Southern hemisphere
        zone = epsgCode - 32700;
        south = true;
      } else {
        throw new IllegalArgumentException("Invalid UTM zone EPSG code: " + srsCode);
      }

      // Build PROJ string for UTM zone
      String projString =
          "+proj=utm +zone=" + zone + (south ? " +south" : "") + " +datum=WGS84 +units=m";
      initializeFromProjString(projString);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid EPSG code format: " + srsCode);
    }
  }

  /**
   * Initializes the projection from a PROJ string.
   *
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
    this.rectifiedGridAngle = (Double) def.getOrDefault("rectifiedGridAngle", 0.0);
    this.x0 = (Double) def.getOrDefault("x0", Values.DEFAULT_X0);
    this.y0 = (Double) def.getOrDefault("y0", Values.DEFAULT_Y0);
    this.k0 = (Double) def.getOrDefault("k0", Values.DEFAULT_K0);

    // Set boolean parameters
    this.noOff = (Boolean) def.getOrDefault("noOff", false);
    this.noRot = (Boolean) def.getOrDefault("noRot", false);
    this.noUoff = (Boolean) def.getOrDefault("noUoff", false);

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

    // Set UTM parameters
    if (def.containsKey("zone")) {
      this.zone = ((Number) def.get("zone")).intValue();
    }
    if (def.containsKey("utmSouth")) {
      this.utmSouth = (Boolean) def.get("utmSouth");
    }

    // Calculate derived parameters
    calculateDerivedParameters();

    // Set datum
    this.datum = Datum.get(this.datumCode);
    if (this.datum == null) {
      this.datum = Datum.getWGS84();
    }

    // Set ellipsoid parameters in datum
    if (this.datum != null) {
      this.datum.setEllipsoidParams(this.a, this.b, this.es, this.ep2);
    }

    // Set transformation methods based on projection type
    initializeProjectionMethods();
  }

  /**
   * Initializes the projection from a WKT string.
   *
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
      if (wktDef.containsKey("b")) {
        this.b = ((Number) wktDef.get("b")).doubleValue();
      }
      if (wktDef.containsKey("rf")) {
        this.rf = ((Number) wktDef.get("rf")).doubleValue();
      }
      if (wktDef.containsKey("es")) {
        this.es = ((Number) wktDef.get("es")).doubleValue();
      }
      if (wktDef.containsKey("e")) {
        this.e = ((Number) wktDef.get("e")).doubleValue();
      }

      // Set projection parameters
      if (wktDef.containsKey("lat0")) {
        this.lat0 = ((Number) wktDef.get("lat0")).doubleValue();
      } else if (wktDef.containsKey("latitude_of_origin")) {
        this.lat0 = Math.toRadians(((Number) wktDef.get("latitude_of_origin")).doubleValue());
      }
      if (wktDef.containsKey("lat1")) {
        this.lat1 = ((Number) wktDef.get("lat1")).doubleValue();
      } else if (wktDef.containsKey("standard_parallel_1")) {
        this.lat1 = Math.toRadians(((Number) wktDef.get("standard_parallel_1")).doubleValue());
      }
      if (wktDef.containsKey("lat2")) {
        this.lat2 = ((Number) wktDef.get("lat2")).doubleValue();
      } else if (wktDef.containsKey("standard_parallel_2")) {
        this.lat2 = Math.toRadians(((Number) wktDef.get("standard_parallel_2")).doubleValue());
      }
      if (wktDef.containsKey("long0")) {
        this.long0 = ((Number) wktDef.get("long0")).doubleValue();
      } else if (wktDef.containsKey("central_meridian")) {
        this.long0 = Math.toRadians(((Number) wktDef.get("central_meridian")).doubleValue());
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
      if (wktDef.containsKey("alpha")) {
        this.alpha = ((Number) wktDef.get("alpha")).doubleValue();
      }
      if (wktDef.containsKey("longc")) {
        this.longc = ((Number) wktDef.get("longc")).doubleValue();
      }
      if (wktDef.containsKey("rectifiedGridAngle")) {
        this.rectifiedGridAngle = ((Number) wktDef.get("rectifiedGridAngle")).doubleValue();
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

      // Set ellipsoid parameters in datum
      if (this.datum != null) {
        this.datum.setEllipsoidParams(this.a, this.b, this.es, this.ep2);
      }

      // Set transformation methods based on projection type
      initializeProjectionMethods();

      // For Web Mercator (EPSG:3857), force spherical approximation
      if ("merc".equals(this.projName)
          && (wktDef.get("title") != null && wktDef.get("title").toString().contains("3857"))) {
        this.sphere = true;
      }

      // Initialize projection-specific parameters
      if (this.init != null) {
        this.init.initialize(this);
      }

    } catch (WKTParseException e) {
      throw new IllegalArgumentException("Failed to parse WKT string: " + e.getMessage(), e);
    }
  }

  /** Initializes projection-specific transformation methods. */
  private void initializeProjectionMethods() {
    switch (this.projName) {
      case "longlat":
      case "identity":
        this.forward = p -> org.apache.sedona.proj.projections.LongLat.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.LongLat.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.LongLat.init(proj);
        break;
      case "merc":
      case "Mercator":
      case "Popular Visualisation Pseudo Mercator":
      case "Mercator_1SP":
      case "Mercator_Auxiliary_Sphere":
      case "Mercator_Variant_A":
        this.forward = p -> org.apache.sedona.proj.projections.Mercator.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Mercator.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Mercator.init(proj);
        break;
      case "lcc":
        this.forward =
            p -> org.apache.sedona.proj.projections.LambertConformalConic.forward(this, p);
        this.inverse =
            p -> org.apache.sedona.proj.projections.LambertConformalConic.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.LambertConformalConic.init(proj);
        break;
      case "aea":
        this.forward = p -> org.apache.sedona.proj.projections.AlbersEqualArea.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.AlbersEqualArea.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.AlbersEqualArea.init(proj);
        break;
      case "tmerc":
        this.forward = p -> org.apache.sedona.proj.projections.TransverseMercator.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.TransverseMercator.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.TransverseMercator.init(proj);
        break;
      case "utm":
        this.forward = p -> org.apache.sedona.proj.projections.UTM.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.UTM.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.UTM.init(proj);
        break;
      case "omerc":
      case "Hotine_Oblique_Mercator":
      case "Hotine_Oblique_Mercator_Azimuth_Center":
        this.forward =
            p -> org.apache.sedona.proj.projections.HotineObliqueMercator.forward(this, p);
        this.inverse =
            p -> org.apache.sedona.proj.projections.HotineObliqueMercator.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.HotineObliqueMercator.init(proj);
        break;
      case "eqdc":
      case "Equidistant_Conic":
        this.forward = p -> org.apache.sedona.proj.projections.EquidistantConic.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.EquidistantConic.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.EquidistantConic.init(proj);
        break;
      case "sinu":
      case "Sinusoidal":
        this.forward = p -> org.apache.sedona.proj.projections.Sinusoidal.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Sinusoidal.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Sinusoidal.init(proj);
        break;
      case "cea":
      case "Cylindrical_Equal_Area":
        this.forward =
            p -> org.apache.sedona.proj.projections.CylindricalEqualArea.forward(this, p);
        this.inverse =
            p -> org.apache.sedona.proj.projections.CylindricalEqualArea.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.CylindricalEqualArea.init(proj);
        break;
      case "aeqd":
      case "Azimuthal_Equidistant":
        this.forward =
            p -> org.apache.sedona.proj.projections.AzimuthalEquidistant.forward(this, p);
        this.inverse =
            p -> org.apache.sedona.proj.projections.AzimuthalEquidistant.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.AzimuthalEquidistant.init(proj);
        break;
      case "eqearth":
      case "Equal_Earth":
      case "Equal Earth":
        this.forward = p -> org.apache.sedona.proj.projections.EqualEarth.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.EqualEarth.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.EqualEarth.init(proj);
        break;
      case "equi":
      case "eqc":
      case "Equirectangular":
      case "Plate_Carree":
        this.forward = p -> org.apache.sedona.proj.projections.Equirectangular.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Equirectangular.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Equirectangular.init(proj);
        break;
      case "gnom":
      case "Gnomonic":
        this.forward = p -> org.apache.sedona.proj.projections.Gnomonic.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Gnomonic.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Gnomonic.init(proj);
        break;
      case "laea":
      case "Lambert_Azimuthal_Equal_Area":
      case "Lambert Azimuthal Equal Area":
        this.forward =
            p -> org.apache.sedona.proj.projections.LambertAzimuthalEqualArea.forward(this, p);
        this.inverse =
            p -> org.apache.sedona.proj.projections.LambertAzimuthalEqualArea.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.LambertAzimuthalEqualArea.init(proj);
        break;
      case "mill":
      case "Miller_Cylindrical":
        this.forward = p -> org.apache.sedona.proj.projections.MillerCylindrical.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.MillerCylindrical.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.MillerCylindrical.init(proj);
        break;
      case "moll":
      case "Mollweide":
        this.forward = p -> org.apache.sedona.proj.projections.Mollweide.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Mollweide.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Mollweide.init(proj);
        break;
      case "ortho":
      case "Orthographic":
        this.forward = p -> org.apache.sedona.proj.projections.Orthographic.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Orthographic.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Orthographic.init(proj);
        break;
      case "robin":
      case "Robinson":
        this.forward = p -> org.apache.sedona.proj.projections.Robinson.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Robinson.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Robinson.init(proj);
        break;
      case "stere":
      case "Stereographic":
      case "Stereographic_South_Pole":
      case "Polar_Stereographic":
      case "Polar_Stereographic_variant_A":
      case "Polar_Stereographic_variant_B":
        this.forward = p -> org.apache.sedona.proj.projections.Stereographic.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.Stereographic.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.Stereographic.init(proj);
        break;
      case "vandg":
      case "VanDerGrinten":
      case "Van_der_Grinten":
      case "Van_der_Grinten_I":
        this.forward = p -> org.apache.sedona.proj.projections.VanDerGrinten.forward(this, p);
        this.inverse = p -> org.apache.sedona.proj.projections.VanDerGrinten.inverse(this, p);
        this.init = proj -> org.apache.sedona.proj.projections.VanDerGrinten.init(proj);
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

  /** Initializes as a longitude/latitude projection. */
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

    // Set ellipsoid parameters in datum
    if (this.datum != null) {
      this.datum.setEllipsoidParams(this.a, this.b, this.es, this.ep2);
    }

    // Set transformation methods (identity for longlat)
    this.forward = p -> new Point(p.x, p.y, p.z, p.m); // Identity transformation
    this.inverse = p -> new Point(p.x, p.y, p.z, p.m); // Identity transformation
    this.init = null; // No initialization needed
  }

  /** Initializes as a Mercator projection. */
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

    // For EPSG:3857 (Web Mercator), force spherical approximation
    // This is the standard for Web Mercator as defined by EPSG
    this.sphere = true;

    // Set datum
    this.datum = Datum.getWGS84();

    // Set ellipsoid parameters in datum
    if (this.datum != null) {
      this.datum.setEllipsoidParams(this.a, this.b, this.es, this.ep2);
    }

    // Set transformation methods
    this.forward = this::mercatorForward;
    this.inverse = this::mercatorInverse;
    this.init = this::mercatorInit;

    // Initialize the projection
    if (this.init != null) {
      this.init.initialize(this);
    }
  }

  /** Calculates derived ellipsoid parameters. */
  private void calculateDerivedParameters() {
    // Validate that the semi-major axis has been properly initialized
    // This parameter is critical for all projection calculations
    if (Double.isNaN(this.a)) {
      throw new IllegalStateException(
          "Ellipsoid semi-major axis (a) must be defined. "
              + "Ensure a valid ellipsoid is specified or provide explicit ellipsoid parameters.");
    }

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

  /** Mercator projection initialization. */
  private void mercatorInit(Projection proj) {
    if (proj.lat_ts != 0) {
      if (proj.sphere) {
        proj.k0 = Math.cos(proj.lat_ts);
      } else {
        proj.k0 = MathUtils.msfnz(proj.e, Math.sin(proj.lat_ts), Math.cos(proj.lat_ts));
      }
    }
  }

  /** Mercator forward transformation. */
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

  /** Mercator inverse transformation. */
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

  /** Adjusts longitude to valid range. */
  private double adjustLon(double lon) {
    return MathUtils.adjustLon(lon);
  }

  /**
   * Registers a projection factory.
   *
   * @param name the projection name
   * @param factory the projection factory
   */
  public static void registerProjection(String name, ProjectionFactory factory) {
    PROJECTIONS.put(name, factory);
  }

  /**
   * Gets a projection factory by name.
   *
   * @param name the projection name
   * @return the projection factory, or null if not found
   */
  public static ProjectionFactory getProjectionFactory(String name) {
    return PROJECTIONS.get(name);
  }

  /**
   * Gets all registered projection names.
   *
   * @return list of projection names
   */
  public static List<String> getProjectionNames() {
    return new ArrayList<>(PROJECTIONS.keySet());
  }
}
