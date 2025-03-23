package fr.paralya.bot.extensions.base

import dev.kord.core.event.gateway.ReadyEvent
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.gameMode
import fr.paralya.bot.i18n.Translations
import io.github.oshai.kotlinlogging.KotlinLogging

class Base : Extension() {
	override val name = "Base"
	private val logger = KotlinLogging.logger(this::class.java.name)
	override suspend fun setup() {
		event<ReadyEvent> {
			action {
				logger.info { "Bot connected to Discord as ${event.self.username}" }
			}
		}

		ephemeralSlashCommand(::StartGameArguments) {
			name = Translations.StartGame.Command.name
			description = Translations.StartGame.Command.description
			action {
				val gameRegistry = getKoin().get<GameRegistry>()
				this@Base.kord.editPresence {
					gameMode(gameRegistry.getGameMode(arguments.game))
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
					gameMode(GameRegistry.NONE)
				}
				respond {
					content = Translations.StopGame.Response.success.translate()
				}
			}
		}
	}

	inner class StartGameArguments : Arguments() {
		val game by stringChoice {
			name = Translations.StartGame.Argument.Game.name
			description = Translations.StartGame.Argument.Game.description
			choices = getKoin().get<GameRegistry>().getGameModes()
		}
	}
}