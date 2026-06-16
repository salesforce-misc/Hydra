/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import javax.sql.DataSource
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer

/**
 * Shared real-broker, real-DB infra for the order-fulfillment integration tests.
 *
 * Backed by Testcontainers: a Postgres 16 container (durable cursor store) and a RabbitMQ 3.13
 * container (transport + effects sink). The containers are `by lazy` singletons so the same
 * Postgres + RabbitMQ instances are reused across the Kotlin and Java integration test suites (Java
 * consumers reach them through `Infra.INSTANCE.*`).
 */
object Infra {
  val dockerAvailable: Boolean by lazy {
    runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
  }

  val postgres: PostgreSQLContainer<*> by lazy {
    PostgreSQLContainer("postgres:16-alpine").also { it.start() }
  }

  val rabbit: RabbitMQContainer by lazy {
    RabbitMQContainer("rabbitmq:3.13-management-alpine").also { it.start() }
  }

  fun dataSource(): DataSource =
    PGSimpleDataSource().apply {
      setURL(postgres.jdbcUrl)
      user = postgres.username
      password = postgres.password
    }

  fun rabbitConnection(): Connection =
    ConnectionFactory()
      .apply {
        host = rabbit.host
        port = rabbit.amqpPort
        username = rabbit.adminUsername
        password = rabbit.adminPassword
      }
      .newConnection()
}
