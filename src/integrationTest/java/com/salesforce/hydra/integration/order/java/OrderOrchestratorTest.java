/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.salesforce.hydra.integration.order.Infra;
import com.salesforce.hydra.integration.order.java.CursorStore.RunStatus;
import com.salesforce.hydra.integration.order.java.OrderDomain.Cancelled;
import com.salesforce.hydra.integration.order.java.OrderDomain.ChargePayment;
import com.salesforce.hydra.integration.order.java.OrderDomain.Completed;
import com.salesforce.hydra.integration.order.java.OrderDomain.NotifyCustomer;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderEvent;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderState;
import com.salesforce.hydra.integration.order.java.OrderDomain.OutOfStock;
import com.salesforce.hydra.integration.order.java.OrderDomain.ReserveInventory;
import com.salesforce.hydra.integration.order.java.OrderDomain.Reserving;
import com.salesforce.hydra.integration.order.java.OrderDomain.SendShipNotice;
import com.salesforce.hydra.integration.order.java.OrderDomain.ShipParcel;
import com.salesforce.hydra.integration.order.java.OrderDomain.Shipping;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validated;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validating;
import com.salesforce.hydra.integration.order.java.Transport.StepMessage;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proofs against live Postgres + RabbitMQ Testcontainers (shared with the Kotlin suite
 * via {@link Infra}) that the Java orchestrator's crash-resume protocol holds: happy path
 * completes, a mid-flight crash resumes from the durable cursor without re-performing effects, and
 * a failure event cancels the run.
 */
class OrderOrchestratorTest {

  private static final long POLL_INTERVAL_MS = 50L;

  private CursorStore store;
  private Transport transport;
  private Effects effects;

  @BeforeEach
  void setUp() {
    assumeTrue(
        Infra.INSTANCE.getDockerAvailable(), "Docker not available — skipping integration test");
    store = new CursorStore(Infra.INSTANCE.dataSource());
    store.createSchema();
    transport = new Transport(Infra.INSTANCE.rabbitConnection());
    transport.declareQueue();
    effects = new Effects();
  }

  @Test
  void scenario1_endToEndAcrossWorkers() {
    final var orchestrator = new Orchestrator(store, transport, effects);
    final var channel = transport.consume(orchestrator::dispatch);

    final var runId = seedRun(new Validating(4_999L, "1 Market St", "SKU-1", 1), new Validated());

    awaitStatus(runId, RunStatus.COMPLETED, 10_000L);
    closeChannel(channel);

    assertThat(effects.actionsFor(runId))
        .containsExactly(
            new ReserveInventory("SKU-1", 1),
            new ChargePayment(4_999L),
            new ShipParcel("1 Market St"),
            new SendShipNotice("1 Market St"))
        .inOrder();
    assertThat(store.load(runId).state()).isEqualTo(new Completed());
  }

  /**
   * Worker A's crash throws inside dispatch AFTER the CAS-advance into Shipping but BEFORE the
   * effect/enqueue → Transport nacks-requeue. When B receives the redelivery, {@code cursor.version
   * > expectedVersion} → resume branch → enqueue next from persisted Shipping → the run completes.
   * A never performed ShipParcel (threw before effects.perform); the resume branch never
   * re-performs prior effects → exactly-once ChargePayment.
   */
  @Test
  void scenario2_crashMidFlightResumesFromCursor() throws InterruptedException {
    final var crashArmed = new AtomicBoolean(true);
    final var crashingOrchestrator =
        new Orchestrator(
            store,
            transport,
            effects,
            (runId, toState) -> {
              if (crashArmed.get() && toState instanceof Shipping) {
                crashArmed.set(false);
                throw new Orchestrator.SimulatedCrash("die after advancing into Shipping");
              }
            });
    final var freshOrchestrator = new Orchestrator(store, transport, effects);

    final var channelA = transport.consume(crashingOrchestrator::dispatch);
    final var runId = seedRun(new Validating(4_999L, "1 Market St", "SKU-1", 1), new Validated());
    // Give A enough wall-time to walk Validating → Reserving → Charging → Shipping (crash).
    Thread.sleep(2_000L);
    closeChannel(channelA);

    final var channelB = transport.consume(freshOrchestrator::dispatch);
    awaitStatus(runId, RunStatus.COMPLETED, 10_000L);
    closeChannel(channelB);

    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(1);
    assertThat(store.load(runId).state()).isEqualTo(new Completed());
  }

  @Test
  void scenario3_failureEdgeCancels() {
    final var orchestrator = new Orchestrator(store, transport, effects);
    final var channel = transport.consume(orchestrator::dispatch);

    final var runId = UUID.randomUUID().toString();
    store.seed(runId, new Reserving(4_999L, "1 Market St", "SKU-1", 1));
    transport.publish(new StepMessage(runId, new OutOfStock(), 0L));

    awaitStatus(runId, RunStatus.CANCELLED, 10_000L);
    closeChannel(channel);

    assertThat(effects.actionsFor(runId)).containsExactly(new NotifyCustomer("out of stock"));
    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(0);
    assertThat(store.load(runId).state()).isEqualTo(new Cancelled("out of stock"));
  }

  private String seedRun(OrderState initial, OrderEvent firstEvent) {
    final var runId = UUID.randomUUID().toString();
    store.seed(runId, initial);
    transport.publish(new StepMessage(runId, firstEvent, 0L));
    return runId;
  }

  private void awaitStatus(String runId, RunStatus expected, long timeoutMs) {
    final var deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L;
    while (System.nanoTime() < deadlineNs) {
      final var cursor = store.load(runId);
      if (cursor != null && cursor.status() == expected) {
        return;
      }
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(interruptedException);
      }
    }
    throw new AssertionError(
        "timeout awaiting " + expected + " for " + runId + "; last cursor=" + store.load(runId));
  }

  private static void closeChannel(com.rabbitmq.client.Channel channel) {
    try {
      channel.close();
    } catch (Exception closeException) {
      throw new RuntimeException(closeException);
    }
  }
}
