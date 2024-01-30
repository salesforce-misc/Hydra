package org.revcloud.quote.framework

import com.salesforce.hydra.Hydra
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.serializersModule
import kotlinx.serialization.serializer
import mu.KLogger
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish

context(Hydra<StateT, EventT, ActionT>, RabbitMQInstance, StatePersistence, Env, KLogger)
abstract class AsyncStep<StateT : Any, EventT : Any, ActionT : Any> {
  protected abstract val stateType: Class<StateT>

  protected abstract fun handleEvent(eventToPublish: EventT): EventT?

  fun execute(event: EventT): EventT? {
    info { "Consumed Event: $event and doing some pre stuff" }
    persistState()
    return runCatching {
        val exitEvent =
          handleEvent(event)!! // ! TODO 22/05/23 gopala.akshintala: Make it non-nullable
        transition(exitEvent) // * NOTE 22/05/23 gopala.akshintala: Callback
      }
      .also {
        info { "Doing post stuff" }
        persistState()
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, event)
      }
      .getOrNull()
      ?.event
  }

  private fun persistState() {
    val quote: StateT =
      state // * NOTE 08/05/23 gopala.akshintala: This is needed for encoding below
    runBlocking { insert(Json.encodeToString(serializersModule.serializer(stateType), quote)) }
  }
}
