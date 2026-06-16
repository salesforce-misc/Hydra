/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Concurrent recording sink for performed [OrderAction]s, keyed by runId so a test can assert
 * exactly-once across crash/resume. The legitimate mutation island in this PoC.
 */
class Effects {
  val performed: MutableList<Pair<String, OrderAction>> = CopyOnWriteArrayList()
  private val counts: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

  fun perform(runId: String, action: OrderAction) {
    performed.add(runId to action)
    counts.merge(action::class.simpleName + ":" + runId, 1, Int::plus)
  }

  fun count(runId: String, actionType: String): Int = counts.getOrDefault("$actionType:$runId", 0)

  fun actionsFor(runId: String): List<OrderAction> =
    performed.filter { it.first == runId }.map { it.second }
}
