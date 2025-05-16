package fr.paralya.bot.common

import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.execute
import dev.kord.core.cache.data.UserData
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.User
import dev.kord.core.entity.Webhook
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.WebhookMessageCreateBuilder
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.any
import dev.kordex.core.utils.hasRole
import dev.kordex.core.utils.permissionsForMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/**
 * Retrieves or creates a webhook in a specified channel.
 *
 * @param channel The ID of the channel where the webhook will be retrieved or created.
 * @param bot The instance of the [ExtensibleBot] used to interact with Discord.
 * @param name The name of the webhook to retrieve or create.
 * @param avatar An optional avatar image for the webhook. If not provided, Discord's default avatar will be used.
 * @return The retrieved or newly created webhook.
 */
suspend fun getWebhook(channel: Snowflake, bot: ExtensibleBot, name: String, avatar: Image? = null): Webhook {
	val webhooks = bot.kordRef.rest.webhook.getChannelWebhooks(channel)
	return (webhooks.firstOrNull { it.name == name } ?: bot.kordRef.rest.webhook.createWebhook(
		channel,
		name
	) {
		this.avatar = avatar ?: getAsset("bot")
	}).let {
		bot.kordRef.getWebhook(it.id)
	}
}

/**
 * Sends a message as a webhook in a specified channel.
 *
 * @param bot The instance of the [ExtensibleBot] used to interact with Discord.
 * @param channel The ID of the channel where the message will be sent.
 * @param name The name of the webhook to use or create.
 * @param avatar An optional avatar image for the webhook. If not provided, Discord's default avatar will be used.
 * @param message A message builder block to configure the message content and properties.
 * @return The message sent by the webhook, or null if the webhook token is unavailable.
 *
 * This function retrieves or creates a webhook in the specified channel, then uses it to send a message
 * with the provided content and properties. The webhook is identified by its name and optionally customized
 * with an avatar image.
 */
suspend fun sendAsWebhook(
	bot: ExtensibleBot,
	channel: Snowflake,
	name: String,
	avatar: Image? = null,
	webhookName: String? = null,
	message: suspend WebhookMessageCreateBuilder.() -> Unit
): Message? {
	val webhook = getWebhook(channel, bot, webhookName ?: name, avatar)
	return webhook.token?.let {
		webhook.execute(it) {
			username = name
			message()
		}
	}
}

/**
 * Sends a message as a webhook in a specified channel with a string avatar URL.
 *
 * @param bot The instance of the [ExtensibleBot] used to interact with Discord.
 * @param channel The ID of the channel where the message will be sent.
 * @param name The name of the webhook to use or create.
 * @param avatar An optional avatar URL for the webhook. If not provided, Discord's default avatar will be used.
 * @param message A message builder block to configure the message content and properties.
 * @return The message sent by the webhook, or null if the webhook token is unavailable.
 */
suspend fun sendAsWebhook(
	bot: ExtensibleBot,
	channel: Snowflake,
	name: String,
	avatar: String? = null,
	webhookName: String? = null,
	message: suspend WebhookMessageCreateBuilder.() -> Unit
): Message? {
	val webhook = getWebhook(channel, bot, webhookName ?: name)
	return webhook.token?.let {
		webhook.execute(it) {
			avatarUrl = avatar
			username = name
			message()
		}
	}
}

/**
 * Translates a key with the current translation context and optional replacements.
 *
 * @param replacements Optional replacements to include in the translation.
 * @return The translated string.
 */
context(TranslatableContext)
suspend fun Key.translateWithContext(vararg replacements: Any?) =
	withContext(this@TranslatableContext).translate(*replacements)

/**
 * Retrieves an image asset from the specified path.
 *
 * @param path The path to the asset file (in the `assets` resource directory).
 * @return The image as an [Image] object.
 * @throws IllegalArgumentException if the resource is not found at the specified path.
 */
suspend fun getAsset(path: String, game: String? = null): Image {
	val resource = object {}.javaClass.getResourceAsStream("/assets/${if (game != null) "$game/" else ""}$path.webp")
		?: throw IllegalArgumentException("Resource at path /assets/${if (game != null) "$game/" else ""}$path not found")
	return Image.raw(withContext(Dispatchers.IO) {
		resource.readAllBytes().also {
			resource.close()
		}
	}, Image.Format.WEBP)
}

/**
 * Retrieves a flow of members who have access to the current text channel.
 *
 * @return A flow of [Member] objects representing the members with access to the channel.
 */
suspend fun TextChannel.getMembersWithAccess(): Flow<Member> {
	return guild.members.filter { member ->
		this.permissionsForMember(member).contains(Permission.ViewChannel)
	}
}

/**
 * Filter a [Flow] of member objects to only include those who have a specific role.
 *
 * @param roleId The ID of the role to filter by.
 * @return A flow of [Member] objects who have the specified role.
 */
suspend fun Flow<Member>.filterByRole(roleId: Snowflake): Flow<Member> {
	return filter { member -> member.roles.any { it.id == roleId } }
}

fun Flow<Member>.filterByRole(role: RoleBehavior): Flow<Member> {
	return filter { it.hasRole(role) }
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
 * @param channel The channel where the messages are located.
 * @param message The message to find the corresponding message for.
 * @return The corresponding message if found, or null if not found.
 */
suspend fun getCorrespondingMessage(channel: MessageChannelBehavior, message: Message): Message? {
	val date = message.timestamp

	channel.getMessagesBefore(Snowflake.max, 20)
		.filter { it.timestamp >= date }
		.toList()
		.sortedBy { it.timestamp }
		.forEach { if (areMessagesSimilar(message, it)) return it }

	channel.getMessagesAfter(Snowflake.min, 20)
		.filter { it.timestamp <= date }
		.toList()
		.sortedByDescending { it.timestamp }
		.forEach { if (areMessagesSimilar(message, it)) return it }

	return null
}

/**
 * Converts a [DiscordUser] to a [User] using the provided [Kord] instance.
 */
fun DiscordUser?.asUser(kord: Kord) = this?.let { User(UserData.from(it), kord) }

/** Extension properties to convert various number types to a [Snowflake] */
val Number.snowflake get() = Snowflake(this.toLong())

/** @see [Number.snowflake] */
val ULong.snowflake get() = Snowflake(this)