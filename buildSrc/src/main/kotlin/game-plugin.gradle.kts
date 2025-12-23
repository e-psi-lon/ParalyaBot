import org.gradle.api.tasks.bundling.Zip

plugins {
	id("kotlin-common")
}

group = "fr.paralya.bot"
version = "0.1.0"

tasks.register("exportToGames") {
	dependsOn(":${project.name}:distZip")
	doLast {
		val distZipTask = tasks.getByName("distZip") as Zip
		val zip = distZipTask.outputs.files.singleFile
		val gamesDir = projectDir.resolve("../games/")
		gamesDir.mkdirs()
		zip.copyTo(gamesDir.resolve(zip.name), true)
		val extractedDir = gamesDir.resolve(zip.nameWithoutExtension)
		if (extractedDir.exists()) {
			extractedDir.deleteRecursively()
		}
	}
}

