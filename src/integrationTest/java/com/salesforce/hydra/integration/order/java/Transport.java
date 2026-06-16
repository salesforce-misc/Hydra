/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.MessageProperties;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderEvent;
import java.io.IOException;
import java.util.function.Predicate;

/**
 * Thin RabbitMQ publish/consume + Jackson codec for {@link StepMessage}.
 *
 * <p>Note: {@code fromState} is deliberately NOT carried in the message — the worker reads the live
 * fromState from the durable cursor (the DB is the single source of truth). The wire only carries
 * the event that just occurred and the {@code expectedVersion} the publisher believed was current;
 * the worker uses {@code expectedVersion} to detect a crash-after-advance redelivery and skip
 * re-performing the side effect.
 */
final class Transport {

  /** One unit of work on the wire. */
  record StepMessage(String runId, OrderEvent event, long expectedVersion) {}

  /** Distinct from the Kotlin "order.steps" so the two suites never cross-consume. */
  static final String ORDER_QUEUE = "order.steps.java";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Connection connection;

  Transport(Connection connection) {
    this.connection = connection;
  }

  void declareQueue() {
    try (final var channel = connection.createChannel()) {
      channel.queueDeclare(ORDER_QUEUE, /* durable= */ true, false, false, null);
    } catch (IOException | java.util.concurrent.TimeoutException ioOrTimeout) {
      throw new RuntimeException(ioOrTimeout);
    }
  }

  void publish(StepMessage message) {
    try (final var channel = connection.createChannel()) {
      final var body = JSON.writeValueAsBytes(message);
      channel.basicPublish("", ORDER_QUEUE, MessageProperties.PERSISTENT_TEXT_PLAIN, body);
    } catch (IOException | java.util.concurrent.TimeoutException ioOrTimeout) {
      throw new RuntimeException(ioOrTimeout);
    }
  }

  /**
   * Manual-ack consumer. The {@code handler} runs on RabbitMQ's own client thread; its boolean
   * return is the ack/nack signal (true → ack, false → nack with requeue). Any RuntimeException
   * thrown by {@code handler} — including the test's {@code SimulatedCrash} — is treated as {@code
   * false} so the message redelivers. Returns the {@link Channel} so a test can close it to halt
   * the consumer.
   */
  Channel consume(Predicate<StepMessage> handler) {
    try {
      final var channel = connection.createChannel();
      channel.basicQos(1);
      final DeliverCallback deliver =
          (consumerTag, delivery) -> {
            final var ack = decodeAndHandle(delivery.getBody(), handler);
            if (ack) {
              channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } else {
              channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
          };
      final CancelCallback cancel = consumerTag -> {};
      channel.basicConsume(ORDER_QUEUE, /* autoAck= */ false, deliver, cancel);
      return channel;
    } catch (IOException ioException) {
      throw new RuntimeException(ioException);
    }
  }

  private static boolean decodeAndHandle(byte[] body, Predicate<StepMessage> handler) {
    try {
      final var message = JSON.readValue(body, StepMessage.class);
      try {
        return handler.test(message);
      } catch (RuntimeException runtimeException) {
        return false;
      }
    } catch (IOException ioException) {
      throw new RuntimeException(ioException);
    }
  }
}
