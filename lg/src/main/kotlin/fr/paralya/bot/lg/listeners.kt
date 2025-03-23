package fr.paralya.bot.lg

import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.execute
import dev.kord.core.cache.data.UserData
import dev.kord.core.entity.Message
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.getCategory
import fr.paralya.bot.common.ConfigManager
import fr.paralya.bot.common.getWebhook
import fr.paralya.bot.common.toSnowflake
import fr.paralya.bot.lg.data.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.koin.core.component.inject


fun DiscordUser?.asUser(kord: Kord) = this?.let { User(UserData.from(it), kord) }
suspend fun LG.registerListeners() {
	val lgConfig by inject<LgConfig>()
	val manager by inject<ConfigManager>()
	val botConfig = manager.botConfig
	event<MessageCreateEvent> {
		action {
			val message = event.message
			if (message.getGuildOrNull() == null && message.author?.isSelf != true && message.content.isNotEmpty()) {
				val webhook = getWebhook(botConfig.dmLogChannelId.toSnowflake(), bot, "LG")
				webhook.execute(webhook.token!!) {
					content = message.content
					username = message.author?.tag ?: "Inconnu"
					avatarUrl = message.author?.avatar?.cdnUrl?.toUrl()
				}
			} else if (message.channelId == botCache.getChannel("INTERVIEW")) {
				if (message.author?.id in botCache.getInterviews()) {
					botCache.removeInterview(message.author!!.id)
					(message.channel as TopGuildChannel).addOverwrite(
						PermissionOverwrite.forMember(
							message.author!!.id,
							denied = Permissions(Permission.SendMessages)
						)
					)
				}
			}
		}
	}

	event<MessageUpdateEvent> {
		action {
			val oldMessage = event.old?.let {
				getCorrespondingMessage(
					MessageChannelBehavior(botConfig.dmLogChannelId.toSnowflake(), kord),
					it
				)
			}
			if (oldMessage == null) {
				val webhook = getWebhook(botConfig.dmLogChannelId.toSnowflake(), bot, "LG")
				webhook.execute(webhook.token!!) {
					content = event.new.content.toString()
					username = event.new.author.value?.asUser(kord)?.tag ?: "Inconnu"
					avatarUrl = event.new.author.value.asUser(kord)?.avatar?.cdnUrl?.toUrl()
				}
			}
		}
	}

	event<MessageDeleteEvent> {
		action {
			val oldMessage = event.message?.let {
				getCorrespondingMessage(
					MessageChannelBehavior(
						botConfig.dmLogChannelId.toSnowflake(),
						kord
					), it
				)
			}
			if (oldMessage == null) {
				val webhook = getWebhook(botConfig.dmLogChannelId.toSnowflake(), bot, "LG")
				webhook.deleteMessage(webhook.token!!, event.message!!.id)
			}
		}
	}

	event<ReadyEvent> {
		action {
			logger.debug { "Fetching channels from categories loup-garou and roles from loup-garou" }
			val paralya = event.guilds.firstOrNull { it.id.value == botConfig.paralyaId } ?: throw IllegalStateException("Paralya guild not found")
			val lGRolesMap = collectChannelsFromCategory(lgConfig.rolesCategory.toSnowflake(), paralya)
			logger.debug { "Found ${lGRolesMap.size} channels in the roles category of werewolf game" }
			val lGMainMap = collectChannelsFromCategory(lgConfig.rolesCategory.toSnowflake(), paralya)
			logger.debug { "Found ${lGMainMap.size} channels in the main category of werewolf game" }
			lGRolesMap.map { (name, value) ->
				botCache.registerChannel(name, value)
			}
			lGMainMap.map { (name, value) ->
				botCache.registerChannel(name, value)
			}
		}
	}
}

private suspend fun collectChannelsFromCategory(categoryId: Snowflake, guild: GuildBehavior): Map<String, Snowflake> {
	return guild.channels
		.filter { it.getCategory()?.id == categoryId }
		.map { channel ->
			val channelName = channel.name
				.replace(Regex("[^A-Za-z0-9_-]"), "")
				.removePrefix("-")
				.replace("-", "_")
				.uppercase()

			channelName to channel.id
		}.toList().toMap()
		.filterKeys { it.isNotBlank() && it != "_".repeat(it.length) }
}


fun areMessagesSimilar(msg1: Message, msg2: Message): Boolean {
	if (msg1.content != msg2.content) return false

	val attachments1 = msg1.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }
	val attachments2 = msg2.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }

	return attachments1 == attachments2
}

// Function to find corresponding message in a channel
suspend fun getCorrespondingMessage(channel: MessageChannelBehavior, message: Message): Message? {
	val date = message.timestamp

	// Look for messages after the original message
	channel.getMessagesBefore(Snowflake.max, 20)
		.filter { it.timestamp >= date }
		.toList()
		.sortedBy { it.timestamp }
		.forEach { msg ->
			if (areMessagesSimilar(message, msg)) {
				return msg
			}
		}

	// Look for messages before the original message
	channel.getMessagesAfter(Snowflake.min, 20)
		.filter { it.timestamp <= date }
		.toList()
		.sortedByDescending { it.timestamp }
		.forEach { msg ->
			if (areMessagesSimilar(message, msg)) {
				return msg
			}
		}

	return null
}