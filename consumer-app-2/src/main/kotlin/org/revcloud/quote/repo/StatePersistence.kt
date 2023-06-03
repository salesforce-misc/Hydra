package org.revcloud.quote.repo

import org.revcloud.quote.sqldelight.StateQueries
import java.time.OffsetDateTime

@JvmInline
value class StateId(val serial: Long)

fun interface StatePersistence {
  /** Creates a new State in the database, and returns the [StateId] of the newly created State */
  suspend fun insert(state: String): StateId
}

fun statePersistence(stateQueries: StateQueries) = StatePersistence { state ->
  val createdAt = OffsetDateTime.now()
  stateQueries.insertAndGetId(state, createdAt, createdAt).executeAsOne()
}
