package fr.paralya.bot.lg.data

import dev.kord.common.entity.Snowflake

sealed class VoteResult {
	data object NoVotes: VoteResult()
	data class Tie(val players: List<Snowflake>): VoteResult() {
		fun toList() = players
	}
	data class Killed(val player: Snowflake): VoteResult()
}