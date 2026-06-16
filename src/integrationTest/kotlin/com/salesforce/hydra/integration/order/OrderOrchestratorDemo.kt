/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import java.util.UUID

/**
 * Runnable scenario-1 demo: spins up the same Postgres + RabbitMQ infra the integration tests use,
 * walks a single order from Validating to Completed, and prints the final cursor + effects trail.
 */
fun main() {
  require(Infra.dockerAvailable) { "Docker is required to run the demo" }

  val store = CursorStore(Infra.dataSource()).also { it.createSchema() }
  val transport = Transport(Infra.rabbitConnection()).also { it.declareQueue() }
  val effects = Effects()
  val orchestrator = Orchestrator(store, transport, effects)
  val channel = transport.consume(orchestrator::dispatch)

  val runId = UUID.randomUUID().toString()
  val initial = Validating(amountCents = 4_999, address = "1 Market St", sku = "SKU-1", qty = 1)
  store.seed(runId = runId, initial = initial)
  transport.publish(StepMessage(runId = runId, event = Validated, expectedVersion = 0L))

  val deadline = System.nanoTime() + 10_000L * 1_000_000L
  while (System.nanoTime() < deadline && store.load(runId)?.status != RunStatus.COMPLETED) {
    Thread.sleep(50)
  }
  channel.close()

  println("final cursor : ${store.load(runId)}")
  println("effects      : ${effects.actionsFor(runId)}")
}
