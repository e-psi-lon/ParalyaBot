import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

plugins {
	kotlin("jvm")
	id("org.jetbrains.kotlinx.kover")
}
val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")


kotlin {
	jvmToolchain(libs.findVersion("jvm").get().toString().toInt())
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-parameters")
	}
}
dependencies {
	compileOnly(kotlin("stdlib"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}

repositories {
	mavenCentral()
}