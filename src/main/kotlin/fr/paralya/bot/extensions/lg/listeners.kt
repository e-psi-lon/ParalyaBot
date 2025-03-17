package fr.paralya.bot.extensions.lg

import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
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
import fr.paralya.bot.LG_MAIN_CATEGORY
import fr.paralya.bot.LG_ROLES_CATEGORY
import fr.paralya.bot.extensions.data.addChannels
import fr.paralya.bot.extensions.data.removeInterview
import fr.paralya.bot.utils.getWebhook
import fr.paralya.bot.utils.toSnowflake
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList


fun DiscordUser?.asUser(kord: Kord) = this?.let { User(UserData.from(it), kord) }
suspend fun LG.registerListeners() {
    event<MessageCreateEvent> {
        action {
            val message = event.message
            if (message.getGuildOrNull() == null && message.author?.isSelf != true && message.content.isNotEmpty()) {
                val webhook = getWebhook(939233865350938644.toULong(), bot, "LG")
                webhook.execute(webhook.token!!) {
                    content = message.content
                    username = message.author?.tag ?: "Inconnu"
                    avatarUrl = message.author?.avatar?.cdnUrl?.toUrl()
                }
            }
            else if (message.channelId.value == channels["INTERVIEW"]) {
                if (message.author?.id?.value in interviews) {
                    removeInterview(message.author!!.id.value)
                    (message.channel as TopGuildChannel).addOverwrite(PermissionOverwrite.forMember(message.author!!.id, denied = Permissions(Permission.SendMessages)))
                }
            }
        }
    }

    event<MessageUpdateEvent> {
        action {
            val oldMessage = event.old?.let { getCorrespondingMessage(MessageChannelBehavior(939233865350938644.toSnowflake(), kord), it) }
            if (oldMessage == null) {
                val webhook = getWebhook(939233865350938644.toULong(), bot, "LG")
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
            val oldMessage = event.message?.let { getCorrespondingMessage(MessageChannelBehavior(939233865350938644.toSnowflake(), kord), it) }
            if (oldMessage == null) {
                val webhook = getWebhook(939233865350938644.toULong(), bot, "LG")
                webhook.deleteMessage(webhook.token!!, event.message!!.id)
            }
        }
    }

    event<ReadyEvent> {
        action {
            logger.debug("Fetching channels from categories loup-garou and roles from loup-garou")
            val paralya = event.guilds.first()
            val lGRolesMap = mutableMapOf<String, ULong>()
            val lGMainMap = mutableMapOf<String, ULong>()
            paralya.channels.collect { channel ->
                if (channel.getCategory()?.id == LG_ROLES_CATEGORY.toSnowflake()) {
                    val channelName = channel.name.replace(Regex("[^A-Za-z0-9_-]"), "")
                        .removePrefix("-").replace("-", "_").uppercase()
                    if (channelName != "_".repeat(channelName.length)) {
                        lGRolesMap[channelName] = channel.id.value
                    }
                }
            }
            logger.debug("Found {} channels in the roles category of werewolf game", lGRolesMap.size)
            paralya.channels.collect { channel ->
                if (channel.getCategory()?.id == LG_MAIN_CATEGORY.toSnowflake()) {
                    val channelName = channel.name.replace(Regex("[^A-Za-z0-9_-]"), "")
                        .removePrefix("-").replace("-", "_").uppercase()
                    if (channelName != "_".repeat(channelName.length)) {
                        lGMainMap[channelName] = channel.id.value
                    }
                }
            }
            logger.debug("Found ${lGMainMap.size} channels in the main category of werewolf game")
            addChannels(lGRolesMap)
            addChannels(lGMainMap)
        }
    }
}

fun areMessagesSimilar(msg1: Message, msg2: Message): Boolean {
    // Compare content
    if (msg1.content != msg2.content) {
        return false
    }

    // Compare attachments
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