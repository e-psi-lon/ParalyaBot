package fr.paralya.bot.common

import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.builder.PresenceBuilder
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.koin.KordExKoinComponent
import fr.paralya.bot.common.I18n


/**
 * [GameRegistry] is a singleton class that manages game modes for the bot.
 * It allows for the registration, retrieval, and unloading of game modes.
 * Registration and unloading are currently unused because the at-runtime game loading is not implemented yet.
 *
 * @property gameModes A mutable map that stores game modes with their associated keys.
 */
class GameRegistry : KordExKoinComponent {
	private val gameModes = mutableMapOf<Key, String>()

	/**
	 * Registers a new game mode with the given key and value.
	 *
	 * @param key The translation key for the game mode.
	 * @param gameMode The name of the game mode.
	 */
	fun registerGameMode(key: Key, gameMode: String) {
		gameModes[key] = gameMode
	}

	/**
	 * Retrieves the game mode associated with the given value.
	 *
	 * @param value The name of the game mode.
	 * @return A pair containing the key and value of the game mode, or NONE if not found.
	 */
	fun getGameMode(value: String) = if (gameModes.containsValue(value)) {
		gameModes.filterValues { it == value }.keys.first() to value
	} else {
		NONE
	}

	/**
	 * Retrieves all registered game modes.
	 *
	 * @return A mutable map containing all game modes with their associated keys.
	 */
	fun getGameModes(): MutableMap<Key, String> {
		return gameModes
	}

	/**
	 * Unloads a game mode by removing it from the registry.
	 *
	 * @param value The name of the game mode to unload.
	 */
	fun unloadGameMode(value: String) {
		if (value == "none") return
		if (!gameModes.containsValue(value)) return
		gameModes.remove(gameModes.filterValues { it == value }.keys.first())
	}

	companion object {
		val NONE = I18n.GameMode.none to "none"
	}
}

/**
 * Extension function to set the game mode in a PresenceBuilder.
 *
 * @param gameMode A pair containing the key and value of the game mode.
 */
fun PresenceBuilder.gameMode(gameMode: Pair<Key, String>) {
	if (gameMode == GameRegistry.NONE) {
		status = PresenceStatus.Idle
		this.watching("Paralya sans animation en cours...")
	} else {
		status = PresenceStatus.Online
		this.playing("une partie de ${gameMode.first.translate()}")
	}
}