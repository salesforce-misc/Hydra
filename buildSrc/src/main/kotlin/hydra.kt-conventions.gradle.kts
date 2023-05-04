import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
}

val libs: VersionCatalog =
  extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  testImplementation(libs.kotestBundle)
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_11.toString()
      // ! -Xjvm-default=all" is needed for Immutables to work with Kotlin default methods
      // https://kotlinlang.org/docs/java-to-kotlin-interop.html#compatibility-modes-for-default-methods
      freeCompilerArgs += listOf("-Xjvm-default=all", "-jvm-target=11", "-Xcontext-receivers")
    }
  }
}
