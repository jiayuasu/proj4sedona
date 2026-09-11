package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.core.Point;

/**
 * Geostationary Satellite View projection (geos).
 * Mirrors: lib/projections/geos.js
 *
 * <p>The view of the Earth from a geostationary satellite. Requires the satellite
 * height above the ellipsoid ({@code +h}); the scan {@code +sweep} axis ('x' or 'y',
 * default 'y') selects the instrument sweep convention. Supports spherical and
 * ellipsoidal forms; points not visible from the satellite are unprojectable (null).</p>
 *
 * <p>proj4js's geos only scales by {@code a} and never applies {@code +x_0/+y_0}. This
 * port applies them (in {@code forward}/{@code inverse}) to match PROJ and this
 * codebase's convention that projections apply offsets themselves ({@code Transform}
 * does not) — an intentional divergence from proj4js. Standard geos CRSs use
 * {@code x_0=y_0=0}, so their output is unchanged.</p>
 */
public class Geostationary implements Projection {

    // The sweep-variant names are PROJ's WKT2/PROJJSON method names (PROJ encodes the
    // sweep axis in the method name); registered so serialized CRSs re-import.
    private static final String[] NAMES = {
        "Geostationary Satellite View", "Geostationary_Satellite",
        "Geostationary Satellite (Sweep X)", "Geostationary Satellite (Sweep Y)", "geos"
    };

    private double a, es, long0, x0, y0;
    private boolean flipAxis;
    private double radiusG1, radiusG, radiusP, radiusP2, radiusPInv2, C;
    private boolean ellipse;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.es = params.es;
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;

        // Sweep comes from +sweep, or — for CRSs parsed from WKT2/PROJJSON, where PROJ
        // encodes it in the method name — from the projection name.
        this.flipAxis = "x".equals(params.sweep)
            || (params.sweep == null && params.projName != null
                && params.projName.toLowerCase().contains("sweep x"));
        // Persist the resolved sweep so re-serialization keeps it (otherwise a CRS
        // parsed from a Sweep X method name would re-export as the default Sweep Y).
        params.sweep = flipAxis ? "x" : "y";
        if (params.h == null) {
            throw new IllegalArgumentException("Geostationary projection requires satellite height (+h)");
        }
        this.radiusG1 = params.h / a;
        if (radiusG1 <= 0 || radiusG1 > 1e10) {
            throw new IllegalArgumentException("Invalid satellite height (+h) for Geostationary projection");
        }
        this.radiusG = 1.0 + radiusG1;
        this.C = radiusG * radiusG - 1.0;

        if (es != 0.0) {
            double oneEs = 1.0 - es;
            this.radiusP = Math.sqrt(oneEs);
            this.radiusP2 = oneEs;
            this.radiusPInv2 = 1 / oneEs;
            this.ellipse = true;
        } else {
            this.radiusP = 1.0;
            this.radiusP2 = 1.0;
            this.radiusPInv2 = 1.0;
            this.ellipse = false;
        }
    }

    @Override
    public Point forward(Point p) {
        double lon = p.x - long0;
        double lat = p.y;
        double vx, vy, vz, tmp;

        if (ellipse) {
            lat = Math.atan(radiusP2 * Math.tan(lat));
            double r = radiusP / Math.hypot(radiusP * Math.cos(lat), Math.sin(lat));
            vx = r * Math.cos(lon) * Math.cos(lat);
            vy = r * Math.sin(lon) * Math.cos(lat);
            vz = r * Math.sin(lat);
        } else {
            tmp = Math.cos(lat);
            vx = Math.cos(lon) * tmp;
            vy = Math.sin(lon) * tmp;
            vz = Math.sin(lat);
        }

        // Visibility check for both branches (radiusPInv2 == 1 for the sphere). proj4js
        // only guards the ellipsoidal path; guard the sphere too, so far-side points are
        // unprojectable (null) rather than projecting to finite garbage, consistent with
        // the inverse (which returns null when the point is off-disk).
        if (((radiusG - vx) * vx - vy * vy - vz * vz * radiusPInv2) < 0.0) {
            return null;
        }

        tmp = radiusG - vx;
        double x, y;
        if (flipAxis) {
            x = radiusG1 * Math.atan(vy / Math.hypot(vz, tmp));
            y = radiusG1 * Math.atan(vz / tmp);
        } else {
            x = radiusG1 * Math.atan(vy / tmp);
            y = radiusG1 * Math.atan(vz / Math.hypot(vy, tmp));
        }
        return new Point(x * a + x0, y * a + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double px = (p.x - x0) / a;
        double py = (p.y - y0) / a;
        double vx = -1.0, vy = 0.0, vz = 0.0;
        double a2, b, det, k;

        if (ellipse) {
            if (flipAxis) {
                vz = Math.tan(py / radiusG1);
                vy = Math.tan(px / radiusG1) * Math.hypot(1.0, vz);
            } else {
                vy = Math.tan(px / radiusG1);
                vz = Math.tan(py / radiusG1) * Math.hypot(1.0, vy);
            }
            double vzp = vz / radiusP;
            a2 = vy * vy + vzp * vzp + vx * vx;
            b = 2 * radiusG * vx;
            det = (b * b) - 4 * a2 * C;
            if (det < 0.0) {
                return null;
            }
            k = (-b - Math.sqrt(det)) / (2.0 * a2);
            vx = radiusG + k * vx;
            vy *= k;
            vz *= k;
            double lon = Math.atan2(vy, vx);
            double lat = Math.atan(vz * Math.cos(lon) / vx);
            lat = Math.atan(radiusPInv2 * Math.tan(lat));
            return new Point(lon + long0, lat, p.z);
        } else {
            if (flipAxis) {
                vz = Math.tan(py / radiusG1);
                vy = Math.tan(px / radiusG1) * Math.sqrt(1.0 + vz * vz);
            } else {
                vy = Math.tan(px / radiusG1);
                vz = Math.tan(py / radiusG1) * Math.sqrt(1.0 + vy * vy);
            }
            a2 = vy * vy + vz * vz + vx * vx;
            b = 2 * radiusG * vx;
            det = (b * b) - 4 * a2 * C;
            if (det < 0.0) {
                return null;
            }
            k = (-b - Math.sqrt(det)) / (2.0 * a2);
            vx = radiusG + k * vx;
            vy *= k;
            vz *= k;
            double lon = Math.atan2(vy, vx);
            double lat = Math.atan(vz * Math.cos(lon) / vx);
            return new Point(lon + long0, lat, p.z);
        }
    }
}
