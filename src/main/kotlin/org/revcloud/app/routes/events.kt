package org.revcloud.app.routes

import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.effect
import arrow.core.raise.fold
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import org.revcloud.app.repo.StatePersistence
import org.revcloud.app.service.insertAndGetId


@Serializable
data class State(val state: String)


context(Application, StatePersistence)
fun eventRoutes() = routing {
  get {
    call.respond(HttpStatusCode.OK, "Hail Hydra!")
  }

  post("/event") {
    respond(HttpStatusCode.Created) {
      val state = receiveCatching<State>().state
      insertAndGetId(state).serial
    }
  }
}

typealias KtorCtx = PipelineContext<Unit, ApplicationCall>

context(KtorCtx)
suspend inline fun <reified A : Any> respond(
  status: HttpStatusCode,
  crossinline block: suspend context(Raise<DomainError>) () -> A
): Unit = effect {
  block(this)
}.fold({ call.respond(status, it) }, { call.respond(status, it) })

context(Raise<IncorrectJson>)
@OptIn(ExperimentalSerializationApi::class)
private suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>
  .receiveCatching(): A =
  catch({ call.receive() }) { e: MissingFieldException -> raise(IncorrectJson(e)) }

sealed interface DomainError

sealed interface ValidationError : DomainError

@OptIn(ExperimentalSerializationApi::class)
data class IncorrectJson(val exception: MissingFieldException) : ValidationError
