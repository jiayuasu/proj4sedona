package org.datasyslab.proj4sedona.projection;

import java.util.Arrays;
import java.util.List;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;
import org.datasyslab.proj4sedona.core.Proj;

/**
 * General Oblique Transformation (ob_tran).
 * Mirrors: lib/projections/ob_tran.js (PROJ's ob_tran.cpp)
 *
 * <p>A meta-projection: rotates the sphere so an arbitrary point becomes the pole
 * (or an arbitrary great circle the equator), then applies an inner projection
 * given by {@code +o_proj}. Used for rotated-pole grids in climate and ocean
 * modeling (e.g. {@code +proj=ob_tran +o_proj=longlat +o_lat_p=... +o_lon_p=...}).</p>
 *
 * <p>Rotation parameter sets, checked in PROJ's order:
 * rotate-about-point ({@code o_alpha, o_lon_c, o_lat_c}), new pole
 * ({@code o_lat_p, o_lon_p}), new equator points
 * ({@code o_lon_1, o_lat_1, o_lon_2, o_lat_2}).</p>
 *
 * <p>When the inner projection is {@code longlat}, output coordinates are the
 * rotated longitude/latitude in degrees (as in proj4js).</p>
 */
public class GeneralObliqueTransformation implements Projection {

    private static final String[] NAMES = {
        "General Oblique Transformation", "General_Oblique_Transformation", "ob_tran"
    };

    private static final List<String> LONGLAT_NAMES = Arrays.asList(LongLat.NAMES);

    private static final int OBLIQUE = 0;
    private static final int TRANSVERSE = 1;

    private double long0;
    private double lamp, cphip, sphip;
    private int type;
    private boolean isIdentity;
    private Proj obliqueProjection;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.long0 = params.getLong0();
        this.over = params.over;

        // Verify required parameters exist.
        String oProj = params.oProj;
        if (oProj == null || oProj.isEmpty()) {
            throw new IllegalArgumentException("Missing parameter: o_proj");
        }
        if ("ob_tran".equals(oProj)) {
            throw new IllegalArgumentException("Invalid value for o_proj: " + oProj);
        }
        this.isIdentity = LONGLAT_NAMES.contains(oProj);

        // Build the inner projection from the original proj string, as upstream does:
        // drop +proj=ob_tran and promote +o_proj to +proj. The inner projection must
        // see lon_0 = 0 (ob_tran applies long0 itself before/after the rotation);
        // upstream zeroes it post-init, we strip the token before construction.
        String projStr = params.srsCode;
        if (projStr == null || !projStr.trim().startsWith("+")) {
            throw new IllegalArgumentException(
                "ob_tran requires a PROJ string definition (to construct the inner +o_proj projection)");
        }
        String innerStr = projStr
            .replace("+proj=ob_tran", "")
            .replace("+o_proj=", "+proj=")
            .replaceAll("\\+lon_0=\\S+", "")
            .trim();
        this.obliqueProjection = new Proj(innerStr);

        // Select the rotation parameter set, in PROJ's order.
        double phip;
        if (params.oAlpha != null || params.oLonC != null || params.oLatC != null) {
            requireAll(params.oAlpha, "o_alpha", params.oLonC, "o_lon_c", params.oLatC, "o_lat_c");
            double lamc = params.oLonC;
            double phic = params.oLatC;
            double alpha = params.oAlpha;
            if (Math.abs(Math.abs(phic) - Values.HALF_PI) <= Values.EPSLN) {
                throw new IllegalArgumentException("Invalid value for o_lat_c: should be < 90°");
            }
            this.lamp = lamc + Math.atan2(-Math.cos(alpha), -Math.sin(alpha) * Math.sin(phic));
            phip = Math.asin(Math.cos(phic) * Math.sin(alpha));
        } else if (params.oLatP != null || params.oLonP != null) {
            requireAll(params.oLatP, "o_lat_p", params.oLonP, "o_lon_p");
            this.lamp = params.oLonP;
            phip = params.oLatP;
        } else if (params.oLon1 != null || params.oLat1 != null
                || params.oLon2 != null || params.oLat2 != null) {
            requireAll(params.oLon1, "o_lon_1", params.oLat1, "o_lat_1");
            requireAll(params.oLon2, "o_lon_2", params.oLat2, "o_lat_2");
            double lam1 = params.oLon1;
            double phi1 = params.oLat1;
            double lam2 = params.oLon2;
            double phi2 = params.oLat2;
            double con = Math.abs(phi1);

            if (Math.abs(phi1) > Values.HALF_PI - Values.EPSLN) {
                throw new IllegalArgumentException("Invalid value for o_lat_1: should be < 90°");
            }
            if (Math.abs(phi2) > Values.HALF_PI - Values.EPSLN) {
                throw new IllegalArgumentException("Invalid value for o_lat_2: should be < 90°");
            }
            if (Math.abs(phi1 - phi2) < Values.EPSLN) {
                throw new IllegalArgumentException(
                    "Invalid value for o_lat_1 and o_lat_2: o_lat_1 should be different from o_lat_2");
            }
            if (con < Values.EPSLN) {
                throw new IllegalArgumentException(
                    "Invalid value for o_lat_1: o_lat_1 should be different from zero");
            }

            this.lamp = Math.atan2(
                Math.cos(phi1) * Math.sin(phi2) * Math.cos(lam1)
                    - Math.sin(phi1) * Math.cos(phi2) * Math.cos(lam2),
                Math.sin(phi1) * Math.cos(phi2) * Math.sin(lam2)
                    - Math.cos(phi1) * Math.sin(phi2) * Math.sin(lam1));
            phip = Math.atan(-Math.cos(lamp - lam1) / Math.tan(phi1));
        } else {
            throw new IllegalArgumentException("No valid parameters provided for ob_tran projection.");
        }

        if (Math.abs(phip) > Values.EPSLN) {
            this.cphip = Math.cos(phip);
            this.sphip = Math.sin(phip);
            this.type = OBLIQUE;
        } else {
            this.type = TRANSVERSE;
        }
    }

    private static void requireAll(Object... valueNamePairs) {
        for (int i = 0; i < valueNamePairs.length; i += 2) {
            if (valueNamePairs[i] == null) {
                throw new IllegalArgumentException("Missing parameter: " + valueNamePairs[i + 1] + ".");
            }
        }
    }

    @Override
    public Point forward(Point p) {
        double lam = ProjMath.adjustLon(p.x - long0, over);
        double phi = p.y;
        double rx, ry;

        if (type == OBLIQUE) {
            double coslam = Math.cos(lam);
            double sinphi = Math.sin(phi);
            double cosphi = Math.cos(phi);
            rx = ProjMath.adjustLon(
                Math.atan2(cosphi * Math.sin(lam), sphip * cosphi * coslam + cphip * sinphi) + lamp);
            ry = Math.asin(sphip * sinphi - cphip * cosphi * coslam);
        } else {
            double cosphi = Math.cos(phi);
            double coslam = Math.cos(lam);
            rx = ProjMath.adjustLon(Math.atan2(cosphi * Math.sin(lam), Math.sin(phi)) + lamp);
            ry = Math.asin(-cosphi * coslam);
        }

        Point result = obliqueProjection.forward(new Point(rx, ry, p.z));
        if (result == null) {
            return null;
        }
        if (isIdentity) {
            // Rotated longitude/latitude output in degrees (as in proj4js).
            return new Point(result.x * Values.R2D, result.y * Values.R2D, p.z);
        }
        return result;
    }

    @Override
    public Point inverse(Point p) {
        double px = p.x;
        double py = p.y;
        if (isIdentity) {
            px *= Values.D2R;
            py *= Values.D2R;
        }

        Point inner = obliqueProjection.inverse(new Point(px, py, p.z));
        if (inner == null) {
            return null;
        }
        double lam = inner.x;
        double phi = inner.y;
        double rx = lam;
        double ry = phi;

        if (lam < Double.MAX_VALUE) {
            lam -= lamp;
            if (type == OBLIQUE) {
                double coslam = Math.cos(lam);
                double sinphi = Math.sin(phi);
                double cosphi = Math.cos(phi);
                rx = Math.atan2(cosphi * Math.sin(lam), sphip * cosphi * coslam - cphip * sinphi);
                ry = Math.asin(sphip * sinphi + cphip * cosphi * coslam);
            } else {
                double cosphi = Math.cos(phi);
                rx = Math.atan2(cosphi * Math.sin(lam), -Math.sin(phi));
                ry = Math.asin(cosphi * Math.cos(lam));
            }
        }

        return new Point(ProjMath.adjustLon(rx + long0), ry, p.z);
    }
}
