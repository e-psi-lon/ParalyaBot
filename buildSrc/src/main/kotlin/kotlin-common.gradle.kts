import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

plugins {
	kotlin("jvm")
}

kotlin {
	jvmToolchain(25)
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-parameters")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

repositories {
	mavenCentral()
}

