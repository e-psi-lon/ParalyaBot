package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.common.*
import fr.paralya.bot.common.i18n.Translations.Messages
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.i18n.Translations
import fr.paralya.bot.lg.i18n.Translations.Lg
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.inject


/**
 * The main extension class for the Werewolf (Loup-Garou) game.
 *
 * This class is responsible for registering commands, event listeners and managing game state.
 *
 *
 * @property name The name of the extension.
 * @property logger The logger for the extension.
 * @property botCache An alias for the bot's cache for easier access.
 * @property prefix The internal prefix for assets, game registry, etc.
 */
class LG : Extension() {
	override val name = "LG"
	val logger = KotlinLogging.logger(this::class.java.name)
	val botCache = kord.cache
	val prefix = "lg"

	override suspend fun setup() {
		val gameRegistry by inject<GameRegistry>()
		gameRegistry.registerGameMode(Translations.GameMode.lg, "lg")
		kord.cache.register(GameData.description, VoteData.description)

		publicSlashCommand {
			name = Lg.Command.name
			description = Lg.Command.description

			registerVotingCommands()
			registerDayCycleCommands()

			ephemeralSubCommand(::NotifArguments, ::Message) {
				name = Lg.Notif.Command.name
				description = Lg.Notif.Command.description
				action { modal ->
					if (guild == null) {
						respond { content = Messages.Error.onlyInGuild.translateWithContext() }
						return@action
					}

					val failed = mutableListOf<String>()
					try {
						guild?.members?.collect { member ->
							if (member.hasRole(arguments.role)) {
								member.dm(
									Lg.Notif.Content.main.translateWithContext(
										modal?.message?.value ?: Lg.Notif.Content.error.translateWithContext()
									)
								) ?: failed.add(member.username)
							}
						}
					} catch (e: Exception) {
						respond { content = Lg.Notif.Response.success.translateWithContext(e.message) }
					}

					respond {
						content = if (failed.isEmpty()) {
							Lg.Notif.Response.success.translateWithContext()
						} else {
							Lg.Notif.Response.failed.translateWithContext(failed.joinToString(", "))
						}
					}
				}
			}

			ephemeralSubCommand(::InterviewArguments) {
				name = Lg.Interview.Command.name
				description = Lg.Interview.Command.description
				action {
					val interviewChannel =
						botCache.getChannelId("INTERVIEW")?.let { guild?.getChannel(it) } as TopGuildChannel?
					if (interviewChannel == null) {
						respond { content = Messages.Error.channelNotFound.translateWithContext("interview") }
						return@action
					}

					val user = arguments.user
					interviewChannel.addOverwrite(
						PermissionOverwrite.forMember(user.id, Permissions(Permission.SendMessages))
					)
				}
			}

			ephemeralSubCommand(::EndDayArguments) {
				name = Lg.EndDay.Command.name
				description = Lg.EndDay.Command.description
				adminOnly {
					val hour = arguments.hour
					val day = arguments.day ?: botCache.getGameData().dayCount
					val config by inject<LgConfig>()

					sendAsWebhook(bot, botCache.getChannelId("ANNONCES_VILLAGE")!!, "ParalyaLG", getAsset("lg", prefix)) {
						content = """
							━━━━━━━━━━━━━━━━━━━━━
							⏲ | Fin du Jour $day à $hour
							<@${config.aliveRole}>
							━━━━━━━━━━━━━━━━━━━━━
						""".trimIndent()
					}
				}
			}
		}

		registerListeners()
	}

	/**
	 * Nested class to define /lg notif command arguments.
	 *
	 * @property role The role to notify.
	 */
	inner class NotifArguments : Arguments() {
		val role by role {
			name = Lg.Notif.Argument.Role.name
			description = Lg.Notif.Argument.Role.description
		}
	}

	/**
	 * Nested class to define /lg interview command arguments.
	 *
	 * @property user The user to be interviewed.
	 */
	inner class InterviewArguments : Arguments() {
		val user by user {
			name = Lg.Interview.Argument.User.name
			description = Lg.Interview.Argument.User.description
		}
	}

	inner class EndDayArguments: Arguments() {
		val hour by string {
			name = Lg.EndDay.Argument.Hour.name
			description = Lg.EndDay.Argument.Hour.description
		 }

		val day by optionalInt {
			name = Lg.EndDay.Argument.Day.name
			description = Lg.EndDay.Argument.Day.description
		}
	}
}