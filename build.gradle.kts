plugins {
  id("hydra.root-conventions")
  id("hydra.sub-conventions")
  id("hydra.kt-conventions")
  id(libs.plugins.detekt.pluginId) apply false
}

dependencies {
  compileOnly(libs.jetbrains.annotations)

  testImplementation(libs.assertj.core)
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
