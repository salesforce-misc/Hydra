package org.revcloud.app.routes

import io.ktor.server.application.Application
import mu.KLogger
import org.revcloud.app.env.Action
import org.revcloud.app.env.Matter
import org.revcloud.app.env.ON_CONDENSED_MESSAGE
import org.revcloud.app.env.ON_FROZEN_MESSAGE
import org.revcloud.app.env.ON_MELTED_MESSAGE
import org.revcloud.app.env.ON_VAPORIZED_MESSAGE
import org.revcloud.app.env.SideEffect
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.publish
import pl.jutupe.ktor_rabbitmq.rabbitConsumer

context(Application, Hydra<Matter, Action, SideEffect>, KLogger)
fun rabbitConsumers() = rabbitConsumer {
  consume<Action>("queue") { action ->
    info { "Consumed Action $action ${Thread.currentThread()}" }
    val transition = transition(action)
    onTransition(transition)
  }
}

context(RabbitMQInstance, KLogger)
fun onTransition(transition: Transition<Matter, Action, SideEffect>) {
  val validTransition = transition as? Transition.Valid ?: return
  when (validTransition.sideEffect) {
    SideEffect.Melted -> {
      info { ON_MELTED_MESSAGE }
      publish("exchange", "routingKey", null, Action.Vaporize)
    }
    SideEffect.Frozen -> {
      info { ON_FROZEN_MESSAGE }
      info { "I am Solid again" }
    }
    SideEffect.Vaporized -> {
      info { ON_VAPORIZED_MESSAGE }
      publish("exchange", "routingKey", null, Action.Condense)
    }
    SideEffect.Condensed -> {
      info { ON_CONDENSED_MESSAGE }
      publish("exchange", "routingKey", null, Action.Freeze)
    }
    else -> error { "Invalid SideEffect" }
  }
}
