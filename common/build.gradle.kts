plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kordex.gradle)
}

kordEx {
	i18n {
		classPackage = "fr.paralya.bot.common.i18n"
		translationBundle = "paralyabot-common"
	}
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}

dependencies {
	testImplementation(kotlin("test"))
	implementation(libs.typesafe.config)
	implementation(libs.kotlin.reflection)
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