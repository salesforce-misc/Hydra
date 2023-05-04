package org.revcloud.app.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

context(Application)
fun eventRoutes() = routing {
  get {
    call.respond(HttpStatusCode.OK, "Hail Hydra!")
  }
}
