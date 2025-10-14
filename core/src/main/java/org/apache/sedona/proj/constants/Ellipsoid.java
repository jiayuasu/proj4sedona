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
package org.apache.sedona.proj.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Ellipsoid definitions for various reference ellipsoids. This class contains the same ellipsoid
 * definitions as the JavaScript version.
 */
public final class Ellipsoid {

  /** Represents an ellipsoid with its parameters. */
  public static class EllipsoidDef {
    public final double a; // Semi-major axis
    public final double b; // Semi-minor axis (optional)
    public final double rf; // Reciprocal of flattening (optional)
    public final String ellipseName; // Human-readable name

    public EllipsoidDef(double a, double b, double rf, String ellipseName) {
      this.a = a;
      this.b = b;
      this.rf = rf;
      this.ellipseName = ellipseName;
    }

    public EllipsoidDef(double a, double rf, String ellipseName) {
      this(a, Double.NaN, rf, ellipseName);
    }
  }

  private static final Map<String, EllipsoidDef> ELLIPSOIDS = new HashMap<>();

  static {
    // Initialize all ellipsoid definitions
    ELLIPSOIDS.put("MERIT", new EllipsoidDef(6378137, 298.257, "MERIT 1983"));
    ELLIPSOIDS.put("SGS85", new EllipsoidDef(6378136, 298.257, "Soviet Geodetic System 85"));
    ELLIPSOIDS.put("GRS80", new EllipsoidDef(6378137, 298.257222101, "GRS 1980(IUGG, 1980)"));
    ELLIPSOIDS.put("IAU76", new EllipsoidDef(6378140, 298.257, "IAU 1976"));
    ELLIPSOIDS.put("airy", new EllipsoidDef(6377563.396, 6356256.91, "Airy 1830"));
    ELLIPSOIDS.put("APL4", new EllipsoidDef(6378137, 298.25, "Appl. Physics. 1965"));
    ELLIPSOIDS.put("NWL9D", new EllipsoidDef(6378145, 298.25, "Naval Weapons Lab., 1965"));
    ELLIPSOIDS.put("mod_airy", new EllipsoidDef(6377340.189, 6356034.446, "Modified Airy"));
    ELLIPSOIDS.put("andrae", new EllipsoidDef(6377104.43, 300, "Andrae 1876 (Den., Iclnd.)"));
    ELLIPSOIDS.put("aust_SA", new EllipsoidDef(6378160, 298.25, "Australian Natl & S. Amer. 1969"));
    ELLIPSOIDS.put("GRS67", new EllipsoidDef(6378160, 298.247167427, "GRS 67(IUGG 1967)"));
    ELLIPSOIDS.put("bessel", new EllipsoidDef(6377397.155, 299.1528128, "Bessel 1841"));
    ELLIPSOIDS.put("bess_nam", new EllipsoidDef(6377483.865, 299.1528128, "Bessel 1841 (Namibia)"));
    ELLIPSOIDS.put("clrk66", new EllipsoidDef(6378206.4, 6356583.8, "Clarke 1866"));
    ELLIPSOIDS.put("clrk80", new EllipsoidDef(6378249.145, 293.4663, "Clarke 1880 mod."));
    ELLIPSOIDS.put(
        "clrk80ign", new EllipsoidDef(6378249.2, 6356515, 293.4660213, "Clarke 1880 (IGN)"));
    ELLIPSOIDS.put("clrk58", new EllipsoidDef(6378293.645208759, 294.2606763692654, "Clarke 1858"));
    ELLIPSOIDS.put("CPM", new EllipsoidDef(6375738.7, 334.29, "Comm. des Poids et Mesures 1799"));
    ELLIPSOIDS.put("delmbr", new EllipsoidDef(6376428, 311.5, "Delambre 1810 (Belgium)"));
    ELLIPSOIDS.put("engelis", new EllipsoidDef(6378136.05, 298.2566, "Engelis 1985"));
    ELLIPSOIDS.put("evrst30", new EllipsoidDef(6377276.345, 300.8017, "Everest 1830"));
    ELLIPSOIDS.put("evrst48", new EllipsoidDef(6377304.063, 300.8017, "Everest 1948"));
    ELLIPSOIDS.put("evrst56", new EllipsoidDef(6377301.243, 300.8017, "Everest 1956"));
    ELLIPSOIDS.put("evrst69", new EllipsoidDef(6377295.664, 300.8017, "Everest 1969"));
    ELLIPSOIDS.put("evrstSS", new EllipsoidDef(6377298.556, 300.8017, "Everest (Sabah & Sarawak)"));
    ELLIPSOIDS.put("fschr60", new EllipsoidDef(6378166, 298.3, "Fischer (Mercury Datum) 1960"));
    ELLIPSOIDS.put("fschr60m", new EllipsoidDef(6378155, 298.3, "Fischer 1960"));
    ELLIPSOIDS.put("fschr68", new EllipsoidDef(6378150, 298.3, "Fischer 1968"));
    ELLIPSOIDS.put("helmert", new EllipsoidDef(6378200, 298.3, "Helmert 1906"));
    ELLIPSOIDS.put("hough", new EllipsoidDef(6378270, 297, "Hough"));
    ELLIPSOIDS.put("intl", new EllipsoidDef(6378388, 297, "International 1909 (Hayford)"));
    ELLIPSOIDS.put("kaula", new EllipsoidDef(6378163, 298.24, "Kaula 1961"));
    ELLIPSOIDS.put("lerch", new EllipsoidDef(6378139, 298.257, "Lerch 1979"));
    ELLIPSOIDS.put("mprts", new EllipsoidDef(6397300, 191, "Maupertius 1738"));
    ELLIPSOIDS.put("new_intl", new EllipsoidDef(6378157.5, 6356772.2, "New International 1967"));
    ELLIPSOIDS.put("plessis", new EllipsoidDef(6376523, 6355863, "Plessis 1817 (France)"));
    ELLIPSOIDS.put("krass", new EllipsoidDef(6378245, 298.3, "Krassovsky, 1942"));
    ELLIPSOIDS.put("SEasia", new EllipsoidDef(6378155, 6356773.3205, "Southeast Asia"));
    ELLIPSOIDS.put("walbeck", new EllipsoidDef(6376896, 6355834.8467, "Walbeck"));
    ELLIPSOIDS.put("WGS60", new EllipsoidDef(6378165, 298.3, "WGS 60"));
    ELLIPSOIDS.put("WGS66", new EllipsoidDef(6378145, 298.25, "WGS 66"));
    ELLIPSOIDS.put("WGS7", new EllipsoidDef(6378135, 298.26, "WGS 72"));
    ELLIPSOIDS.put("WGS84", new EllipsoidDef(6378137, 298.257223563, "WGS 84"));
    ELLIPSOIDS.put("sphere", new EllipsoidDef(6370997, 6370997, "Normal Sphere (r=6370997)"));
  }

  private Ellipsoid() {
    // Utility class - prevent instantiation
  }

  /**
   * Gets an ellipsoid definition by name.
   *
   * @param name the ellipsoid name
   * @return the ellipsoid definition, or null if not found
   */
  public static EllipsoidDef get(String name) {
    return ELLIPSOIDS.get(name);
  }

  /**
   * Gets all available ellipsoid names.
   *
   * @return set of ellipsoid names
   */
  public static java.util.Set<String> getNames() {
    return ELLIPSOIDS.keySet();
  }

  /**
   * Checks if an ellipsoid with the given name exists.
   *
   * @param name the ellipsoid name
   * @return true if the ellipsoid exists
   */
  public static boolean exists(String name) {
    return ELLIPSOIDS.containsKey(name);
  }

  /**
   * Gets the WGS84 ellipsoid definition.
   *
   * @return WGS84 ellipsoid definition
   */
  public static EllipsoidDef getWGS84() {
    return ELLIPSOIDS.get("WGS84");
  }

  /**
   * Gets the sphere ellipsoid definition.
   *
   * @return sphere ellipsoid definition
   */
  public static EllipsoidDef getSphere() {
    return ELLIPSOIDS.get("sphere");
  }
}
