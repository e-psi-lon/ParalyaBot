package fr.paralya.bot.extensions.data

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.data.DataDescription
import dev.kord.cache.api.data.description
import dev.kord.cache.api.put
import dev.kord.cache.api.query
import dev.kord.common.entity.Snowflake
import dev.kord.core.cache.idEq
import dev.kord.core.entity.User
import fr.paralya.bot.utils.toSnowflake
import kotlinx.serialization.Serializable

@Serializable
data class VoteData(
    val id: Snowflake,
    val type: String, // "WEREWOLF" or "VILLAGE"
    val isCurrent: Boolean = false,
    val votes: Map<Snowflake, Snowflake> = emptyMap(), //
    val choices: List<Snowflake> = listOf(),
    val corbeau: Snowflake = 0.toSnowflake()
) {
    companion object {
        val description: DataDescription<VoteData, Snowflake> = description(VoteData::id)

        fun createWerewolfVote(id: Snowflake, isCurrent: Boolean = false): VoteData =
            VoteData(id, "WEREWOLF", isCurrent)

        fun createVillageVote(id: Snowflake, isCurrent: Boolean = false): VoteData =
            VoteData(id, "VILLAGE", isCurrent)
    }

    fun vote(voterId: Snowflake, targetId: Snowflake): VoteData {
        val updatedVotes = votes.toMutableMap()
        updatedVotes[voterId] = targetId
        return copy(votes = updatedVotes)
    }

    fun voteCorbeau(targetId: Snowflake): VoteData {
        return if (type == "VILLAGE") copy(corbeau = targetId) else this
    }

    fun setChoices(choices: List<Snowflake>): VoteData = copy(choices = choices)

    fun setCurrent(isCurrent: Boolean): VoteData = copy(isCurrent = isCurrent)
}

suspend fun DataCache.getCurrentVote(type: String): VoteData? {
    return query<VoteData> {
        this.idEq(VoteData::type, type)
        this.idEq(VoteData::isCurrent, true)
    }.singleOrNull()
}

suspend fun DataCache.updateVote(voteData: VoteData) {
    put(voteData)
}

suspend fun DataCache.vote(type: String, voterId: Snowflake, target: User): Boolean {
    val vote = getCurrentVote(type) ?: return false
    val updated = vote.vote(voterId, target.id)
    put(updated)
    return true
}

suspend fun DataCache.setVoteChoices(voteType: String, choices: List<Snowflake>): Boolean {
    val vote = getCurrentVote(voteType) ?: return false
    val updated = vote.setChoices(choices)
    put(updated)
    return true
}

suspend fun DataCache.voteCorbeau(voteType: String, targetId: Snowflake): Boolean {
    val vote = getCurrentVote(voteType) ?: return false
    if (vote.type != "VILLAGE") return false
    val updated = vote.voteCorbeau(targetId)
    put(updated)
    return true
}