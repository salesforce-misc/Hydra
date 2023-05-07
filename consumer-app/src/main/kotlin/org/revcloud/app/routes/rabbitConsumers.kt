package org.revcloud.app.routes

import io.ktor.server.application.Application
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.rabbitConsumer

context(Application)
fun rabbitConsumers() = rabbitConsumer {
  consume<Action>("queue") { event ->
    println("Consumed Action $event")
  }
  consume<Matter>("queue") { matter ->
    println("Consumed Matter $matter")
    matterMachine.with { initialState(matter) }
  }
  consume<State>("queue") { state ->
    println("Consumed State $state")
  }
}


