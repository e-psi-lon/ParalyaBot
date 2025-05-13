package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.threads.edit
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.utils.getTopChannel
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.common.*
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.i18n.Translations.Lg
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList

/**
 * Represents the state of the game, either [DAY] or [NIGHT].
 * This enum is used to manage the game cycle and determine the current phase of the game.
 *
 * @property DAY Represents the daytime phase of the game.
 * @property NIGHT Represents the nighttime phase of the game.
 */
enum class LGState {
	DAY, NIGHT;

	fun next() = when (this) {
		DAY -> NIGHT
		NIGHT -> DAY
	}
}

/**
 * Registers the commands for managing the day and night cycle in the game.
 * This includes commands for starting a new day, ending the current day, and transitioning to night.
 *
 * @receiver The instance of the [LG] extension that will handle the commands.
 */
context(LG)
suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerDayCycleCommands() {
	ephemeralSubCommand(::DayArguments) {
		name = Lg.Day.Command.name
		description = Lg.Day.Command.description

		adminOnly {
			val force = arguments.force
			val kill = arguments.kill
			val botCache = this@LG.botCache
			val gameData = botCache.getGameData()

			if (gameData.state == LGState.DAY) {
				respond { content = Lg.Day.Response.Error.alreadyDay.translateWithContext() }
				return@adminOnly
			}
			val newVote = botCache.getCurrentVote(LGState.DAY)
				?: VoteData.createVillageVote(System.currentTimeMillis().snowflake).setCurrent(true)
			botCache.updateVote(newVote)

			val oldWerewolfVote = botCache.getCurrentVote(LGState.NIGHT)?.apply {
				setCurrent(false)
				botCache.updateVote(this)
			}
			val newVoteWerewolf = botCache.getCurrentVote(LGState.NIGHT)
				?: VoteData.createWerewolfVote(System.currentTimeMillis().snowflake).setCurrent(true)
			botCache.updateVote(newVoteWerewolf)
			val config = getKoin().get<LgConfig>()
			val aliveRole = config.aliveRole.snowflake
			if (oldWerewolfVote?.votes?.isNotEmpty() == true) {
				val voteCount = oldWerewolfVote.votes.values.groupingBy { it }.eachCount()
				val maxVote = voteCount.maxByOrNull { it.value }?.key
				val maxVotedPlayers = voteCount.filter { it.key == maxVote }.keys
				when {
					maxVotedPlayers.size > 1 && !force -> {
						sendAsWebhook(this@LG.bot, botCache.getChannelId("LOUPS_VOTE")!!, "ParalyaLG", getAsset("lg", this@LG.prefix)) {
							content = Lg.DayCycle.Response.Other.equality.translateWithContext(
								maxVotedPlayers.joinToString(", ") { "<@${it.value}>" }
							)
						}

						newVoteWerewolf.apply {
							setChoices(maxVotedPlayers.toList())
							botCache.updateVote(this)
						}

						respond { content = Lg.DayCycle.Response.Other.secondVote.translateWithContext() }
						return@adminOnly
					}
					maxVotedPlayers.size == 1 && kill -> {
						val playerToKill = maxVotedPlayers.first()
						guild!!.getMember(playerToKill).apply {
							addRole(config.deadRole.snowflake, Lg.System.Permissions.PlayerKilled.reason.translateWithContext())
							removeRole(aliveRole, Lg.System.Permissions.PlayerKilled.reason.translateWithContext())
						}

						respond {
							content = Lg.DayCycle.Response.Success.killed.translateWithContext(guild!!.getMember(playerToKill).effectiveName)
						}
					}
				}
			}
			newVoteWerewolf.apply {
				setChoices(emptyList())
				botCache.updateVote(this)
			}
			botCache.nextDay()
			listOf("VILLAGE", "VOTES", "SUJET").forEach { channelName ->
				botCache.getChannel(channelName)
					?.getTopChannel()
					?.addRolePermissions(aliveRole, Permission.ViewChannel, Permission.SendMessages)
			}
			// For each thread in the SUJET channel, unlock it
			botCache.getChannel("SUJET")?.activeThreads?.collect { it.edit { locked = false } }
			botCache.getChannel("LOUPS_CHAT")?.getMembersWithAccess()
				?.filter { it.hasRole(guild!!.getRole(aliveRole)) }
				?.toList()?.forEach { member ->
					val reason = Lg.System.Permissions.Day.reason.translateWithContext()

					botCache.getChannel("LOUPS_VOTE")?.apply {
						addMemberPermissions(member.id, Permission.ViewChannel, reason = reason)
						removeMemberPermission(member.id, Permission.SendMessages, reason = reason)
					}

					botCache.getChannel("LOUP_CHAT")?.apply {
						addMemberPermissions(member.id, Permission.ViewChannel, reason = reason)
						removeMemberPermission(member.id, Permission.SendMessages, reason = reason)
					}
				}

			respond { content = Lg.Day.Response.success.translateWithContext() }
		}
	}
	ephemeralSubCommand(::NightArguments) {
		name = Lg.Night.Command.name
		description = Lg.Night.Command.description

		adminOnly {
			val force = arguments.force
			val kill = arguments.kill
			val botCache = this@LG.botCache
			val gameData = botCache.getGameData()

		}
	}
}

/**
 * Base class for day and night cycle arguments.
 * This class defines common arguments for both day and night commands.
 * It includes a force argument to force the transition and a kill argument to determine if a player should be
 * killed.
 *
 * @property force Indicates whether to force the transition.
 * @property kill Indicates whether to kill a player.
 */
private abstract class BaseDayCycleArguments : Arguments() {
	val force by defaultingBoolean {
		name = Lg.DayCycle.Argument.Force.name
		description = getForceDescription()
		defaultValue = false
	}
	val kill by defaultingBoolean {
		name = Lg.DayCycle.Argument.Kill.name
		description = Lg.DayCycle.Argument.Kill.description
		defaultValue = true
	}
	abstract fun getForceDescription(): Key
}

/**
 * Arguments for the day command.
 * This class extends the [BaseDayCycleArguments] and provides a specific force description for the day command.
 */
private class DayArguments : BaseDayCycleArguments() {
	override fun getForceDescription() = Lg.Day.Argument.Force.description
}
/**
 * Arguments for the night command.
 * This class extends the [BaseDayCycleArguments] and provides a specific force description for the night command.
 */
private class NightArguments : BaseDayCycleArguments() {
	override fun getForceDescription() = Lg.Night.Argument.Force.description
}