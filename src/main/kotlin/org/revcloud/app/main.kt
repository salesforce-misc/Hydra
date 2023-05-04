package org.revcloud.app

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.github.nomisrev.routes.health
import org.revcloud.app.routes.eventRoutes
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.awaitCancellation
import org.revcloud.app.env.Dependencies
import org.revcloud.app.env.Env
import org.revcloud.app.env.configure
import org.revcloud.app.env.dependencies

fun main(): Unit = SuspendApp {
  val env = Env()
  resourceScope {
    val dependencies = dependencies(env)
    server(Netty, host = env.http.host, port = env.http.port) {
      app(dependencies)
    }
    awaitCancellation()
  }
}

fun Application.app(module: Dependencies) {
  configure()
  health(module.healthCheck)
  eventRoutes()
}
