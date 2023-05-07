package org.revcloud.app.env

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.*
import kotlinx.serialization.json.Json
import org.revcloud.app.routes.eventRoutes
import org.revcloud.app.routes.health
import org.revcloud.app.routes.rabbitConsumers
import pl.jutupe.ktor_rabbitmq.RabbitMQ
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import kotlin.time.Duration.Companion.days

fun Application.configure(rabbitMQInstanceToConfigure: RabbitMQInstance) {
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
    rabbitMQInstance = rabbitMQInstanceToConfigure.apply { enableLogging() }
  }
}

fun Application.app(module: Dependencies) {
  configure(module.rabbitMQInstance)
  with(module.statePersistence) {
    health(module.healthCheck)
    eventRoutes()
  }
  with(module.matterMachine) {
    rabbitConsumers()
  }
}
