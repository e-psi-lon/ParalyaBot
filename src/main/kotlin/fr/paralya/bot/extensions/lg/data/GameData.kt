package fr.paralya.bot.extensions.lg.data

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.data.DataDescription
import dev.kord.cache.api.data.description
import dev.kord.cache.api.put
import dev.kord.cache.api.query
import dev.kord.common.entity.Snowflake
import fr.paralya.bot.extensions.lg.LGState
import kotlinx.serialization.Serializable

@Serializable
data class GameData(
	val id: Snowflake = Snowflake(0),
	val state: LGState = LGState.NIGHT,
	val dayCount: Int = 0,
	val nightCount: Int = 0,
	val channels: Map<String, Snowflake> = mapOf(),
	val interviews: List<Snowflake> = listOf()
) {
	companion object {
		val description: DataDescription<GameData, Snowflake> = description(GameData::id)
	}

	fun nextDay(): GameData = copy(state = LGState.DAY, dayCount = dayCount + 1)
	fun nextNight(): GameData = copy(state = LGState.NIGHT, nightCount = nightCount + 1)

	fun registerChannel(type: String, channelId: Snowflake): GameData {
		val updatedChannels = channels.toMutableMap()
		updatedChannels[type] = channelId
		return copy(channels = updatedChannels)
	}

	fun addInterview(interviewId: Snowflake): GameData {
		val updatedInterviews = interviews.toMutableList()
		updatedInterviews.add(interviewId)
		return copy(interviews = updatedInterviews)
	}

	fun removeInterview(interviewId: Snowflake): GameData {
		val updatedInterviews = interviews.filter { it != interviewId }
		return copy(interviews = updatedInterviews)
	}
}

// Cache extension functions
suspend fun DataCache.getGameData(): GameData {
	return query<GameData>().singleOrNull() ?: GameData()
}

suspend fun DataCache.updateGameData(modifier: (GameData) -> GameData) {
	val current = getGameData()
	val updated = modifier(current)
	put(updated)
}

suspend fun DataCache.nextDay() {
	updateGameData { it.nextDay() }
}

suspend fun DataCache.nextNight() {
	updateGameData { it.nextNight() }
}

suspend fun DataCache.registerChannel(type: String, channelId: Snowflake) {
	updateGameData { it.registerChannel(type, channelId) }
}

suspend fun DataCache.getChannel(type: String): Snowflake? {
	return getGameData().channels[type]
}

suspend fun DataCache.addInterview(interviewId: Snowflake) {
	updateGameData { it.addInterview(interviewId) }
}

suspend fun DataCache.getInterviews(): List<Snowflake> {
	return getGameData().interviews
}

suspend fun DataCache.removeInterview(interviewId: Snowflake) {
	updateGameData { it.removeInterview(interviewId) }
}

suspend fun DataCache.resetGame() {
	put(GameData())
}