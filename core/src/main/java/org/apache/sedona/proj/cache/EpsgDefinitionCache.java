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
package org.apache.sedona.proj.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sedona.proj.projjson.ProjJsonDefinition;
import org.apache.sedona.proj.projjson.ProjJsonParser;

/**
 * Cache for EPSG code definitions fetched from spatialreference.org. This prevents redundant
 * network calls for the same EPSG codes.
 */
public class EpsgDefinitionCache {

  // Base URL for spatialreference.org PROJJSON API
  private static final String SPATIAL_REFERENCE_URL = "https://spatialreference.org/ref/epsg/";

  // Cache for EPSG code -> PROJ string mappings
  private static final Map<String, String> DEFINITION_CACHE = new ConcurrentHashMap<>();

  // Object mapper for parsing PROJJSON
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Gets a PROJ string definition for an EPSG code, fetching from spatialreference.org if not
   * cached.
   *
   * @param epsgCode the EPSG code (e.g., "EPSG:4326" or "4326")
   * @return the PROJ string definition
   * @throws IOException if fetching or parsing fails
   */
  public static String getDefinition(String epsgCode) throws IOException {
    if (epsgCode == null || epsgCode.isEmpty()) {
      throw new IllegalArgumentException("EPSG code cannot be null or empty");
    }

    // Normalize EPSG code (remove "EPSG:" prefix if present)
    String normalizedCode = epsgCode.startsWith("EPSG:") ? epsgCode.substring(5) : epsgCode;

    // Check cache first
    if (DEFINITION_CACHE.containsKey(epsgCode)) {
      return DEFINITION_CACHE.get(epsgCode);
    }

    // Fetch from spatialreference.org
    String projString = fetchFromSpatialReference(normalizedCode);

    // Cache the result (use original epsgCode as key to maintain format)
    DEFINITION_CACHE.put(epsgCode, projString);

    return projString;
  }

  /**
   * Fetches PROJJSON from spatialreference.org and converts to PROJ string.
   *
   * @param epsgCodeNumber the EPSG code number (e.g., "4326")
   * @return the PROJ string definition
   * @throws IOException if fetching or parsing fails
   */
  private static String fetchFromSpatialReference(String epsgCodeNumber) throws IOException {
    // Construct URL
    String url = SPATIAL_REFERENCE_URL + epsgCodeNumber + "/projjson.json";

    // Fetch PROJJSON
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(5000); // 5 second timeout
    conn.setReadTimeout(5000);

    int responseCode = conn.getResponseCode();
    if (responseCode != 200) {
      throw new IOException(
          "EPSG code not found: EPSG:"
              + epsgCodeNumber
              + " (HTTP "
              + responseCode
              + ") from "
              + url);
    }

    // Read response
    StringBuilder response = new StringBuilder();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
    }

    // Parse PROJJSON
    ProjJsonDefinition projJsonDef =
        OBJECT_MAPPER.readValue(response.toString(), ProjJsonDefinition.class);

    // Convert to PROJ string
    return ProjJsonParser.toProjString(projJsonDef);
  }

  /**
   * Checks if an EPSG code is cached.
   *
   * @param epsgCode the EPSG code
   * @return true if cached
   */
  public static boolean isCached(String epsgCode) {
    return DEFINITION_CACHE.containsKey(epsgCode);
  }

  /**
   * Removes an EPSG code from the cache.
   *
   * @param epsgCode the EPSG code
   * @return the removed definition, or null if not found
   */
  public static String removeFromCache(String epsgCode) {
    return DEFINITION_CACHE.remove(epsgCode);
  }

  /** Clears the EPSG definition cache. */
  public static void clearCache() {
    DEFINITION_CACHE.clear();
  }

  /**
   * Gets the number of cached EPSG definitions.
   *
   * @return the cache size
   */
  public static int getCacheSize() {
    return DEFINITION_CACHE.size();
  }

  /**
   * Gets all cached EPSG codes.
   *
   * @return array of cached EPSG codes
   */
  public static String[] getCachedEpsgCodes() {
    return DEFINITION_CACHE.keySet().toArray(new String[0]);
  }

  /**
   * Pre-loads common EPSG codes into the cache to avoid network calls. This can be called at
   * application startup.
   */
  public static void preloadCommonCodes() {
    // Note: Hardcoded EPSG codes (4326, 3857, 4269, UTM zones) are handled
    // directly in Projection class and don't need network fetching
    // This method is provided for future use if additional codes need pre-loading
  }
}
