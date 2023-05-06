package org.revcloud.app.routes

import io.ktor.server.application.Application
import kotlinx.serialization.Serializable
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.rabbitConsumer



context(Application)
fun rabbitConsumers() = rabbitConsumer {
  consume<Event>("queue") { body ->
    println("Consumed Event $body")
  }
  consume<State>("queue") { body ->
    println("Consumed State $body")

  }
}

@Serializable
data class Event(val state: String)


