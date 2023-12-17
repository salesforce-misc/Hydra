plugins {
  id("hydra.kt-conventions")
  id("hydra.consumer.sample-conventions")
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
    jreVersion = JavaVersion.VERSION_21
    localImageName = "ktor-arrow-example"
    imageTag = "latest"
  }
}

sqldelight {
  databases {
    create("SqlDelight") {
      packageName.set("org.revcloud.quote.sqldelight")
      dialect(libs.sqldelight.postgresql.get())
    }
  }
}

tasks {
  withType<PublishToMavenRepository>().configureEach { enabled = false }
  withType<PublishToMavenLocal>().configureEach { enabled = false }
}
