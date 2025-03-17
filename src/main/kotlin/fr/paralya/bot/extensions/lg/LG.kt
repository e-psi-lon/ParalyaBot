package fr.paralya.bot.extensions.lg

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.extensions.data.Channels
import fr.paralya.bot.extensions.data.Interviews
import fr.paralya.bot.extensions.data.VillageVotes
import fr.paralya.bot.extensions.data.WereWolfVotes
import fr.paralya.bot.get
import fr.paralya.bot.i18n.Translations.Lg
import fr.paralya.bot.i18n.Translations.Messages
import fr.paralya.bot.set
import fr.paralya.bot.utils.Message
import fr.paralya.bot.utils.toSnowflake
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LG: Extension() {
    override val name = "LG"
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    var timeState: LGState
        get() = runBlocking { bot.get<LGState> { } } ?: LGState.DAY
        set(value) = runBlocking { bot.set(value) }
    var wereWolfVotes: WereWolfVotes
        get() = runBlocking { bot.get<WereWolfVotes> { } } ?: WereWolfVotes()
        set(value) = runBlocking { bot.set(value) }
    var villageVotes: VillageVotes
        get() = runBlocking { bot.get<VillageVotes> { } } ?: VillageVotes()
        set(value) = runBlocking { bot.set(value) }
    var interviews: List<ULong>
        get() = runBlocking { bot.get<Interviews> { } }?.interviews ?: emptyList()
        set(value) = runBlocking { bot.set(Interviews(value)) }
    var channels: Map<String, ULong>
        get() = runBlocking { bot.get<Channels> { } }?.channels ?: emptyMap()
        set(value) = runBlocking { bot.set(Channels(value)) }


    override suspend fun setup() {
        publicSlashCommand {
            name = Lg.Command.name
            description = Lg.Command.description
            registerVotingCommands(this@LG)
            registerDayCycleCommands(this@LG)
            ephemeralSubCommand(::NotifArguments, ::Message) {
                name = Lg.Notif.Command.name
                description = Lg.Notif.Command.description
                action { modal ->
                    val failed = mutableListOf<String>()
                    if (guild == null) {
                        respond {
                            content = Messages.Error.onlyInGuild.translate()
                        }
                        return@action
                    }
                    try {
                        logger.debug("guild supplier : {}", guild?.supplier)
                        guild?.members?.collect { member ->
                            logger.debug("Checking member ${member.username}")
                            if (member.hasRole(arguments.role)) {
                                member.dm(
                                    "━━━━━━━━━━━━━━━━━━\n🐺 LGNotifications ¦ ${modal?.message?.value ?: "Une erreur a eu lieu, merci de la rapporter à l'équipe technique."}\n━━━━━━━━━━━━━━━━━━"
                                ) ?: failed.add(member.username)
                            }
                        }
                    } catch (e: Exception) {
                        respond {
                            content = Lg.Notif.Response.success.translate(e.message)
                        }
                    }
                    respond {
                        content = if (failed.isEmpty()) {
                            Lg.Notif.Response.success.translate()
                        } else {
                            Lg.Notif.Response.failed.translate(failed.joinToString(", "))
                        }
                    }
                }
            }
            ephemeralSubCommand(::InterviewArguments) {
                name = Lg.Interview.Command.name
                description = Lg.Interview.Command.description
                action {
                    val interviewChannel = channels["INTERVIEW"]?.toSnowflake()?.let { it1 -> guild?.getChannel(it1) } as TopGuildChannel?
                    if (interviewChannel == null) {
                        respond {
                            content = Messages.Error.channelNotFound.translate("interview")
                        }
                        return@action
                    }
                    val user = arguments.user
                    interviewChannel.addOverwrite(PermissionOverwrite.forMember(user.id, Permissions(Permission.SendMessages)))
                }
            }
        }
        registerListeners()
    }

    inner class NotifArguments: Arguments() {
        val role by role {
            name = Lg.Notif.Argument.Role.name
            description = Lg.Notif.Argument.Role.description
        }
    }

    inner class InterviewArguments: Arguments() {
        val user by user {
            name = Lg.Interview.Argument.User.name
            description = Lg.Interview.Argument.User.description
        }
    }
}

