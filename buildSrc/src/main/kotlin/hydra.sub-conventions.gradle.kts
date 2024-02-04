/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

import com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA

plugins {
  java
  `maven-publish`
  signing
  id("org.jetbrains.kotlinx.kover")
  id("com.adarshr.test-logger")
}

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(11)) }
  withJavadocJar()
  withSourcesJar()
}

testing {
  suites {
    val test by getting(JvmTestSuite::class) { useJUnitJupiter("5.10.1") }
  }
}

