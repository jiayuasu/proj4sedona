package org.datasyslab.proj4sedona.constants;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive parity gate for proj4js's generated datum table.
 *
 * <p>The checked-in TSV is generated from the pinned upstream Datum.js. This
 * test fixes its complete content by count and SHA-256, then verifies that every
 * canonical record and datum-name alias is represented by the runtime registry.
 */
class DatumRegistryParityTest {

    private static final String RESOURCE =
            "/org/datasyslab/proj4sedona/constants/proj4js-datums.tsv";
    private static final String UPSTREAM_COMMIT =
            "888ce3a8a5b27e03f08c062c80121225333de8f8";
    private static final String UPSTREAM_SOURCE =
            "https://github.com/proj4js/proj4js/blob/" + UPSTREAM_COMMIT
                    + "/lib/constants/Datum.js";
    private static final String DATA_SHA256 =
            "52fa7af521bc8acc1836d4ec476272d726c3cab5c9d037a2b7a1e338370c8f79";
    private static final String NULL_MARKER = "\\N";
    private static final int CANONICAL_RECORDS = 438;
    private static final int DATUM_NAME_ALIASES = 18;
    private static final int THREE_PARAMETER_RECORDS = 330;
    private static final int SEVEN_PARAMETER_RECORDS = 107;
    private static final int NAD_GRID_RECORDS = 1;

    // These public constants predate the generated table and intentionally carry
    // useful ellipsoid metadata that the corresponding upstream EPSG rows omit.
    private static final Map<String, String> JAVA_ELLIPSE_EXTENSIONS = Map.of(
            "EPSG_4289", "bessel",
            "EPSG_4283", "GRS80",
            "EPSG_4617", "GRS80",
            "EPSG_8351", "bessel");

    @Test
    void matchesPinnedProj4jsDatumRegistryExhaustively() throws Exception {
        Snapshot snapshot = readSnapshot();

        assertEquals(UPSTREAM_SOURCE, snapshot.source);
        assertEquals(UPSTREAM_COMMIT, snapshot.commit);
        assertEquals(CANONICAL_RECORDS, snapshot.canonicalRecords);
        assertEquals(DATUM_NAME_ALIASES, snapshot.datumNameAliases);
        assertEquals(THREE_PARAMETER_RECORDS, snapshot.threeParameterRecords);
        assertEquals(SEVEN_PARAMETER_RECORDS, snapshot.sevenParameterRecords);
        assertEquals(NAD_GRID_RECORDS, snapshot.nadGridRecords);
        assertEquals(NULL_MARKER, snapshot.nullMarker);
        assertEquals(DATA_SHA256, snapshot.dataSha256);
        assertEquals(CANONICAL_RECORDS, snapshot.rows.size());
        assertEquals(snapshot.dataSha256, sha256(snapshot.data));

        Map<String, Datum> actualByCode = new HashMap<>();
        Set<String> expectedCodes = new HashSet<>();
        String previousCode = null;
        int aliases = 0;
        int threeParameters = 0;
        int sevenParameters = 0;
        int nadgrids = 0;
        for (Row row : snapshot.rows) {
            assertTrue(expectedCodes.add(row.code), "duplicate snapshot datum " + row.code);
            if (previousCode != null) {
                assertTrue(previousCode.compareTo(row.code) < 0, "snapshot must be sorted by code");
            }
            previousCode = row.code;

            Datum actual = Datum.get(row.code);
            assertNotNull(actual, "missing runtime datum " + row.code);
            assertNull(actualByCode.put(row.code, actual), "duplicate runtime datum " + row.code);
            assertEquals(row.code, actual.getCode(), row.code + " canonical identity");
            assertEquals(row.towgs84, actual.getTowgs84(), row.code + " towgs84");
            assertEquals(row.nadgrids, actual.getNadgrids(), row.code + " nadgrids");
            assertEquals(
                    row.ellipse != null ? row.ellipse : JAVA_ELLIPSE_EXTENSIONS.get(row.code),
                    actual.getEllipse(),
                    row.code + " ellipse");
            assertEquals(row.datumName, actual.getDatumName(), row.code + " datumName");
            assertSame(actual, Datum.get(row.code), row.code + " canonical lookup");
            if (row.datumName != null) {
                aliases++;
                assertSame(actual, Datum.get(row.datumName), row.code + " upstream name alias");
            }
            if (row.towgs84 != null) {
                int arity = row.towgs84.split(",").length;
                if (arity == 3) {
                    threeParameters++;
                } else if (arity == 7) {
                    sevenParameters++;
                }
            }
            if (row.nadgrids != null) {
                nadgrids++;
            }
        }

        assertEquals(CANONICAL_RECORDS, actualByCode.size());
        assertEquals(DATUM_NAME_ALIASES, aliases);
        assertEquals(THREE_PARAMETER_RECORDS, threeParameters);
        assertEquals(SEVEN_PARAMETER_RECORDS, sevenParameters);
        assertEquals(NAD_GRID_RECORDS, nadgrids);

        Set<Datum> expectedCanonicalDatums =
                Collections.newSetFromMap(new IdentityHashMap<>());
        expectedCanonicalDatums.addAll(actualByCode.values());
        Set<Datum> registeredCanonicalDatums = registeredCanonicalDatums();
        assertEquals(
                CANONICAL_RECORDS,
                registeredCanonicalDatums.size(),
                "runtime registry contains extra canonical datum objects");
        assertEquals(
                expectedCanonicalDatums,
                registeredCanonicalDatums,
                "runtime canonical datum objects differ from the upstream snapshot");

        // Equal parameter values are not aliases: their authority codes must keep
        // distinct objects and canonical identities.
        assertNotSame(Datum.get("EPSG_9059"), Datum.get("EPSG_4269"));
    }

    private static Snapshot readSnapshot() throws IOException {
        InputStream input = DatumRegistryParityTest.class.getResourceAsStream(RESOURCE);
        assertNotNull(input, "missing " + RESOURCE);

        String source = null;
        String commit = null;
        Integer canonicalRecords = null;
        Integer datumNameAliases = null;
        Integer threeParameterRecords = null;
        Integer sevenParameterRecords = null;
        Integer nadGridRecords = null;
        String nullMarker = null;
        String dataSha256 = null;
        boolean sawHeader = false;
        StringBuilder data = new StringBuilder();
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("# Source: ")) {
                    assertNull(source, "duplicate source header");
                    source = line.substring("# Source: ".length());
                }
                if (line.startsWith("# Upstream commit: ")) {
                    assertNull(commit, "duplicate upstream commit header");
                    commit = line.substring("# Upstream commit: ".length());
                }
                if (line.startsWith("# Canonical records: ")) {
                    assertNull(canonicalRecords, "duplicate canonical-record count");
                    canonicalRecords = Integer.valueOf(
                            line.substring("# Canonical records: ".length()));
                }
                if (line.startsWith("# Datum-name aliases: ")) {
                    assertNull(datumNameAliases, "duplicate datum-name-alias count");
                    datumNameAliases = Integer.valueOf(
                            line.substring("# Datum-name aliases: ".length()));
                }
                if (line.startsWith("# Three-parameter records: ")) {
                    assertNull(threeParameterRecords, "duplicate three-parameter count");
                    threeParameterRecords = Integer.valueOf(
                            line.substring("# Three-parameter records: ".length()));
                }
                if (line.startsWith("# Seven-parameter records: ")) {
                    assertNull(sevenParameterRecords, "duplicate seven-parameter count");
                    sevenParameterRecords = Integer.valueOf(
                            line.substring("# Seven-parameter records: ".length()));
                }
                if (line.startsWith("# NAD-grid records: ")) {
                    assertNull(nadGridRecords, "duplicate NAD-grid count");
                    nadGridRecords = Integer.valueOf(
                            line.substring("# NAD-grid records: ".length()));
                }
                if (line.startsWith("# Null marker: ")) {
                    assertNull(nullMarker, "duplicate null marker");
                    nullMarker = line.substring("# Null marker: ".length());
                }
                if (line.startsWith("# Data SHA-256: ")) {
                    assertNull(dataSha256, "duplicate data SHA-256");
                    dataSha256 = line.substring("# Data SHA-256: ".length());
                }
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!sawHeader) {
                    assertEquals("code\ttowgs84\tellipse\tdatumName\tnadgrids", line);
                    sawHeader = true;
                    continue;
                }

                data.append(line).append('\n');
                String[] fields = line.split("\t", -1);
                assertEquals(5, fields.length, "malformed snapshot row: " + line);
                for (String field : fields) {
                    assertTrue(!field.isEmpty(), "empty snapshot field: " + line);
                }
                rows.add(new Row(
                        fields[0],
                        nullMarkerToNull(fields[1]),
                        nullMarkerToNull(fields[2]),
                        nullMarkerToNull(fields[3]),
                        nullMarkerToNull(fields[4])));
            }
        }
        assertTrue(sawHeader, "missing snapshot header");
        assertNotNull(source, "missing upstream source");
        assertNotNull(commit, "missing upstream commit");
        assertNotNull(canonicalRecords, "missing canonical-record count");
        assertNotNull(datumNameAliases, "missing datum-name-alias count");
        assertNotNull(threeParameterRecords, "missing three-parameter count");
        assertNotNull(sevenParameterRecords, "missing seven-parameter count");
        assertNotNull(nadGridRecords, "missing NAD-grid count");
        assertNotNull(nullMarker, "missing null marker");
        assertNotNull(dataSha256, "missing data SHA-256");
        return new Snapshot(
                source,
                commit,
                canonicalRecords,
                datumNameAliases,
                threeParameterRecords,
                sevenParameterRecords,
                nadGridRecords,
                nullMarker,
                dataSha256,
                data.toString(),
                rows);
    }

    @SuppressWarnings("unchecked")
    private static Set<Datum> registeredCanonicalDatums()
            throws ReflectiveOperationException {
        Field datumsField = Datum.class.getDeclaredField("DATUMS");
        datumsField.setAccessible(true);
        Map<String, Datum> registry = (Map<String, Datum>) datumsField.get(null);
        Set<Datum> canonicalDatums = Collections.newSetFromMap(new IdentityHashMap<>());
        canonicalDatums.addAll(registry.values());
        return canonicalDatums;
    }

    private static String sha256(String value) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte element : digest) {
            hex.append(String.format("%02x", element & 0xff));
        }
        return hex.toString();
    }

    private static String nullMarkerToNull(String value) {
        return NULL_MARKER.equals(value) ? null : value;
    }

    private static final class Snapshot {
        private final String source;
        private final String commit;
        private final int canonicalRecords;
        private final int datumNameAliases;
        private final int threeParameterRecords;
        private final int sevenParameterRecords;
        private final int nadGridRecords;
        private final String nullMarker;
        private final String dataSha256;
        private final String data;
        private final List<Row> rows;

        private Snapshot(
                String source,
                String commit,
                int canonicalRecords,
                int datumNameAliases,
                int threeParameterRecords,
                int sevenParameterRecords,
                int nadGridRecords,
                String nullMarker,
                String dataSha256,
                String data,
                List<Row> rows) {
            this.source = source;
            this.commit = commit;
            this.canonicalRecords = canonicalRecords;
            this.datumNameAliases = datumNameAliases;
            this.threeParameterRecords = threeParameterRecords;
            this.sevenParameterRecords = sevenParameterRecords;
            this.nadGridRecords = nadGridRecords;
            this.nullMarker = nullMarker;
            this.dataSha256 = dataSha256;
            this.data = data;
            this.rows = rows;
        }
    }

    private static final class Row {
        private final String code;
        private final String towgs84;
        private final String ellipse;
        private final String datumName;
        private final String nadgrids;

        private Row(
                String code,
                String towgs84,
                String ellipse,
                String datumName,
                String nadgrids) {
            this.code = code;
            this.towgs84 = towgs84;
            this.ellipse = ellipse;
            this.datumName = datumName;
            this.nadgrids = nadgrids;
        }
    }
}
