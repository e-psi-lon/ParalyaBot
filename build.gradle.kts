import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
	alias(libs.plugins.kotlinx.serialization)
	alias(libs.plugins.shadow)
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

kordEx {
	module("web-backend")
	bot {
		dataCollection(DataCollection.None)
		mainClass = "fr.paralya.bot.ParalyaBotKt"
	}
}

i18n {
	bundle("paralyabot.strings", "fr.paralya.bot") {
		className = "I18n"
		publicVisibility = false
	}
}



val libraries = libs
val typesafeProjects = projects
subprojects {
	apply(plugin = "kotlinx-serialization")
	apply(plugin = "kotlin")

	dependencies {
		if (name != "common") {
			compileOnly(typesafeProjects.common) // The common subproject serve as a base for all other subprojects
			testImplementation(testFixtures(typesafeProjects.common)) // The common subproject also includes test dependencies
		}
	}
}


dependencies {
	testImplementation(kotlin("test"))
	implementation(libs.logback)
    implementation(libs.kotlinx.html)
	implementation(libraries.kordex.i18n.runtime)
	implementation(projects.common)
	implementation(projects.lg)
    implementation(projects.sta)
}

tasks {
	shadowJar {
		archiveBaseName.set("paralya-bot")
		archiveClassifier.set("")
		archiveVersion.set(version as String)
	}
}
