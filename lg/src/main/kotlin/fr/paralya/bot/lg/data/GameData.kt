package fr.paralya.bot.lg.data

import dev.kord.cache.api.*
import dev.kord.cache.api.data.*
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import dev.kordex.core.commands.application.ApplicationCommandContext
import fr.paralya.bot.lg.LGState

/**
 * Represents the game data for the Werewolf game.
 *
 * @property id The unique identifier for the game.
 * @property state The current state of the game (day or night).
 * @property dayCount Counter for the number of days passed in the game.
 * @property lastWereWolfMessageSender ID of the last player who sent a message in the werewolf channel.
 * @property currentProfilePicture Used to select which variant of the profile picture to use.
 * @property nightCount Counter for the number of nights passed in the game.
 * @property channels Map of channel types/name to their respective [Snowflake] IDs.
 * @property interviews List of interview channel IDs.
 */
data class GameData(
	val id: Snowflake = Snowflake(0),
	val state: LGState = LGState.NIGHT,
	val dayCount: Int = 0,
	val lastWereWolfMessageSender: Snowflake = Snowflake(0),
	val currentProfilePicture: Boolean = false,
	val nightCount: Int = 0,
	val channels: Map<String, Snowflake> = mapOf(),
	val interviews: List<Snowflake> = listOf()
) {
	companion object {
		/** Description for caching [GameData] instances by their id */
		val description = description<GameData, Snowflake>(GameData::id)
	}

	/**
	 * Creates a copy of the current game data, advancing to the next day.
	 * @return A new [GameData] instance with a state set to DAY and dayCount incremented.
	 */
	fun nextDay() = copy(state = LGState.DAY, dayCount = dayCount + 1)

	/**
	 * Creates a copy of the current game data, advancing to the next night.
	 * @return A new [GameData] instance with a state set to NIGHT and nightCount incremented.
	 */
	fun nextNight() = copy(state = LGState.NIGHT, nightCount = nightCount + 1)

	/**
	 * Registers a channel in the game.
	 * @param type The type of channel to register.
	 * @param channelId The [Snowflake] ID of the channel.
	 * @return A new [GameData] instance with the channel added to the channels map.
	 */
	fun registerChannel(type: String, channelId: Snowflake) =
		copy(channels = channels + (type to channelId))

	/**
	 * Adds an interview channel to the game.
	 * @param interviewId The [Snowflake] ID of the interview channel.
	 * @return A new [GameData] instance with the interview added to the interview list.
	 */
	fun addInterview(interviewId: Snowflake) =
		copy(interviews = interviews + interviewId)

	/**
	 * Removes an interview channel from the game.
	 * @param interviewId The [Snowflake] ID of the interview channel to remove.
	 * @return A new [GameData] instance with the specified interview removed from the interview list.
	 */
	fun removeInterview(interviewId: Snowflake) =
		copy(interviews = interviews.filter { it != interviewId })
}

// Cache extension functions

/**
 * Retrieves the current game data from the cache or creates a new one if none exists.
 * @return The current [GameData] instance.
 */
suspend fun DataCache.getGameData() = query<GameData>().singleOrNull() ?: GameData()

/**
 * Resets the game data by removing existing data and adding a fresh [GameData] instance.
 */
suspend fun DataCache.resetGameData() {
	remove<GameData>()
	put(GameData())
}

/**
 * Updates the game data using the provided modifier function.
 * @param modifier A function that transforms the current [GameData] to a new [GameData].
 */
suspend fun DataCache.updateGameData(modifier: (GameData) -> GameData) {
	put(modifier(getGameData()))
}

/**
 * Advances the game to the next day.
 */
suspend fun DataCache.nextDay() = updateGameData { it.nextDay() }

/**
 * Advances the game to the next night.
 */
suspend fun DataCache.nextNight() = updateGameData { it.nextNight() }

/**
 * Registers a channel in the game data.
 * @param type The type of channel to register.
 * @param channelId The [Snowflake] ID of the channel.
 */
suspend fun DataCache.registerChannel(type: String, channelId: Snowflake) =
	updateGameData { it.registerChannel(type, channelId) }

/**
 * Gets the channel id based on its [type] which is his role in the game.
 */
suspend fun DataCache.getChannelId(type: LgChannelType) = getGameData().channels[type.name]
suspend fun DataCache.getChannelId(type: String) = getGameData().channels[type]

/**
 * Gets the [TextChannel] for a specific channel [type] within an application command context
 * allowing to access to the channel itself.
 */
context(ApplicationCommandContext)
suspend fun DataCache.getChannel(type: LgChannelType) =
	getChannelId(type)?.let { this@ApplicationCommandContext.guild!!.getChannel(it) as TextChannel }
/**
 * Adds an interview channel to the game data.
 * @param interviewId The [Snowflake] ID of the interview channel.
 */
suspend fun DataCache.addInterview(interviewId: Snowflake) =
	updateGameData { it.addInterview(interviewId) }

/**
 * Gets the list of all interview channel IDs.
 * @return A list of [Snowflake] IDs for interview channels.
 */
suspend fun DataCache.getInterviews() = getGameData().interviews

/**
 * Removes an interview channel from the game data.
 * @param interviewId The [Snowflake] ID of the interview channel to remove.
 */
suspend fun DataCache.removeInterview(interviewId: Snowflake) =
	updateGameData { it.removeInterview(interviewId) }

/**
 * Resets the entire game by replacing the game data with a fresh instance.
 */
suspend fun DataCache.resetGame() = put(GameData())

/**
 * Gets the ID of the last player who sent a message in the werewolf channel.
 * @return [Snowflake] ID of the last werewolf message sender.
 */
suspend fun DataCache.getLastWerewolfMessageSender() =
	getGameData().lastWereWolfMessageSender

/**
 * Sets the ID of the last player who sent a message in the werewolf channel.
 * @param senderId The [Snowflake] ID of the sender.
 */
suspend fun DataCache.setLastWerewolfMessageSender(senderId: Snowflake) =
	updateGameData { it.copy(lastWereWolfMessageSender = senderId) }

/**
 * Toggles the profile picture state in the game data.
 */
suspend fun DataCache.updateProfilePicture() =
	updateGameData { it.copy(currentProfilePicture = !it.currentProfilePicture) }

/**
 * Gets the current profile picture state.
 * @return Current profile picture boolean state.
 */
suspend fun DataCache.getProfilePictureState() =
	getGameData().currentProfilePicture