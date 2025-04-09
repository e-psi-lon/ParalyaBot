package fr.paralya.bot.lg.data

import dev.kord.cache.api.*
import dev.kord.cache.api.data.description
import dev.kord.common.entity.Snowflake
import dev.kord.core.cache.idEq
import dev.kord.core.entity.User
import fr.paralya.bot.common.toSnowflake
import fr.paralya.bot.lg.LGState

data class VoteData(
	val id: Snowflake,
	val type: String,
	val isCurrent: Boolean = false,
	val votes: Map<Snowflake, Snowflake> = emptyMap(),
	val choices: List<Snowflake> = listOf(),
	val corbeau: Snowflake = 0.toSnowflake()
) {
	companion object {
		val description = description<VoteData, Snowflake>(VoteData::id)

		fun createWerewolfVote(id: Snowflake, isCurrent: Boolean = true) =
			VoteData(id, LGState.NIGHT.name, isCurrent)

		fun createVillageVote(id: Snowflake, isCurrent: Boolean = true) =
			VoteData(id, LGState.DAY.name, isCurrent)
	}

	fun vote(voterId: Snowflake, targetId: Snowflake) =
		copy(votes = votes + (voterId to targetId))

	fun voteCorbeau(targetId: Snowflake) =
		if (type == LGState.DAY.name) copy(corbeau = targetId) else this

	fun setChoices(choices: List<Snowflake>) = copy(choices = choices)

	fun setCurrent(isCurrent: Boolean) = copy(isCurrent = isCurrent)
}

suspend fun DataCache.getCurrentVote(type: LGState? = null): VoteData? {
	val queryType = type?.name ?: getGameData().state.name
	return query<VoteData> {
		idEq(VoteData::type, queryType)
		idEq(VoteData::isCurrent, true)
	}.singleOrNull()
}

suspend fun DataCache.updateVote(voteData: VoteData) {
	put(voteData)
}

suspend fun DataCache.vote(voterId: Snowflake, target: User) =
	getCurrentVote()?.let {
		put(it.vote(voterId, target.id))
		true
	} ?: false

suspend fun DataCache.setVoteChoices(choices: List<Snowflake>) =
	getCurrentVote()?.let {
		put(it.setChoices(choices))
		true
	} ?: false

suspend fun DataCache.voteCorbeau(targetId: Snowflake) =
	getCurrentVote(LGState.DAY)?.let {
		put(it.voteCorbeau(targetId))
		true
	} ?: false