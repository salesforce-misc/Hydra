/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.integration.order.java.OrderDomain.OrderAction;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Concurrent recording sink for performed {@link OrderAction}s, keyed by runId so the test can
 * assert exactly-once across crash/resume. The legitimate mutation island in this PoC.
 */
final class Effects {

  record Entry(String runId, OrderAction action) {}

  final List<Entry> performed = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> counts = new ConcurrentHashMap<>();

  void perform(String runId, OrderAction action) {
    performed.add(new Entry(runId, action));
    counts.merge(action.getClass().getSimpleName() + ":" + runId, 1, Integer::sum);
  }

  int count(String runId, String actionType) {
    return counts.getOrDefault(actionType + ":" + runId, 0);
  }

  List<OrderAction> actionsFor(String runId) {
    return performed.stream()
        .filter(entry -> entry.runId().equals(runId))
        .map(Entry::action)
        .toList();
  }
}
