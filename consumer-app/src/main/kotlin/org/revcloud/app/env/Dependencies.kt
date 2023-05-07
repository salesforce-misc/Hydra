package org.revcloud.app.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import mu.KotlinLogging
import org.revcloud.app.repo.StatePersistence
import org.revcloud.app.repo.statePersistence
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import pl.jutupe.ktor_rabbitmq.RabbitMQConfiguration
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish
import kotlin.time.Duration.Companion.seconds

class Dependencies(
  val statePersistence: StatePersistence,
  val rabbitMQInstance: RabbitMQInstance,
  val matterMachine: Hydra<Matter, Action, SideEffect>,
  val healthCheck: HealthCheckRegistry
)

context(ResourceScope)
suspend fun dependencies(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val healthCheck = HealthCheckRegistry(Dispatchers.Default) { register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds) }
  val sqlDelight = sqlDelight(hikari)
  val rabbitMQInstance = RabbitMQInstance(RabbitMQConfiguration.create().apply {
      uri = env.rabbitMQ.uri
      connectionName = "hydra"
      serialize {
        if (it.javaClass.superclass.kotlin.isSealed) {
          Json.encodeToString(Json.serializersModule.serializer(it.javaClass.superclass), it).toByteArray()
        } else {
          Json.encodeToString(Json.serializersModule.serializer(it.javaClass), it).toByteArray()
        }
      }
      deserialize { bytes, type ->
        Json.decodeFromString(Json.serializersModule.serializer(type.javaObjectType), bytes.decodeToString())
      }
      initialize {
        exchangeDeclare("exchange", "direct", true)
        queueDeclare("queue", true, false, false, emptyMap())
        queueBind("queue", "exchange", "routingKey")
      }
    })
  val matterMachine = Hydra.create<Matter, Action, SideEffect> {
    val logger = KotlinLogging.logger {}
    initialState(Matter.Solid)
    state<Matter.Solid> {
      on<Action.OnMelted> {
        transitionTo(Matter.Liquid, SideEffect.LogMelted)
      }
    }
    state<Matter.Liquid> {
      on<Action.OnFrozen> {
        transitionTo(Matter.Solid, SideEffect.LogFrozen)
      }
      on<Action.OnVaporized> {
        transitionTo(Matter.Gas, SideEffect.LogVaporized)
      }
    }
    state<Matter.Gas> {
      on<Action.OnCondensed> {
        transitionTo(Matter.Liquid, SideEffect.LogCondensed)
      }
    }
    onTransition {
      val validTransition = it as? Transition.Valid ?: return@onTransition
      when (validTransition.sideEffect) {
        SideEffect.LogMelted -> {
          logger.info { ON_MELTED_MESSAGE }
          rabbitMQInstance.publish("exchange", "routingKey", null, Action.OnVaporized)
        }
        SideEffect.LogFrozen -> {
          logger.info { ON_FROZEN_MESSAGE }
          logger.info { "I am Solid again" }
        }
        SideEffect.LogVaporized -> {
          logger.info { ON_VAPORIZED_MESSAGE }
          rabbitMQInstance.publish("exchange", "routingKey", null, Action.OnCondensed)
        }
        SideEffect.LogCondensed -> {
          logger.info { ON_CONDENSED_MESSAGE }
          rabbitMQInstance.publish("exchange", "routingKey", null, Action.OnFrozen)
        }
        else -> logger.error { "Invalid SideEffect" }
      }
    }
  }

  return Dependencies(
    statePersistence(sqlDelight.stateQueries),
    rabbitMQInstance,
    matterMachine,
    healthCheck
  )
}
