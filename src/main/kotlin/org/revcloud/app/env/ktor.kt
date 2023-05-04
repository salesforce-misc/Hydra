package org.revcloud.app.env

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.*
import kotlin.time.Duration.Companion.days

fun Application.configure() {
  install(DefaultHeaders)
  install(CORS) {
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowNonSimpleContentTypes = true
    maxAgeDuration = 3.days
  }
}
