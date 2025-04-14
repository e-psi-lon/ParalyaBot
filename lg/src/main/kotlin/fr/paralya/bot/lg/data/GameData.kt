package fr.paralya.bot.lg.data

import dev.kord.cache.api.*
import dev.kord.cache.api.data.*
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import dev.kordex.core.commands.application.ApplicationCommandContext
import fr.paralya.bot.lg.LGState

data class GameData(
	val id: Snowflake = Snowflake(0),
	val state: LGState = LGState.NIGHT,
	val dayCount: Int = 0,
	val nightCount: Int = 0,
	val channels: Map<String, Snowflake> = mapOf(),
	val interviews: List<Snowflake> = listOf()
) {
	companion object {
		val description = description<GameData, Snowflake>(GameData::id)
	}

	fun nextDay() = copy(state = LGState.DAY, dayCount = dayCount + 1)
	fun nextNight() = copy(state = LGState.NIGHT, nightCount = nightCount + 1)

	fun registerChannel(type: String, channelId: Snowflake) =
		copy(channels = channels + (type to channelId))

	fun addInterview(interviewId: Snowflake) =
		copy(interviews = interviews + interviewId)

	fun removeInterview(interviewId: Snowflake) =
		copy(interviews = interviews.filter { it != interviewId })
}

// Cache extension functions
suspend fun DataCache.getGameData() = query<GameData>().singleOrNull() ?: GameData()

suspend fun DataCache.updateGameData(modifier: (GameData) -> GameData) {
	put(modifier(getGameData()))
}

suspend fun DataCache.nextDay() = updateGameData { it.nextDay() }
suspend fun DataCache.nextNight() = updateGameData { it.nextNight() }
suspend fun DataCache.registerChannel(type: String, channelId: Snowflake) =
	updateGameData { it.registerChannel(type, channelId) }
suspend fun DataCache.getChannelId(type: String) = getGameData().channels[type]

context(ApplicationCommandContext)
suspend fun DataCache.getChannel(type: String) =
	getChannelId(type)?.let { this@ApplicationCommandContext.guild!!.getChannel(it) as TextChannel }

suspend fun DataCache.addInterview(interviewId: Snowflake) =
	updateGameData { it.addInterview(interviewId) }
suspend fun DataCache.getInterviews() = getGameData().interviews
suspend fun DataCache.removeInterview(interviewId: Snowflake) =
	updateGameData { it.removeInterview(interviewId) }
suspend fun DataCache.resetGame() = put(GameData())