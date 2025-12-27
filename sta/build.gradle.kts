plugins {
	id("game-plugin")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
}

kordEx {
	ignoreIncompatibleKotlinVersion = true // Temporary fix for KordEx until it's updated to support Kotlin 2.2.21+
	plugin {
		id = "paralya-sta"
		version = getVersion() as String
		description = "ParalyaBot's Stats Arena game plugin"
		pluginClass = "fr.paralya.bot.sta.StaBotPlugin"
	}
}

i18n {
	bundle("paralyabot-sta.strings", "fr.paralya.bot.sta") {
		className = "I18n"
		publicVisibility = false
	}
}


