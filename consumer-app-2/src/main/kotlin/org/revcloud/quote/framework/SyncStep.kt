package org.revcloud.quote.framework

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence

context(Hydra<StateT, EventT, ActionT>, StatePersistence, Env, KLogger)
abstract class SyncStep<StateT : Any, EventT : Any, ActionT : Any> {
  protected abstract val stateType: Class<StateT>

  protected abstract fun handleEvent(event: EventT): EventT?

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
      }
      .getOrNull()
      ?.event
  }

  private fun persistState() {
    val quote: StateT =
      state // * NOTE 08/05/23 gopala.akshintala: This is needed for encoding below
    runBlocking { insert(Json.encodeToString(Json.serializersModule.serializer(stateType), quote)) }
  }
}
