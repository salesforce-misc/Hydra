plugins {
  id("hydra.root-conventions")
  id("hydra.sub-conventions")
  id("hydra.kt-conventions")
  alias(libs.plugins.ktor)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.sqldelight)
  id(libs.plugins.detekt.pluginId) apply false
}
application {
  mainClass by "org.revcloud.app.MainKt"
  applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
}
dependencies {
  compileOnly(libs.jetbrains.annotations)

  implementation(libs.hikari)
  implementation(libs.sqldelight.jdbc)
  implementation(libs.postgresql)
  implementation(libs.bundles.arrow)
  implementation(libs.bundles.ktor.server)
  implementation(libs.bundles.suspendapp)
  implementation(libs.bundles.cohort)
  implementation(libs.bundles.kotlin.logging)

  testImplementation(libs.assertj.core)
}
sqldelight {
  databases {
    create("SqlDelight") {
      packageName.set("org.revcloud.hydra.sqldelight")
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
koverReport {
  defaults {
    xml {
      onCheck = true
    }
  }
}
