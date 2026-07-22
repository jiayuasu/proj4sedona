package org.datasyslab.proj4sedona.benchmark;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParityCheckTest {

    @Test
    void passingComparisonIsFullyAccounted() {
        ParityCheck check = new ParityCheck();
        check.expect("transform", "mercator/origin");
        check.compared("transform", "mercator/origin", 0.001, 0.01);
        check.finalizeCoverage();

        ParityCheck.Coverage coverage = check.coverageBySuite().get("transform");
        assertEquals(1, coverage.expected());
        assertEquals(1, coverage.compared());
        assertEquals(0, coverage.failed());
        assertFalse(check.hasFailures());
        check.throwIfFailed();
    }

    @Test
    void exactTolerancePassesButLargerErrorFails() {
        ParityCheck check = new ParityCheck();
        check.expect("transform", "exact");
        check.expect("transform", "over");
        check.compared("transform", "exact", 0.01, 0.01);
        check.compared("transform", "over", 0.0100001, 0.01);
        check.finalizeCoverage();

        assertEquals(2, check.coverageBySuite().get("transform").compared());
        assertEquals(1, check.coverageBySuite().get("transform").mismatches());
        assertTrue(check.failures().get(0).contains("exceeds tolerance"));
        assertThrows(IllegalStateException.class, check::throwIfFailed);
    }

    @Test
    void nonFiniteValuesAndInvalidToleranceAreFailures() {
        ParityCheck check = new ParityCheck();
        check.expect("grid", "nan");
        check.expect("grid", "infinite-tolerance");
        check.compared("grid", "nan", Double.NaN, 1e-6);
        check.compared("grid", "infinite-tolerance", 0, Double.POSITIVE_INFINITY);
        check.finalizeCoverage();

        assertEquals(2, check.coverageBySuite().get("grid").failed());
        assertEquals(2, check.failures().size());
    }

    @Test
    void explicitFailureAndPendingRowsFail() {
        ParityCheck check = new ParityCheck();
        check.expect("parser", "bad-input");
        check.expect("parser", "forgotten");
        check.failed("parser", "bad-input", "parse exception");
        check.finalizeCoverage();

        assertEquals(2, check.coverageBySuite().get("parser").failed());
        assertTrue(check.failures().stream().anyMatch(v -> v.contains("not accounted")));
    }

    @Test
    void declaredMissingRowsBecomeExpectedFailuresAndExtraRowsAreReported() {
        ParityCheck check = new ParityCheck();
        check.reconcileCount("serializer", "top-level", 2, 1);
        check.reconcileCount("serializer", "other", 1, 2);
        check.finalizeCoverage();

        assertEquals(1, check.coverageBySuite().get("serializer").expected());
        assertEquals(1, check.coverageBySuite().get("serializer").failed());
        assertEquals(2, check.failures().size());
    }

    @Test
    void duplicateUnexpectedAndMultipleOutcomesAreRejected() {
        ParityCheck check = new ParityCheck();
        check.expect("transform", "one");
        check.expect("transform", "one");
        check.compared("transform", "one", 0, 0);
        check.skipped("transform", "one", "already compared");
        check.failed("transform", "unexpected", "not registered");
        check.finalizeCoverage();

        assertEquals(1, check.coverageBySuite().get("transform").expected());
        assertEquals(3, check.failures().size());
    }

    @Test
    void skipRequiresReasonAndIsListedInOrder() {
        ParityCheck check = new ParityCheck();
        check.expect("transform", "a");
        check.expect("transform", "b");
        check.skipped("transform", "a", "unsupported datum parameters");
        check.skipped("transform", "b", " ");
        check.finalizeCoverage();

        assertEquals(Collections.singletonList(
            "transform/a: unsupported datum parameters"), check.skips());
        assertEquals(1, check.coverageBySuite().get("transform").skipped());
        assertEquals(1, check.coverageBySuite().get("transform").failed());
    }

    @Test
    void staleAndUndeclaredExceptionIdsFailAudit() {
        ParityCheck check = new ParityCheck();
        Set<String> declared = new LinkedHashSet<>();
        declared.add("used");
        declared.add("stale");
        Set<String> used = new LinkedHashSet<>();
        used.add("used");
        used.add("surprise");

        check.auditDeclaredUses("transform", "skip", declared, used);
        check.finalizeCoverage();

        assertEquals(2, check.failures().size());
        assertTrue(check.failures().get(0).contains("stale"));
        assertTrue(check.failures().get(1).contains("undeclared"));
    }

    @Test
    void exposedCollectionsAreReadOnlyAndFinalizationLocksMutation() {
        ParityCheck check = new ParityCheck();
        check.expect("suite", "row");
        check.compared("suite", "row", 0, 0);
        check.finalizeCoverage();

        Map<String, ParityCheck.Coverage> coverage = check.coverageBySuite();
        assertThrows(UnsupportedOperationException.class, coverage::clear);
        assertThrows(UnsupportedOperationException.class, () -> check.failures().add("x"));
        assertThrows(IllegalStateException.class, () -> check.expect("suite", "next"));
    }
}
