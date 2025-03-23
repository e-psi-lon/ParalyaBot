package fr.paralya.bot.lg.data

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.data.DataDescription
import dev.kord.cache.api.data.description
import dev.kord.cache.api.put
import dev.kord.cache.api.query
import dev.kord.common.entity.Snowflake
import dev.kord.core.cache.idEq
import dev.kord.core.entity.User
import fr.paralya.bot.common.toSnowflake
import fr.paralya.bot.lg.LGState

data class VoteData(
	val id: Snowflake,
	val type: String,
	val isCurrent: Boolean = false,
	val votes: Map<Snowflake, Snowflake> = emptyMap(), //
	val choices: List<Snowflake> = listOf(),
	val corbeau: Snowflake = 0.toSnowflake()
) {
	companion object {
		val description: DataDescription<VoteData, Snowflake> = description(VoteData::id)

		fun createWerewolfVote(id: Snowflake, isCurrent: Boolean = true): VoteData =
			VoteData(id, LGState.NIGHT.name, isCurrent)

		fun createVillageVote(id: Snowflake, isCurrent: Boolean = true): VoteData =
			VoteData(id, LGState.DAY.name, isCurrent)
	}

	fun vote(voterId: Snowflake, targetId: Snowflake): VoteData {
		val updatedVotes = votes.toMutableMap()
		updatedVotes[voterId] = targetId
		return copy(votes = updatedVotes)
	}

	fun voteCorbeau(targetId: Snowflake): VoteData {
		return if (type == LGState.DAY.name) copy(corbeau = targetId) else this
	}

	fun setChoices(choices: List<Snowflake>): VoteData = copy(choices = choices)

	fun setCurrent(isCurrent: Boolean): VoteData = copy(isCurrent = isCurrent)
}

suspend fun DataCache.getCurrentVote(type: LGState? = null): VoteData? {
	return if (type == null) {
		val time = getGameData().state
		query<VoteData> {
			this.idEq(VoteData::type, time.name)
			this.idEq(VoteData::isCurrent, true)
		}.singleOrNull()
	} else {
		query<VoteData> {
			this.idEq(VoteData::type, type.name)
			this.idEq(VoteData::isCurrent, true)
		}.singleOrNull()
	}
}

suspend fun DataCache.updateVote(voteData: VoteData) {
	put(voteData)
}

suspend fun DataCache.vote(voterId: Snowflake, target: User): Boolean {
	val vote = getCurrentVote() ?: return false
	val updated = vote.vote(voterId, target.id)
	put(updated)
	return true
}

suspend fun DataCache.setVoteChoices(choices: List<Snowflake>): Boolean {
	val vote = getCurrentVote() ?: return false
	val updated = vote.setChoices(choices)
	put(updated)
	return true
}

suspend fun DataCache.voteCorbeau(targetId: Snowflake): Boolean {
	val vote = getCurrentVote(LGState.DAY) ?: return false
	val updated = vote.voteCorbeau(targetId)
	put(updated)
	return true
}