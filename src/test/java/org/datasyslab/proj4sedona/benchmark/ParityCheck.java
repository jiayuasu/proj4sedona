package org.datasyslab.proj4sedona.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accounts for every reference row consumed by the pyproj parity benchmark.
 *
 * <p>Each expected row must finish exactly once as compared, explicitly skipped,
 * or failed. Infrastructure failures are recorded separately so the report can
 * still be written before the build fails.</p>
 */
final class ParityCheck {

    enum Outcome {
        PENDING,
        COMPARED,
        SKIPPED,
        FAILED
    }

    static final class Coverage {
        private int expected;
        private int compared;
        private int skipped;
        private int failed;
        private int mismatches;

        int expected() {
            return expected;
        }

        int compared() {
            return compared;
        }

        int skipped() {
            return skipped;
        }

        int failed() {
            return failed;
        }

        int mismatches() {
            return mismatches;
        }
    }

    private final Map<String, Coverage> coverageBySuite = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, Outcome>> outcomesBySuite =
        new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final List<String> skips = new ArrayList<>();
    private boolean finalized;

    void expect(String suite, String id) {
        ensureNotFinalized();
        validateName("suite", suite);
        validateName("case id", id);
        LinkedHashMap<String, Outcome> suiteOutcomes = outcomesBySuite.computeIfAbsent(
            suite, ignored -> new LinkedHashMap<>());
        if (suiteOutcomes.containsKey(id)) {
            addFailure(label(suite, id) + ": duplicate reference case id");
            return;
        }
        suiteOutcomes.put(id, Outcome.PENDING);
        coverage(suite).expected++;
    }

    void compared(String suite, String id, double error, double tolerance) {
        ensureNotFinalized();
        if (!Double.isFinite(error) || error < 0) {
            failed(suite, id, "comparison produced an invalid error: " + error);
            return;
        }
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            failed(suite, id, "invalid tolerance: " + tolerance);
            return;
        }
        if (!finish(suite, id, Outcome.COMPARED)) {
            return;
        }

        Coverage suiteCoverage = coverage(suite);
        suiteCoverage.compared++;
        if (error > tolerance) {
            suiteCoverage.mismatches++;
            addFailure(String.format(
                "%s: error %.12g exceeds tolerance %.12g",
                label(suite, id), error, tolerance));
        }
    }

    void skipped(String suite, String id, String reason) {
        ensureNotFinalized();
        if (reason == null || reason.trim().isEmpty()) {
            failed(suite, id, "skip is missing a documented reason");
            return;
        }
        if (!finish(suite, id, Outcome.SKIPPED)) {
            return;
        }
        coverage(suite).skipped++;
        skips.add(label(suite, id) + ": " + reason.trim());
    }

    void failed(String suite, String id, String reason) {
        ensureNotFinalized();
        String detail = reason == null || reason.trim().isEmpty()
            ? "failure is missing a reason" : reason.trim();
        if (!finish(suite, id, Outcome.FAILED)) {
            return;
        }
        coverage(suite).failed++;
        addFailure(label(suite, id) + ": " + detail);
    }

    void infrastructureFailure(String suite, String reason) {
        ensureNotFinalized();
        validateName("suite", suite);
        coverage(suite);
        String detail = reason == null || reason.trim().isEmpty()
            ? "infrastructure failure is missing a reason" : reason.trim();
        addFailure(suite + ": " + detail);
    }

    /** Reconciles a declared fixture count without hiding missing or extra rows. */
    void reconcileCount(String suite, String scope, int declared, int actual) {
        ensureNotFinalized();
        if (declared < 0) {
            infrastructureFailure(suite, scope + " has a negative declared count: " + declared);
            return;
        }
        if (declared > actual) {
            accountMissing(suite, scope, declared - actual);
        } else if (declared < actual) {
            infrastructureFailure(suite, scope + " declares " + declared
                + " row(s), but " + actual + " were present");
        }
    }

    void accountMissing(String suite, String scope, int missingCount) {
        ensureNotFinalized();
        if (missingCount < 0) {
            infrastructureFailure(suite, scope + " has a negative missing count: " + missingCount);
            return;
        }
        for (int i = 0; i < missingCount; i++) {
            String id = "__missing/" + scope + "/" + (i + 1);
            expect(suite, id);
            failed(suite, id, "expected reference row is missing");
        }
    }

    /** Fails when a declared skip or tolerance override was not exercised. */
    void auditDeclaredUses(
            String suite, String kind, Set<String> declared, Set<String> used) {
        ensureNotFinalized();
        Set<String> stale = new LinkedHashSet<>(declared);
        stale.removeAll(used);
        for (String id : stale) {
            infrastructureFailure(suite, "stale " + kind + " declaration: " + id);
        }
        Set<String> undeclared = new LinkedHashSet<>(used);
        undeclared.removeAll(declared);
        for (String id : undeclared) {
            infrastructureFailure(suite, "undeclared " + kind + " use: " + id);
        }
    }

    void finalizeCoverage() {
        if (finalized) {
            return;
        }
        for (Map.Entry<String, LinkedHashMap<String, Outcome>> suiteEntry
                : outcomesBySuite.entrySet()) {
            String suite = suiteEntry.getKey();
            List<String> pending = new ArrayList<>();
            for (Map.Entry<String, Outcome> row : suiteEntry.getValue().entrySet()) {
                if (row.getValue() == Outcome.PENDING) {
                    pending.add(row.getKey());
                }
            }
            for (String id : pending) {
                failed(suite, id, "expected reference row was not accounted for");
            }
        }
        finalized = true;
    }

    boolean hasFailures() {
        return !failures.isEmpty();
    }

    Map<String, Coverage> coverageBySuite() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(coverageBySuite));
    }

    List<String> failures() {
        return Collections.unmodifiableList(new ArrayList<>(failures));
    }

    List<String> skips() {
        return Collections.unmodifiableList(new ArrayList<>(skips));
    }

    void throwIfFailed() {
        if (!finalized) {
            throw new IllegalStateException("Coverage must be finalized before checking failures");
        }
        if (hasFailures()) {
            throw new IllegalStateException(
                "Parity checks failed with " + failures.size()
                    + " regression(s); see target/benchmark_report.md");
        }
    }

    private boolean finish(String suite, String id, Outcome outcome) {
        validateName("suite", suite);
        validateName("case id", id);
        Map<String, Outcome> suiteOutcomes = outcomesBySuite.get(suite);
        Outcome previous = suiteOutcomes == null ? null : suiteOutcomes.get(id);
        if (previous == null) {
            addFailure(label(suite, id) + ": result was recorded without an expectation");
            return false;
        }
        if (previous != Outcome.PENDING) {
            addFailure(label(suite, id) + ": multiple outcomes recorded (first was "
                + previous.name().toLowerCase() + ")");
            return false;
        }
        suiteOutcomes.put(id, outcome);
        return true;
    }

    private Coverage coverage(String suite) {
        return coverageBySuite.computeIfAbsent(suite, ignored -> new Coverage());
    }

    private void addFailure(String failure) {
        failures.add(failure);
    }

    private String label(String suite, String id) {
        return suite + "/" + id;
    }

    private void validateName(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be non-empty");
        }
    }

    private void ensureNotFinalized() {
        if (finalized) {
            throw new IllegalStateException("Coverage is already finalized");
        }
    }
}
