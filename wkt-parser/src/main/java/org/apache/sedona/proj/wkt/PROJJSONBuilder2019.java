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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PROJJSON builder for WKT2-2019 format. Ported from the JavaScript wkt-parser
 * PROJJSONBuilder2019.js file.
 */
public class PROJJSONBuilder2019 extends PROJJSONBuilderBase {

  /**
   * Converts a WKT2-2019 node to PROJJSON.
   *
   * @param node the WKT node
   * @param result the result map to populate
   * @return the PROJJSON map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> convert(List<Object> node, Map<String, Object> result) {
    PROJJSONBuilderBase.convert(node, result);

    // Handle CS node for WKT2-2019
    List<Object> csNode = findNode(node, "CS");
    if (csNode != null && csNode.size() > 1) {
      Map<String, Object> coordSystem = new HashMap<>();
      coordSystem.put("subtype", csNode.get(1));
      coordSystem.put("axis", extractAxes(node));
      result.put("coordinate_system", coordSystem);
    }

    // Handle USAGE node for WKT2-2019
    List<Object> usageNode = findNode(node, "USAGE");
    if (usageNode != null) {
      Map<String, Object> usage = new HashMap<>();

      List<Object> scope = findNode(usageNode, "SCOPE");
      if (scope != null && scope.size() > 1) {
        usage.put("scope", scope.get(1));
      }

      List<Object> area = findNode(usageNode, "AREA");
      if (area != null && area.size() > 1) {
        usage.put("area", area.get(1));
      }

      List<Object> bbox = findNode(usageNode, "BBOX");
      if (bbox != null && bbox.size() > 1) {
        List<Object> bboxValues = new ArrayList<>();
        for (int i = 1; i < bbox.size(); i++) {
          bboxValues.add(bbox.get(i));
        }
        usage.put("bbox", bboxValues);
      }

      result.put("usage", usage);
    }

    return result;
  }

  /**
   * Converts a WKT2-2019 node to PROJJSON (convenience method without result parameter).
   *
   * @param node the WKT node
   * @return the PROJJSON map
   */
  public static Map<String, Object> convert(List<Object> node) {
    return convert(node, new HashMap<>());
  }
}
