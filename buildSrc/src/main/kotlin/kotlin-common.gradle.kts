import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

plugins {
	kotlin("jvm")
}

val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
	jvmToolchain(libs.findVersion("jvm").get().toString().toInt())
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