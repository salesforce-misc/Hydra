/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.google.common.truth.Truth.assertThat
import com.rabbitmq.client.Connection
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * End-to-end proofs against live Postgres + RabbitMQ Testcontainers that the orchestrator's
 * crash-resume protocol holds: happy path completes, a mid-flight crash resumes from the durable
 * cursor without re-performing effects, and a failure event cancels the run.
 */
class OrderOrchestratorKtTest {

  private lateinit var store: CursorStore
  private lateinit var transport: Transport
  private lateinit var effects: Effects

  private val connection: Connection by lazy { Infra.rabbitConnection() }

  @BeforeEach
  fun setUp() {
    assumeTrue(Infra.dockerAvailable, "Docker not available — skipping integration test")
    store = CursorStore(Infra.dataSource()).also { it.createSchema() }
    transport = Transport(connection).also { it.declareQueue() }
    effects = Effects()
  }

  @Test
  fun scenario1_endToEndAcrossWorkers() {
    val orchestrator = Orchestrator(store, transport, effects)
    val channel = transport.consume(orchestrator::dispatch)

    val runId =
      seedRun(
        initial = Validating(amountCents = 4_999, address = "1 Market St", sku = "SKU-1", qty = 1),
        firstEvent = Validated,
      )

    awaitStatus(runId, RunStatus.COMPLETED)
    channel.close()

    assertThat(effects.actionsFor(runId))
      .containsExactly(
        ReserveInventory(sku = "SKU-1", qty = 1),
        ChargePayment(amountCents = 4_999),
        ShipParcel(address = "1 Market St"),
        SendShipNotice(address = "1 Market St"),
      )
      .inOrder()
    assertThat(store.load(runId)!!.state).isEqualTo(Completed)
  }

  /**
   * Worker A's crash throws inside dispatch AFTER the CAS-advance into Shipping but BEFORE the
   * effect/enqueue → Transport nacks-requeue. When B receives the redelivery, `cursor.version >
   * expectedVersion` → resume branch → enqueue next from persisted Shipping → the run completes. A
   * never performed ShipParcel (threw before effects.perform); the resume branch never re-performs
   * prior effects → exactly-once ChargePayment.
   */
  @Test
  fun scenario2_crashMidFlightResumesFromCursor() {
    val crashArmed = AtomicBoolean(true)
    val crashingOrchestrator =
      Orchestrator(store, transport, effects) { _, toState ->
        if (crashArmed.get() && toState is Shipping) {
          crashArmed.set(false)
          throw SimulatedCrash("die after advancing into Shipping")
        }
      }
    val freshOrchestrator = Orchestrator(store, transport, effects)

    val channelA = transport.consume(crashingOrchestrator::dispatch)
    val runId =
      seedRun(
        initial = Validating(amountCents = 4_999, address = "1 Market St", sku = "SKU-1", qty = 1),
        firstEvent = Validated,
      )
    // Give A enough wall-time to walk Validating → Reserving → Charging → Shipping (crash).
    Thread.sleep(2_000)
    channelA.close()

    val channelB = transport.consume(freshOrchestrator::dispatch)
    awaitStatus(runId, RunStatus.COMPLETED)
    channelB.close()

    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(1)
    assertThat(store.load(runId)!!.state).isEqualTo(Completed)
  }

  @Test
  fun scenario3_failureEdgeCancels() {
    val orchestrator = Orchestrator(store, transport, effects)
    val channel = transport.consume(orchestrator::dispatch)

    val runId = UUID.randomUUID().toString()
    store.seed(
      runId = runId,
      initial = Reserving(amountCents = 4_999, address = "1 Market St", sku = "SKU-1", qty = 1),
    )
    transport.publish(StepMessage(runId = runId, event = OutOfStock, expectedVersion = 0L))

    awaitStatus(runId, RunStatus.CANCELLED)
    channel.close()

    assertThat(effects.actionsFor(runId)).containsExactly(NotifyCustomer(reason = "out of stock"))
    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(0)
  }

  private fun seedRun(initial: OrderState, firstEvent: OrderEvent): String {
    val runId = UUID.randomUUID().toString()
    store.seed(runId = runId, initial = initial)
    transport.publish(StepMessage(runId = runId, event = firstEvent, expectedVersion = 0L))
    return runId
  }

  private fun awaitStatus(runId: String, expected: RunStatus, timeoutMs: Long = 10_000) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000L
    while (System.nanoTime() < deadline) {
      val cursor = store.load(runId)
      if (cursor?.status == expected) return
      Thread.sleep(POLL_INTERVAL_MS)
    }
    error("timeout awaiting $expected for $runId; last cursor=${store.load(runId)}")
  }

  private companion object {
    const val POLL_INTERVAL_MS: Long = 50
  }
}
