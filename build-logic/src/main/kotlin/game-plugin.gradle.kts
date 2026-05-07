import org.gradle.api.tasks.bundling.Zip

plugins {
	id("kotlin-common")
}

group = "fr.paralya.bot"
version = providers.gradleProperty("plugin.${project.name}.version").get()

tasks.register("exportToPluginsDir") {
	dependsOn(":${project.name}:distZip")
	doLast {
		val distZipTask = tasks.getByName("distZip") as Zip
		val zip = distZipTask.outputs.files.singleFile
		val gamesDir = projectDir.resolve("../container/plugins")
		gamesDir.mkdirs()
		zip.copyTo(gamesDir.resolve(zip.name), true)
		val extractedDir = gamesDir.resolve(zip.nameWithoutExtension)
		if (extractedDir.exists()) {
			extractedDir.deleteRecursively()
		}
	}
}

