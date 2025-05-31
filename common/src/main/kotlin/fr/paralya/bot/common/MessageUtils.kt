package fr.paralya.bot.common

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.request.RestRequestException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private val logger = KotlinLogging.logger("MessageUtils")

/**
 * Sends a temporary message to a channel and deletes it after a specified delay.
 *
 * @param delay The delay in milliseconds before the message is deleted. Default is 10 milliseconds.
 * @param messageBuilder A lambda to build the message content.
 * @return The sent message.
 */
suspend fun MessageChannelBehavior.sendTemporaryMessage(delay: Duration = 10.seconds, messageBuilder: UserMessageCreateBuilder.() -> Unit): Message {
	return createMessage(messageBuilder).also {
		it.kord.launch(
			context = Dispatchers.IO + CoroutineName("TemporaryMessageDeletion-${it.id}"),
		) {
			delay(delay)
			try {
				it.delete()
			} catch (e: RestRequestException) {
				logger.error(e) { "Failed to delete temporary message" }
			}
		}
	}
}

/**
 * Sends a temporary message to a channel with a simple string content and deletes it after a specified delay.
 *
 * @param message The content of the message to send.
 * @param delay The delay in milliseconds before the message is deleted. Default is 10 milliseconds.
 * @return The sent message.
 */
suspend fun MessageChannelBehavior.sendTemporaryMessage(message: String, delay: Duration = 10.seconds): Message {
	return sendTemporaryMessage(delay) {
		content = message
	}
}


/**
 * Checks if two messages are similar based on their content and attachments.
 *
 * @param msg1 The first message to compare.
 * @param msg2 The second message to compare.
 * @return `true` if the messages are similar, `false` otherwise.
 */
fun areMessagesSimilar(msg1: Message, msg2: Message): Boolean {
	if (msg1.content != msg2.content) return false

	val attachments1 = msg1.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }
	val attachments2 = msg2.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }

	return attachments1 == attachments2
}

/**
 * Retrieves the corresponding message in the channel based on the timestamp of the provided message.
 *
 * @param message The message to find the corresponding message for.
 * @return The corresponding message if found, or null if not found.
 */
suspend fun MessageChannelBehavior.getCorrespondingMessage(message: Message): Message? {
	val date = message.timestamp

	val beforeMessage = getMessagesBefore(Snowflake.max, 20)
		.filter { it.timestamp >= date }
		.toList()
		.sortedBy { it.timestamp }
		.firstOrNull { areMessagesSimilar(message, it) }

	if (beforeMessage != null) return beforeMessage
	return getMessagesAfter(Snowflake.min, 20)
		.filter { it.timestamp <= date }
		.toList()
		.sortedByDescending { it.timestamp }
		.firstOrNull { areMessagesSimilar(message, it) } ?: run {
			logger.warn { "No corresponding similar message found for message ${message.id} when searching in channel $id" }
			null
		}
}