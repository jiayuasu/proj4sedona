package org.datasyslab.proj4sedona.grid;

import java.util.List;

/**
 * Represents a loaded NAD grid file (NTv2 or GeoTIFF format).
 */
public class GridData {
    private final NADGridHeader header;
    private final List<Subgrid> subgrids;

    public GridData(NADGridHeader header, List<Subgrid> subgrids) {
        this.header = header;
        this.subgrids = subgrids;
    }

    public NADGridHeader getHeader() { return header; }
    public List<Subgrid> getSubgrids() { return subgrids; }

    public Subgrid findSubgrid(double lon, double lat) {
        for (Subgrid subgrid : subgrids) {
            if (subgrid.contains(lon, lat)) {
                return subgrid;
            }
        }
        return null;
    }
}
