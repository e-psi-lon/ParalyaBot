plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
	`java-test-fixtures`
}

kordEx {
	ignoreIncompatibleKotlinVersion = true // Temporary fix for KordEx until it's updated to support Kotlin 2.2.21+
}

i18n {
	bundle("paralyabot-common.strings", "fr.paralya.bot.common") {
		className = "I18n"
	}
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

fun DependencyHandlerScope.bothTestScopes(dependencyNotation: Any) {
	testImplementation(dependencyNotation)
	testFixturesImplementation(dependencyNotation)
}


dependencies {
	// Test dependencies (exposed to test fixtures)
	bothTestScopes(kotlin("test"))
	bothTestScopes(libs.koin.test) // For tests that involve Koin
	bothTestScopes(libs.mockk) // Allow mocking in tests
	bothTestScopes(libs.junit)

	// Main dependencies (used internally by the common module)
	implementation(libs.typesafe.config)
	implementation(libs.kotlinx.serialization.hocon)
	implementation(libs.kotlinx.serialization.cbor)

	// Exposed dependencies, for use in plugins
	api(libs.konform)
	api(libs.kordex.i18n.runtime)
}
