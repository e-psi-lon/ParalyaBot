package fr.paralya.bot.extensions.base

import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import fr.paralya.bot.common.*
import fr.paralya.bot.i18n.Translations
import fr.paralya.bot.common.i18n.Translations.Common
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.inject

/**
 * Base extension for the bot.
 *
 * This extension handles the basic setup and configuration of the bot.
 * It includes basic commands and event listeners.
 *
 * @property name The name of the extension.
 * @property logger The logger for the extension.
 */
class Base : Extension() {
	override val name = "Base"
	private val logger = KotlinLogging.logger(this::class.java.name)
	override suspend fun setup() {
		val botConfig = inject<ConfigManager>().value.botConfig
		val dmChannelId = botConfig.dmLogChannelId.snowflake
		event<ReadyEvent> {
			action {
				logger.info { "Bot connected to Discord as ${event.self.username}" }
			}
		}

		event<MessageCreateEvent> {
			action {
				val message = event.message

				if (message.getGuildOrNull() == null && message.author?.isSelf != true && message.content.isNotEmpty()) {
					sendAsWebhook(bot, dmChannelId,message.author?.tag ?: "Inconnu", message.author?.avatar?.cdnUrl?.toUrl(), "DM") {
						content = message.content
						if (message.referencedMessage != null) embed {
							title = Common.Transmission.Reference.title.translateWithContext()
							description = message.referencedMessage!!.content
						}
					}
				}

			}
		}
		event<MessageUpdateEvent> {
			action {
				if (event.old?.getGuildOrNull() == null && event.old?.author?.isSelf != true) {
					val oldMessage =
						event.old?.let { getCorrespondingMessage(MessageChannelBehavior(dmChannelId, kord), it) }

					if (oldMessage != null) {
						sendAsWebhook(
							bot,
							dmChannelId,
							event.new.author.value?.asUser(kord)?.tag ?: "Inconnu",
							event.new.author.value.asUser(kord)?.avatar?.cdnUrl?.toUrl(),
							"DM"
						) {
							content = event.new.content.toString()
							embed {
								title = Common.Transmission.Update.title.translateWithContext()
								description = event.new.content.toString()
							}
						}
					}
				}
			}
		}

		event<MessageDeleteEvent> {
			action {
				if (event.getGuildOrNull() == null && event.message?.author?.isSelf != true) {
					val oldMessage =
						event.message?.let { getCorrespondingMessage(MessageChannelBehavior(dmChannelId, kord), it) }

					if (oldMessage != null && event.message != null) {
						val webhook = getWebhook(dmChannelId, bot, "DM")
						webhook.deleteMessage(webhook.token!!, event.message!!.id)
					}
				}
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
					content = Translations.StartGame.Response.success.translateWithContext(arguments.game)
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
					content = Translations.StopGame.Response.success.translateWithContext()
				}
			}
		}
	}

	/**
	 * Arguments for the start game command.
	 * This class defines the arguments required to start a game.
	 * It includes a game argument to specify the game mode.
	 *
	 * @property game The game mode to start. Loaded from the game registry.
	 */
	inner class StartGameArguments : Arguments() {
		val game by stringChoice {
			name = Translations.StartGame.Argument.Game.name
			description = Translations.StartGame.Argument.Game.description
			choices = getKoin().get<GameRegistry>().getGameModes()
		}
	}
}