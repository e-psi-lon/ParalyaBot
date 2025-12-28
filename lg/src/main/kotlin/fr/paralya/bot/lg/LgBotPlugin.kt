package fr.paralya.bot.lg

import fr.paralya.bot.common.ApiVersion
import fr.paralya.bot.common.CommonModule
import fr.paralya.bot.common.plugin.Plugin
import fr.paralya.bot.lg.data.LgConfig

/**
 * The main plugin class for the Werewolf (Loup-Garou) game.
 *
 * This class is responsible for setting up the configuration [LgConfig] and initializing the [LG] extension.
 * It is currently not used because the plugin loader from KordEx is still in development.
 *
 */
@ApiVersion(CommonModule.API_VERSION)
class LgBotPlugin : Plugin() {
	override val name = "LG"
	override val key = I18n.GameMode.lg

	override fun defineConfig() = define<LgConfig>()

	/**
	 * Setup function that initializes the plugin.
	 */
	override suspend fun onSetup() {
		extension(::LG)
	}
}