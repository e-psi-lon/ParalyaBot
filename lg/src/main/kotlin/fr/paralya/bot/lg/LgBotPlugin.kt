package fr.paralya.bot.lg

import dev.kordex.core.plugins.KordExPlugin
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.lg.data.LgConfig
import org.koin.core.component.inject

/**
 * The main plugin class for the Werewolf (Loup-Garou) game.
 *
 * This class is responsible for setting up the configuration [LgConfig] and initializing the [LG] extension.
 * It is currently not used because the plugin loader from KordEx is still in development.
 *
 */
class LgBotPlugin : KordExPlugin() {
	/**
	 * Setup function that initializes the plugin.
	 */
	override suspend fun setup() {
		bot.logger.info { "Setting up LG plugin" }
		try {
			getKoin()
			bot.logger.info { "Koin already started, registering LG config" }
			registerLgConfig()
		} catch (e: IllegalStateException) {
			bot.logger.info { "Koin not started, registering LG config hook after Koin setup" }
			settings {
				hooks { afterKoinSetup { registerLgConfig() } }
			}
		}
		extension(::LG)
	}

	/**
	 * Registers the [LgConfig] configuration class with the [ConfigManager].
	 *
	 * This allows the plugin to manage its configuration settings.
	 */
	private fun registerLgConfig() {
		val configManager by inject<ConfigManager>()
		configManager.registerConfig(::LgConfig, "lgConfig")
	}
}