plugins {
	alias(libs.plugins.kordex.gradle)
}


group = "fr.paralya.bot"
version = "0.1.0"

kordEx {
	plugin {
		id = "paralya-sta"
		version = getVersion() as String
		description = "ParalyaBot's Stats Arena game plugin"
		pluginClass = "fr.paralya.bot.lg.StatsAreBotPlugin"
	}

	i18n {
		classPackage = "fr.paralya.bot.sta"
		className = "I18n"
		translationBundle = "paralyabot-sta"
		publicVisibility = false
	}
}

repositories {
	mavenCentral()
}

dependencies {
	testImplementation(libs.mockk)
	testImplementation(libs.koin.test)
}

tasks.test {
	useJUnitPlatform()
}
kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-parameters")
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