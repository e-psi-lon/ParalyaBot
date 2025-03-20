package fr.paralya.bot.extensions.base

import dev.kord.core.event.gateway.ReadyEvent
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import fr.paralya.bot.i18n.Translations
import org.slf4j.LoggerFactory

class Base: Extension() {
    override val name = "Base"
    private val logger = LoggerFactory.getLogger(this::class.java)
    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                logger.info("Bot connected to Discord as ${event.self.username}")
                logger.debug("Fetching channels from categories loup-garou and roles from loup-garou")
            }
        }

        ephemeralSlashCommand(::StartGameArguments) {
            name = Translations.StartGame.Command.name
            description = Translations.StartGame.Command.description
            action {
                this@Base.kord.editPresence {
                    gameMode(arguments.game)
                }
                respond {
                    content = Translations.StartGame.Response.success.translate(arguments.game)
                }
            }
        }

        ephemeralSlashCommand {
            name = Translations.StopGame.Command.name
            description = Translations.StopGame.Command.description
            action {
                val bKord = this@Base.kord
                bKord.editPresence {
                    gameMode(GameModes.NONE)
                }
                respond {
                    content = Translations.StopGame.Response.success.translate()
                }
            }
        }
    }

    inner class StartGameArguments: Arguments() {
        val game by enumChoice<GameModes> {
            name = Translations.StartGame.Argument.Game.name
            description = Translations.StartGame.Argument.Game.description
            typeName = Translations.StartGame.Argument.Game.typeName
            choices = GameModes.entries.associateBy { it.readableName }.toMutableMap().apply {
                remove(GameModes.NONE.readableName)
            }
        }
    }
}