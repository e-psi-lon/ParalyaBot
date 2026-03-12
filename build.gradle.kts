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
	apply(plugin = "dev.detekt")

	dependencies {
		if (name != "common") {
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
	implementation(libs.kordex.i18n.runtime)
	implementation(projects.common)
}

tasks {
	shadowJar {
		archiveBaseName.set("paralya-bot")
		archiveClassifier.set("")
		archiveVersion.set(version as String)
		isPreserveFileTimestamps = false
		isReproducibleFileOrder = true

		listOf("brkitr", "translit", "rbnf").forEach { category ->
			exclude("com/ibm/icu/impl/data/icudata/$category/**")
		}
		listOf(
			"aix-*", "sunos-*", "darwin-*", "freebsd-*", "openbsd-*", "dragonflybsd-*",
			"linux-arm*", "linux-mips*", "linux-ppc*", "linux-s390x", "linux-loongarch*",
			"linux-riscv*", "linux-aarch64", "linux-x86",
			"win32-x86", "win32-aarch64"
		).forEach { platform ->
			exclude("com/sun/jna/$platform/**")
		}

		exclude { file ->
			val path = file.relativePath.pathString
			if (!path.startsWith("com/ibm/icu/impl/data/icudata/")) return@exclude false

			val lastName = file.relativePath.lastName
			// Keep all non-res/icu binary data files (.cfu, .nrm, .spp, .brk, .dict, etc.)
			if (!lastName.endsWith(".res") && !lastName.endsWith(".icu")) return@exclude false

			val name = lastName.removeSuffix(".res").removeSuffix(".icu")

			val keepFiles = setOf(
				"root", "pool", "res_index", "metadata", "supplementalData",
				"langInfo", "zoneinfo64", "metaZones", "timezoneTypes",
				"windowsZones", "tzdbNames", "numberingSystems", "plurals",
				"pluralRanges", "currencyNumericCodes", "keyTypeData",
				"grammaticalFeatures", "genderList", "dayPeriods",
				"icustd", "icuver", "units", "unames", "uprops",
				"confusables", "pnames", "ucase"
			)
			if (name in keepFiles) return@exclude false
			if (name.startsWith("en") || name.startsWith("fr")) return@exclude false

			name.first().isLetter()
		}

		listOf("osx", "linux_aarch_64").forEach { platform ->
			exclude("META-INF/native/libnetty_*_${platform}*.*")
		}
		exclude("META-INF/native/netty_*_x86_32.dll")
		listOf("x86", "arm64").forEach { arch ->
			exclude("org/fusesource/jansi/internal/native/Windows/$arch/**")
		}

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
		doLast {
			val pluginsDir = file("./container/plugins")
			if (pluginsDir.exists()) {
				pluginsDir.deleteRecursively()
			}
			pluginsDir.mkdirs()
		}
	}

	register<JavaExec>("runFull") {
		dependsOn("cleanPlugins")
		dependsOn(subprojects
			.filter { it.name !in listOf("sta") }
			.mapNotNull { it.tasks.findByName("exportToPluginsDir") }
		)
		val runTask = getByName<JavaExec>("runShadow")
		mainClass = runTask.mainClass
		classpath = runTask.classpath
		jvmArgs = runTask.jvmArgs
	}
}