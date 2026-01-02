plugins {
	id("game-plugin")
	alias(libs.plugins.kordex.gradle)
	alias(libs.plugins.kordex.i18n)
}

kordEx {
	ignoreIncompatibleKotlinVersion = true // Temporary fix for KordEx until it's updated to support Kotlin 2.2.21+
	plugin {
		id = "paralya-lg"
		version = getVersion() as String
		description = "ParalyaBot's Werewolf game plugin"
		pluginClass = "fr.paralya.bot.lg.LgPlugin"
	}
}

i18n {
	bundle("paralyabot-lg.strings", "fr.paralya.bot.lg") {
		className = "I18n"
		publicVisibility = false
	}
}


