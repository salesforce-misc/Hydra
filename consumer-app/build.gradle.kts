plugins {
  id("hydra.kt-conventions")
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.sqldelight)
}
application {
  mainClass by "org.revcloud.app.MainKt"
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
}
dependencies {
  implementation(project(":hydra"))
  compileOnly(libs.jetbrains.annotations)
  implementation(libs.hikari)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.postgresql)
  implementation(libs.rabbitmq.client)
  implementation(libs.ktor.rabbitmq)
  implementation(libs.jackson.kotlin)
  implementation(libs.bundles.arrow)
  implementation(libs.bundles.ktor.server)
  implementation(libs.bundles.suspendapp)
  implementation(libs.bundles.cohort)
  implementation(libs.bundles.kotlin.logging)

  testImplementation(libs.assertj.core)
}
ktor {
  docker {
    jreVersion.set(io.ktor.plugin.features.JreVersion.JRE_17)
    localImageName.set("hydra-consumer-app")
    imageTag.set("latest")
  }
}
sqldelight {
  databases {
    create("SqlDelight") {
      packageName.set("org.revcloud.app.sqldelight")
      dialect(libs.sqldelight.postgresql.get())
    }
  }
}
testing {
  suites {
    val test by getting(JvmTestSuite::class) {
      useJUnitJupiter(libs.versions.junit.get())
    }
    val integrationTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation(libs.assertj.core)
      }
    }
  }
}
