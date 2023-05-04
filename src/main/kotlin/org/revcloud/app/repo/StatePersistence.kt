package org.revcloud.app.repo

import org.revcloud.hydra.sqldelight.StateQueries
import java.time.OffsetDateTime

@JvmInline
value class StateId(val serial: Long)

interface StatePersistence {
  /** Creates a new State in the database, and returns the [StateId] of the newly created State */
  suspend fun insert(state: String): StateId
}

fun statePersistence(stateQueries: StateQueries) = object : StatePersistence {
  override suspend fun insert(state: String): StateId {
    val createdAt = OffsetDateTime.now()
    return stateQueries.insertAndGetId(state, createdAt, createdAt).executeAsOne()
  }
}
