/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.integration.order.Infra;
import com.salesforce.hydra.integration.order.java.CursorStore.RunStatus;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validated;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validating;
import com.salesforce.hydra.integration.order.java.Transport.StepMessage;
import java.util.UUID;

/**
 * Runnable scenario-1 demo for the Java mirror: spins up the same Postgres + RabbitMQ infra the
 * integration tests use, walks a single order from Validating to Completed, and prints the final
 * cursor + effects trail.
 */
final class OrderOrchestratorDemo {

  private OrderOrchestratorDemo() {}

  public static void main(String[] args) throws Exception {
    if (!Infra.INSTANCE.getDockerAvailable()) {
      throw new IllegalStateException("Docker is required to run the demo");
    }

    final var store = new CursorStore(Infra.INSTANCE.dataSource());
    store.createSchema();
    final var transport = new Transport(Infra.INSTANCE.rabbitConnection());
    transport.declareQueue();
    final var effects = new Effects();
    final var orchestrator = new Orchestrator(store, transport, effects);
    final var channel = transport.consume(orchestrator::dispatch);

    final var runId = UUID.randomUUID().toString();
    final var initial = new Validating(4_999L, "1 Market St", "SKU-1", 1);
    store.seed(runId, initial);
    transport.publish(new StepMessage(runId, new Validated(), 0L));

    final var deadlineNs = System.nanoTime() + 10_000L * 1_000_000L;
    while (System.nanoTime() < deadlineNs) {
      final var cursor = store.load(runId);
      if (cursor != null && cursor.status() == RunStatus.COMPLETED) {
        break;
      }
      Thread.sleep(50L);
    }
    channel.close();

    System.out.println("final cursor : " + store.load(runId));
    System.out.println("effects      : " + effects.actionsFor(runId));
  }
}
