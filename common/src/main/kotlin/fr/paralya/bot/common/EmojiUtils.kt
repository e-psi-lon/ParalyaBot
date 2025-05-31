package fr.paralya.bot.common

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import kotlinx.coroutines.delay

/**
 * Converts a string emoji (unicode or custom emoji format) to a [ReactionEmoji].
 *
 * @param emoji The emoji string to convert (e.g. "👍" or `<:name:id>")
 * @return The corresponding [ReactionEmoji]
 */
fun parseEmoji(emoji: String): ReactionEmoji {
	val customEmojiRegex = "<(a)?:([a-zA-Z0-9_]+):([0-9]+)>".toRegex()
	val match = customEmojiRegex.matchEntire(emoji)

	return if (match != null) {
		val (animated, name, id) = match.destructured
		val isAnimated = animated.isNotEmpty()
		ReactionEmoji.Custom(Snowflake(id), name, isAnimated)
	} else {
		ReactionEmoji.Unicode(emoji)
	}
}

/**
 * Adds multiple reactions to a message.
 *
 * @param emojis List of emoji strings to add as reactions
 */
suspend fun Message.addReactions(emojis: List<String>) {
	for (emoji in emojis) {
		addReaction(parseEmoji(emoji))
		delay(250) // Add small delay to avoid rate limiting
	}
}

/**
 * Adds multiple reactions to a message.
 *
 * @param emojis Vararg of emoji strings to add as reactions
 */
suspend fun Message.addReactions(vararg emojis: String) {
	addReactions(emojis.toList())
}
/**
 * Formats a ReactionEmoji for display in a message.
 *
 * @return The formatted emoji string
 */
fun ReactionEmoji.format(): String {
	return when (this) {
		is ReactionEmoji.Custom -> this.mention
		is ReactionEmoji.Unicode -> this.name
	}
}

/**
 * Extension function to easily add an emoji to message content in builders.
 *
 * @param emoji The emoji to add (unicode or custom emoji format)
 */
fun MessageCreateBuilder.appendEmoji(emoji: String) {
	content = (content ?: "") + emoji
}
