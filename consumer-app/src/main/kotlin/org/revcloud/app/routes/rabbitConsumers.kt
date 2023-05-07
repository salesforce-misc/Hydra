package org.revcloud.app.routes

import io.ktor.server.application.Application
import org.revcloud.app.env.Action
import org.revcloud.app.env.Matter
import org.revcloud.app.env.SideEffect
import org.revcloud.hydra.Hydra
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.rabbitConsumer

context(Application, Hydra<Matter, Action, SideEffect>)
fun rabbitConsumers() = rabbitConsumer {
  consume<Action>("queue") { action ->
    println("Consumed Action $action")
    this@Hydra.transition(action)
  }
}


