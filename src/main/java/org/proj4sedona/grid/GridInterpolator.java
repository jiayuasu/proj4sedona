package org.proj4sedona.grid;

import org.proj4sedona.common.ProjMath;

/**
 * Bilinear interpolation for grid shift values.
 */
public final class GridInterpolator {

    private GridInterpolator() {}

    public static double[] interpolate(double lon, double lat, Subgrid grid) {
        double[] del = grid.getDel();
        int[] lim = grid.getLim();
        double[][] cvs = grid.getCvs();

        double tx = lon / del[0];
        double ty = lat / del[1];
        int ix = (int) Math.floor(tx);
        int iy = (int) Math.floor(ty);

        if (ix < 0 || ix >= lim[0] - 1) return new double[]{Double.NaN, Double.NaN};
        if (iy < 0 || iy >= lim[1] - 1) return new double[]{Double.NaN, Double.NaN};

        double fx = tx - ix;
        double fy = ty - iy;

        int idx = iy * lim[0] + ix;
        double[] f00 = cvs[idx];
        double[] f10 = cvs[idx + 1];
        double[] f01 = cvs[idx + lim[0]];
        double[] f11 = cvs[idx + lim[0] + 1];

        double m00 = (1.0 - fx) * (1.0 - fy);
        double m10 = fx * (1.0 - fy);
        double m01 = (1.0 - fx) * fy;
        double m11 = fx * fy;

        double shiftLon = m00 * f00[0] + m10 * f10[0] + m01 * f01[0] + m11 * f11[0];
        double shiftLat = m00 * f00[1] + m10 * f10[1] + m01 * f01[1] + m11 * f11[1];

        return new double[]{shiftLon, shiftLat};
    }

    public static double[] applyForward(double lon, double lat, Subgrid grid) {
        double[] ll = grid.getLl();
        double relLon = lon - ll[0];
        double relLat = lat - ll[1];
        relLon = ProjMath.adjustLon(relLon - Math.PI) + Math.PI;

        double[] shift = interpolate(relLon, relLat, grid);
        if (Double.isNaN(shift[0])) return new double[]{Double.NaN, Double.NaN};
        return new double[]{lon + shift[0], lat + shift[1]};
    }

    public static double[] applyInverse(double lon, double lat, Subgrid grid) {
        double[] ll = grid.getLl();
        double relLon = lon - ll[0];
        double relLat = lat - ll[1];
        relLon = ProjMath.adjustLon(relLon - Math.PI) + Math.PI;

        double[] shift = interpolate(relLon, relLat, grid);
        if (Double.isNaN(shift[0])) return new double[]{Double.NaN, Double.NaN};

        double tLon = relLon - shift[0];
        double tLat = relLat - shift[1];

        int maxIterations = 9;
        double tolerance = 1e-12;

        for (int i = 0; i < maxIterations; i++) {
            double[] del = interpolate(tLon, tLat, grid);
            if (Double.isNaN(del[0])) break;

            double difLon = relLon - (del[0] + tLon);
            double difLat = relLat - (del[1] + tLat);
            tLon += difLon;
            tLat += difLat;

            if (Math.abs(difLon) <= tolerance && Math.abs(difLat) <= tolerance) break;
        }

        return new double[]{ProjMath.adjustLon(tLon + ll[0]), tLat + ll[1]};
    }
}
