plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
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
	configurations = listOf("compileOnly")

}

i18n {
	bundle("paralyabot-common.strings", "fr.paralya.bot.common") {
		className = "I18n"
	}
}

group = "fr.paralya.bot"
version = property("paralyabot.version")!!

dependencies {
	implementation(libs.kotlinx.serialization.hocon)
	implementation(libs.kotlinx.serialization.cbor)
}

tasks {
	register<Copy>("copyRuntimeClasspath") {
		description = "Copies the runtime dependencies to the build directory"
		val outputDir = layout.buildDirectory.dir("deps")
		from(configurations.runtimeClasspath)
		into(outputDir)
	}
	jar {
		archiveBaseName.set("paralya-bot-common")
		archiveClassifier.set("")
		archiveVersion.set("")
		isPreserveFileTimestamps = false
		isReproducibleFileOrder = true
	}

	register("generateVersion") {
		description = "Generates a Kotlin object containing the common module's version information"
		val outputDir = layout.buildDirectory.dir("generated/version/main/kotlin/fr/paralya/bot/common")
		outputs.dir(outputDir)

		doLast {
			val versionFile = outputDir.get().asFile.resolve("CommonModule.kt")
			versionFile.parentFile.mkdirs()
			versionFile.writeText("""
            package fr.paralya.bot.common
            
            object CommonModule {
                const val API_VERSION = "${project.version}"
                const val MIN_COMPATIBLE_VERSION = "1.0.0"
            }
        """.trimIndent())
		}
	}

	compileKotlin {
		dependsOn("generateVersion")
	}
}