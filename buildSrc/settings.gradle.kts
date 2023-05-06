dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../libs.versions.toml")) }
    }

  pluginManagement {
    repositories {
      mavenCentral()
      gradlePluginPortal()
      google()
      maven("https://jitpack.io")
      maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
  }
}
