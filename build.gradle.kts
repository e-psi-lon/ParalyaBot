import dev.kordex.gradle.plugins.kordex.DataCollection

val excludedDependencies = listOf(
	"net.fellbaum" to "jemoji",
	"commons-validator" to "commons-validator",
	// Kept until KordEx abstracts it away
	// "io.sentry" to "sentry"
)

configurations.all {
	excludedDependencies.forEach { (group, module) ->
		exclude(group = group, module = module)
	}
}

plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
	alias(libs.plugins.kotlinx.serialization)
	alias(libs.plugins.shadow)
	alias(libs.plugins.detekt)
}

group = "fr.paralya.bot"
version = property("paralyabot.version")!!

kordEx {
	kordExVersion.set(libs.versions.kordex.library)
	kordVersion.set(libs.versions.kord)
	bot {
		voice = false
		dataCollection(DataCollection.None)
		mainClass = "fr.paralya.bot.ParalyaBotKt"
	}
	configurations = listOf("compileOnly", "shadow")
}

i18n {
	bundle("paralyabot.strings", "fr.paralya.bot") {
		className = "I18n"
		publicVisibility = false
	}
}

val excludedPlugins = listOf("sta")

val libraries = libs
val typesafeProjects = projects
subprojects {
	pluginManager.apply {
		apply("kotlinx-serialization")
		apply("kotlin")
		apply("dev.detekt")
	}

	dependencies {
		if (path != typesafeProjects.common.path) {
			compileOnly(typesafeProjects.common) // The common subproject serves as a base for all other subprojects
			testImplementation(testFixtures(typesafeProjects.common)) // The common subproject also includes test dependencies
		}
	}
}

allprojects {
	dependencyLocking {
		lockAllConfigurations()
	}
}


buildscript {
	configurations.classpath {
		resolutionStrategy.activateDependencyLocking()
	}
}

dependencies {
	testImplementation(kotlin("test"))
	implementation(libs.logback)
    implementation(libs.kotlinx.html)
	compileOnly(projects.common)
	shadow(projects.common)
}

tasks {
	shadowJar {
		archiveBaseName.set("paralya-bot")
		archiveClassifier.set("")
		archiveVersion.set("")
		isPreserveFileTimestamps = false
		isReproducibleFileOrder = true

		listOf(
			"META-INF/maven/**",
			"META-INF/proguard/**",
			"META-INF/LICENSE*",
			"META-INF/NOTICE*",
			"META-INF/DEPENDENCIES",
			"META-INF/*.SF",
			"META-INF/*.DSA",
			"META-INF/*.RSA",
			"META-INF/versions/**",
			"**/*.kotlin_builtins",
			"**/*.kotlin_metadata",
			"**/*.md",
			"**/README*",
			"**/CHANGELOG*",
			"**/LICENSE*",
			"**/NOTICE*"
		).forEach(::exclude)

		mergeServiceFiles()
	}

	register("cleanPlugins") {
		description = "Wipes the local plugins directory and recreates an empty one to ensure a fresh environment."
        doLast {
			val pluginsDir = file("./container/plugins")
			if (pluginsDir.exists()) {
				pluginsDir.deleteRecursively()
			}
			pluginsDir.mkdirs()
		}
	}

	register<JavaExec>("runFull") {
		description = "Full execution from scratch: Cleans, exports all subproject plugins (excluding a pre-defined list), and runs the application via ShadowJar configuration."
        dependsOn("cleanPlugins")
		dependsOn(subprojects
			.filter { it.name !in excludedPlugins }
			.mapNotNull { it.tasks.findByName("exportToPluginsDir") }
		)
		val runTask = getByName<JavaExec>("runShadow")
		mainClass = runTask.mainClass
		classpath = runTask.classpath
		jvmArgs = runTask.jvmArgs + listOf("--enable-native-access=ALL-UNNAMED")
	}
}