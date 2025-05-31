import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kotlinx.serialization)
	alias(libs.plugins.shadow)
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}

kordEx {
	module("web-backend")
	bot {
		dataCollection(DataCollection.None)
		mainClass = "fr.paralya.bot.ParalyaBotKt"
	}
	i18n {
		classPackage = "fr.paralya.bot"
		className = "I18n"
		translationBundle = "paralyabot"
		publicVisibility = false
	}
}

val libraries = libs
val typesafeProjects = projects
subprojects {
	apply(plugin = "kotlinx-serialization")
	apply(plugin = "kotlin")

	dependencies {
		testImplementation(kotlin("test"))
		testImplementation(libraries.koin.test)  // For tests that involve Koin
		testImplementation(libraries.mockk)  // Allow mocking in tests
		if (name != "common") {
			implementation(typesafeProjects.common) // The common subproject serve as a base for all other subprojects
		}
		implementation(libraries.konform) // For config validation
	}
}


dependencies {
	testImplementation(kotlin("test"))
	implementation(libs.logback)
	implementation(projects.common)
	implementation(projects.lg)
}

tasks.test {
	useJUnitPlatform()
}

tasks {
	shadowJar {
		archiveBaseName.set("paralya-bot")
		archiveClassifier.set("")
		archiveVersion.set(version as String)
	}
}

kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-receivers")
	}
}