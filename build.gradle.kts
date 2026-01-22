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
}

group = "fr.paralya.bot"
version = property("paralyabot.version")!!

kordEx {
	ignoreIncompatibleKotlinVersion = true // Temporary fix for KordEx until it's updated to support Kotlin 2.2.21+
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

		listOf(
			"ar", "be", "bg", "ca", "cs", "da", "de", "el", "es", "et", "fa", "fi", "fil",
			"he", "hi", "hr", "hu", "id", "is", "it", "ja", "ko", "lt", "lv", "mk", "ms",
			"nb", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sr", "sv", "th", "tr",
			"uk", "vi", "zh", "zh_Hans", "zh_Hant"
		).forEach { lang ->
			exclude("com/ibm/icu/impl/data/icudata/coll/${lang}.res")
			exclude("com/ibm/icu/impl/data/icudata/coll/${lang}_*.res")
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
}