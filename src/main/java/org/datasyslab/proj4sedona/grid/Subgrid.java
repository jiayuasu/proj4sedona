package org.datasyslab.proj4sedona.grid;

/**
 * Represents a single subgrid within a NAD grid file.
 */
public class Subgrid {
    private final double[] ll;
    private final double[] del;
    private final int[] lim;
    private final int count;
    private final double[][] cvs;

    public Subgrid(double[] ll, double[] del, int[] lim, int count, double[][] cvs) {
        this.ll = ll;
        this.del = del;
        this.lim = lim;
        this.count = count;
        this.cvs = cvs;
    }

    public double[] getLl() { return ll; }
    public double[] getDel() { return del; }
    public int[] getLim() { return lim; }
    public int getCount() { return count; }
    public double[][] getCvs() { return cvs; }

    public boolean contains(double lon, double lat) {
        double epsilon = (Math.abs(del[1]) + Math.abs(del[0])) / 10000.0;
        double minX = ll[0] - epsilon;
        double minY = ll[1] - epsilon;
        double maxX = ll[0] + (lim[0] - 1) * del[0] + epsilon;
        double maxY = ll[1] + (lim[1] - 1) * del[1] + epsilon;
        return lon >= minX && lon <= maxX && lat >= minY && lat <= maxY;
    }
}
