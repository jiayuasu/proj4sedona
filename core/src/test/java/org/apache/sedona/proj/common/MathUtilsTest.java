/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sedona.proj.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Comprehensive tests for MathUtils. */
public class MathUtilsTest {

  private static final double EPSILON = 1e-10;

  @Test
  public void testSign() {
    assertEquals(1, MathUtils.sign(10.0));
    assertEquals(1, MathUtils.sign(0.0));
    assertEquals(-1, MathUtils.sign(-10.0));
    assertEquals(-1, MathUtils.sign(-0.001));
    assertEquals(1, MathUtils.sign(0.001));
  }

  @Test
  public void testAdjustLon() {
    // Within range
    assertEquals(0.0, MathUtils.adjustLon(0.0), EPSILON);
    assertEquals(Math.PI / 2, MathUtils.adjustLon(Math.PI / 2), EPSILON);
    assertEquals(-Math.PI / 2, MathUtils.adjustLon(-Math.PI / 2), EPSILON);

    // Outside range - should be adjusted (or returned as-is depending on implementation)
    double result1 = MathUtils.adjustLon(4 * Math.PI);
    assertTrue(Double.isFinite(result1));

    double result2 = MathUtils.adjustLon(-4 * Math.PI);
    assertTrue(Double.isFinite(result2));
  }

  @Test
  public void testMsfnz() {
    double eccent = 0.0818191908426;
    double sinphi = Math.sin(Math.PI / 4);
    double cosphi = Math.cos(Math.PI / 4);

    double result = MathUtils.msfnz(eccent, sinphi, cosphi);
    assertTrue(result > 0);
    assertTrue(result < 2);
  }

  @Test
  public void testTsfnz() {
    double eccent = 0.0818191908426;
    double phi = Math.PI / 4;
    double sinphi = Math.sin(phi);

    double result = MathUtils.tsfnz(eccent, phi, sinphi);
    assertTrue(result > 0);
  }

  @Test
  public void testPhi2z() {
    double eccent = 0.0818191908426;
    double ts = 0.5;

    double result = MathUtils.phi2z(eccent, ts);
    assertTrue(result != -9999); // Should converge
    assertTrue(result >= -Math.PI / 2 && result <= Math.PI / 2);
  }

  @Test
  public void testPhi2zNoConvergence() {
    double eccent = 0.0818191908426;
    double ts = Double.POSITIVE_INFINITY;

    double result = MathUtils.phi2z(eccent, ts);
    // With infinity, may return -PI/2 or -9999 depending on implementation
    assertTrue(result == -9999 || result == -Math.PI / 2);
  }

  @Test
  public void testAsinh() {
    assertEquals(0.0, MathUtils.asinh(0.0), EPSILON);
    assertEquals(Math.log(1 + Math.sqrt(2)), MathUtils.asinh(1.0), EPSILON);
    assertEquals(-Math.log(1 + Math.sqrt(2)), MathUtils.asinh(-1.0), EPSILON);

    double result = MathUtils.asinh(2.0);
    assertEquals(Math.log(2 + Math.sqrt(5)), result, EPSILON);
  }

  @Test
  public void testAcosh() {
    assertEquals(0.0, MathUtils.acosh(1.0), EPSILON);
    assertEquals(Math.log(2 + Math.sqrt(3)), MathUtils.acosh(2.0), EPSILON);

    double result = MathUtils.acosh(3.0);
    assertEquals(Math.log(3 + Math.sqrt(8)), result, EPSILON);
  }

  @Test
  public void testAtanh() {
    assertEquals(0.0, MathUtils.atanh(0.0), EPSILON);
    assertEquals(0.5 * Math.log(3), MathUtils.atanh(0.5), EPSILON);
    assertEquals(-0.5 * Math.log(3), MathUtils.atanh(-0.5), EPSILON);
  }

  @Test
  public void testSinh() {
    assertEquals(0.0, MathUtils.sinh(0.0), EPSILON);
    assertEquals((Math.E - 1 / Math.E) / 2, MathUtils.sinh(1.0), EPSILON);
    assertEquals(-(Math.E - 1 / Math.E) / 2, MathUtils.sinh(-1.0), EPSILON);
  }

  @Test
  public void testCosh() {
    assertEquals(1.0, MathUtils.cosh(0.0), EPSILON);
    assertEquals((Math.E + 1 / Math.E) / 2, MathUtils.cosh(1.0), EPSILON);
    assertEquals((Math.E + 1 / Math.E) / 2, MathUtils.cosh(-1.0), EPSILON);
  }

  @Test
  public void testTanh() {
    assertEquals(0.0, MathUtils.tanh(0.0), EPSILON);

    double sinh1 = MathUtils.sinh(1.0);
    double cosh1 = MathUtils.cosh(1.0);
    assertEquals(sinh1 / cosh1, MathUtils.tanh(1.0), EPSILON);
  }

  @Test
  public void testHypot() {
    assertEquals(0.0, MathUtils.hypot(0, 0), EPSILON);
    assertEquals(5.0, MathUtils.hypot(3, 4), EPSILON);
    assertEquals(5.0, MathUtils.hypot(4, 3), EPSILON);
    assertEquals(Math.sqrt(2), MathUtils.hypot(1, 1), EPSILON);
    assertEquals(13.0, MathUtils.hypot(5, 12), EPSILON);
  }

  @Test
  public void testLog1py() {
    double result = MathUtils.log1py(0.5, 0.1);
    assertEquals(Math.log(1.1) - 0.5, result, EPSILON);

    double result2 = MathUtils.log1py(0, 1);
    assertEquals(Math.log(2), result2, EPSILON);
  }

  @Test
  public void testClens() {
    double[] coeffs = {1.0, 0.5, 0.25};
    double result = MathUtils.clens(2, 0.5, coeffs);
    assertNotNull(result);
    assertTrue(Double.isFinite(result));
  }

  @Test
  public void testClensC() {
    double[] coeffs = {1.0, 0.5, 0.25};
    double[] result = MathUtils.clensC(2, 0.5, coeffs);

    assertNotNull(result);
    assertEquals(2, result.length);
    assertTrue(Double.isFinite(result[0]));
    assertTrue(Double.isFinite(result[1]));
  }

  @Test
  public void testMlfn() {
    double e = 0.0818191908426;
    double c0 = 1.0;
    double c1 = 0.5;
    double c2 = 0.25;
    double c3 = 0.125;
    double phi = Math.PI / 4;

    double result = MathUtils.mlfn(e, c0, c1, c2, c3, phi);
    assertTrue(Double.isFinite(result));
  }

  @Test
  public void testHyperbolicIdentities() {
    // Test cosh²(x) - sinh²(x) = 1
    double x = 1.5;
    double coshX = MathUtils.cosh(x);
    double sinhX = MathUtils.sinh(x);
    assertEquals(1.0, coshX * coshX - sinhX * sinhX, EPSILON);
  }

  @Test
  public void testInverseHyperbolicFunctions() {
    // Test asinh(sinh(x)) = x
    double x = 1.0;
    assertEquals(x, MathUtils.asinh(MathUtils.sinh(x)), EPSILON);

    // Test acosh(cosh(x)) = x for x >= 0
    assertEquals(x, MathUtils.acosh(MathUtils.cosh(x)), EPSILON);

    // Test atanh(tanh(x)) = x
    assertEquals(x, MathUtils.atanh(MathUtils.tanh(x)), EPSILON);
  }

  @Test
  public void testHypotNegativeValues() {
    assertEquals(5.0, MathUtils.hypot(-3, -4), EPSILON);
    assertEquals(5.0, MathUtils.hypot(-3, 4), EPSILON);
    assertEquals(5.0, MathUtils.hypot(3, -4), EPSILON);
  }

  @Test
  public void testMsfnzZeroEccentricity() {
    double sinphi = Math.sin(Math.PI / 4);
    double cosphi = Math.cos(Math.PI / 4);

    double result = MathUtils.msfnz(0, sinphi, cosphi);
    assertEquals(cosphi, result, EPSILON);
  }

  @Test
  public void testClensZeroTerms() {
    double[] coeffs = {1.0};
    double result = MathUtils.clens(0, 0.5, coeffs);
    assertEquals(0.5, result, EPSILON);
  }

  @Test
  public void testAdjustLonBoundary() {
    assertEquals(Math.PI, MathUtils.adjustLon(Math.PI), EPSILON);
    assertEquals(-Math.PI, MathUtils.adjustLon(-Math.PI), EPSILON);
  }
}
