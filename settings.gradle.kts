rootProject.name = "ParalyaBot"


pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()

		maven("https://snapshots-repo.kordex.dev")
		maven("https://releases-repo.kordex.dev")
	}
}

// Enable type-safe project accessor
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


include("common")
include("lg")
include("sta")
