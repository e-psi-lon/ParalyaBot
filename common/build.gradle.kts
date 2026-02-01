plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
	`java-test-fixtures`
}

kotlin {
	sourceSets {
		main {
			kotlin.srcDir(layout.buildDirectory.dir("generated/version/main/kotlin"))
		}
	}
}

kordEx {
	kordExVersion.set(libs.versions.kordex.library)
	kordVersion.set(libs.versions.kord)
}

i18n {
	bundle("paralyabot-common.strings", "fr.paralya.bot.common") {
		className = "I18n"
	}
}

group = "fr.paralya.bot"
version = property("paralyabot.version")!!

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
	implementation(libs.kotlinx.serialization.hocon)
	implementation(libs.kotlinx.serialization.cbor)

	// Exposed dependencies, for use in plugins
	api(libs.konform)
	api(libs.kordex.i18n.runtime)
}



tasks.register("generateVersion") {
	val outputDir = layout.buildDirectory.dir("generated/version/main/kotlin/fr/paralya/bot/common")
	outputs.dir(outputDir)

	doLast {
		val versionFile = outputDir.get().asFile.resolve("CommonModule.kt")
		versionFile.parentFile.mkdirs()
		versionFile.writeText("""
            package fr.paralya.bot.common
            
            object CommonModule {
                const val API_VERSION = "${project.version}"
                const val MIN_COMPATIBLE_VERSION = "0.0.1"
            }
        """.trimIndent())
	}
}

tasks.compileKotlin {
	dependsOn("generateVersion")
}