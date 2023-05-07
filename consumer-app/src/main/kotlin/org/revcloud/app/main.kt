package org.revcloud.app

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.ktor.server.netty.Netty
import kotlinx.coroutines.awaitCancellation
import org.revcloud.app.env.Env
import org.revcloud.app.env.app
import org.revcloud.app.env.init

fun main(): Unit = SuspendApp {
  val env = Env()
  resourceScope {
    val dependencies = init(env)
    server(Netty, host = env.http.host, port = env.http.port) {
      app(dependencies)
    }
    awaitCancellation()
  }
}
