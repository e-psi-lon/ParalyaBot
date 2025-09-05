package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.entity.effectiveName
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.common.*
import fr.paralya.bot.common.I18n.Messages
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.I18n.Lg
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
	val voteManager = VoteManager(botCache)

	override suspend fun setup() {
		val gameRegistry by inject<GameRegistry>()
		gameRegistry.registerGameMode(I18n.GameMode.lg, "lg")
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
						guild?.getChannel(botCache.getChannelId(LgChannelType.INTERVIEW)!!) as TopGuildChannel
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

					sendAsWebhook(
						bot,
						botCache.getChannelId(LgChannelType.ANNONCES_VILLAGE)!!,
						"ParalyaLG",
						getAsset("paralya_lg", prefix)
					) {
						content = """
							━━━━━━━━━━━━━━━━━━━━━
							⏲ | Fin du Jour $day à $hour
							<@${config.aliveRole}>
							━━━━━━━━━━━━━━━━━━━━━
						""".trimIndent()
					}
					respond {
						content = Lg.EndDay.Response.success.translateWithContext(
							day,
							hour
						)
					}
				}
			}

            ephemeralSubCommand(::KillArguments) {
                name = Lg.Kill.Command.name
                description = Lg.Kill.Command.description

                adminOnly {
                    val target = arguments.target
                    val reason = arguments.reason ?: Lg.Kill.Argument.Reason.default.translateWithContext()
                    val config by inject<LgConfig>()

                    guild!!.getMember(target.id).swapRoles(
                        config.deadRole.snowflake,
                        config.aliveRole.snowflake,
                        reason
                    )
                    respond {
                        content = Lg.Kill.Response.success.translateWithContext(target.effectiveName, reason)
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

	inner class EndDayArguments : Arguments() {
		val hour by string {
			name = Lg.EndDay.Argument.Hour.name
			description = Lg.EndDay.Argument.Hour.description
		}

		val day by optionalInt {
			name = Lg.EndDay.Argument.Day.name
			description = Lg.EndDay.Argument.Day.description
		}
	}

    inner class KillArguments : Arguments() {
        val target by user {
            name = Lg.Kill.Argument.Target.name
            description = Lg.Kill.Argument.Target.description
        }

        val reason by optionalString {
            name = Lg.Kill.Argument.Reason.name
            description = Lg.Kill.Argument.Reason.description
        }
    }
}