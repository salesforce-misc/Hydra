package org.revcloud.app.repo

@JvmInline
value class StateId(val serial: Long)

interface StatePersistence {
  /** Creates a new State in the database, and returns the [StateId] of the newly created State */
  suspend fun insert(username: String, email: String, password: String): StateId
}

