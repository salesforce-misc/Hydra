package org.revcloud.app.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import kotlinx.coroutines.Dispatchers
import mu.KLogger
import mu.KotlinLogging
import org.revcloud.app.repo.StatePersistence
import org.revcloud.app.repo.statePersistence
import org.revcloud.hydra.Hydra
import kotlin.time.Duration.Companion.seconds

class Dependencies(
  val env: Env,
  val statePersistence: StatePersistence,
  val matterMachine: Hydra<Matter, Action, SideEffect>,
  val healthCheck: HealthCheckRegistry,
  val logger: KLogger
)

context(ResourceScope)
suspend fun init(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val healthCheck = HealthCheckRegistry(Dispatchers.Default) { register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds) }
  val sqlDelight = sqlDelight(hikari)
  val matterMachine = Hydra.create {
    initialState(Matter.Solid)
    state<Matter.Solid> {
      on<Action.Melt> {
        transitionTo(Matter.Liquid, SideEffect.Melted)
      }
    }
    state<Matter.Liquid> {
      on<Action.Freeze> {
        transitionTo(Matter.Solid, SideEffect.Frozen)
      }
      on<Action.Vaporize> {
        transitionTo(Matter.Gas, SideEffect.Vaporized)
      }
    }
    state<Matter.Gas> {
      on<Action.Condense> {
        transitionTo(Matter.Liquid, SideEffect.Condensed)
      }
    }
  }

  return Dependencies(
    env,
    statePersistence(sqlDelight.stateQueries),
    matterMachine,
    healthCheck,
    KotlinLogging.logger {}
  )
}
