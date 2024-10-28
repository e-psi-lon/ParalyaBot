package fr.paralya.bot.extensions.lg

import dev.kord.core.behavior.execute
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.getCategory
import fr.paralya.bot.LG_MAIN_CATEGORY
import fr.paralya.bot.LG_ROLES_CATEGORY
import fr.paralya.bot.utils.getWebhook
import fr.paralya.bot.utils.toSnowflake

suspend fun LG.registerListeners() {
    event<MessageCreateEvent> {
        action {
            val message = event.message
            // Si e message a ete envoye en mp, pas par le bot et n'est pas vide
            if (message.getGuildOrNull() == null && message.author?.isSelf != true && message.content.isNotEmpty()) {
                // On envoie un message via un webhook dans le channel de configuration
                val webhook = getWebhook(939233865350938644.toULong(), bot, "LG")
                webhook.execute(webhook.token!!) {
                    content = message.content
                    username = message.author?.tag ?: "Inconnu"
                    avatarUrl = message.author?.avatar?.cdnUrl?.toUrl()
                }
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
            channels.putAll(lGRolesMap)
            channels.putAll(lGMainMap)
        }
    }

}