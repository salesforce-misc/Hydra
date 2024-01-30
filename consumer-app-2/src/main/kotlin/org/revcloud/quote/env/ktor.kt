/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.env

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import org.revcloud.quote.routes.health
import org.revcloud.quote.routes.quoteRoutes
import org.revcloud.quote.routes.rabbitConsumers
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

fun Application.app(module: Dependencies) {
  with(module.env) {
    configure(module.rabbitMq)
    with(module.logger) {
      with(module.statePersistence) {
        health(module.healthCheck)
        with(module.quoteMachine) {
          quoteRoutes()
          rabbitConsumers()
        }
      }
    }
  }
}

context(Env)
fun Application.configure(rabbitMq: RabbitMQInstance) {
  install(DefaultHeaders)
  install(ContentNegotiation) {
    json(
      Json {
        prettyPrint = true
        isLenient = true
      }
    )
  }
  install(CORS) {
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowNonSimpleContentTypes = true
    maxAgeDuration = 3.days
  }
  install(RabbitMQ) { rabbitMQInstance = rabbitMq }
}
