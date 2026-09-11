/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package org.datasyslab.proj4sedona.projection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the registry-owned canonical PROJ short codes (apache/sedona#3103).
 *
 * <p>External PROJ validity of a projection alias cannot be inferred from the
 * registry — the deliberately-registered typo alias {@code gstmerg} re-parses here
 * but PROJ rejects it — so valid codes are declared at registration and resolved via
 * {@link ProjectionRegistry#resolveProjCode(String)}.</p>
 *
 * <p>Coverage is derived from the live registry (not a hand-maintained list), so a
 * new built-in that omits canonical codes is caught rather than silently skipped.</p>
 */
class ProjectionRegistryCodesTest {

  @BeforeEach
  void setUp() {
    ProjectionRegistry.reset();
    ProjectionRegistry.start();
  }

  @Test
  void everyBuiltinDeclaresACanonicalCode() {
    // The registry, freshly started, holds only built-ins; every one must declare a
    // PROJ code. This is what makes the suite exhaustive: a projection added via
    // add(Supplier) — or via add(List, ...) but forgotten — surfaces here.
    List<String> missing = ProjectionRegistry.projectionsMissingProjCodes();
    assertTrue(missing.isEmpty(), "built-in projections without a declared PROJ code: " + missing);
  }

  @Test
  void everyDeclaredCodeResolvesAndIsPreserved() {
    // Iterates the live registry. Each declared code registers a projection, is a
    // valid code, and is returned as its canonical (lower-case) form when supplied
    // directly — including projections with several codes (e.g. tmerc / etmerc).
    for (List<String> codes : ProjectionRegistry.declaredCodeLists()) {
      for (String code : codes) {
        assertNotNull(ProjectionRegistry.get(code), "registered: " + code);
        assertTrue(ProjectionRegistry.isValidProjCode(code), "valid code: " + code);
        assertEquals(code.toLowerCase(), ProjectionRegistry.resolveProjCode(code),
            "declared code preserved: " + code);
      }
    }
  }

  @Test
  void everyAliasResolvesToThePreferredCode() {
    // Iterates the live registry: every non-code alias in a projection's NAMES
    // resolves to that projection's preferred (first) declared code; an alias that is
    // itself a declared code is preserved as-is.
    List<String> mismatches = new ArrayList<>();
    for (List<String> codes : ProjectionRegistry.declaredCodeLists()) {
      String preferred = codes.get(0);
      Projection p = ProjectionRegistry.get(preferred);
      assertNotNull(p, preferred);
      for (String alias : p.getNames()) {
        String resolved = ProjectionRegistry.resolveProjCode(alias);
        String expected =
            ProjectionRegistry.isValidProjCode(alias) ? alias.toLowerCase() : preferred;
        if (!expected.equals(resolved)) {
          mismatches.add(alias + " -> " + resolved + " (expected " + expected + ")");
        }
      }
    }
    assertTrue(mismatches.isEmpty(), "alias resolution mismatches: " + mismatches);
  }

  @Test
  void multipleValidCodesArePreservedWhenSuppliedDirectly() {
    // tmerc and etmerc are both valid codes for the extended transverse mercator;
    // each is preserved when supplied directly (not normalized to the other).
    assertEquals("tmerc", ProjectionRegistry.resolveProjCode("tmerc"));
    assertEquals("etmerc", ProjectionRegistry.resolveProjCode("etmerc"));
  }

  @Test
  void typoAliasResolvesToTheValidCode() {
    // gstmerg is registered (parses) but is not a valid PROJ code; it resolves to
    // gstmerc, which PROJ accepts.
    assertFalse(ProjectionRegistry.isValidProjCode("gstmerg"));
    assertTrue(ProjectionRegistry.isValidProjCode("gstmerc"));
    assertEquals("gstmerc", ProjectionRegistry.resolveProjCode("gstmerg"));
  }

  @Test
  void declaredCodesAreCanonicalizedAtStorage() {
    // A caller passing a mixed-case / whitespace-padded code must not leak that
    // spelling: resolveProjCode's preserve path and its alias path must agree.
    ProjectionRegistry.reset();
    ProjectionRegistry.add(List.of("  MyCode  ", "Alt_Code"), () -> new Projection() {
      public String[] getNames() { return new String[] {"MyCode", "Alt_Code", "My Custom"}; }
      public void init(ProjectionParams params) {}
      public org.datasyslab.proj4sedona.core.Point forward(org.datasyslab.proj4sedona.core.Point p) { return p; }
      public org.datasyslab.proj4sedona.core.Point inverse(org.datasyslab.proj4sedona.core.Point p) { return p; }
    });
    // preferred code is stored canonical (lower-case, trimmed)
    assertEquals("mycode", ProjectionRegistry.resolveProjCode("My Custom"),
        "alias resolves to the canonical preferred code");
    assertEquals("mycode", ProjectionRegistry.resolveProjCode("  MyCode  "),
        "the declared code itself canonicalizes");
    assertTrue(ProjectionRegistry.isValidProjCode("mycode"));
    assertTrue(ProjectionRegistry.isValidProjCode("alt_code"));
  }

  @Test
  void unknownNamesReturnNull() {
    assertNull(ProjectionRegistry.resolveProjCode("Totally_Unknown_Projection"));
    assertNull(ProjectionRegistry.resolveProjCode(null));
    assertFalse(ProjectionRegistry.isValidProjCode("Totally_Unknown_Projection"));
  }

  @Test
  void resolveProjCodeStartsTheRegistry() {
    // P2: resolveProjCode must work without a prior start()/Proj construction.
    ProjectionRegistry.reset();
    assertEquals("aea", ProjectionRegistry.resolveProjCode("Albers_Conic_Equal_Area"));
  }

  @Test
  void customProjectionsHaveNoDeclaredCodes() {
    // A projection registered via add(Supplier) declares no codes, so resolveProjCode
    // returns null and callers keep the original spelling. It is also reported by
    // projectionsMissingProjCodes (only built-ins are expected to declare codes).
    ProjectionRegistry.reset();
    ProjectionRegistry.add(() -> new Projection() {
      public String[] getNames() { return new String[] {"MyCustom_Proj"}; }
      public void init(ProjectionParams params) {}
      public org.datasyslab.proj4sedona.core.Point forward(org.datasyslab.proj4sedona.core.Point p) { return p; }
      public org.datasyslab.proj4sedona.core.Point inverse(org.datasyslab.proj4sedona.core.Point p) { return p; }
    });
    assertNotNull(ProjectionRegistry.get("MyCustom_Proj"));
    assertNull(ProjectionRegistry.resolveProjCode("MyCustom_Proj"));
    assertFalse(ProjectionRegistry.isValidProjCode("MyCustom_Proj"));
    assertTrue(ProjectionRegistry.projectionsMissingProjCodes().contains("MyCustom_Proj"));
  }
}
