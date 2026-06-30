package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.core.Point;

/**
 * Swiss Oblique Mercator (somerc) projection.
 * Mirrors: lib/projections/somerc.js
 *
 * <p>The conformal cylindrical projection with an oblique axis used by the Swiss
 * national grids — e.g. EPSG:21781 (CH1903 / LV03) and EPSG:2056 (CH1903+ / LV95).
 * This is the {@code +proj=somerc} algorithm (the canonical PROJ definition for
 * those CRSs), based on the swisstopo formulas.</p>
 */
public class SwissObliqueMercator implements Projection {

    // proj4js names somerc only "somerc". The extra alias lets a CRS round-trip
    // through proj4sedona's WKT/PROJJSON serializer, which emits the EPSG method
    // name "Swiss Oblique Mercator" (registry lookup normalizes spaces/underscores).
    private static final String[] NAMES = {"somerc", "Swiss_Oblique_Mercator"};

    private static final int MAX_ITER = 20;

    private double e, R, alpha, b0, K, lambda0, x0, y0;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        double phy0 = params.getLat0();
        this.lambda0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        double sinPhy0 = Math.sin(phy0);
        double semiMajorAxis = params.a;
        double invF = params.rf;
        double flattening = 1 / invF;
        double e2 = 2 * flattening - Math.pow(flattening, 2);
        this.e = Math.sqrt(e2);
        this.R = params.k0 * semiMajorAxis * Math.sqrt(1 - e2) / (1 - e2 * Math.pow(sinPhy0, 2));
        this.alpha = Math.sqrt(1 + e2 / (1 - e2) * Math.pow(Math.cos(phy0), 4));
        this.b0 = Math.asin(sinPhy0 / this.alpha);
        double k1 = Math.log(Math.tan(Math.PI / 4 + this.b0 / 2));
        double k2 = Math.log(Math.tan(Math.PI / 4 + phy0 / 2));
        double k3 = Math.log((1 + e * sinPhy0) / (1 - e * sinPhy0));
        this.K = k1 - this.alpha * k2 + this.alpha * e / 2 * k3;
    }

    @Override
    public Point forward(Point p) {
        double Sa1 = Math.log(Math.tan(Math.PI / 4 - p.y / 2));
        double Sa2 = this.e / 2 * Math.log((1 + this.e * Math.sin(p.y)) / (1 - this.e * Math.sin(p.y)));
        double S = -this.alpha * (Sa1 + Sa2) + this.K;

        // spheric latitude
        double b = 2 * (Math.atan(Math.exp(S)) - Math.PI / 4);

        // spheric longitude
        double I = this.alpha * (p.x - this.lambda0);

        // pseudo equatorial rotation
        double rotI = Math.atan(Math.sin(I) / (Math.sin(this.b0) * Math.tan(b) + Math.cos(this.b0) * Math.cos(I)));
        double rotB = Math.asin(Math.cos(this.b0) * Math.sin(b) - Math.sin(this.b0) * Math.cos(b) * Math.cos(I));

        double y = this.R / 2 * Math.log((1 + Math.sin(rotB)) / (1 - Math.sin(rotB))) + this.y0;
        double x = this.R * rotI + this.x0;
        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double Y = p.x - this.x0;
        double X = p.y - this.y0;

        double rotI = Y / this.R;
        double rotB = 2 * (Math.atan(Math.exp(X / this.R)) - Math.PI / 4);

        double b = Math.asin(Math.cos(this.b0) * Math.sin(rotB) + Math.sin(this.b0) * Math.cos(rotB) * Math.cos(rotI));
        double I = Math.atan(Math.sin(rotI) / (Math.cos(this.b0) * Math.cos(rotI) - Math.sin(this.b0) * Math.tan(rotB)));

        double lambda = this.lambda0 + I / this.alpha;

        double S;
        double phy = b;
        double prevPhy = -1000;
        int iteration = 0;
        while (Math.abs(phy - prevPhy) > 0.0000001) {
            if (++iteration > MAX_ITER) {
                return null;
            }
            S = 1 / this.alpha * (Math.log(Math.tan(Math.PI / 4 + b / 2)) - this.K)
                    + this.e * Math.log(Math.tan(Math.PI / 4 + Math.asin(this.e * Math.sin(phy)) / 2));
            prevPhy = phy;
            phy = 2 * Math.atan(Math.exp(S)) - Math.PI / 2;
        }

        return new Point(lambda, phy, p.z);
    }
}
