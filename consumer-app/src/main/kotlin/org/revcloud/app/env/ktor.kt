package org.revcloud.app.env

import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.serializersModule
import kotlinx.serialization.serializer
import pl.jutupe.ktor_rabbitmq.RabbitMQ

@OptIn(ExperimentalSerializationApi::class)
fun Application.configure() {
  install(DefaultHeaders)
  install(ContentNegotiation) {
    json(Json {
      prettyPrint = true
      isLenient = true
    })
  }
  install(CORS) {
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowNonSimpleContentTypes = true
    maxAgeDuration = 3.days
  }
  install(RabbitMQ) {
    uri = "amqp://guest:guest@localhost:5672"
    connectionName = "hydra"
    enableLogging()
    serialize {
      if (it.javaClass.superclass.kotlin.isSealed) {
        Json.encodeToString(serializersModule.serializer(it.javaClass.superclass), it).toByteArray()  
      } else {
        Json.encodeToString(serializersModule.serializer(it.javaClass), it).toByteArray()  
      }
    }
    deserialize { bytes, type ->
      Json.decodeFromString(serializersModule.serializer(type.javaObjectType), bytes.decodeToString())
    }
    initialize {
      exchangeDeclare("exchange", "direct", true)
      queueDeclare("queue", true, false, false, emptyMap())
      queueBind("queue", "exchange", "routingKey")
    }
  }
}
