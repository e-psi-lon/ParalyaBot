package fr.paralya.bot.extensions.lg

import dev.kord.core.entity.User
import kotlinx.serialization.Serializable

@Serializable
data class WereWolfVotes(
    var isCurrent: Boolean = false,
    val votes: MutableMap<ULong, ULong> = mutableMapOf(),
    var choices: List<ULong> = listOf()
) {

    fun vote(target: User, reason: String?) {
        votes[target.id.value] = votes.getOrDefault(target.id.value, 0.toULong()) + 1.toULong()
    }
}

@Serializable
data class VillageVotes(
    var isCurrent: Boolean = false,
    val votes: MutableMap<ULong, ULong> = mutableMapOf(),
    var choices: List<ULong> = listOf(),
    var corbeau: ULong = 0.toULong()
) {
    fun vote(target: ULong) {
        votes[target] = votes.getOrDefault(target, 0.toULong()) + 1.toULong()
    }
}