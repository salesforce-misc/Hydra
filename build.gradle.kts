import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.report.ReportMergeTask

plugins {
  id("hydra.sub-conventions")
  id("hydra.kt-conventions")
  id(libs.plugins.kover.pluginId)
  id(libs.plugins.detekt.pluginId) apply false
}
allprojects {
  apply(plugin = "hydra.root-conventions")
}
dependencies {
  compileOnly(libs.jetbrains.annotations)
  implementation(libs.bundles.kotlin.logging)
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
val detektReportMerge by tasks.registering(ReportMergeTask::class) {
  output.set(rootProject.buildDir.resolve("reports/detekt/merge.xml"))
}
subprojects {
  apply(plugin = "hydra.sub-conventions")
  tasks.withType<Detekt>().configureEach {
    reports {
      xml.required = true
      html.required = true
    }
  }
  plugins.withType<DetektPlugin> {
    tasks.withType<Detekt> detekt@{
      finalizedBy(detektReportMerge)
      detektReportMerge.configure {
        input.from(this@detekt.xmlReportFile)
      }
    }
  }
}
