package org.revcloud.app.env

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val PORT: Int = 7070
private const val JDBC_URL: String = "jdbc:postgresql://localhost:5432/hydra"
private const val JDBC_USER: String = "postgres"
private const val JDBC_PW: String = "postgres"
private const val JDBC_DRIVER: String = "org.postgresql.Driver"
private const val AUTH_SECRET: String = "MySuperStrongSecret"
private const val AUTH_ISSUER: String = "KtorArrowExampleIssuer"
private const val AUTH_DURATION: Int = 30

data class Env(
  val dataSource: DataSource = DataSource(),
  val http: Http = Http(),
  val auth: Auth = Auth(),
) {
  data class Http(
    val host: String = System.getenv("HOST") ?: "0.0.0.0",
    val port: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: PORT,
  )

  data class DataSource(
    val url: String = System.getenv("POSTGRES_URL") ?: JDBC_URL,
    val username: String = System.getenv("POSTGRES_USERNAME") ?: JDBC_USER,
    val password: String = System.getenv("POSTGRES_PASSWORD") ?: JDBC_PW,
    val driver: String = JDBC_DRIVER,
  )

  data class Auth(
    val secret: String = System.getenv("JWT_SECRET") ?: AUTH_SECRET,
    val issuer: String = System.getenv("JWT_ISSUER") ?: AUTH_ISSUER,
    val duration: Duration = (System.getenv("JWT_DURATION")?.toIntOrNull() ?: AUTH_DURATION).days
  )
}
