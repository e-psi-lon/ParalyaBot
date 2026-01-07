package fr.paralya.bot.lg

import dev.kord.core.cache.idEq
import dev.kord.core.entity.User
import dev.kordex.core.koin.KordExKoinComponent
import fr.paralya.bot.common.cache.atomic
import fr.paralya.bot.common.cache.idEq
import fr.paralya.bot.common.cache.putSerialized
import fr.paralya.bot.common.cache.querySerialized
import fr.paralya.bot.common.cache.updateSerialized
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.data.GamePhase.PhaseType
import fr.paralya.bot.lg.data.Target
import kotlinx.coroutines.sync.Mutex
import org.koin.core.component.inject

/**
 * Manager class responsible for handling voting operations in the Werewolf game.
 * This provides a centralized way to manage votes for both day and night phases.
 */
class VoteManager : KordExKoinComponent {
	private val lg by inject<LG>()
	private val botCache by lazy { lg.botCache }
	private val pluginNamespace by lazy { lg.pluginRef.pluginId }
	private val voteMutex = Mutex()


	/**
	 * Gets the current vote for the specified game phase
	 * @param phase The game phase (DAY or NIGHT)
	 * @return The current VoteData for the specified phase, or null if none exists
	 */
	suspend fun getCurrentVote(phase: PhaseType?): VoteData? {
		val queryType = phase ?: botCache.getGameData().phase.type
		return botCache.querySerialized<VoteData>(pluginNamespace, VoteData::id) {
			idEq(VoteData::type, queryType)
			idEq(VoteData::isCurrent, true)
		}.singleOrNull()
	}

	private suspend fun updateCurrentVote(
		type: PhaseType? = null,
		transform: (VoteData) -> VoteData
	) = botCache.atomic(voteMutex) {
		val queryType = type ?: getGameData().phase.type
		updateSerialized(pluginNamespace, VoteData::id, block = {
			idEq(VoteData::type, queryType)
			idEq(VoteData::isCurrent, true)
		}, transform = transform)
	}

	suspend fun putVote(voteData: VoteData) =
		botCache.putSerialized(pluginNamespace, voteData, VoteData::id)

	/**
	 * Registers a vote from a user for a target
	 * @param voterId The ID of the voter
	 * @param target The user being voted for
	 */
	suspend fun vote(voterId: Voter, target: User)  = updateCurrentVote { it.vote(voterId, target.id) }

    suspend fun unvote(voterId: Voter) = updateCurrentVote { it.unvote(voterId) }

	suspend fun setVoteChoices(choices: List<Target>) = updateCurrentVote { it.setChoices(choices) }

	/**
	 * Registers a vote from the Corbeau role
	 * @param targetId The ID of the player being marked by the Corbeau
	 */
	suspend fun voteCorbeau(targetId: Target) = updateCurrentVote { it.voteCorbeau(targetId) }

    /**
     * Removes the Corbeau's vote
     */
	suspend fun unvoteCorbeau() = updateCurrentVote { it.unvoteCorbeau() }


	/**
	 * Creates a new vote for the village (day phase)
	 * @return The newly created vote data
	 */
	suspend fun createVillageVote(): VoteData = createVote(PhaseType.DAY)

	/**
	 * Creates a new vote for the werewolves (night phase)
	 * @return The newly created vote data
	 */
	suspend fun createWerewolfVote(): VoteData = createVote(PhaseType.NIGHT)

	private suspend fun createVote(phase: PhaseType)  = botCache.atomic(voteMutex) {
		val newVote = (
			getCurrentVote(phase) ?:
			VoteData.createVote(phase, System.currentTimeMillis().snowflake, true)
		).setCurrent(true)
		putVote(newVote)
		newVote
	}

	suspend fun resetVotes(phase: PhaseType) = botCache.atomic(voteMutex) {
        putVote(
            getCurrentVote(phase)?.copy(
                votes = emptyMap()
            ) ?: return@atomic
        )
    }

	/**
	 * Finishes the current vote for a specific game phase
	 * @param phase The game phase (DAY or NIGHT)
	 * @return The finished vote data with current=false
	 */
	suspend fun finishCurrentVote(phase: PhaseType): VoteData? = botCache.atomic(voteMutex) {
		val currentVote = getCurrentVote(phase) ?: return@atomic null
		currentVote.setCurrent(false).also { putVote(it) }
	}

	/**
	 * Counts votes for a given vote, including corbeau bonus for day votes
	 * @param vote The vote data to count
	 * @return Map of target IDs to their vote counts
	 */
	fun getVoteCount(vote: VoteData): Map<Target, Int> {
		val voteCount = vote.votes.values.groupingBy { it }.eachCount().toMutableMap()

		// Add corbeau vote if present (only for day votes)
		if (vote.type == PhaseType.DAY && vote.corbeau != null) voteCount[vote.corbeau] = (voteCount[vote.corbeau] ?: 0) + 2

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