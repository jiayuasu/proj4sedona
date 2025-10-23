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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PROJJSON builder for WKT2-2015 format. Ported from the JavaScript wkt-parser
 * PROJJSONBuilder2015.js file.
 */
public class PROJJSONBuilder2015 extends PROJJSONBuilderBase {

  /**
   * Converts a WKT2-2015 node to PROJJSON.
   *
   * @param node the WKT node
   * @param result the result map to populate
   * @return the PROJJSON map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> convert(List<Object> node, Map<String, Object> result) {
    PROJJSONBuilderBase.convert(node, result);

    // Skip CS and USAGE nodes for WKT2-2015
    if (result.containsKey("coordinate_system")) {
      Map<String, Object> coordSystem = (Map<String, Object>) result.get("coordinate_system");
      if ("Cartesian".equals(coordSystem.get("subtype"))) {
        result.remove("coordinate_system");
      }
    }
    if (result.containsKey("usage")) {
      result.remove("usage");
    }

    return result;
  }

  /**
   * Converts a WKT2-2015 node to PROJJSON (convenience method without result parameter).
   *
   * @param node the WKT node
   * @return the PROJJSON map
   */
  public static Map<String, Object> convert(List<Object> node) {
    return convert(node, new HashMap<>());
  }
}
