package org.datasyslab.proj4sedona.projection;

import org.datasyslab.proj4sedona.common.ProjMath;
import org.datasyslab.proj4sedona.constants.Values;
import org.datasyslab.proj4sedona.core.Point;

/**
 * Equidistant Cylindrical (Plate Carrée / Equirectangular) projection.
 * Mirrors: lib/projections/eqc.js
 * 
 * <p>A simple cylindrical projection where meridians and parallels are
 * equally spaced straight lines. Also known as Plate Carrée when lat_ts=0.</p>
 */
public class EquidistantCylindrical implements Projection {

    private static final String[] NAMES = {
        "Equidistant_Cylindrical", "Plate_Carree", "eqc", "Equirectangular"
    };

    private double a, lat0, latTs, long0, x0, y0, rc;
    private Boolean over;

    @Override
    public String[] getNames() { return NAMES; }

    @Override
    public void init(ProjectionParams params) {
        this.a = params.a;
        this.lat0 = params.getLat0();
        this.latTs = params.getLatTs();
        this.long0 = params.getLong0();
        this.x0 = params.x0;
        this.y0 = params.y0;
        this.over = params.over;
        this.rc = Math.cos(latTs);
    }

    @Override
    public Point forward(Point p) {
        double lon = ProjMath.adjustLon(p.x - long0, over);
        double lat = p.y;
        double x = a * lon * rc + x0;
        double y = a * lat + y0;
        return new Point(x, y, p.z);
    }

    @Override
    public Point inverse(Point p) {
        double x = p.x - x0;
        double y = p.y - y0;
        double lon = ProjMath.adjustLon(x / (a * rc) + long0, over);
        double lat = y / a;
        return new Point(lon, lat, p.z);
    }
}
