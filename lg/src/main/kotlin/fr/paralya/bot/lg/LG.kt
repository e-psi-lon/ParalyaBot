package fr.paralya.bot.lg

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
import fr.paralya.bot.common.ConfigManager
import fr.paralya.bot.common.Message
import fr.paralya.bot.lg.data.GameData
import fr.paralya.bot.lg.data.VoteData
import fr.paralya.bot.lg.data.getChannel
import fr.paralya.bot.lg.i18n.Translations.Lg
import fr.paralya.bot.common.i18n.Translations.Messages
import fr.paralya.bot.lg.data.LgConfig
import org.koin.core.component.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LG: Extension() {
    override val name = "LG"
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    val botCache = kord.cache


    override suspend fun setup() {
        val configManager = ConfigManager()
        val lgConfig by inject<LgConfig>()
        configManager.loadConfigSection(lgConfig, "games.lg")
        kord.cache.register(
            GameData.description,
            VoteData.description
        )
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
                    val interviewChannel = botCache.getChannel("INTERVIEW")?.let { it1 -> guild?.getChannel(it1) } as TopGuildChannel?
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

