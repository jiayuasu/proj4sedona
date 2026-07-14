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
 */
class ProjectionRegistryCodesTest {

  @BeforeEach
  void setUp() {
    ProjectionRegistry.reset();
    ProjectionRegistry.start();
  }

  /** The declared PROJ codes for every built-in projection (preferred first). */
  private static final String[][] BUILTIN_CODES = {
    {"longlat"}, {"merc"}, {"tmerc", "etmerc"}, {"utm"}, {"eqc"}, {"cea"},
    {"somerc"}, {"omerc"}, {"cass"}, {"mill"}, {"gstmerc"}, {"lcc"}, {"aea"},
    {"eqdc"}, {"poly"}, {"krovak"}, {"bonne"}, {"stere"}, {"sterea"}, {"laea"},
    {"aeqd"}, {"gnom"}, {"ortho"}, {"tpers"}, {"sinu"}, {"moll"}, {"robin"},
    {"vandg"}, {"eqearth"}, {"eck6"}, {"geos"}, {"nzmg"}, {"geocent"}, {"qsc"},
    {"ob_tran"},
  };

  @Test
  void everyDeclaredCodeResolvesToItsProjection() {
    // Each declared code resolves to a registered projection and is preserved
    // (a valid code supplied directly is returned unchanged).
    for (String[] codes : BUILTIN_CODES) {
      for (String code : codes) {
        assertNotNull(ProjectionRegistry.get(code), "registered: " + code);
        assertTrue(ProjectionRegistry.isValidProjCode(code), "valid code: " + code);
        assertEquals(code, ProjectionRegistry.resolveProjCode(code),
            "declared code preserved verbatim: " + code);
      }
    }
  }

  @Test
  void everyAliasResolvesToThePreferredCode() {
    // Every non-code alias in a projection's NAMES resolves to that projection's
    // preferred (first) declared code.
    List<String> missing = new ArrayList<>();
    for (String[] codes : BUILTIN_CODES) {
      String preferred = codes[0];
      Projection p = ProjectionRegistry.get(preferred);
      assertNotNull(p, preferred);
      for (String alias : p.getNames()) {
        String resolved = ProjectionRegistry.resolveProjCode(alias);
        // An alias that is itself a declared valid code is preserved as-is; any
        // other alias must map to the preferred code.
        boolean aliasIsCode = ProjectionRegistry.isValidProjCode(alias);
        String expected = aliasIsCode ? alias.toLowerCase() : preferred;
        if (!expected.equals(resolved)) {
          missing.add(alias + " -> " + resolved + " (expected " + expected + ")");
        }
      }
    }
    assertTrue(missing.isEmpty(), "alias resolution mismatches: " + missing);
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
    // returns null and callers keep the original spelling.
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
  }
}
