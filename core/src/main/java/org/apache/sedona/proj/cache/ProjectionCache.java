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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sedona.proj.core.Projection;

/**
 * Thread-safe cache for Projection objects to avoid repeated parsing. This significantly improves
 * performance when the same projections are used repeatedly.
 */
public class ProjectionCache {
  private static final Map<String, Projection> CACHE = new ConcurrentHashMap<>();
  private static final int MAX_CACHE_SIZE = 1000;

  /**
   * Gets a projection from cache or creates and caches a new one.
   *
   * @param projString the PROJ string
   * @return the cached or newly created projection
   */
  public static Projection getProjection(String projString) {
    if (projString == null || projString.trim().isEmpty()) {
      return null;
    }

    // Check cache first
    Projection cached = CACHE.get(projString);
    if (cached != null) {
      return cached;
    }

    // Create new projection and cache it
    Projection projection = new Projection(projString);

    // Limit cache size to prevent memory issues
    if (CACHE.size() >= MAX_CACHE_SIZE) {
      // Simple eviction: clear half the cache
      CACHE.clear();
    }

    CACHE.put(projString, projection);
    return projection;
  }

  /** Clears the projection cache. */
  public static void clearCache() {
    CACHE.clear();
  }

  /**
   * Gets the current cache size.
   *
   * @return the number of cached projections
   */
  public static int getCacheSize() {
    return CACHE.size();
  }

  /**
   * Removes a specific projection from cache.
   *
   * @param projString the PROJ string to remove
   * @return the removed projection, or null if not found
   */
  public static Projection removeFromCache(String projString) {
    return CACHE.remove(projString);
  }
}
