/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.internal

class Matcher<L : Any, out R : L> private constructor(private val clazz: Class<R>) {

  private val predicates = mutableListOf<(L) -> Boolean>({ clazz.isInstance(it) })

  fun where(predicate: R.() -> Boolean): Matcher<L, R> = apply {
    predicates.add { @Suppress("UNCHECKED_CAST") (it as R).predicate() }
  }

  fun matches(value: L) = predicates.all { it(value) }

  fun matches(clazz: Class<out L>) = clazz == this.clazz

  companion object {
    @JvmStatic fun <L : Any, R : L> any(clazz: Class<R>): Matcher<L, R> = Matcher(clazz)

    @JvmStatic
    fun <L : Any, R : L> eq(value: R, clazz: Class<R>): Matcher<L, R> =
      any<L, R>(clazz).where { this == value }

    inline fun <L : Any, reified R : L> any(): Matcher<L, R> = any(R::class.java)

    inline fun <L : Any, reified R : L> eq(value: R): Matcher<L, R> =
      any<L, R>().where { this == value }
  }
}
