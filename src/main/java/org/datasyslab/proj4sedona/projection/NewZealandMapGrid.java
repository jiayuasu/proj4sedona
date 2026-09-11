package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * New Zealand Map Grid projection (nzmg).
 * Mirrors: lib/projections/nzmg.js
 *
 * <p>The conformal projection defined by complex-polynomial series fitted to New
 * Zealand (Department of Land and Survey Technical Circular 1973/32; EPSG:27200).
 * The series coefficients are fixed constants of the projection definition.</p>
 *
 * <p>The inverse refines a series first-approximation with Newton iterations:
 * 0 iterations gives km accuracy, 1 gives meter accuracy, 2 gives mm accuracy.
 * proj4js uses 1 (incl. upstream 8c632d0, which fixed the count not being applied);
 * this port matches.</p>
 */
public class NewZealandMapGrid implements Projection {

    private static final String[] NAMES = {"New_Zealand_Map_Grid", "nzmg"};

    /** Inverse refinement iterations (1 = meter accuracy, matching proj4js). */
    private static final int ITERATIONS = 1;

    // Series coefficients (1-based indexing preserved from the definition).
    private static final double[] A = {
        0, 0.6399175073, -0.1358797613, 0.063294409, -0.02526853, 0.0117879,
        -0.0055161, 0.0026906, -0.001333, 0.00067, -0.00034
    };
    private static final double[] B_RE = {
        0, 0.7557853228, 0.249204646, -0.001541739, -0.10162907, -0.26623489, -0.6870983
    };
    private static final double[] B_IM = {
        0, 0, 0.003371507, 0.041058560, 0.01727609, -0.36249218, -1.1651967
    };
    private static final double[] C_RE = {
        0, 1.3231270439, -0.577245789, 0.508307513, -0.15094762, 1.01418179, 1.9660549
    };
    private static final double[] C_IM = {
        0, 0, -0.007809598, -0.112208952, 0.18200602, 1.64497696, 2.5127645
    };
    private static final double[] D = {
        0, 1.5627014243, 0.5185406398, -0.03333098, -0.1052906, -0.0368594,
        0.007317, 0.01220, 0.00394, -0.0013
    };

    private double a, lat0, long0, x0, y0;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.lat0 = params.getLat0();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
    }

    @Override
    public Point forward(Point p) {
        double deltaLat = p.y - lat0;
        double deltaLon = p.x - long0;

        // 1. d_phi in seconds of arc * 1e-5; d_lambda in radians.
        double dPhi = deltaLat / Values.SEC_TO_RAD * 1E-5;
        double dLambda = deltaLon;
        double dPhiN = 1; // d_phi^0

        double dPsi = 0;
        for (int n = 1; n <= 10; n++) {
            dPhiN = dPhiN * dPhi;
            dPsi = dPsi + A[n] * dPhiN;
        }

        // 2. theta
        double thRe = dPsi;
        double thIm = dLambda;

        // 3. z = sum B[n] * theta^n (complex)
        double thNRe = 1, thNIm = 0;
        double zRe = 0, zIm = 0;
        for (int n = 1; n <= 6; n++) {
            double thNRe1 = thNRe * thRe - thNIm * thIm;
            double thNIm1 = thNIm * thRe + thNRe * thIm;
            thNRe = thNRe1;
            thNIm = thNIm1;
            zRe = zRe + B_RE[n] * thNRe - B_IM[n] * thNIm;
            zIm = zIm + B_IM[n] * thNRe + B_RE[n] * thNIm;
        }

        // 4. easting/northing
        return new Point(zIm * a + x0, zRe * a + y0, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double deltaX = p.x - x0;
        double deltaY = p.y - y0;

        // 1. z
        double zRe = deltaY / a;
        double zIm = deltaX / a;

        // 2a. theta first approximation (km accuracy)
        double zNRe = 1, zNIm = 0;
        double thRe = 0, thIm = 0;
        for (int n = 1; n <= 6; n++) {
            double zNRe1 = zNRe * zRe - zNIm * zIm;
            double zNIm1 = zNIm * zRe + zNRe * zIm;
            zNRe = zNRe1;
            zNIm = zNIm1;
            thRe = thRe + C_RE[n] * zNRe - C_IM[n] * zNIm;
            thIm = thIm + C_IM[n] * zNRe + C_RE[n] * zNIm;
        }

        // 2b. Newton refinement
        for (int i = 0; i < ITERATIONS; i++) {
            double thNRe = thRe, thNIm = thIm;
            double numRe = zRe, numIm = zIm;
            for (int n = 2; n <= 6; n++) {
                double thNRe1 = thNRe * thRe - thNIm * thIm;
                double thNIm1 = thNIm * thRe + thNRe * thIm;
                thNRe = thNRe1;
                thNIm = thNIm1;
                numRe = numRe + (n - 1) * (B_RE[n] * thNRe - B_IM[n] * thNIm);
                numIm = numIm + (n - 1) * (B_IM[n] * thNRe + B_RE[n] * thNIm);
            }

            thNRe = 1;
            thNIm = 0;
            double denRe = B_RE[1], denIm = B_IM[1];
            for (int n = 2; n <= 6; n++) {
                double thNRe1 = thNRe * thRe - thNIm * thIm;
                double thNIm1 = thNIm * thRe + thNRe * thIm;
                thNRe = thNRe1;
                thNIm = thNIm1;
                denRe = denRe + n * (B_RE[n] * thNRe - B_IM[n] * thNIm);
                denIm = denIm + n * (B_IM[n] * thNRe + B_RE[n] * thNIm);
            }

            // Complex division num/den
            double den2 = denRe * denRe + denIm * denIm;
            thRe = (numRe * denRe + numIm * denIm) / den2;
            thIm = (numIm * denRe - numRe * denIm) / den2;
        }

        // 3. d_phi (seconds of arc * 1e-5) and d_lambda
        double dPsi = thRe;
        double dLambda = thIm;
        double dPsiN = 1;

        double dPhi = 0;
        for (int n = 1; n <= 9; n++) {
            dPsiN = dPsiN * dPsi;
            dPhi = dPhi + D[n] * dPsiN;
        }

        // 4. latitude/longitude
        double lat = lat0 + dPhi * Values.SEC_TO_RAD * 1E5;
        double lon = long0 + dLambda;
        return new Point(lon, lat, p.z);
    }
}
