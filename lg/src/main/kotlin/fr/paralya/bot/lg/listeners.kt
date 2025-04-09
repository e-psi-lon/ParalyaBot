package fr.paralya.bot.lg

import dev.kord.common.entity.*
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.cache.data.UserData
import dev.kord.core.entity.*
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.*
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.getCategory
import fr.paralya.bot.common.*
import fr.paralya.bot.lg.data.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.inject

fun DiscordUser?.asUser(kord: Kord) = this?.let { User(UserData.from(it), kord) }

suspend fun LG.registerListeners() {
	val lgConfig by inject<LgConfig>()
	val botConfig = inject<ConfigManager>().value.botConfig
	val dmChannelId = botConfig.dmLogChannelId.toSnowflake()

	event<MessageCreateEvent> {
		action {
			val message = event.message

			if (message.getGuildOrNull() == null && message.author?.isSelf != true && message.content.isNotEmpty()) {
				sendAsWebhook(bot, dmChannelId,message.author?.tag ?: "Inconnu", message.author?.avatar?.cdnUrl?.toUrl(), "DM") {
					content = message.content
				}
			}
			else if (message.channelId == botCache.getChannelId("INTERVIEW") && message.author?.id in botCache.getInterviews()) {
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

	event<MessageUpdateEvent> {
		action {
			val oldMessage = event.old?.let { getCorrespondingMessage(MessageChannelBehavior(dmChannelId, kord), it) }

			if (oldMessage == null) {
				sendAsWebhook(bot, dmChannelId,event.new.author.value?.asUser(kord)?.tag ?: "Inconnu", event.new.author.value.asUser(kord)?.avatar?.cdnUrl?.toUrl(), "DM") {
					content = event.new.content.toString()
				}
			}
		}
	}

	event<MessageDeleteEvent> {
		action {
			val oldMessage = event.message?.let { getCorrespondingMessage(MessageChannelBehavior(dmChannelId, kord), it) }

			if (oldMessage == null && event.message != null) {
				val webhook = getWebhook(dmChannelId, bot, "DM")
				webhook.deleteMessage(webhook.token!!, event.message!!.id)
			}
		}
	}

	event<ReadyEvent> {
		action {
			logger.debug { "Récupération des canaux des catégories loup-garou" }
			val paralya = event.guilds.firstOrNull { it.id.value == botConfig.paralyaId }
				?: throw IllegalStateException("Serveur Paralya non trouvé")

			val rolesChannels = collectChannelsFromCategory(lgConfig.rolesCategory.toSnowflake(), paralya)
			val mainChannels = collectChannelsFromCategory(lgConfig.rolesCategory.toSnowflake(), paralya)

			logger.debug { "Trouvé ${rolesChannels.size} canaux dans la catégorie des rôles" }
			logger.debug { "Trouvé ${mainChannels.size} canaux dans la catégorie principale" }

			(rolesChannels + mainChannels).forEach { (name, id) ->
				botCache.registerChannel(name, id)
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
		}
		.toList()
		.toMap()
		.filterKeys { it.isNotBlank() && it != "_".repeat(it.length) }
}

fun areMessagesSimilar(msg1: Message, msg2: Message): Boolean {
	if (msg1.content != msg2.content) return false

	val attachments1 = msg1.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }
	val attachments2 = msg2.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }

	return attachments1 == attachments2
}

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