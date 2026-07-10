package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Quadrilateralized Spherical Cube projection (qsc).
 * Mirrors: lib/projections/qsc.js (itself rewritten from PROJ's PJ_qsc.c)
 *
 * <p>Projects the sphere onto the six faces of a circumscribed cube using the
 * COBE quadrilateralized spherical cube mapping. The face is selected from the
 * projection center (lat_0/lon_0). Ellipsoids are handled by the geodetic-to-
 * geocentric latitude shift described in Lambers &amp; Kolb (2012).</p>
 */
public class QuadrilateralizedSphericalCube implements Projection {

    private static final String[] NAMES = {
        "Quadrilateralized Spherical Cube", "Quadrilateralized_Spherical_Cube", "qsc"
    };

    // Cube faces
    private static final int FACE_FRONT = 1;
    private static final int FACE_RIGHT = 2;
    private static final int FACE_BACK = 3;
    private static final int FACE_LEFT = 4;
    private static final int FACE_TOP = 5;
    private static final int FACE_BOTTOM = 6;

    // Areas within a cube face
    private static final int AREA_0 = 1;
    private static final int AREA_1 = 2;
    private static final int AREA_2 = 3;
    private static final int AREA_3 = 4;

    private double a, b, es, lat0, long0, x0, y0;
    private int face;
    private double oneMinusF, oneMinusFSquared;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.b = params.b;
        this.es = params.es;
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;

        // Determine the cube face from the center of projection.
        if (lat0 >= Values.HALF_PI - Values.FORTPI / 2.0) {
            this.face = FACE_TOP;
        } else if (lat0 <= -(Values.HALF_PI - Values.FORTPI / 2.0)) {
            this.face = FACE_BOTTOM;
        } else if (Math.abs(long0) <= Values.FORTPI) {
            this.face = FACE_FRONT;
        } else if (Math.abs(long0) <= Values.HALF_PI + Values.FORTPI) {
            this.face = long0 > 0.0 ? FACE_RIGHT : FACE_LEFT;
        } else {
            this.face = FACE_BACK;
        }

        // Values for the ellipsoid <-> sphere shift described in [LK12].
        if (es != 0) {
            this.oneMinusF = 1 - (a - b) / a;
            this.oneMinusFSquared = oneMinusF * oneMinusF;
        }
    }

    @Override
    public Point forward(Point p) {
        double lat, lon;
        double theta, phi;
        double t, mu;
        int[] area = {0};

        // move lon according to projection's lon
        double px = p.x - long0;

        // Convert the geodetic latitude to a geocentric latitude ([LK12] shift).
        if (es != 0) {
            lat = Math.atan(oneMinusFSquared * Math.tan(p.y));
        } else {
            lat = p.y;
        }

        // Convert the input lat, lon into theta, phi as used by QSC.
        lon = px;
        if (face == FACE_TOP) {
            phi = Values.HALF_PI - lat;
            if (lon >= Values.FORTPI && lon <= Values.HALF_PI + Values.FORTPI) {
                area[0] = AREA_0;
                theta = lon - Values.HALF_PI;
            } else if (lon > Values.HALF_PI + Values.FORTPI || lon <= -(Values.HALF_PI + Values.FORTPI)) {
                area[0] = AREA_1;
                theta = (lon > 0.0 ? lon - Values.SPI : lon + Values.SPI);
            } else if (lon > -(Values.HALF_PI + Values.FORTPI) && lon <= -Values.FORTPI) {
                area[0] = AREA_2;
                theta = lon + Values.HALF_PI;
            } else {
                area[0] = AREA_3;
                theta = lon;
            }
        } else if (face == FACE_BOTTOM) {
            phi = Values.HALF_PI + lat;
            if (lon >= Values.FORTPI && lon <= Values.HALF_PI + Values.FORTPI) {
                area[0] = AREA_0;
                theta = -lon + Values.HALF_PI;
            } else if (lon < Values.FORTPI && lon >= -Values.FORTPI) {
                area[0] = AREA_1;
                theta = -lon;
            } else if (lon < -Values.FORTPI && lon >= -(Values.HALF_PI + Values.FORTPI)) {
                area[0] = AREA_2;
                theta = -lon - Values.HALF_PI;
            } else {
                area[0] = AREA_3;
                theta = (lon > 0.0 ? -lon + Values.SPI : -lon - Values.SPI);
            }
        } else {
            if (face == FACE_RIGHT) {
                lon = shiftLonOrigin(lon, +Values.HALF_PI);
            } else if (face == FACE_BACK) {
                lon = shiftLonOrigin(lon, +Values.SPI);
            } else if (face == FACE_LEFT) {
                lon = shiftLonOrigin(lon, -Values.HALF_PI);
            }
            double sinlat = Math.sin(lat);
            double coslat = Math.cos(lat);
            double sinlon = Math.sin(lon);
            double coslon = Math.cos(lon);
            double q = coslat * coslon;
            double r = coslat * sinlon;
            double s = sinlat;

            if (face == FACE_FRONT) {
                phi = Math.acos(q);
                theta = fwdEquatFaceTheta(phi, s, r, area);
            } else if (face == FACE_RIGHT) {
                phi = Math.acos(r);
                theta = fwdEquatFaceTheta(phi, s, -q, area);
            } else if (face == FACE_BACK) {
                phi = Math.acos(-q);
                theta = fwdEquatFaceTheta(phi, s, -r, area);
            } else if (face == FACE_LEFT) {
                phi = Math.acos(-r);
                theta = fwdEquatFaceTheta(phi, s, q, area);
            } else {
                // Impossible
                phi = theta = 0;
                area[0] = AREA_0;
            }
        }

        // Compute mu and nu for the area of definition ([OL76] Eq. 3-21 / 3-38).
        mu = Math.atan((12 / Values.SPI)
            * (theta + Math.acos(Math.sin(theta) * Math.cos(Values.FORTPI)) - Values.HALF_PI));
        t = Math.sqrt((1 - Math.cos(phi))
            / (Math.cos(mu) * Math.cos(mu))
            / (1 - Math.cos(Math.atan(1 / Math.cos(theta)))));

        // Apply the result to the real area.
        if (area[0] == AREA_1) {
            mu += Values.HALF_PI;
        } else if (area[0] == AREA_2) {
            mu += Values.SPI;
        } else if (area[0] == AREA_3) {
            mu += 1.5 * Values.SPI;
        }

        // Now compute x, y from mu and nu
        double x = t * Math.cos(mu) * a + x0;
        double y = t * Math.sin(mu) * a + y0;
        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double lam, phiOut;
        double mu, nu, cosmu, tannu;
        double tantheta, theta, cosphi, phi;
        double t;
        int[] area = {0};

        // de-offset
        double px = (p.x - x0) / a;
        double py = (p.y - y0) / a;

        // Convert the input x, y to the mu and nu angles as used by QSC.
        nu = Math.atan(Math.sqrt(px * px + py * py));
        mu = Math.atan2(py, px);
        if (px >= 0.0 && px >= Math.abs(py)) {
            area[0] = AREA_0;
        } else if (py >= 0.0 && py >= Math.abs(px)) {
            area[0] = AREA_1;
            mu -= Values.HALF_PI;
        } else if (px < 0.0 && -px >= Math.abs(py)) {
            area[0] = AREA_2;
            mu = (mu < 0.0 ? mu + Values.SPI : mu - Values.SPI);
        } else {
            area[0] = AREA_3;
            mu += Values.HALF_PI;
        }

        // Compute phi and theta for the area of definition.
        t = (Values.SPI / 12) * Math.tan(mu);
        tantheta = Math.sin(t) / (Math.cos(t) - (1 / Math.sqrt(2)));
        theta = Math.atan(tantheta);
        cosmu = Math.cos(mu);
        tannu = Math.tan(nu);
        cosphi = 1 - cosmu * cosmu * tannu * tannu
            * (1 - Math.cos(Math.atan(1 / Math.cos(theta))));
        if (cosphi < -1) {
            cosphi = -1;
        } else if (cosphi > +1) {
            cosphi = +1;
        }

        // Apply the result to the real area on the cube face.
        if (face == FACE_TOP) {
            phi = Math.acos(cosphi);
            phiOut = Values.HALF_PI - phi;
            if (area[0] == AREA_0) {
                lam = theta + Values.HALF_PI;
            } else if (area[0] == AREA_1) {
                lam = (theta < 0.0 ? theta + Values.SPI : theta - Values.SPI);
            } else if (area[0] == AREA_2) {
                lam = theta - Values.HALF_PI;
            } else {
                lam = theta;
            }
        } else if (face == FACE_BOTTOM) {
            phi = Math.acos(cosphi);
            phiOut = phi - Values.HALF_PI;
            if (area[0] == AREA_0) {
                lam = -theta + Values.HALF_PI;
            } else if (area[0] == AREA_1) {
                lam = -theta;
            } else if (area[0] == AREA_2) {
                lam = -theta - Values.HALF_PI;
            } else {
                lam = (theta < 0.0 ? -theta - Values.SPI : -theta + Values.SPI);
            }
        } else {
            // Compute phi and lam via cartesian unit sphere coordinates.
            double q = cosphi;
            double r, s;
            t = q * q;
            if (t >= 1) {
                s = 0;
            } else {
                s = Math.sqrt(1 - t) * Math.sin(theta);
            }
            t += s * s;
            if (t >= 1) {
                r = 0;
            } else {
                r = Math.sqrt(1 - t);
            }
            // Rotate q,r,s into the correct area.
            if (area[0] == AREA_1) {
                t = r;
                r = -s;
                s = t;
            } else if (area[0] == AREA_2) {
                r = -r;
                s = -s;
            } else if (area[0] == AREA_3) {
                t = r;
                r = s;
                s = -t;
            }
            // Rotate q,r,s into the correct cube face.
            if (face == FACE_RIGHT) {
                t = q;
                q = -r;
                r = t;
            } else if (face == FACE_BACK) {
                q = -q;
                r = -r;
            } else if (face == FACE_LEFT) {
                t = q;
                q = r;
                r = -t;
            }
            // Now compute phi and lam from the unit sphere coordinates.
            phiOut = Math.acos(-s) - Values.HALF_PI;
            lam = Math.atan2(r, q);
            if (face == FACE_RIGHT) {
                lam = shiftLonOrigin(lam, -Values.HALF_PI);
            } else if (face == FACE_BACK) {
                lam = shiftLonOrigin(lam, -Values.SPI);
            } else if (face == FACE_LEFT) {
                lam = shiftLonOrigin(lam, +Values.HALF_PI);
            }
        }

        // Apply the shift from the sphere to the ellipsoid ([LK12]).
        if (es != 0) {
            boolean invertSign = phiOut < 0;
            double tanphi = Math.tan(phiOut);
            double xa = b / Math.sqrt(tanphi * tanphi + oneMinusFSquared);
            phiOut = Math.atan(Math.sqrt(a * a - xa * xa) / (oneMinusF * xa));
            if (invertSign) {
                phiOut = -phiOut;
            }
        }

        return new Point(lam + long0, phiOut, p.z);
    }

    /**
     * Helper for the forward projection: compute the theta angle and determine
     * the area number on an equatorial cube face.
     */
    private static double fwdEquatFaceTheta(double phi, double y, double x, int[] area) {
        double theta;
        if (phi < Values.EPSLN) {
            area[0] = AREA_0;
            theta = 0.0;
        } else {
            theta = Math.atan2(y, x);
            if (Math.abs(theta) <= Values.FORTPI) {
                area[0] = AREA_0;
            } else if (theta > Values.FORTPI && theta <= Values.HALF_PI + Values.FORTPI) {
                area[0] = AREA_1;
                theta -= Values.HALF_PI;
            } else if (theta > Values.HALF_PI + Values.FORTPI || theta <= -(Values.HALF_PI + Values.FORTPI)) {
                area[0] = AREA_2;
                theta = (theta >= 0.0 ? theta - Values.SPI : theta + Values.SPI);
            } else {
                area[0] = AREA_3;
                theta += Values.HALF_PI;
            }
        }
        return theta;
    }

    /** Helper: shift the longitude, wrapping at ±SPI. */
    private static double shiftLonOrigin(double lon, double offset) {
        double slon = lon + offset;
        if (slon < -Values.SPI) {
            slon += Values.TWO_PI;
        } else if (slon > +Values.SPI) {
            slon -= Values.TWO_PI;
        }
        return slon;
    }
}
