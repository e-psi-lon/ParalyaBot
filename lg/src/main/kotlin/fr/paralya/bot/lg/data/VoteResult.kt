package fr.paralya.bot.lg.data

import dev.kord.common.entity.Snowflake

sealed interface VoteResult {
	data object NoVotes : VoteResult
	@JvmInline
    value class Tie(val players: List<Snowflake>) : VoteResult {
		fun toList() = players
	}

	@JvmInline
    value class Killed(val player: Snowflake) : VoteResult
}
