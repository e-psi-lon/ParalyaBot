package fr.paralya.bot.lg

import dev.kord.cache.api.DataCache
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.User
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.lg.data.*

/**
 * Manager class responsible for handling voting operations in the Werewolf game.
 * This provides a centralized way to manage votes for both day and night phases.
 */
class VoteManager(private val botCache: DataCache) {

	/**
	 * Gets the current vote for the specified game state
	 * @param state The game state (DAY or NIGHT)
	 * @return The current VoteData for the specified state, or null if none exists
	 */
	suspend fun getCurrentVote(state: LGState): VoteData? {
		return botCache.getCurrentVote(state)
	}

	/**
	 * Registers a vote from a user for a target
	 * @param voterId The ID of the voter
	 * @param target The user being voted for
	 */
	suspend fun vote(voterId: Snowflake, target: User) {
		botCache.vote(voterId, target)
	}

	/**
	 * Registers a vote from the Raven role
	 * @param targetId The ID of the player being marked by the Raven
	 */
	suspend fun voteCorbeau(targetId: Snowflake) {
		botCache.voteCorbeau(targetId)
	}

	/**
	 * Creates a new vote for the village (day phase)
	 * @return The newly created vote data
	 */
	suspend fun createVillageVote(): VoteData {
		val newVote =
			botCache.getCurrentVote(LGState.DAY) ?: VoteData.createVillageVote(System.currentTimeMillis().snowflake)
				.setCurrent(true)
		botCache.updateVote(newVote)
		return newVote
	}

	/**
	 * Creates a new vote for the werewolves (night phase)
	 * @return The newly created vote data
	 */
	suspend fun createWerewolfVote(): VoteData {
		val newVote =
			botCache.getCurrentVote(LGState.NIGHT) ?: VoteData.createWerewolfVote(System.currentTimeMillis().snowflake)
				.setCurrent(true)
		botCache.updateVote(newVote)
		return newVote
	}

	/**
	 * Updates an existing vote with new data
	 * @param voteData The vote data to update
	 */
	suspend fun updateVote(voteData: VoteData) {
		botCache.updateVote(voteData)
	}

	/**
	 * Finishes the current vote for a specific game state
	 * @param state The game state (DAY or NIGHT)
	 * @return The finished vote data with current=false
	 */
	suspend fun finishCurrentVote(state: LGState): VoteData? {
		val currentVote = getCurrentVote(state) ?: return null
		currentVote.setCurrent(false)
		updateVote(currentVote)
		return currentVote
	}

	/**
	 * Calculates the result of a vote
	 * @param vote The vote data to calculate results for
	 * @param kill Whether to kill the voted player
	 * @param force Whether to force the vote result
	 * @return The result of the vote as a VoteResult object
	 */
	fun calculateVoteResult(
		vote: VoteData,
		kill: Boolean,
		force: Boolean
	): VoteResult {
		val voteCount = vote.votes.values.groupingBy { it }.eachCount()
		val maxVote = voteCount.maxByOrNull { it.value }?.key
		val maxVotedPlayers = voteCount.filter { it.key == maxVote }.keys

		return when {
			voteCount.isEmpty() -> VoteResult.NoVotes
			maxVotedPlayers.size > 1 && !force -> VoteResult.Tie(maxVotedPlayers.toList())
			maxVotedPlayers.size == 1 && kill -> VoteResult.Killed(maxVotedPlayers.first())
			else -> VoteResult.NoVotes
		}
	}
}