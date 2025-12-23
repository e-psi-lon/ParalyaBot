plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
	`java-test-fixtures`
}

i18n {
	bundle("paralyabot-common.strings", "fr.paralya.bot.common") {
		className = "I18n"
	}
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

val byteBuddyAgent: Configuration by configurations.creating

dependencies {
	// Test dependencies
	testImplementation(kotlin("test"))
	testImplementation(libs.koin.test)  // For tests that involve Koin
	testImplementation(libs.mockk)  // Allow mocking in tests

	// Main dependencies (used internally by the common module)
	implementation(libs.typesafe.config)
	implementation(libs.kotlinx.serialization.hocon)

	// Expose test dependencies
	testFixturesImplementation(kotlin("test"))
	testFixturesImplementation(libs.konform)
	testFixturesImplementation(libs.mockk)

	// Exposed dependencies, for use in plugins
	api(libs.konform)
	api(libs.kordex.i18n.runtime)

	// An agent for testing
	byteBuddyAgent("net.bytebuddy:byte-buddy-agent:1.17.5")
}

tasks.test {
	jvmArgs("-javaagent:${byteBuddyAgent.asPath}")
}
