plugins { id("io.ktor.plugin") }

val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  implementation(libs.rabbitMQ)
  implementation(libs.ktorRabbitMQ)
}
