/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.salesforce.hydra.statemachine.Transition

/** Test hook: forces a redelivery AFTER the durable advance, BEFORE the side effect / enqueue. */
class SimulatedCrash(message: String) : RuntimeException(message)

/**
 * Single-message dispatcher implementing the crash-resume protocol.
 *
 * The exactly-once guarantee for side effects rests on a strict ordering:
 * 1. CAS-advance the durable cursor (the DB is the SOURCE OF TRUTH).
 * 2. THEN perform the action.
 * 3. THEN enqueue the next step.
 *
 * Two redelivery shapes have to be handled WITHOUT re-performing the action:
 * - **Crash-after-advance**: a prior worker won the CAS but died before enqueueing the next step.
 *   On redelivery, `cursor.version > message.expectedVersion`, so we re-derive and re-enqueue from
 *   the persisted state and skip the effect.
 * - **CAS-lost race**: another worker advanced past us in the same generation. We resume from the
 *   current durable truth and skip the effect.
 *
 * The action is performed ONLY on the fresh-step path (step 7 in [dispatch]) AFTER a successful CAS
 * — never on either resume branch.
 */
class Orchestrator(
  private val store: CursorStore,
  private val transport: Transport,
  private val effects: Effects,
  private val crashAfterAdvance: (runId: String, toState: OrderState) -> Unit = { _, _ -> },
) {
  private val hydra = orderMachine()

  /** Returns true → ack the message, false → nack-requeue. */
  fun dispatch(message: StepMessage): Boolean {
    // 1) Unknown run → drop (ack).
    val cursor = store.load(message.runId) ?: return true

    // 2) RESUME — crash-after-advance: persisted state is already past this message's generation.
    //    Re-derive & re-enqueue the next step from the durable state; do NOT re-perform the effect.
    if (cursor.version > message.expectedVersion) {
      enqueueNext(message.runId, cursor)
      return true
    }

    // 3) Run is already terminal — nothing to do.
    if (cursor.status != RunStatus.RUNNING) return true

    // 4) Read the transition off the persisted state. Invalid → fail the run durably.
    val transition = hydra.readTransitionAndNotifyListeners(cursor.state, message.event)
    if (transition !is Transition.Valid) {
      store.advance(
        runId = message.runId,
        expectedVersion = cursor.version,
        newState = Cancelled(reason = "invalid: ${message.event}"),
        newStatus = RunStatus.FAILED,
      )
      return true
    }

    // 5) CAS-advance to the next state. A null return means another worker advanced first —
    //    resume from current truth and skip the effect.
    val toState: OrderState = transition.toState
    val advanced =
      store.advance(
        runId = message.runId,
        expectedVersion = cursor.version,
        newState = toState,
        newStatus = statusFor(toState),
      )
        ?: run {
          store.load(message.runId)?.let { enqueueNext(message.runId, it) }
          return true
        }

    // 6) Test hook — forces a crash AFTER durable advance, BEFORE side effect / enqueue.
    crashAfterAdvance(message.runId, toState)

    // 7) Fresh-step path: NOW perform the effect. Exactly-once hinges on this being unreachable
    //    on either resume branch above.
    transition.action?.let { effects.perform(message.runId, it) }

    // 8) Enqueue the next step from the advanced cursor.
    enqueueNext(message.runId, advanced)
    return true
  }

  /** Publish the successful "doer" event for the next state, if any (terminal states stop here). */
  private fun enqueueNext(runId: String, cursor: Cursor) {
    nextEventFor(cursor.state)?.let { event ->
      transport.publish(StepMessage(runId = runId, event = event, expectedVersion = cursor.version))
    }
  }

  private fun statusFor(state: OrderState): RunStatus =
    when (state) {
      is Completed -> RunStatus.COMPLETED
      is Cancelled -> RunStatus.CANCELLED
      else -> RunStatus.RUNNING
    }

  /**
   * Models the successful "doer" event for steps reached AFTER the seed: Reserving→Reserved,
   * Charging→Charged, Shipping→Shipped, Notifying→Notified. Validating is driven by the seed event
   * from the test, and Completed/Cancelled are terminal.
   */
  private fun nextEventFor(state: OrderState): OrderEvent? =
    when (state) {
      is Reserving -> Reserved
      is Charging -> Charged
      is Shipping -> Shipped
      is Notifying -> Notified
      else -> null
    }
}
