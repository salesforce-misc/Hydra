package org.revcloud.app.service

import org.revcloud.app.repo.StateId
import org.revcloud.app.repo.StatePersistence

context(StatePersistence)
suspend fun insertAndGetId(state: String): StateId = insert(state)
