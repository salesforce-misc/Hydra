/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.report.ReportMergeTask

plugins {
  id(libs.plugins.kover.pluginId)
  id(libs.plugins.detekt.pluginId) apply false
}

allprojects { apply(plugin = "hydra.root-conventions") }

dependencies {
  kover(project(":hydra"))
  kover(project(":consumer-app"))
}

koverReport { defaults { xml { onCheck = true } } }

val detektReportMerge by
  tasks.registering(ReportMergeTask::class) {
    output.set(rootProject.layout.buildDirectory.file("reports/detekt/merge.xml"))
  }

subprojects {
  apply(plugin = "hydra.sub-conventions")
  tasks.withType<Detekt>().configureEach { reports { html.required = true } }
  plugins.withType<DetektPlugin> {
    tasks.withType<Detekt> detekt@{
      finalizedBy(detektReportMerge)
      detektReportMerge.configure { input.from(this@detekt.htmlReportFile) }
    }
  }
}
