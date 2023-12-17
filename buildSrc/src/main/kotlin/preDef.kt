import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

val Provider<PluginDependency>.pluginId: String
  get() = get().pluginId

infix fun <T> Property<T>.by(value: T) {
  set(value)
}

private fun VersionCatalog.getLibrary(library: String): Provider<MinimalExternalModuleDependency> =
  findLibrary(library).get()

private fun VersionCatalog.getBundle(bundle: String) = findBundle(bundle).get()

private fun VersionCatalog.getPlugin(plugin: String): Provider<PluginDependency> =
  findPlugin(plugin).get()

internal val VersionCatalog.kotestBundle: Provider<ExternalModuleDependencyBundle>
  get() = getBundle("kotest")

internal val VersionCatalog.rabbitMQ: Provider<MinimalExternalModuleDependency>
  get() = getLibrary("rabbitmq-client")
internal val VersionCatalog.ktorRabbitMQ: Provider<MinimalExternalModuleDependency>
  get() = getLibrary("ktor-rabbitmq")
