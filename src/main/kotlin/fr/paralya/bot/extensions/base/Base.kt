package fr.paralya.bot.extensions.base

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.checks.noGuild
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.defaultingEnumChoice
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalSnowflake
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import fr.paralya.bot.common.*
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.I18n
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.cache.CachedData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
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
		kord.cache.register(CachedData.description)
		val botConfig = inject<ConfigManager>().value.botConfig
		val dmChannelId = botConfig.dmLogChannelId.snowflake
		event<ReadyEvent> {
			action {
				logger.info { "Bot connected to Discord as ${event.self.username}" }
			}
		}

		event<MessageCreateEvent> {
			check {
				noGuild()
				isNotBot()
				failIf { message?.isNotEmpty() == false }
			}
			action {
				val message = event.message
				sendAsWebhook(
					bot,
					dmChannelId,
					message.author?.tag ?: "Inconnu",
					message.author?.avatar?.cdnUrl?.toUrl(),
					"DM"
				) {
					content = message.content
					if (message.referencedMessage != null) embed {
						title = I18n.Transmission.Reference.title.contextTranslate()
						description = message.referencedMessage!!.content
					}
				}
			}
		}
		event<MessageUpdateEvent> {
			check {
				noGuild()
				isNotBot()
			}

			action {
				val oldMessage =
					event.old?.let { MessageChannelBehavior(dmChannelId, kord).getCorrespondingMessage(it) }
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
							title = I18n.Transmission.Update.title.contextTranslate()
							description = event.new.content.toString()
						}
					}
				}
			}
		}

		event<MessageDeleteEvent> {
			check {
				noGuild()
				isNotBot()
				failIf { event.message != null }
			}
			action {
				val oldMessage =
					event.message?.let { MessageChannelBehavior(dmChannelId, kord).getCorrespondingMessage(it) }
				if (oldMessage != null) {
					val webhook = getWebhook(dmChannelId, bot, "DM")
					webhook.deleteMessage(webhook.token!!, oldMessage.id)
				}
			}
		}

		ephemeralSlashCommand(::StartGameArguments) {
			name = I18n.StartGame.Command.name
			description = I18n.StartGame.Command.description
			action {
				val gameRegistry = getKoin().get<GameRegistry>()
				this@Base.kord.editPresence {
					gameMode(gameRegistry.getGameMode(arguments.game))
				}
				respond {
					content = I18n.StartGame.Response.success.contextTranslate(arguments.game)
				}
			}
		}

		ephemeralSlashCommand {
			name = I18n.StopGame.Command.name
			description = I18n.StopGame.Command.description
			action {
				val bKord = this@Base.kord
				bKord.editPresence {
					gameMode(GameRegistry.NONE)
				}
				respond {
					content = I18n.StopGame.Response.success.contextTranslate()
				}
			}
		}

        ephemeralSlashCommand(::ExportArguments) {
            name = I18n.ChatExport.Command.name
            description = I18n.ChatExport.Command.description

            adminOnly {
                val channel = arguments.channel as MessageChannelBehavior? ?: channel
                val messages = when {
                arguments.start != null && arguments.end != null ->
                    channel.getMessagesAfter(arguments.start!!).takeWhile { it.id != arguments.end!! }
                arguments.start != null && arguments.count != null ->
                    channel.getMessagesAfter(arguments.start!!).take(arguments.count!!)
                else ->
                    channel.getMessagesBefore(channel.asChannel().lastMessageId ?: Snowflake.max, arguments.count!!)
            }
                respond {
                    content = I18n.ChatExport.Response.Success.txt.contextTranslate()
                    when (arguments.format.parsed) {
                        Format.TXT -> addStringExport(channel, guild, messages, arguments.anonymous)
                        Format.HTML -> addHtmlExport(channel, guild, messages, arguments)
                    }
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
			name = I18n.StartGame.Argument.Game.name
			description = I18n.StartGame.Argument.Game.description
			choices = getKoin().get<GameRegistry>().getGameModes()
		}
	}

    class ExportArguments : Arguments() {
        val count: Int? by optionalInt {
            name = I18n.ChatExport.Argument.Count.name
            description = I18n.ChatExport.Argument.Count.description
            minValue = 1
            maxValue = 1000
            validate {
                if (value != null && end != null)
                    return@validate fail(I18n.ChatExport.Argument.Count.Error.mutuallyExclusive)
            }
        }

        val start: Snowflake? by optionalSnowflake {
            name = I18n.ChatExport.Argument.Start.name
            description = I18n.ChatExport.Argument.Start.description
        }

        val end: Snowflake? by optionalSnowflake {
            name = I18n.ChatExport.Argument.End.name
            description = I18n.ChatExport.Argument.End.description
            validate {
				return@validate if (start == null && value != null)
                     fail(I18n.ChatExport.Argument.End.Error.startRequired)
                else if (value != null && value!! < start!!)
                    fail(I18n.ChatExport.Argument.End.Error.invalidRange)
				else Unit
            }
        }

        val channel by optionalChannel {
            name = I18n.ChatExport.Argument.Channel.name
            description = I18n.ChatExport.Argument.Channel.description
        }

        val format = defaultingEnumChoice<Format> {
            name = I18n.ChatExport.Argument.Format.name
            description = I18n.ChatExport.Argument.Format.description
            typeName = I18n.ChatExport.Argument.Format.typeName
            defaultValue = Format.TXT
        }

        val anonymous by defaultingBoolean {
            name = I18n.ChatExport.Argument.Anonymous.name
            description = I18n.ChatExport.Argument.Anonymous.description
            defaultValue = false
        }
    }
}

enum class Format: ChoiceEnum {
    TXT {
        override val readableName = I18n.ChatExport.Argument.Format.txt
    }, HTML {
        override val readableName = I18n.ChatExport.Argument.Format.html
    }
}