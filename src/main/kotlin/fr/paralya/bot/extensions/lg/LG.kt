package fr.paralya.bot.extensions.lg

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.extensions.data.Channels
import fr.paralya.bot.extensions.data.Interviews
import fr.paralya.bot.extensions.data.VillageVotes
import fr.paralya.bot.extensions.data.WereWolfVotes
import fr.paralya.bot.get
import fr.paralya.bot.i18n.Translations.Lg
import fr.paralya.bot.i18n.Translations.Messages
import fr.paralya.bot.utils.Message
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

