package org.proj4sedona.grid;

/**
 * Information about a NAD grid reference from a projection definition.
 */
public class NadgridInfo {
    private final String name;
    private final boolean mandatory;
    private final GridData grid;
    private final boolean isNull;

    public NadgridInfo(String name, boolean mandatory, GridData grid, boolean isNull) {
        this.name = name;
        this.mandatory = mandatory;
        this.grid = grid;
        this.isNull = isNull;
    }

    public String getName() { return name; }
    public boolean isMandatory() { return mandatory; }
    public GridData getGrid() { return grid; }
    public boolean isNull() { return isNull; }
}
