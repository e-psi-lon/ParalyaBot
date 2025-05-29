plugins {
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



val byteBuddyAgent: Configuration by configurations.creating

dependencies {
	testImplementation(kotlin("test"))
	implementation(libs.logback)
	implementation(libs.typesafe.config)
	implementation(libs.kotlinx.serialization.hocon)
	byteBuddyAgent("net.bytebuddy:byte-buddy-agent:1.17.5")
}

tasks.test {
	useJUnitPlatform()
	jvmArgs("-javaagent:${byteBuddyAgent.asPath}")
}
kotlin {
	jvmToolchain(21)
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-receivers")
	}
}