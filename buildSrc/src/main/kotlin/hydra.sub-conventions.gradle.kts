import com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA

plugins {
  java
  `maven-publish`
  signing
  id("com.adarshr.test-logger")
}
repositories {
  mavenCentral()
}
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}
tasks {
  testlogger.theme = MOCHA
  withType<Jar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
  withType<PublishToMavenRepository>().configureEach {
    doLast {
      logger.lifecycle("Successfully uploaded ${publication.groupId}:${publication.artifactId}:${publication.version} to ${repository.name}")
    }
  }
  withType<PublishToMavenLocal>().configureEach {
    doLast {
      logger.lifecycle("Successfully created ${publication.groupId}:${publication.artifactId}:${publication.version} in MavenLocal")
    }
  }
}
publishing {
  publications.create<MavenPublication>("hydra") {
    val subprojectJarName = tasks.jar.get().archiveBaseName.get()
    artifactId = if (subprojectJarName == "hydra-root") "hydra" else "hydra-$subprojectJarName"
    from(components["java"])
    pom {
      name.set(artifactId)
      description.set(project.description)
      url.set("https://git.soma.salesforce.com/CCSPayments/Hydra")
      licenses {
        license {
          name.set("The Apache License, Version 2.0")
          url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }
      developers {
        developer {
          id.set("gopala.akshintala@salesforce.com")
          name.set("Gopal S Akshintala")
          email.set("gopala.akshintala@salesforce.com")
        }
      }
      scm {
        connection.set("scm:git:https://git.soma.salesforce.com/ccspayments/Hydra")
        developerConnection.set("scm:git:git@git.soma.salesforce.com:ccspayments/Hydra.git")
        url.set("https://git.soma.salesforce.com/ccspayments/hydra")
      }
    }
  }
  repositories {
    maven {
      name = "Nexus"
      val releasesRepoUrl =
        uri("https://nexus.soma.salesforce.com/nexus/content/repositories/releases")
      val snapshotsRepoUrl =
        uri("https://nexus.soma.salesforce.com/nexus/content/repositories/snapshots")
      url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
      val nexusUsername: String by project
      val nexusPassword: String by project
      credentials {
        username = nexusUsername
        password = nexusPassword
      }
    }
  }
}
