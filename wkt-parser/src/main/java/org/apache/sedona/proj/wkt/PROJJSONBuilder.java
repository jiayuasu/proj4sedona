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
import java.util.Map;

/**
 * Builds a PROJJSON object from a WKT2 array structure. Ported from the JavaScript wkt-parser
 * buildPROJJSON.js file.
 */
public class PROJJSONBuilder {

  /**
   * Detects the WKT2 version based on the structure of the WKT.
   *
   * @param root the root WKT array node
   * @return the detected WKT2 version
   */
  @SuppressWarnings("unchecked")
  private static WKTVersion detectWKT2Version(List<Object> root) {
    // Check for WKT2-2019-specific nodes
    for (Object child : root) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && "USAGE".equals(childList.get(0))) {
          return WKTVersion.WKT2_2019;
        }
      }
    }

    // Check for WKT2-2015-specific nodes
    for (Object child : root) {
      if (child instanceof List) {
        List<Object> childList = (List<Object>) child;
        if (!childList.isEmpty() && "CS".equals(childList.get(0))) {
          return WKTVersion.WKT2_2015;
        }
      }
    }

    // Check for BOUNDCRS, PROJCRS, or GEOGCRS
    if (!root.isEmpty()) {
      String rootType = String.valueOf(root.get(0));
      if ("BOUNDCRS".equals(rootType) || "PROJCRS".equals(rootType) || "GEOGCRS".equals(rootType)) {
        return WKTVersion.WKT2_2015;
      }
    }

    // Default to WKT2-2015 if no specific indicators are found
    return WKTVersion.WKT2_2015;
  }

  /**
   * Builds a PROJJSON object from a WKT2 array structure.
   *
   * @param root the root WKT array node
   * @return the PROJJSON map
   */
  public static Map<String, Object> build(List<Object> root) {
    WKTVersion version = detectWKT2Version(root);

    if (version == WKTVersion.WKT2_2019) {
      return PROJJSONBuilder2019.convert(root);
    } else {
      return PROJJSONBuilder2015.convert(root);
    }
  }
}
