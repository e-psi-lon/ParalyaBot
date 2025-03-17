import dev.kordex.gradle.plugins.kordex.DataCollection

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kordex.gradle)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.shadow)
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kordEx {
    bot {
        dataCollection(DataCollection.Minimal)
        mainClass = "fr.paralya.bot.ParalyaBotKt"
    }
    i18n {
        classPackage = "fr.paralya.bot.i18n"
        translationBundle = "paralyabot"
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.logback)
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    shadowJar {
        archiveBaseName.set("paralya-bot")
        archiveClassifier.set("")
        archiveVersion.set(version as String)
    }
}

kotlin {
    jvmToolchain(21)
}