package fr.paralya.bot.common

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.execute
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.Webhook
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.WebhookMessageCreateBuilder
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.SlashCommandContext
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.permissionsForMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

/**
 * Retrieves or creates a webhook in a specified channel.
 *
 * @param channel The ID of the channel where the webhook will be retrieved or created.
 * @param bot The instance of the ExtensibleBot used to interact with Discord.
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
 * @param bot The instance of the ExtensibleBot used to interact with Discord.
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
context(C)
suspend fun <C : SlashCommandContext<*, A, M>, A : Arguments, M : ModalForm> sendAsWebhook(
	bot: ExtensibleBot,
	channel: Snowflake,
	name: String,
	avatar: Image? = null,
	message: suspend WebhookMessageCreateBuilder.() -> Unit
): Message? {
	val webhook = getWebhook(channel, bot, name, avatar)
	return webhook.token?.let { webhook.execute(it) { message() } }
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
 * @return The image as an `Image` object.
 * @throws IllegalArgumentException if the resource is not found at the specified path.
 */
suspend fun getAsset(path: String): Image {
	val resource = object {}.javaClass.getResourceAsStream("/assets/$path.webp")
		?: throw IllegalArgumentException("Resource at $path not found")
	return Image.raw(withContext(Dispatchers.IO) {
		resource.readAllBytes().also {
			resource.close()
		}
	}, Image.Format.WEBP)
}

/**
 * Retrieves a flow of members who have access to the current text channel.
 *
 * @return A flow of `Member` objects representing the members with access to the channel.
 */
suspend fun TextChannel.getMembersWithAccess(): Flow<Member> {
	return guild.members.filter { member ->
		this.permissionsForMember(member).contains(Permission.ViewChannel)
	}
}

fun ULong.toSnowflake() = Snowflake(this)
fun Long.toSnowflake() = Snowflake(this)
fun Int.toSnowflake() = Snowflake(this.toLong())