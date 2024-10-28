package fr.paralya.bot.extensions.lg

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.extensions.base.Base
import fr.paralya.bot.i18n.Translations.Lg
import fr.paralya.bot.i18n.Translations.Messages
import fr.paralya.bot.utils.Message
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LG: Extension() {
    override val name = "LG"
    private val baseExtension: Base
        get() = bot.extensions["Base"]?.let { it as Base } ?: error("Base extension not found")
    val villageVotes = VillageVotes()
    val wereWolfVotes = WereWolfVotes()
    var timeState = LGState.DAY
    val channels: MutableMap<String, ULong> = mutableMapOf()
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun setup() {
        registerListeners()
        publicSlashCommand {
            name = Lg.Command.name
            description = Lg.Command.description
            ephemeralSubCommand(::NotifArguments, ::Message) {
                name = Lg.Notif.Command.name
                description = Lg.Notif.Command.description
                action { modal ->
                    if (guild == null) {
                        respond {
                            content = Messages.Error.only_in_guild.translate()
                        }
                        return@action
                    }
                    try {

                        guild?.members?.collect { member ->
                            if (member.hasRole(arguments.role)) {
                                member.fetchUser().getDmChannelOrNull()?.createMessage(
                                    "━━━━━━━━━━━━━━━━━━\n🐺 LGNotifications ¦ ${modal?.message?.value ?: "Une erreur a eu lieu, merci de la rapporter à l'équipe technique."}\n━━━━━━━━━━━━━━━━━━"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        respond {
                            content = Lg.Notif.Response.success.translate(e.message)
                        }
                    }
                    respond {
                        content = Lg.Notif.Response.success.translate()
                    }
                }
            }
        }
    }

    inner class NotifArguments: Arguments() {
        val role by role {
            name = Lg.Notif.Argument.Role.name
            description = Lg.Notif.Argument.Role.description
        }
    }
}

