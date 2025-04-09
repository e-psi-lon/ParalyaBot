plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kordex.gradle)
}


group = "fr.paralya.bot"
version = "0.1.0"

kordEx {
	plugin {
		id = "paralya-lg"
		version = getVersion() as String
		description = "ParalyaBot's Werewolf game plugin"
		pluginClass = "fr.paralya.bot.lg.LgBotPlugin"
	}

	i18n {
		classPackage = "fr.paralya.bot.lg.i18n"
		translationBundle = "paralyabot-lg"
	}
}

repositories {
	mavenCentral()
}

dependencies {
	testImplementation(kotlin("test"))
	implementation(projects.common)
}

tasks.test {
	useJUnitPlatform()
}
kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-receivers")
	}
}


task("exportToGames") {
	// This task depends on the jar task and moves it to the games folder
	dependsOn(":lg:distZip")
	doLast {
		val zip = project(":lg").tasks.getByName("distZip").outputs.files.singleFile
		val gamesDir = projectDir.resolve("../games/")
		gamesDir.mkdirs()
		zip.copyTo(gamesDir.resolve(zip.name), true)
		val extractedDir = gamesDir.resolve(zip.nameWithoutExtension)
		if (extractedDir.exists()) {
			extractedDir.deleteRecursively()
		}

	}
}