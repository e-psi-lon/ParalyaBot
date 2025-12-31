package fr.paralya.bot.lg

import dev.kord.core.entity.User
import dev.kordex.core.koin.KordExKoinComponent
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.data.Target
import org.koin.core.component.inject

/**
 * Manager class responsible for handling voting operations in the Werewolf game.
 * This provides a centralized way to manage votes for both day and night phases.
 */
class VoteManager : KordExKoinComponent {
	private val lg by inject<LG>()
	private val botCache by lazy { lg.botCache }


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
	suspend fun vote(voterId: Voter, target: User) {
		botCache.vote(voterId, target)
	}

    suspend fun unvote(voterId: Voter) {
        botCache.unvote(voterId)
    }

	/**
	 * Registers a vote from the Corbeau role
	 * @param targetId The ID of the player being marked by the Corbeau
	 */
	suspend fun voteCorbeau(targetId: Target) {
		botCache.voteCorbeau(targetId)
	}

    /**
     * Removes the Corbeau's vote
     */
	suspend fun unvoteCorbeau() {
		botCache.unvoteCorbeau()
	}


	/**
	 * Creates a new vote for the village (day phase)
	 * @return The newly created vote data
	 */
	suspend fun createVillageVote(): VoteData {
		val newVote =
			botCache.getCurrentVote(LGState.DAY) ?: VoteData.createVillageVote(System.currentTimeMillis().snowflake)
				.setCurrent(true)
		botCache.putVote(newVote)
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
		botCache.putVote(newVote)
		return newVote
	}

	/**
	 * Updates an existing vote with new data
	 * @param voteData The vote data to update
	 */
	suspend fun updateVote(voteData: VoteData) {
		botCache.putVote(voteData)
	}

	suspend fun resetVotes(state: LGState) {
        botCache.putVote(
            getCurrentVote(state)?.copy(
                votes = emptyMap()
            ) ?: return
        )
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
	 * Counts votes for a given vote, including corbeau bonus for day votes
	 * @param vote The vote data to count
	 * @return Map of target IDs to their vote counts
	 */
	fun getVoteCount(vote: VoteData): Map<Target, Int> {
		val voteCount = vote.votes.values.groupingBy { it }.eachCount().toMutableMap()

		// Add corbeau vote if present (only for day votes)
		if (vote.type == LGState.DAY && vote.corbeau != 0.snowflake) voteCount[vote.corbeau] = (voteCount[vote.corbeau] ?: 0) + 2

		return voteCount
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
		val voteCount = getVoteCount(vote)
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