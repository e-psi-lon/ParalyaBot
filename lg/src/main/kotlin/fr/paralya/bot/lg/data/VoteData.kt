package fr.paralya.bot.lg.data

import dev.kord.cache.api.*
import dev.kord.cache.api.data.description
import dev.kord.common.entity.Snowflake
import dev.kord.core.cache.idEq
import dev.kord.core.entity.User
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.lg.LGState

/**
 * Represents voting data for a Loup-Garou (Werewolf) game.
 *
 * @property id The unique identifier for this vote session
 * @property type The type of vote, corresponding to a game state (e.g., DAY or NIGHT)
 * @property isCurrent Flag indicating if this vote is the current active vote
 * @property votes Map of voter IDs to their voted target IDs
 * @property choices List of possible choices/targets for this vote
 * @property corbeau Special "Raven" vote, used for the Corbeau role's additional vote
 */
data class VoteData(
	val id: Snowflake,
	val type: String,
	val isCurrent: Boolean = false,
	val votes: Map<Snowflake, Snowflake> = emptyMap(),
	val choices: List<Snowflake> = listOf(),
	val corbeau: Snowflake = 0.snowflake,
) {
	companion object {
		/** Cache description for VoteData, allowing it to be stored and retrieved from the DataCache */
		val description = description<VoteData, Snowflake>(VoteData::id)

		/**
		 * Creates a new werewolf vote for the night phase.
		 *
		 * @param id The unique identifier for this vote
		 * @param isCurrent Whether this vote should be marked as current
		 * @return A new VoteData instance for werewolf voting
		 */
		fun createWerewolfVote(id: Snowflake, isCurrent: Boolean = true) =
			VoteData(id, LGState.NIGHT.name, isCurrent)
		/**
		 * Creates a new village vote for the day phase.
		 *
		 * @param id The unique identifier for this vote
		 * @param isCurrent Whether this vote should be marked as current
		 * @return A new VoteData instance for village voting
		 */
		fun createVillageVote(id: Snowflake, isCurrent: Boolean = true) =
			VoteData(id, LGState.DAY.name, isCurrent)
	}

	/**
	 * Records a vote from a voter targeting another player.
	 *
	 * @param voterId The ID of the player casting the vote
	 * @param targetId The ID of the player being voted for
	 * @return A new VoteData with the updated votes map
	 */
	fun vote(voterId: Snowflake, targetId: Snowflake) =
		copy(votes = votes + (voterId to targetId))

	/**
	 * Records a special Corbeau (Raven) vote if this is a day vote.
	 * The Corbeau role can place an additional vote on a player.
	 *
	 * @param targetId The ID of the player receiving the Corbeau's vote
	 * @return A new VoteData with the updated Corbeau vote, or unchanged if not a day vote
	 */
	fun voteCorbeau(targetId: Snowflake) =
		if (type == LGState.DAY.name) copy(corbeau = targetId) else this

	/**
	 * Updates the list of valid voting choices/targets.
	 *
	 * @param choices The list of player IDs that can be voted for
	 * @return A new VoteData with the updated choices
	 */
	fun setChoices(choices: List<Snowflake>) = copy(choices = choices)

	/**
	 * Updates the current status of this vote.
	 *
	 * @param isCurrent Whether this vote is the current active vote
	 * @return A new VoteData with the updated current status
	 */
	fun setCurrent(isCurrent: Boolean) = copy(isCurrent = isCurrent)
}

/**
 * Retrieves the current active vote of the specified type from the cache.
 *
 * @param type Optional game state to specify which type of vote to retrieve
 * @return The current VoteData matching the type, or null if none exists
 */
suspend fun DataCache.getCurrentVote(type: LGState? = null): VoteData? {
	val queryType = type?.name ?: getGameData().state.name
	return query<VoteData> {
		idEq(VoteData::type, queryType)
		idEq(VoteData::isCurrent, true)
	}.singleOrNull()
}

/**
 * Updates the vote data in the cache.
 *
 * @param voteData The vote data to update
 */
suspend fun DataCache.updateVote(voteData: VoteData) {
	put(voteData)
}
/**
 * Records a vote from a player to a target in the current vote.
 *
 * @param voterId The ID of the player casting the vote
 * @param target The user being voted for
 * @return true if the vote was recorded, false if there's no current vote
 */
suspend fun DataCache.vote(voterId: Snowflake, target: User) =
	getCurrentVote()?.let {
		put(it.vote(voterId, target.id))
		true
	} ?: false

/**
 * Updates the list of valid choices/targets for the current vote.
 *
 * @param choices The list of player IDs that can be voted for
 * @return true if the choices were updated, false if there's no current vote
 */
suspend fun DataCache.setVoteChoices(choices: List<Snowflake>) =
	getCurrentVote()?.let {
		put(it.setChoices(choices))
		true
	} ?: false

/**
 * Records a special Corbeau (Raven) vote in the current day vote.
 *
 * @param targetId The ID of the player receiving the Corbeau's vote
 * @return true if the Corbeau vote was recorded, false if there's no current day vote
 */
suspend fun DataCache.voteCorbeau(targetId: Snowflake) =
	getCurrentVote(LGState.DAY)?.let {
		put(it.voteCorbeau(targetId))
		true
	} ?: false