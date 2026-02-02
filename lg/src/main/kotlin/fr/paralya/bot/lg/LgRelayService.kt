package fr.paralya.bot.lg

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.User
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.core.event.message.ReactionRemoveEvent
import dev.kord.core.exception.EntityNotFoundException
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.events.EventContext
import dev.kordex.core.koin.KordExKoinComponent
import fr.paralya.bot.common.config.BotConfig
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.format
import fr.paralya.bot.common.getAsset
import fr.paralya.bot.common.getCorrespondingMessage
import fr.paralya.bot.common.getWebhook
import fr.paralya.bot.common.isAdmin
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.sendTemporaryMessage
import fr.paralya.bot.lg.data.getLastWerewolfMessageSender
import fr.paralya.bot.lg.data.getProfilePictureState
import fr.paralya.bot.lg.data.setLastWerewolfMessageSender
import fr.paralya.bot.lg.data.toggleProfilePicture
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes
import fr.paralya.bot.lg.I18n as Lg

class LgRelayService : KordExKoinComponent {
    private val lg by inject<LG>()
    private val logger by lazy { lg.logger }
    private val botCache by lazy { lg.botCache }
    private val bot by lazy { lg.bot }

    fun User?.shouldIgnore(botConfig: BotConfig) = this.isAdmin(botConfig) || this?.isBot == true || this?.isSelf == true

    context(context: EventContext<MessageCreateEvent>)
    suspend fun onMessageSent(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val botConfig by this.inject<BotConfig>()
        val message = context.event.message
        if (message.author.shouldIgnore(botConfig)) return

        if (message.content.length > 2000) {
            val channel = message.channel
            logger.warn { "Received a message too long (${message.content.length} characters). It'll be deleted and the user will be alerted." }
            channel.sendTemporaryMessage(
                Lg.Transmission.Error.messageTooLong.contextTranslate(message.content.length),
                1.minutes
            )
            message.content.chunked(2000).forEach {
                channel.sendTemporaryMessage(it, 1.minutes)
            }
            message.delete(Lg.Transmission.Error.Reason.messageTooLong.contextTranslate())
        }

        logger.debug { "Message sender is ${message.author?.id?.value}" }
        val (userName, userAvatar) = getMessageIdentity(message.author, isAnonymous, true)
        logger.debug { "Avatar is $userName and name is $userAvatar" }
        val content = buildRelayContent(message)
        if (isAnonymous) sendAsWebhook(
            bot,
            outChannel,
            userName,
            getAsset(userAvatar, lg.prefix),
            webhookName,
            content
        ) else sendAsWebhook(
            bot,
            outChannel,
            userName,
            userAvatar,
            webhookName,
            content
        )
    }


    context(context: EventContext<MessageDeleteEvent>)
    suspend fun onMessageDelete(
        webhookName: String,
        outChannel: Snowflake?,
    ) {
        val botConfig by this.inject<BotConfig>()
        val event = context.event
        if (event.message?.author.shouldIgnore(botConfig)) return
        val oldMessage = outChannel?.let { MessageChannelBehavior(outChannel, bot.kordRef).getCorrespondingMessage(event.message!!) }
        if (oldMessage != null) {
            val webhook = getWebhook(outChannel, bot, webhookName)
            try {
                webhook.token?.let { webhook.deleteMessage(it, oldMessage.id) }
            } catch (e: Exception) {
                logger.error(e) { "Error while deleting message" }
            }
        }
    }

    context(context: EventContext<MessageUpdateEvent>)
    suspend fun onMessageUpdate(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val botConfig by this.inject<BotConfig>()
        val event = context.event
        if (event.old?.author.shouldIgnore(botConfig)) return
        val oldMessage = event.old?.let { MessageChannelBehavior(outChannel, bot.kordRef).getCorrespondingMessage(it) }
        val webhook = getWebhook(outChannel, bot, webhookName)
        val newMessage = event.message.asMessage()
        if (oldMessage != null) {
            try {
                webhook.token?.let {
                    webhook.getMessage(it, oldMessage.id).edit {
                        content = newMessage.content
                        if (newMessage.referencedMessage != null) embed {
                            title = Lg.Transmission.Reference.title.contextTranslate()
                            description = newMessage.referencedMessage!!.content
                        } else embeds?.clear()
                    }
                }
            } catch (_: Exception) {
                logger.debug { "Message sender is ${event.old?.author?.id?.value}" }
                val (userName, userAvatar) = getMessageIdentity(event.old?.author, isAnonymous)
                logger.debug { "Avatar is $userName and name is $userAvatar" }
                val content = buildRelayContent(newMessage) {
                    embed {
                        title = Lg.Transmission.Update.title.contextTranslate()
                        description = oldMessage.content
                    }
                }
                sendAsWebhook(
                    bot,
                    outChannel,
                    userName,
                    userAvatar,
                    webhookName,
                    content
                )
            }
        }
    }


    context(context: EventContext<ReactionAddEvent>)
    suspend fun onReactionAdd(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val event = context.event
        try {
            onReactionChange(
                webhookName, outChannel, isAnonymous,
                event.getUserOrNull(), event.message.asMessage(), event.emoji, true
            )
        } catch (_: EntityNotFoundException) {
            logger.debug { "Message not found while processing reaction add" }
        }
    }

    context(context: EventContext<ReactionRemoveEvent>)
    suspend fun onReactionRemove(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val event = context.event
        try {
            onReactionChange(
                webhookName, outChannel, isAnonymous,
                event.getUserOrNull(), event.message.asMessage(), event.emoji, false
            )
        } catch (_: EntityNotFoundException) {
            logger.debug { "Message not found while processing reaction remove" }
        }
    }


    context(ctx: EventContext<*>)
    private suspend fun onReactionChange(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean,
        author: User?,
        message: Message,
        emoji: ReactionEmoji,
        isAdd: Boolean
    ) {
        val botConfig by this.inject<BotConfig>()
        if (message.author.shouldIgnore(botConfig)) return
        if (author.shouldIgnore(botConfig)) return
        val (userName, userAvatar) = getMessageIdentity(author, isAnonymous)
        val content = buildRelayReactionContent(emoji, message, isAdd)
        if (isAnonymous) sendAsWebhook(
            bot,
            outChannel,
            userName,
            getAsset(userAvatar, lg.prefix),
            webhookName,
            content
        ) else sendAsWebhook(
            bot,
            outChannel,
            userName,
            userAvatar,
            webhookName,
            content
        )
    }

    private suspend fun getMessageIdentity(
        author: User?,
        isAnonymous: Boolean,
        updateExisting: Boolean = false
    ) = if (isAnonymous) {
        if (updateExisting && author?.id != botCache.getLastWerewolfMessageSender()) {
            botCache.setLastWerewolfMessageSender(author!!.id)
            botCache.toggleProfilePicture()
        }
        (if (botCache.getProfilePictureState()) "🐺 Anonyme" else "🐺Anonyme") to (if (botCache.getProfilePictureState()) "wolf_variant_2" else "wolf_variant_1")
    } else (author?.username ?: "Message Author") to (author?.avatar?.cdnUrl?.toUrl() ?: "")

    context(ctx: EventContext<*>)
    private fun buildRelayContent(
        message: Message,
        additionalElements: (suspend MessageBuilder.() -> Unit)? = null
    ): suspend MessageBuilder.() -> Unit = {
        val botConfig by ctx.inject<BotConfig>()
        content = message.content

        if (message.referencedMessage != null && !message.referencedMessage!!.author.isAdmin(botConfig)) embed {
            title = Lg.Transmission.Reference.title.contextTranslate()
            description = message.referencedMessage!!.content
        }

        additionalElements?.invoke(this)
    }

    context(ctx: EventContext<*>)
    private fun buildRelayReactionContent(
        reaction: ReactionEmoji,
        message: Message,
        isAdd: Boolean,
        additionalElements: (suspend MessageBuilder.() -> Unit)? = null
    ): suspend MessageBuilder.() -> Unit = {
        content = (if (isAdd) Lg.Transmission.Reaction.add else Lg.Transmission.Reaction.remove).contextTranslate(reaction.format())

        embed {
            title = Lg.Transmission.Reaction.Content.title.contextTranslate()
            description = message.content
        }
        val botConfig by ctx.inject<BotConfig>()
        if (message.referencedMessage != null && !message.referencedMessage!!.author.isAdmin(botConfig)) embed {
            title = Lg.Transmission.Reference.title.contextTranslate()
            description = message.referencedMessage!!.content
        }

        additionalElements?.invoke(this)
    }
}