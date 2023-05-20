plugins {
  `kotlin-dsl`
}
repositories {
  mavenCentral()
  gradlePluginPortal()
  maven("https://jitpack.io")
  maven("https://oss.sonatype.org/content/repositories/snapshots")
}
dependencies {
  implementation(libs.kotlin.gradle)
  implementation(libs.spotless.gradle)
  implementation(libs.detekt.gradle)
  implementation(libs.kover.gradle)
  implementation(libs.spotbugs.gradle)
  implementation(libs.testLogger.gradle)
  // Consumer
  implementation(libs.ktor.gradle)
}
