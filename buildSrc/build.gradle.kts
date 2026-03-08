plugins {
	`kotlin-dsl`
}

repositories {
	gradlePluginPortal()
	mavenCentral()
}

kotlin {
	jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
	implementation(plugin(libs.plugins.kotlin.jvm))
	implementation(plugin(libs.plugins.kover))
}



fun DependencyHandlerScope.plugin(plugin: Provider<PluginDependency>): Provider<String> =
	plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }