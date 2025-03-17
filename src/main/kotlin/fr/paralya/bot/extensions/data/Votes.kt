package fr.paralya.bot.extensions.data

import dev.kord.core.entity.User
import kotlinx.serialization.Serializable


open class Votes {
    open fun vote(target: User): Votes = this
    open fun setChoices(choices: List<ULong>): Votes = this
    open fun setCurrent(isCurrent: Boolean): Votes = this
}


@Serializable
data class WereWolfVotes(
    val isCurrent: Boolean = false,
    val votes: Map<ULong, ULong> = mapOf(),
    val choices: List<ULong> = listOf()
): Votes() {

    override fun vote(target: User) = this.copy(
        votes = votes + (target.id.value to votes.getOrDefault(target.id.value, 0.toULong()) + 1.toULong())
    )
    override fun setChoices(choices: List<ULong>) = this.copy(choices = choices)
    override fun setCurrent(isCurrent: Boolean) = this.copy(isCurrent = isCurrent)
}

@Serializable
data class VillageVotes(
    val isCurrent: Boolean = false,
    val votes: MutableMap<ULong, ULong> = mutableMapOf(),
    val choices: List<ULong> = listOf(),
    val corbeau: ULong = 0.toULong()
): Votes() {
    override fun vote(target: User) = this.copy(
        votes = votes.apply { this[target.id.value] = this.getOrDefault(target.id.value, 0.toULong()) + 1.toULong() }
    )

    fun voteCorbeau(target: ULong) = this.copy(corbeau = target)
    override fun setChoices(choices: List<ULong>) = this.copy(choices = choices)
    override fun setCurrent(isCurrent: Boolean) = this.copy(isCurrent = isCurrent)
}