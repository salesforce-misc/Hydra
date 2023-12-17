package org.revcloud.order.env

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
import kotlinx.serialization.serializer
import org.revcloud.order.routes.health
import org.revcloud.order.routes.orderRoutes
import org.revcloud.order.routes.rabbitConsumers
import pl.jutupe.ktor_rabbitmq.RabbitMQ

fun Application.app(module: Dependencies) {
  with(module.env) {
    configure()
    with(module.logger) {
      with(module.statePersistence) {
        health(module.healthCheck)
        orderRoutes()
        with(module.orderMachine) { rabbitConsumers() }
      }
    }
  }
}

context(Env)
fun Application.configure() {
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
  install(RabbitMQ) {
    uri = rabbitMQ.uri
    connectionName = "hydra"
    enableLogging()
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
      exchangeDeclare(rabbitMQ.exchange, "direct", true)
      queueDeclare("queue", true, false, false, emptyMap())
      queueBind("queue", "exchange", "routingKey")
    }
  }
}
