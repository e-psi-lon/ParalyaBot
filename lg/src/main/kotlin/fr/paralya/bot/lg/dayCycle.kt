package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.utils.getTopChannel
import fr.paralya.bot.common.*
import fr.paralya.bot.lg.LGState.DAY
import fr.paralya.bot.lg.LGState.NIGHT
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.i18n.Translations.Lg
import kotlinx.coroutines.flow.toList
import org.koin.core.component.inject

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
			val voteManager = this@LG.voteManager

			if (gameData.state == DAY) {
				respond { content = Lg.Day.Response.Error.alreadyDay.translateWithContext() }
				return@adminOnly
			}
			voteManager.createVillageVote()
			val oldWerewolfVote = voteManager.finishCurrentVote(NIGHT)
			val newVoteWerewolf = voteManager.createWerewolfVote()
			val config by inject<LgConfig>()
			val aliveRole = config.aliveRole.snowflake
			if (oldWerewolfVote?.votes?.isNotEmpty() == true) {
				when (val result = voteManager.calculateVoteResult(oldWerewolfVote, kill, force)) {
					is VoteResult.NoVotes -> return@adminOnly
					is VoteResult.Tie -> {
						sendAsWebhook(
							this@LG.bot,
							botCache.getChannelId(LgChannelType.LOUPS_VOTE)!!,
							"ParalyaLG",
							getAsset("paralya_lg", this@LG.prefix)
						) {
							content = Lg.DayCycle.Response.Other.equality.translateWithContext(
								result.players.joinToString(", ") { "<@${it.value}>" }
							)
						}
						newVoteWerewolf.apply {
							setChoices(result.toList())
							botCache.updateVote(this)
						}
						respond { content = Lg.DayCycle.Response.Other.secondVote.translateWithContext() }
						return@adminOnly
					}

					is VoteResult.Killed -> {
						guild!!.getMember(result.player).swapRoles(
							config.deadRole.snowflake,
							aliveRole,
							Lg.System.Permissions.PlayerKilled.reason.translateWithContext()
						)
						respond {
							content =
								Lg.DayCycle.Response.Success.killed.translateWithContext(guild!!.getMember(result.player).effectiveName)
						}
					}
				}
			}
			newVoteWerewolf.apply {
				setChoices(emptyList())
				botCache.updateVote(this)
			}
			botCache.nextDay()
			listOf(LgChannelType.VILLAGE, LgChannelType.VOTES, LgChannelType.SUJETS).forEach { channelName ->
				botCache.getChannel(channelName)
					?.getTopChannel()
					?.addRolePermissions(aliveRole, Permission.ViewChannel, Permission.SendMessages)
			}
			// For each thread in the SUJET channel, unlock it
			botCache.getChannel(LgChannelType.SUJETS)?.activeThreads?.changeLockAll(false)
			botCache.getChannel(LgChannelType.LOUPS_CHAT)?.getMembersWithAccess()
				?.filterByRole(aliveRole)
				?.toList()?.forEach { member ->
					val reason = Lg.System.Permissions.Day.reason.translateWithContext()
					listOf(LgChannelType.LOUPS_VOTE, LgChannelType.LOUPS_CHAT).forEach { channelName ->
						botCache.getChannel(channelName)?.getTopChannel()?.apply {
							addMemberPermissions(member.id, Permission.ViewChannel, reason = reason)
							removeRolePermissions(member.id, Permission.SendMessages, reason = reason)
						}
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
			val voteManager = this@LG.voteManager
			if (gameData.state == NIGHT) {
				respond { content = Lg.Night.Response.Error.alreadyNight.translateWithContext() }
				return@adminOnly
			}
			voteManager.createWerewolfVote()
			val oldVillageVote = voteManager.finishCurrentVote(DAY)
			val newVoteVillage = voteManager.createVillageVote()
			val config by inject<LgConfig>()
			val aliveRole = config.aliveRole.snowflake
			if (oldVillageVote?.votes?.isNotEmpty() == true) {
				val response = when (val result = voteManager.calculateVoteResult(oldVillageVote, kill, force)) {
					is VoteResult.NoVotes -> return@adminOnly
					is VoteResult.Tie -> {
						sendAsWebhook(
							this@LG.bot,
							botCache.getChannelId(LgChannelType.VOTES)!!,
							"ParalyaLG",
							getAsset("paralya_lg", this@LG.prefix),
						) {
							content = Lg.DayCycle.Response.Other.equality.translateWithContext(
								result.players.joinToString(", ") { "<@${it.value}>" }
							)
						}
						newVoteVillage.apply {
							setChoices(result.players.toList())
							botCache.updateVote(this)
						}
						Lg.DayCycle.Response.Other.secondVote.translateWithContext()
					}

					is VoteResult.Killed -> {
						guild!!.getMember(result.player).swapRoles(
							config.deadRole.snowflake,
							aliveRole,
							Lg.System.Permissions.PlayerKilled.reason.translateWithContext()
						)
						Lg.DayCycle.Response.Success.killed.translateWithContext(
							guild!!.getMember(result.player).effectiveName
						)
					}
				}
				respond { content = response }
				return@adminOnly
			}
			newVoteVillage.apply {
				setChoices(emptyList())
				botCache.updateVote(this)
			}
			botCache.nextNight()
			listOf(LgChannelType.VILLAGE, LgChannelType.VOTES, LgChannelType.SUJETS).forEach { channelName ->
				botCache.getChannel(channelName)
					?.getTopChannel()
					?.run {
						removeRolePermissions(aliveRole, Permission.ViewChannel, Permission.SendMessages)
						sendAsWebhook(this@LG.bot, this.id, "ParalyaLG", getAsset("paralya_lg", this@LG.prefix)) {
							content = Lg.System.separator.translateWithContext()
						}
					}

			}
			// For each thread in the SUJET channel, lock it
			botCache.getChannel(LgChannelType.SUJETS)?.activeThreads?.changeLockAll(true)
			botCache.getChannel(LgChannelType.LOUPS_CHAT)?.getMembersWithAccess()
				?.filterByRole(aliveRole)
				?.toList()?.forEach { member ->
					val reason = Lg.System.Permissions.Night.reason.translateWithContext()

					listOf(LgChannelType.LOUPS_VOTE, LgChannelType.LOUPS_VOTE).forEach { channelName ->
						botCache.getChannel(channelName)?.getTopChannel()?.apply {
							addMemberPermissions(member.id, Permission.ViewChannel, reason = reason)
							addMemberPermissions(member.id, Permission.SendMessages, reason = reason)
						}
					}
				}
			if (oldVillageVote?.corbeau != 0.snowflake) sendAsWebhook(
				this@LG.bot,
				botCache.getChannelId(LgChannelType.VOTES)!!,
				"Corbeau",
				getAsset("\"\uD83D\uDC26\u200D⬛ Corbeau\"", this@LG.prefix)
			) {
				content = Lg.Night.Response.Other.corbeau.translateWithContext(oldVillageVote?.corbeau?.value ?: 0)
			}
			respond { content = Lg.Night.Response.success.translateWithContext() }
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