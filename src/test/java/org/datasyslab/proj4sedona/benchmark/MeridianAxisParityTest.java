package org.datasyslab.proj4sedona.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.datasyslab.proj4sedona.core.CoordinateAxis;
import org.datasyslab.proj4sedona.core.Proj;
import org.datasyslab.proj4sedona.core.ProjectionDef;
import org.datasyslab.proj4sedona.parser.CRSSerializer;
import org.datasyslab.proj4sedona.parser.WktParser;
import org.datasyslab.proj4sedona.projection.ProjectionRegistry;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Consumes the generated active-EPSG meridian-axis fixture.
 *
 * <p>The benchmark profile invokes this suite through {@link SpeedBenchmark}
 * after its pre-integration-test generator has created the fixture.</p>
 */
final class MeridianAxisParityTest {

    static final String SUITE = "meridian-axis";
    private static final String SCHEMA_VERSION = "1.0";
    private static final double ANGLE_TOLERANCE = Math.toRadians(1e-9);
    private static final List<String> FORMATS = Arrays.asList(
        "wkt2", "projjson", "proj_string");
    private static final Set<String> UNSUPPORTED_METHOD_CASES =
        new LinkedHashSet<>(Arrays.asList("epsg_2985", "epsg_2986"));

    private MeridianAxisParityTest() {
    }

    static void run(ParityCheck parity) throws IOException {
        Path refFile = Paths.get(
            "target/pyproj-reference/meridian_axis_reference.json");
        if (!Files.exists(refFile)) {
            parity.infrastructureFailure(SUITE,
                "reference file is missing: " + refFile);
            return;
        }

        JsonObject root = readReference(refFile);
        if (!"pyproj".equals(requiredString(
                root, "generator", "meridian-axis root"))) {
            parity.infrastructureFailure(SUITE, "generator must be pyproj");
        }
        requiredString(root, "pyproj_version", "meridian-axis root");
        requiredString(root, "proj_version", "meridian-axis root");
        requiredString(root, "epsg_version", "meridian-axis root");
        requiredString(root, "epsg_date", "meridian-axis root");
        requiredString(root, "proj_data_version", "meridian-axis root");

        List<String> declaredFormats = stringList(
            requiredArray(root, "expected_formats", "meridian-axis root"),
            "meridian-axis formats");
        if (!declaredFormats.equals(FORMATS)) {
            parity.infrastructureFailure(SUITE,
                "expected_formats must be exactly " + FORMATS
                    + ", but was " + declaredFormats);
        }

        List<String> baselineCodes = stringList(
            requiredArray(root, "baseline_codes", "meridian-axis root"),
            "meridian-axis baseline codes");
        int baselineCount = requiredInt(
            root, "baseline_case_count", "meridian-axis root");
        if (baselineCount != baselineCodes.size()) {
            parity.infrastructureFailure(SUITE,
                "baseline_case_count declares " + baselineCount + ", but "
                    + baselineCodes.size() + " baseline codes were present");
        }
        if (new LinkedHashSet<>(baselineCodes).size() != baselineCodes.size()) {
            parity.infrastructureFailure(SUITE,
                "baseline_codes contains duplicate entries");
        }

        JsonArray cases = requiredArray(
            root, "test_cases", "meridian-axis root");
        parity.reconcileCount(SUITE, "test-cases",
            requiredInt(root, "expected_case_count", "meridian-axis root"),
            cases.size());
        Set<String> discoveredCodes = new LinkedHashSet<>();

        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex++) {
            consumeCase(parity, cases, caseIndex, discoveredCodes);
        }

        Set<String> missingBaseline = new LinkedHashSet<>(baselineCodes);
        missingBaseline.removeAll(discoveredCodes);
        if (!missingBaseline.isEmpty()) {
            parity.infrastructureFailure(SUITE,
                "fixture is missing baseline EPSG codes: " + missingBaseline);
        }
        if (discoveredCodes.size() != cases.size()) {
            parity.infrastructureFailure(SUITE,
                "test_cases contains duplicate EPSG codes");
        }
        parity.reconcileCount(SUITE, "comparisons",
            requiredInt(
                root, "expected_comparison_count", "meridian-axis root"),
            cases.size() * FORMATS.size() * 2);
        parity.reconcileCount(SUITE, "parser-comparisons",
            requiredInt(
                root, "expected_parser_comparison_count",
                "meridian-axis root"),
            cases.size() * 2);
        System.out.println("   Meridian-axis correctness: "
            + cases.size() + " CRSs, "
            + (cases.size() * 2) + " parser comparisons and "
            + (cases.size() * FORMATS.size() * 2) + " export outcomes");
    }

    private static void consumeCase(
            ParityCheck parity,
            JsonArray cases,
            int caseIndex,
            Set<String> discoveredCodes) {
        JsonObject testCase = requiredObject(cases.get(caseIndex),
            "meridian-axis case " + caseIndex);
        String caseContext = "meridian-axis case " + caseIndex;
        String caseId = requiredString(testCase, "case_id", caseContext);
        String authority = requiredString(
            testCase, "authority", caseContext);
        String code = requiredString(testCase, "code", caseContext);
        requiredString(testCase, "name", caseContext);
        String sourceWkt2 = requiredString(
            testCase, "source_wkt2", caseContext);
        JsonObject sourceProjjson = requiredObject(
            testCase, "source_projjson", caseContext);
        String projectionMethod = requiredString(
            requiredObject(
                requiredObject(
                    sourceProjjson, "conversion", caseContext + " source"),
                "method", caseContext + " source conversion"),
            "name", caseContext + " source method");
        JsonArray expectedAxes = requiredArray(
            testCase, "expected_axes", caseContext);
        boolean legacyAxisOmitted = requiredBoolean(
            testCase, "legacy_proj_axis_omitted", caseContext);
        boolean pyprojLegacyExported = requiredBoolean(
            testCase, "pyproj_legacy_proj_exported", caseContext);
        String referenceProjString = optionalString(
            testCase, "legacy_proj_string", null);

        discoveredCodes.add(code);
        String caseError = null;
        if (!"EPSG".equals(authority)) {
            caseError = joinErrors(caseError,
                "authority must be EPSG, but was " + authority);
        }
        if (!caseId.equals("epsg_" + code)) {
            caseError = joinErrors(caseError,
                "case_id does not match EPSG code " + code);
        }
        String resolvedMethod =
            ProjectionRegistry.resolveProjCode(projectionMethod);
        boolean expectedUnsupported =
            UNSUPPORTED_METHOD_CASES.contains(caseId);
        if (resolvedMethod == null && !expectedUnsupported) {
            caseError = joinErrors(caseError,
                "unexpected unsupported projection method "
                    + projectionMethod);
        } else if (resolvedMethod != null && expectedUnsupported) {
            caseError = joinErrors(caseError,
                "stale unsupported-method declaration for "
                    + projectionMethod);
        } else if (expectedUnsupported
                && !"Polar Stereographic (variant C)".equals(
                    projectionMethod)) {
            caseError = joinErrors(caseError,
                "unsupported-method declaration expected Polar "
                    + "Stereographic (variant C), but found "
                    + projectionMethod);
        }
        if (expectedAxes.size() == 0) {
            caseError = joinErrors(caseError,
                "expected_axes must not be empty");
        }
        if (!legacyAxisOmitted) {
            caseError = joinErrors(caseError,
                "legacy_proj_axis_omitted must be true");
        }
        if (pyprojLegacyExported) {
            if (referenceProjString == null
                    || referenceProjString.trim().isEmpty()) {
                caseError = joinErrors(caseError,
                    "pyproj legacy export is declared but missing");
            } else if (containsProjAxisToken(referenceProjString)) {
                caseError = joinErrors(caseError,
                    "pyproj legacy export unexpectedly contains +axis");
            }
        } else if (referenceProjString != null) {
            caseError = joinErrors(caseError,
                "pyproj legacy export is present despite being unavailable");
        }

        String parserError = parseAxisMetadata(
            sourceWkt2, sourceProjjson, expectedAxes, caseId, parity);
        caseError = joinErrors(caseError, parserError);

        consumeSourceFormats(
            parity, caseId, "projjson_source", sourceProjjson.toString(),
            caseError, expectedUnsupported, projectionMethod, expectedAxes);
        consumeSourceFormats(
            parity, caseId, "wkt2_source", sourceWkt2,
            caseError, expectedUnsupported, projectionMethod, expectedAxes);
    }

    private static void consumeSourceFormats(
            ParityCheck parity,
            String caseId,
            String sourceFormat,
            String source,
            String caseError,
            boolean expectedUnsupported,
            String projectionMethod,
            JsonArray expectedAxes) {
        Proj original = null;
        String sourceError = caseError;
        if (sourceError == null && !expectedUnsupported) {
            try {
                original = new Proj(source);
                String axisError = coordinateAxisDifference(
                    expectedAxes, original.getParams().coordinateAxes);
                if (axisError != null) {
                    sourceError = sourceFormat + " axis mismatch: " + axisError;
                }
            } catch (Exception e) {
                sourceError = "Java " + sourceFormat + " parse failed: "
                    + describeException(e);
            }
        }

        for (String format : FORMATS) {
            consumeFormat(
                parity, caseId, sourceFormat, format, sourceError,
                expectedUnsupported, projectionMethod, original, expectedAxes);
        }
    }

    @SuppressWarnings("unchecked")
    private static String parseAxisMetadata(
            String sourceWkt2,
            JsonObject sourceProjjson,
            JsonArray expectedAxes,
            String caseId,
            ParityCheck parity) {
        String combinedError = null;
        String projjsonId = caseId + "/projjson_parse";
        parity.expect(SUITE, projjsonId);
        try {
            Map<String, Object> source = new Gson().fromJson(
                sourceProjjson, Map.class);
            ProjectionDef definition = WktParser.parse(source);
            String error = coordinateAxisDifference(
                expectedAxes, definition.getCoordinateAxes());
            if (error == null) {
                parity.compared(SUITE, projjsonId, 0.0, 0.0);
            } else {
                parity.failed(SUITE, projjsonId, error);
                combinedError = "source PROJJSON axis mismatch: " + error;
            }
        } catch (Exception e) {
            String error = "source PROJJSON parse failed: "
                + describeException(e);
            parity.failed(SUITE, projjsonId, error);
            combinedError = error;
        }

        String wkt2Id = caseId + "/wkt2_parse";
        parity.expect(SUITE, wkt2Id);
        try {
            ProjectionDef definition = WktParser.parse(sourceWkt2);
            String error = coordinateAxisDifference(
                expectedAxes, definition.getCoordinateAxes());
            if (error == null) {
                parity.compared(SUITE, wkt2Id, 0.0, 0.0);
            } else {
                parity.failed(SUITE, wkt2Id, error);
                combinedError = joinErrors(
                    combinedError, "source WKT2 axis mismatch: " + error);
            }
        } catch (Exception e) {
            String error = "source WKT2 parse failed: "
                + describeException(e);
            parity.failed(SUITE, wkt2Id, error);
            combinedError = joinErrors(combinedError, error);
        }
        return combinedError;
    }

    private static void consumeFormat(
            ParityCheck parity,
            String caseId,
            String sourceFormat,
            String format,
            String caseError,
            boolean expectedUnsupported,
            String projectionMethod,
            Proj original,
            JsonArray expectedAxes) {
        String id = caseId + "/" + sourceFormat + "/" + format;
        parity.expect(SUITE, id);
        if (caseError != null) {
            parity.failed(SUITE, id, caseError);
            return;
        }
        if (expectedUnsupported) {
            parity.skipped(SUITE, id,
                projectionMethod + " is not implemented; its WKT2 and "
                    + "PROJJSON axis metadata passed parser parity");
            return;
        }

        try {
            String serialized = serialize(original, format);
            if (serialized == null || serialized.trim().isEmpty()) {
                parity.failed(SUITE, id,
                    "Java " + format + " export is empty");
                return;
            }

            if ("proj_string".equals(format)) {
                String legacyError = legacyProjAxisDifference(serialized);
                if (legacyError != null) {
                    parity.failed(SUITE, id, legacyError);
                    return;
                }
                parity.compared(SUITE, id, 0.0, 0.0);
                return;
            }

            Proj firstRoundTrip = new Proj(serialized);
            String semanticError = joinErrors(
                SpeedBenchmark.serializerSemanticDifference(
                    original, firstRoundTrip),
                coordinateAxisDifference(
                    expectedAxes,
                    firstRoundTrip.getParams().coordinateAxes));
            if (semanticError != null) {
                parity.failed(SUITE, id,
                    "Java " + format
                        + " round-trip changed CRS semantics: "
                        + semanticError);
                return;
            }

            String secondSerialized = serialize(firstRoundTrip, format);
            Proj secondRoundTrip = new Proj(secondSerialized);
            semanticError = joinErrors(
                SpeedBenchmark.serializerSemanticDifference(
                    original, secondRoundTrip),
                coordinateAxisDifference(
                    expectedAxes,
                    secondRoundTrip.getParams().coordinateAxes));
            if (semanticError != null) {
                parity.failed(SUITE, id,
                    "Java " + format
                        + " repeated round-trip changed CRS semantics: "
                        + semanticError);
                return;
            }
            parity.compared(SUITE, id, 0.0, 0.0);
        } catch (Exception e) {
            parity.failed(SUITE, id,
                "Java " + format + " round-trip failed: "
                    + describeException(e));
        }
    }

    private static String serialize(Proj projection, String format) {
        switch (format) {
            case "wkt2":
                return CRSSerializer.toWkt2(projection);
            case "proj_string":
                return CRSSerializer.toProjString(projection);
            case "projjson":
                return CRSSerializer.toProjJson(projection);
            default:
                throw new IllegalArgumentException(
                    "Unsupported serializer format: " + format);
        }
    }

    private static String legacyProjAxisDifference(String serialized) {
        if (containsProjAxisToken(serialized)) {
            return "Java legacy PROJ export unexpectedly contains +axis";
        }
        try {
            Proj reparsed = new Proj(serialized);
            List<CoordinateAxis> axes = reparsed.getParams().coordinateAxes;
            if (axes != null && !axes.isEmpty()) {
                return "Java legacy PROJ round-trip retained coordinate-axis metadata";
            }
        } catch (Exception e) {
            return "Java legacy PROJ round-trip failed: " + describeException(e);
        }
        return null;
    }

    private static boolean containsProjAxisToken(String projString) {
        for (String token : projString.trim().split("\\s+")) {
            if (token.startsWith("+axis=")) {
                return true;
            }
        }
        return false;
    }

    private static String coordinateAxisDifference(
            JsonArray expectedAxes, List<CoordinateAxis> actualAxes) {
        if (actualAxes == null) {
            return "coordinate axes are null";
        }
        if (expectedAxes.size() != actualAxes.size()) {
            return "coordinate axis count " + expectedAxes.size()
                + " -> " + actualAxes.size();
        }

        for (int index = 0; index < expectedAxes.size(); index++) {
            String label = "axis " + (index + 1);
            JsonObject expected = requiredObject(
                expectedAxes.get(index), label);
            CoordinateAxis actual = actualAxes.get(index);
            if (actual == null) {
                return label + " is null";
            }

            String expectedName = requiredString(expected, "name", label);
            String actualName = actual.getName();
            if (actualName == null
                    || (!actualName.trim().isEmpty()
                        && !expectedName.equalsIgnoreCase(actualName))) {
                return label + " name " + expectedName + " -> "
                    + actualName;
            }
            String expectedAbbreviation = optionalString(
                expected, "abbreviation", null);
            if (!Objects.equals(
                    expectedAbbreviation, actual.getAbbreviation())) {
                return label + " abbreviation " + expectedAbbreviation + " -> "
                    + actual.getAbbreviation();
            }
            String expectedDirection = requiredString(
                expected, "direction", label);
            if (!expectedDirection.equals(actual.getDirection())) {
                return label + " direction " + expectedDirection + " -> "
                    + actual.getDirection();
            }

            int expectedOrder = requiredInt(expected, "order", label);
            int actualOrder = actual.getOrder() == null
                ? index + 1 : actual.getOrder();
            if (expectedOrder != actualOrder) {
                return label + " order " + expectedOrder + " -> "
                    + actualOrder;
            }

            String unitError = coordinateUnitDifference(
                label + " unit",
                requiredObject(expected, "unit", label),
                actual.getUnit());
            if (unitError != null) {
                return unitError;
            }

            JsonObject expectedMeridian = optionalObject(
                expected, "meridian");
            CoordinateAxis.Meridian actualMeridian = actual.getMeridian();
            if (expectedMeridian == null) {
                if (actualMeridian != null) {
                    return label + " gained a meridian";
                }
                continue;
            }
            if (actualMeridian == null) {
                return label + " lost its meridian";
            }

            JsonObject expectedMeridianUnit = requiredObject(
                expectedMeridian, "unit", label + " meridian");
            unitError = coordinateUnitDifference(
                label + " meridian unit",
                expectedMeridianUnit,
                actualMeridian.getUnit());
            if (unitError != null) {
                return unitError;
            }

            double expectedLongitude = requiredDouble(
                expectedMeridian, "longitude", label + " meridian");
            double expectedFactor = requiredDouble(
                expectedMeridianUnit,
                "conversion_factor",
                label + " meridian unit");
            Double actualFactor = actualMeridian.getUnit()
                .getConversionFactor();
            if (actualFactor == null || !Double.isFinite(actualFactor)) {
                return label + " meridian has no finite unit factor";
            }
            double expectedRadians = expectedLongitude * expectedFactor;
            double actualRadians =
                actualMeridian.getLongitude() * actualFactor;
            if (!semanticModuloAngleEquals(
                    expectedRadians, actualRadians)) {
                return label + " meridian " + expectedRadians + " -> "
                    + actualRadians + " radians";
            }
        }
        return null;
    }

    private static String coordinateUnitDifference(
            String label,
            JsonObject expected,
            CoordinateAxis.Unit actual) {
        if (actual == null) {
            return label + " is missing";
        }
        String expectedType = requiredString(expected, "type", label);
        if (!expectedType.equals(actual.getType())) {
            return label + " type " + expectedType + " -> "
                + actual.getType();
        }
        String expectedName = requiredString(expected, "name", label);
        if (!expectedName.equals(actual.getName())) {
            return label + " name " + expectedName + " -> "
                + actual.getName();
        }
        double expectedFactor = requiredDouble(
            expected, "conversion_factor", label);
        Double actualFactor = actual.getConversionFactor();
        if (actualFactor == null || !Double.isFinite(actualFactor)) {
            return label + " has no finite conversion factor";
        }
        if (!semanticDoubleEquals(expectedFactor, actualFactor)) {
            return label + " conversion factor " + expectedFactor + " -> "
                + actualFactor;
        }
        return null;
    }

    private static boolean semanticModuloAngleEquals(
            double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) {
            return false;
        }
        double difference = Math.IEEEremainder(
            first - second, 2.0 * Math.PI);
        return Math.abs(difference) <= ANGLE_TOLERANCE;
    }

    private static boolean semanticDoubleEquals(
            double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) {
            return Double.doubleToLongBits(first)
                == Double.doubleToLongBits(second);
        }
        double scale = Math.max(
            1.0, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= 1e-12 * scale;
    }

    private static JsonObject readReference(Path path) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path)) {
            root = new Gson().fromJson(reader, JsonObject.class);
        }
        if (root == null) {
            throw new IllegalArgumentException("reference root is null");
        }
        String version = requiredString(
            root, "version", "meridian-axis root");
        if (!SCHEMA_VERSION.equals(version)) {
            throw new IllegalArgumentException(
                "unsupported reference schema " + version + " (expected "
                    + SCHEMA_VERSION + ")");
        }
        return root;
    }

    private static JsonObject requiredObject(
            JsonElement value, String context) {
        if (value == null || value.isJsonNull() || !value.isJsonObject()) {
            throw new IllegalArgumentException(
                context + " must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private static JsonObject requiredObject(
            JsonObject owner, String field, String context) {
        return requiredObject(
            owner == null ? null : owner.get(field),
            context + "." + field);
    }

    private static JsonObject optionalObject(
            JsonObject owner, String field) {
        if (owner == null) {
            return null;
        }
        JsonElement value = owner.get(field);
        return value == null || value.isJsonNull() || !value.isJsonObject()
            ? null : value.getAsJsonObject();
    }

    private static JsonArray requiredArray(
            JsonObject owner, String field, String context) {
        JsonElement value = owner == null ? null : owner.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonArray()) {
            throw new IllegalArgumentException(context + "." + field
                + " must be a JSON array");
        }
        return value.getAsJsonArray();
    }

    private static String requiredString(
            JsonObject owner, String field, String context) {
        String value = optionalString(owner, field, null);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(context + "." + field
                + " must be a non-empty string");
        }
        return value;
    }

    private static String optionalString(
            JsonObject owner, String field, String defaultValue) {
        if (owner == null) {
            return defaultValue;
        }
        JsonElement value = owner.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return value.getAsString();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    private static int requiredInt(
            JsonObject owner, String field, String context) {
        JsonElement value = owner == null ? null : owner.get(field);
        try {
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            return value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(context + "." + field
                + " must be an integer", e);
        }
    }

    private static double requiredDouble(
            JsonObject owner, String field, String context) {
        JsonElement value = owner == null ? null : owner.get(field);
        try {
            if (value == null || value.isJsonNull()
                    || !value.isJsonPrimitive()) {
                throw new IllegalArgumentException();
            }
            double number = value.getAsDouble();
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException();
            }
            return number;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(context + "." + field
                + " must be a finite number", e);
        }
    }

    private static boolean requiredBoolean(
            JsonObject owner, String field, String context) {
        JsonElement value = owner == null ? null : owner.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(context + "." + field
                + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static List<String> stringList(
            JsonArray values, String context) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (value == null || value.isJsonNull()
                    || !value.isJsonPrimitive()) {
                throw new IllegalArgumentException(
                    context + " entry " + index + " must be a string");
            }
            result.add(value.getAsString());
        }
        return result;
    }

    private static String joinErrors(String first, String second) {
        if (first == null || first.trim().isEmpty()) {
            return second == null || second.trim().isEmpty() ? null : second;
        }
        if (second == null || second.trim().isEmpty()) {
            return first;
        }
        return first + "; " + second;
    }

    private static String describeException(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
            + (message == null || message.trim().isEmpty()
                ? "" : ": " + message);
    }
}
