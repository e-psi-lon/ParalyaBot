package fr.paralya.bot.lg

import dev.kordex.core.plugins.KordExPlugin
import fr.paralya.bot.common.ConfigManager
import fr.paralya.bot.lg.data.LgConfig
import org.koin.core.component.inject

class LgBotPlugin: KordExPlugin() {
	override suspend fun setup() {
		bot.logger.info { "Setting up LG plugin" }
		try {
			// We try to get the Koin instance, if it fails, we register, it's because
			// it's not started yet
			getKoin()
			bot.logger.info { "Koin already started, registering LG config" }
			extension(::LG)
			val configManager by inject<ConfigManager>()
			configManager.registerConfig(::LgConfig, "lgConfig")
		} catch (e: IllegalStateException) {
			bot.logger.info { "Koin not started, registering LG config hook after Koin setup" }
			settings {
				hooks {
					afterKoinSetup {
						val configManager by inject<ConfigManager>()
						configManager.registerConfig(::LgConfig, "lgConfig")
					}
				}

			}
		}
		extension(::LG)
	}
}