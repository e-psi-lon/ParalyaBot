package fr.paralya.bot.lg.data

import dev.kordex.core.extensions.Extension

/**
 * Enum class for a typesafe accessor of channels in the game.
 */
enum class LgChannelType {
	// Village channels
	ANNONCES_VILLAGE,
	SUJETS,
	VILLAGE,
	VOTES,

	// Special roles channels
	CORBEAU,
	INTERVIEW,
	PETITE_FILLE,

	// Werewolf channels
	LOUPS_CHAT,
	LOUPS_VOTE;

	context(extension: Extension)
    internal suspend fun toId() = extension.kord.cache.getChannelId(this)
}