package org.revcloud.quote.env

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.closeable
import arrow.fx.coroutines.continuations.ResourceScope
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.OffsetDateTime
import javax.sql.DataSource
import org.revcloud.quote.repo.StateId
import org.revcloud.quote.sqldelight.SqlDelight
import org.revcloud.quote.sqldelight.State

suspend fun ResourceScope.hikari(env: Env.DataSource): HikariDataSource = autoCloseable {
  HikariDataSource(
    HikariConfig().apply {
      jdbcUrl = env.url
      username = env.username
      password = env.password
      driverClassName = env.driver
    }
  )
}

suspend fun ResourceScope.sqlDelight(dataSource: DataSource): SqlDelight {
  val driver = closeable { dataSource.asJdbcDriver() }
  SqlDelight.Schema.create(driver)
  return SqlDelight(
    driver,
    State.Adapter(stateIdAdapter, offsetDateTimeAdapter, offsetDateTimeAdapter)
  )
}

private val stateIdAdapter = columnAdapter(::StateId, StateId::serial)
private val offsetDateTimeAdapter = columnAdapter(OffsetDateTime::parse, OffsetDateTime::toString)

private inline fun <A : Any, B> columnAdapter(
  crossinline decode: (databaseValue: B) -> A,
  crossinline encode: (value: A) -> B
): ColumnAdapter<A, B> =
  object : ColumnAdapter<A, B> {
    override fun decode(databaseValue: B): A = decode(databaseValue)

    override fun encode(value: A): B = encode(value)
  }
