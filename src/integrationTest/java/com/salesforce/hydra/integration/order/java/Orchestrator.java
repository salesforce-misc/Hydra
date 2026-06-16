/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.Hydra;
import com.salesforce.hydra.integration.order.java.CursorStore.Cursor;
import com.salesforce.hydra.integration.order.java.CursorStore.RunStatus;
import com.salesforce.hydra.integration.order.java.OrderDomain.Cancelled;
import com.salesforce.hydra.integration.order.java.OrderDomain.Charged;
import com.salesforce.hydra.integration.order.java.OrderDomain.Charging;
import com.salesforce.hydra.integration.order.java.OrderDomain.Completed;
import com.salesforce.hydra.integration.order.java.OrderDomain.Notified;
import com.salesforce.hydra.integration.order.java.OrderDomain.Notifying;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderAction;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderEvent;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderState;
import com.salesforce.hydra.integration.order.java.OrderDomain.Reserved;
import com.salesforce.hydra.integration.order.java.OrderDomain.Reserving;
import com.salesforce.hydra.integration.order.java.OrderDomain.Shipped;
import com.salesforce.hydra.integration.order.java.OrderDomain.Shipping;
import com.salesforce.hydra.integration.order.java.Transport.StepMessage;
import com.salesforce.hydra.statemachine.Transition;
import java.util.function.BiConsumer;

/**
 * Single-message dispatcher implementing the crash-resume protocol.
 *
 * <p>The exactly-once guarantee for side effects rests on a strict ordering: (1) CAS-advance the
 * durable cursor (the DB is the SOURCE OF TRUTH), (2) THEN perform the action, (3) THEN enqueue the
 * next step.
 *
 * <p>Two redelivery shapes have to be handled WITHOUT re-performing the action:
 *
 * <ul>
 *   <li><b>Crash-after-advance</b>: a prior worker won the CAS but died before enqueueing the next
 *       step. On redelivery, {@code cursor.version > message.expectedVersion}, so we re-derive and
 *       re-enqueue from the persisted state and skip the effect.
 *   <li><b>CAS-lost race</b>: another worker advanced past us in the same generation. We resume
 *       from the current durable truth and skip the effect.
 * </ul>
 *
 * <p>The action is performed ONLY on the fresh-step path (step 7 in {@link #dispatch}) AFTER a
 * successful CAS — never on either resume branch.
 */
final class Orchestrator {

  /** Test hook: forces a redelivery AFTER the durable advance, BEFORE the side effect / enqueue. */
  static final class SimulatedCrash extends RuntimeException {
    SimulatedCrash(String message) {
      super(message);
    }
  }

  private final CursorStore store;
  private final Transport transport;
  private final Effects effects;
  private final BiConsumer<String, OrderState> crashAfterAdvance;
  private final Hydra<OrderState, OrderEvent, OrderAction> hydra = OrderMachine.create();

  Orchestrator(CursorStore store, Transport transport, Effects effects) {
    this(store, transport, effects, (runId, toState) -> {});
  }

  Orchestrator(
      CursorStore store,
      Transport transport,
      Effects effects,
      BiConsumer<String, OrderState> crashAfterAdvance) {
    this.store = store;
    this.transport = transport;
    this.effects = effects;
    this.crashAfterAdvance = crashAfterAdvance;
  }

  /** Returns true → ack the message, false → nack-requeue. */
  boolean dispatch(StepMessage message) {
    // 1) Unknown run → drop (ack).
    final var cursor = store.load(message.runId());
    if (cursor == null) {
      return true;
    }

    // 2) RESUME — crash-after-advance: persisted state is already past this message's generation.
    //    Re-derive & re-enqueue the next step from the durable state; do NOT re-perform the effect.
    if (cursor.version() > message.expectedVersion()) {
      enqueueNext(message.runId(), cursor);
      return true;
    }

    // 3) Run is already terminal — nothing to do.
    if (cursor.status() != RunStatus.RUNNING) {
      return true;
    }

    // 4) Read the transition off the persisted state. Invalid → fail the run durably.
    final var transition = hydra.readTransitionAndNotifyListeners(cursor.state(), message.event());
    if (!(transition instanceof Transition.Valid<OrderState, OrderEvent, OrderAction> valid)) {
      store.advance(
          message.runId(),
          cursor.version(),
          new Cancelled("invalid: " + message.event()),
          RunStatus.FAILED);
      return true;
    }

    // 5) CAS-advance to the next state. A null return means another worker advanced first —
    //    resume from current truth and skip the effect.
    final var toState = valid.getToState();
    final var advanced =
        store.advance(message.runId(), cursor.version(), toState, statusFor(toState));
    if (advanced == null) {
      final var current = store.load(message.runId());
      if (current != null) {
        enqueueNext(message.runId(), current);
      }
      return true;
    }

    // 6) Test hook — forces a crash AFTER durable advance, BEFORE side effect / enqueue.
    crashAfterAdvance.accept(message.runId(), toState);

    // 7) Fresh-step path: NOW perform the effect. Exactly-once hinges on this being unreachable
    //    on either resume branch above.
    final var action = valid.getAction();
    if (action != null) {
      effects.perform(message.runId(), action);
    }

    // 8) Enqueue the next step from the advanced cursor.
    enqueueNext(message.runId(), advanced);
    return true;
  }

  /** Publish the successful "doer" event for the next state, if any (terminal states stop here). */
  private void enqueueNext(String runId, Cursor cursor) {
    final var next = nextEventFor(cursor.state());
    if (next != null) {
      transport.publish(new StepMessage(runId, next, cursor.version()));
    }
  }

  private static RunStatus statusFor(OrderState state) {
    return switch (state) {
      case Completed completed -> RunStatus.COMPLETED;
      case Cancelled cancelled -> RunStatus.CANCELLED;
      default -> RunStatus.RUNNING;
    };
  }

  /**
   * Models the successful "doer" event for steps reached AFTER the seed: Reserving→Reserved,
   * Charging→Charged, Shipping→Shipped, Notifying→Notified. Validating is driven by the seed event
   * from the test, and Completed/Cancelled are terminal.
   */
  private static OrderEvent nextEventFor(OrderState state) {
    return switch (state) {
      case Reserving reserving -> new Reserved();
      case Charging charging -> new Charged();
      case Shipping shipping -> new Shipped();
      case Notifying notifying -> new Notified();
      default -> null;
    };
  }
}
