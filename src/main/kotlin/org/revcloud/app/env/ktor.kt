package org.revcloud.app.env

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.maxAgeDuration
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days

fun Application.configure() {
  install(DefaultHeaders)
  install(ContentNegotiation) {
    json(Json {
      prettyPrint = true
      isLenient = true
    })
  }
  install(CORS) {
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowNonSimpleContentTypes = true
    maxAgeDuration = 3.days
  }
}
