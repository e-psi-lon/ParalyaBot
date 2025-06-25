plugins {
	alias(libs.plugins.kordex.gradle)
}

kordEx {
	i18n {
		classPackage = "fr.paralya.bot.common"
		className = "I18n"
		translationBundle = "paralyabot-common"
	}
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}


val byteBuddyAgent: Configuration by configurations.creating

dependencies {
	// Test dependencies
	testApi(kotlin("test"))
	testApi(libs.koin.test)  // For tests that involve Koin
	testApi(libs.mockk)  // Allow mocking in tests

	// Main dependencies (used internally by the common module)
	implementation(libs.typesafe.config)
	implementation(libs.kotlinx.serialization.hocon)

	// Exposed dependencies, for use in plugins
	api(libs.konform)

	// An agent for testing
	byteBuddyAgent("net.bytebuddy:byte-buddy-agent:1.17.5")
}

tasks.test {
	useJUnitPlatform()
	jvmArgs("-javaagent:${byteBuddyAgent.asPath}")
}


kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-receivers")
	}
}