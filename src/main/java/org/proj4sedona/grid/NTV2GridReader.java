package org.proj4sedona.grid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for NTv2 binary grid files (.gsb format).
 */
public final class NTV2GridReader {
    private static final double SECONDS_TO_RADIANS = Math.PI / (180.0 * 3600.0);

    private NTV2GridReader() {}

    public static GridData read(byte[] data) {
        return read(data, true);
    }

    public static GridData read(byte[] data, boolean includeErrorFields) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        boolean littleEndian = detectLittleEndian(buffer);
        buffer.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

        NADGridHeader header = readHeader(buffer);
        List<Subgrid> subgrids = readSubgrids(buffer, header, includeErrorFields);
        return new GridData(header, subgrids);
    }

    private static boolean detectLittleEndian(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        int nFields = buffer.getInt(8);
        if (nFields == 11) return false;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return true;
    }

    private static NADGridHeader readHeader(ByteBuffer buffer) {
        NADGridHeader header = new NADGridHeader();
        header.setnFields(buffer.getInt(8));
        header.setnSubgridFields(buffer.getInt(24));
        header.setnSubgrids(buffer.getInt(40));
        header.setShiftType(decodeString(buffer, 56, 64).trim());
        header.setFromSemiMajorAxis(buffer.getDouble(120));
        header.setFromSemiMinorAxis(buffer.getDouble(136));
        header.setToSemiMajorAxis(buffer.getDouble(152));
        header.setToSemiMinorAxis(buffer.getDouble(168));
        return header;
    }

    private static List<Subgrid> readSubgrids(ByteBuffer buffer, NADGridHeader header, boolean includeErrorFields) {
        int gridOffset = 176;
        List<Subgrid> grids = new ArrayList<>();

        for (int i = 0; i < header.getnSubgrids(); i++) {
            double lowerLatitude = buffer.getDouble(gridOffset + 72);
            double upperLatitude = buffer.getDouble(gridOffset + 88);
            double lowerLongitude = buffer.getDouble(gridOffset + 104);
            double upperLongitude = buffer.getDouble(gridOffset + 120);
            double latitudeInterval = buffer.getDouble(gridOffset + 136);
            double longitudeInterval = buffer.getDouble(gridOffset + 152);
            int gridNodeCount = buffer.getInt(gridOffset + 168);

            int lngColumnCount = (int) Math.round(1 + (upperLongitude - lowerLongitude) / longitudeInterval);
            int latColumnCount = (int) Math.round(1 + (upperLatitude - lowerLatitude) / latitudeInterval);

            double[] ll = new double[]{secondsToRadians(lowerLongitude), secondsToRadians(lowerLatitude)};
            double[] del = new double[]{secondsToRadians(longitudeInterval), secondsToRadians(latitudeInterval)};
            int[] lim = new int[]{lngColumnCount, latColumnCount};

            int nodesOffset = gridOffset + 176;
            int recordLength = includeErrorFields ? 16 : 8;
            double[][] nodes = new double[gridNodeCount][];
            for (int j = 0; j < gridNodeCount; j++) {
                int nodeOffset = nodesOffset + j * recordLength;
                float latitudeShift = buffer.getFloat(nodeOffset);
                float longitudeShift = buffer.getFloat(nodeOffset + 4);
                nodes[j] = new double[]{secondsToRadians(longitudeShift), secondsToRadians(latitudeShift)};
            }

            grids.add(new Subgrid(ll, del, lim, gridNodeCount, nodes));
            gridOffset += 176 + gridNodeCount * recordLength;
        }
        return grids;
    }

    private static String decodeString(ByteBuffer buffer, int start, int end) {
        byte[] bytes = new byte[end - start];
        buffer.position(start);
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static double secondsToRadians(double seconds) {
        return seconds * SECONDS_TO_RADIANS;
    }
}
