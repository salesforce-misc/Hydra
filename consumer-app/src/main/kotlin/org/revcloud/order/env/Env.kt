package org.revcloud.order.env

private const val PORT: Int = 7070
private const val JDBC_URL: String = "jdbc:postgresql://localhost:5432/hydra"
private const val JDBC_USER: String = "postgres"
private const val JDBC_PW: String = "postgres"
private const val JDBC_DRIVER: String = "org.postgresql.Driver"
private const val RABBIT_MQ_URI = "amqp://guest:guest@localhost:5672"

data class Env(
  val dataSource: DataSource = DataSource(),
  val http: Http = Http(),
  val rabbitMQ: RabbitMQ = RabbitMQ()
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

  data class RabbitMQ(
    val uri: String = System.getenv("RABBIT_MQ_URI") ?: RABBIT_MQ_URI,
    val exchange: String = "hydra",
    val queue: String = "hydra",
    val routingKey: String = "hydra"
  )
}
