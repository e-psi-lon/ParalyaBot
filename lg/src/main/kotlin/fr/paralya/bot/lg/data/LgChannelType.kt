package fr.paralya.bot.lg.data

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
	LOUPS_VOTE
}