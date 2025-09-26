package fr.paralya.bot.extensions.base

import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.MessageType
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Embed
import dev.kord.core.entity.Invite
import dev.kord.core.entity.Message
import dev.kord.core.entity.Reaction
import dev.kord.core.entity.Sticker
import dev.kord.core.entity.User
import kotlinx.datetime.Instant
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.HtmlTagMarker
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.svg
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlin.time.ExperimentalTime


@HtmlTagMarker
fun HEAD.unsafeScript(js: String) = script { unsafe { +js } }

@HtmlTagMarker
inline fun FlowContent.preambleEntry(small: Boolean = false, crossinline block: DIV.() -> Unit) =
    div("preamble__entry" + if (small) " preamble__entry--small" else "", block)

@HtmlTagMarker
fun FlowContent.preambleEntry(content: String, small: Boolean = false) = preambleEntry(small) { +content }

@HtmlTagMarker
inline fun FlowContent.postambleEntry(crossinline block: DIV.() -> Unit) = div("postamble__entry", block)

@HtmlTagMarker
fun FlowContent.postambleEntry(content: String) = postambleEntry { +content }

private val DISCORD_SYSTEM_MESSAGES_VISIBLE: Set<MessageType> = setOf(
    MessageType.RecipientAdd,
    MessageType.RecipientRemove,
    MessageType.Call,
    MessageType.ChannelNameChange,
    MessageType.ChannelIconChange,
    MessageType.ChannelPinnedMessage,
    MessageType.UserJoin,
    MessageType.ThreadCreated
)

private val DISCORD_SYSTEM_MESSAGES_HIDDEN: Set<MessageType> = setOf(
    MessageType.GuildBoost,
    MessageType.GuildBoostTier1,
    MessageType.GuildBoostTier2,
    MessageType.GuildBoostTier3,
    MessageType.ChannelFollowAdd,
    MessageType.GuildDiscoveryDisqualified,
    MessageType.GuildDiscoveryRequalified,
    MessageType.GuildDiscoveryGracePeriodInitialWarning,
    MessageType.GuildDiscoveryGracePeriodFinalWarning,
    MessageType.AutoModerationAction,
    MessageType.RoleSubscriptionPurchase,
    MessageType.InteractionPremiumUpsell,
    MessageType.StageStart,
    MessageType.StageEnd,
    MessageType.StageSpeaker,
    MessageType.StageTopic,
    MessageType.GuildApplicationPremiumSubscription,
    // MessageType.GuildIncidentAlertModeEnabled,
    // MessageType.GuildIncidentAlertModeDisabled,
    // MessageType.GuildIncidentReportRaid,
    // MessageType.GuildIncidentReportFalseAlarm,
    MessageType.PurchaseNotification,
    // MessageType.PollResult
)

private fun iconForMessageType(type: MessageType): String = when (type) {
    MessageType.RecipientAdd -> "join-icon"
    MessageType.RecipientRemove -> "leave-icon"
    MessageType.Call -> "call-icon"
    MessageType.ChannelNameChange -> "pencil-icon"
    MessageType.ChannelIconChange -> "pencil-icon"
    MessageType.ChannelPinnedMessage -> "pin-icon"
    MessageType.UserJoin -> "join-icon"
    MessageType.ThreadCreated -> "thread-icon"
    else -> "pencil-icon"
}

@HtmlTagMarker
fun FlowContent.span(text: String) = span { +text }



@OptIn(BlockingAccessor::class, KordExperimental::class, KordUnsafe::class, ExperimentalTime::class)
@HtmlTagMarker
fun FlowContent.discordMessageContainer(message: Message, index: Int, mentionedUsers: List<User>) {
    val author = message.author
    val authorColor = author?.accentColor
    val authorName = author?.username
    div("chatlog__message-container" + if (message.isPinned) " chatlog__message-container--pinned" else "") {
        id = "chatlog__message-container-${message.id.value}"
        div("chatlog__message") {
            when (message.type) {
                in DISCORD_SYSTEM_MESSAGES_HIDDEN -> return@div
                in DISCORD_SYSTEM_MESSAGES_VISIBLE -> {
                    div("chatlog__message-aside") {
                        svg("chatlog__system-notification-icon") {
                            unsafe { "<use href=\"#@${iconForMessageType(message.type)}\"></use>" }
                        }
                    }
                    div("chatlog__message-primary") {
                        span("chatlog__system-notification-author") {
                            if (authorColor != null) {
                                style = "color: #${authorColor.rgb.toString(16).padStart(6, '0')};"
                            }
                            +(authorName ?: "Inconnu")
                            title = author?.globalName ?: "Inconnu"
                            attributes["data-user-id"] = author?.id?.value.toString()
                        }
                        span()
                        span("chatlog__system-notification-content") {
                            when (message.type) {
                                MessageType.RecipientAdd -> {
                                    if (mentionedUsers.isNotEmpty()) {
                                        span(text = "ajouté")
                                        a(classes = "chatlog__system-notification-link") {
                                            title = mentionedUsers[0].globalName ?: "Inconnu"
                                            +mentionedUsers[0].username
                                        }
                                        span(text = " au groupe.")
                                    }
                                }

                                MessageType.RecipientRemove -> {
                                    if (mentionedUsers.isNotEmpty()) {
                                        if (author?.id == mentionedUsers[0].id) {
                                            span(text = "a quitté le groupe.")
                                        } else {
                                            span(text = "a supprimé")
                                            a(classes = "chatlog__system-notification-link") {
                                                title = mentionedUsers[0].globalName ?: "Inconnu"
                                                +mentionedUsers[0].username
                                            }
                                            span(text = " du groupe.")
                                        }
                                    }
                                }

                                MessageType.Call -> {
                                    val endedTimestamp = message.call?.endedTimestamp?.value?.toInt()
                                        ?: (message.timestamp.toEpochMilliseconds() / 1000).toInt()
                                    val startedTimestamp = message.timestamp.toEpochMilliseconds() / 1000
                                    span(text = "a commencé un appel qui a duré ${(endedTimestamp - startedTimestamp) / 60} minutes.")
                                }

                                MessageType.ChannelNameChange -> {
                                    span(text = "a changé le nom du salon: ")
                                    span("chatlog__system-notification-link") { +message.content }
                                }

                                MessageType.ChannelIconChange -> {
                                    span(text = "a changé l'icône du salon." )
                                }

                                MessageType.ChannelPinnedMessage -> {
                                    if (message.referencedMessage != null) {
                                        span(text = "a épinglé ")
                                        a(
                                            "#chatlog__message-container-${message.referencedMessage!!.id.value}",
                                            "chatlog__system-notification-link"
                                        ) {
                                            +"un message"
                                        }
                                        span(text = " dans ce salon")
                                    }
                                }

                                MessageType.ThreadCreated -> {
                                    span(text = "a commencé un fil.")
                                }

                                MessageType.UserJoin -> {
                                    span(text = "a rejoint le serveur.")
                                }

                                else -> {}
                            }
                        }
                    }
                }
                else -> {

                }
            }
            timestamp(message.timestamp, message.id, message.type in DISCORD_SYSTEM_MESSAGES_VISIBLE)
        }
    }
}

@HtmlTagMarker
fun DIV.attachmentBlock(attachment: Attachment) {}
@HtmlTagMarker
fun DIV.embedBlock(embed: Embed) {}
@HtmlTagMarker
fun DIV.replyBlock(referencedMessage: Message?) {}
@HtmlTagMarker
fun DIV.reactionsBlock(reactions: List<Reaction>) {}
@HtmlTagMarker
fun DIV.stickersBlock(stickers: List<Sticker>) {}
@HtmlTagMarker
fun DIV.invitesBlock(invites: List<Invite>) {}
@HtmlTagMarker
fun DIV.timestamp(timestamp: Instant, messageId: Snowflake, isSystem: Boolean = false) {
    span("chatlog__${if (isSystem) "system-notification-" else ""}timestamp") {
        title = formatDiscordTimestamp(timestamp)
        a(href="#chatlog__message-container-${messageId.value}") { +formatDiscordTimestamp(timestamp) }
    }
}

fun formatDiscordTimestamp(timestamp: Instant): String {
    return ""
}