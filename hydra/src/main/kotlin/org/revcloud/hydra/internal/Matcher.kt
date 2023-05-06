package org.revcloud.hydra.internal

class Matcher<T : Any, out R : T> private constructor(private val clazz: Class<R>) {

  private val predicates = mutableListOf<(T) -> Boolean>({ clazz.isInstance(it) })

  fun where(predicate: R.() -> Boolean): Matcher<T, R> = apply {
    predicates.add {
      @Suppress("UNCHECKED_CAST")
      (it as R).predicate()
    }
  }

  fun matches(value: T) = predicates.all { it(value) }

  companion object {
    @JvmStatic
    fun <T : Any, R : T> any(clazz: Class<R>): Matcher<T, R> = Matcher(clazz)

    @JvmStatic
    fun <T : Any, R : T> eq(value: R, clazz: Class<R>): Matcher<T, R> = any<T, R>(clazz).where { this == value }

    inline fun <T : Any, reified R : T> any(): Matcher<T, R> = any(R::class.java)

    inline fun <T : Any, reified R : T> eq(value: R): Matcher<T, R> = any<T, R>().where { this == value }
  }
}
