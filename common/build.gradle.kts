plugins {
	id("kotlin-common")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
	`java-test-fixtures`
	alias(libs.plugins.shadow)
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


tasks {
	shadowJar {
		archiveBaseName.set("paralya-bot-common")
		archiveClassifier.set("")
		archiveVersion.set("")
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
                const val MIN_COMPATIBLE_VERSION = "0.0.1"
            }
        """.trimIndent())
		}
	}

	compileKotlin {
		dependsOn("generateVersion")
	}
}