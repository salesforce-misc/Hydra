/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.MessageProperties
import kotlinx.serialization.Serializable

/**
 * One unit of work on the wire.
 *
 * NOTE: `fromState` is deliberately NOT carried in the message — the worker reads the live
 * `fromState` from the durable cursor (the DB is the single source of truth). The wire only carries
 * the [event] that just occurred and the [expectedVersion] the publisher believed was current; the
 * worker uses [expectedVersion] to detect a crash-after-advance redelivery and skip re-performing
 * the side effect.
 */
@Serializable
data class StepMessage(val runId: String, val event: OrderEvent, val expectedVersion: Long)

const val ORDER_QUEUE: String = "order.steps"

/** Thin RabbitMQ publish/consume + sealed-class-aware codec for [StepMessage]. */
class Transport(private val connection: Connection) {

  fun declareQueue() {
    connection.createChannel().use { channel ->
      channel.queueDeclare(
        /* queue = */ ORDER_QUEUE,
        /* durable = */ true,
        /* exclusive = */ false,
        /* autoDelete = */ false,
        /* arguments = */ null,
      )
    }
  }

  fun publish(message: StepMessage) {
    val body = orderJson.encodeToString(StepMessage.serializer(), message).toByteArray()
    connection.createChannel().use { channel ->
      channel.basicPublish(
        /* exchange = */ "",
        /* routingKey = */ ORDER_QUEUE,
        MessageProperties.PERSISTENT_TEXT_PLAIN,
        body,
      )
    }
  }

  /**
   * Manual-ack consumer. The [handler] runs on RabbitMQ's own client thread; its Boolean return is
   * the ack/nack signal (true → ack, false → nack with requeue). Any exception thrown by [handler]
   * — including the test's `SimulatedCrash` — is treated as `false` so the message redelivers.
   * Returns the [Channel] so a test can close it to halt the consumer.
   */
  fun consume(handler: (StepMessage) -> Boolean): Channel {
    val channel = connection.createChannel()
    channel.basicQos(1)
    val deliver = DeliverCallback { _, delivery ->
      val message = orderJson.decodeFromString(StepMessage.serializer(), String(delivery.body))
      val ack = runCatching { handler(message) }.getOrDefault(false)
      if (ack) {
        channel.basicAck(delivery.envelope.deliveryTag, false)
      } else {
        channel.basicNack(delivery.envelope.deliveryTag, false, true)
      }
    }
    val cancel = CancelCallback { _ -> }
    channel.basicConsume(ORDER_QUEUE, /* autoAck= */ false, deliver, cancel)
    return channel
  }
}
