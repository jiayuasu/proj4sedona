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
package org.apache.sedona.proj.wkt;

import java.util.List;

/**
 * Detects the version of a WKT string (WKT1, WKT2-2015, or WKT2-2019). Ported from the JavaScript
 * wkt-parser detectWKTVersion.js file.
 */
public class WKTVersionDetector {

  /**
   * Detects whether the WKT string is WKT1 or WKT2.
   *
   * @param wkt the WKT string
   * @return the detected version
   */
  public static WKTVersion detectVersion(String wkt) {
    // Normalize the WKT string for easier keyword matching
    String normalizedWKT = wkt.toUpperCase();

    // Check for WKT2-specific keywords
    if (normalizedWKT.contains("PROJCRS")
        || normalizedWKT.contains("GEOGCRS")
        || normalizedWKT.contains("BOUNDCRS")
        || normalizedWKT.contains("VERTCRS")
        || normalizedWKT.contains("LENGTHUNIT")
        || normalizedWKT.contains("ANGLEUNIT")
        || normalizedWKT.contains("SCALEUNIT")) {
      return detectWKT2Version(wkt);
    }

    // Check for WKT1-specific keywords
    if (normalizedWKT.contains("PROJCS")
        || normalizedWKT.contains("GEOGCS")
        || normalizedWKT.contains("LOCAL_CS")
        || normalizedWKT.contains("VERT_CS")
        || normalizedWKT.contains("UNIT")) {
      return WKTVersion.WKT1;
    }

    // Default to WKT1 if no specific indicators are found
    return WKTVersion.WKT1;
  }

  /**
   * Detects the WKT2 version based on the structure of the parsed WKT.
   *
   * @param root the root WKT node
   * @return the detected WKT2 version
   */
  public static WKTVersion detectWKT2Version(List<Object> root) {
    // Check for WKT2-2019-specific nodes
    if (containsNode(root, "USAGE")) {
      return WKTVersion.WKT2_2019;
    }

    // Default to WKT2-2015
    return WKTVersion.WKT2_2015;
  }

  /**
   * Detects the WKT2 version based on the WKT string.
   *
   * @param wkt the WKT string
   * @return the detected WKT2 version
   */
  private static WKTVersion detectWKT2Version(String wkt) {
    // Check for WKT2-2019-specific keywords
    if (wkt.toUpperCase().contains("USAGE")) {
      return WKTVersion.WKT2_2019;
    }

    // Default to WKT2-2015
    return WKTVersion.WKT2_2015;
  }

  /**
   * Checks if the WKT node contains a child with the specified keyword.
   *
   * @param node the WKT node
   * @param keyword the keyword to search for
   * @return true if the keyword is found, false otherwise
   */
  @SuppressWarnings("unchecked")
  private static boolean containsNode(List<Object> node, String keyword) {
    for (Object child : node) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && keyword.equals(childList.get(0))) {
          return true;
        }
      }
    }
    return false;
  }
}
