/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.repo

import java.time.OffsetDateTime
import org.revcloud.quote.sqldelight.StateQueries

@JvmInline value class StateId(val serial: Long)

fun interface StatePersistence {
  /** Creates a new State in the database, and returns the [StateId] of the newly created State */
  suspend fun insert(state: String): StateId
}

fun statePersistence(stateQueries: StateQueries) = StatePersistence { state ->
  val createdAt = OffsetDateTime.now()
  stateQueries.insertAndGetId(state, createdAt, createdAt).executeAsOne()
}
