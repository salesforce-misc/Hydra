/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.env

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import pl.jutupe.ktor_rabbitmq.RabbitMQConfiguration
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

fun rabbitMqInstance(env: Env): RabbitMQInstance =
  RabbitMQInstance(
    RabbitMQConfiguration.create().apply {
      uri = env.rabbitMQ.uri
      connectionName = "hydra"
      serialize {
        if (it.javaClass.superclass.kotlin.isSealed) {
          Json.encodeToString(Json.serializersModule.serializer(it.javaClass.superclass), it)
            .toByteArray()
        } else {
          Json.encodeToString(Json.serializersModule.serializer(it.javaClass), it).toByteArray()
        }
      }
      deserialize { bytes, type ->
        Json.decodeFromString(
          Json.serializersModule.serializer(type.javaObjectType),
          bytes.decodeToString()
        )
      }
      initialize {
        exchangeDeclare(env.rabbitMQ.exchange, "direct", true)
        queueDeclare("queue", true, false, false, emptyMap())
        queueBind("queue", "exchange", "routingKey")
      }
    }
  )
