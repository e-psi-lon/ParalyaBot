package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.i18n.Key
import dev.kordex.core.utils.getTopChannel
import fr.paralya.bot.common.*
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.data.GamePhase.PhaseType
import fr.paralya.bot.lg.I18n as Lg
import kotlinx.coroutines.flow.toList
import org.koin.core.component.inject

private val DAY_CHANNELS = listOf(
	LgChannelType.VILLAGE,
	LgChannelType.VOTES,
	LgChannelType.SUJETS
)

private val WOLF_CHANNELS = listOf(
	LgChannelType.LOUPS_VOTE,
	LgChannelType.LOUPS_CHAT
)

/**
 * Registers the commands for managing the day and night cycle in the game.
 * This includes commands for starting a new day, ending the current day, and transitioning to night.
 *
 * @receiver The instance of the [LG] extension that will handle the commands.
 */
context(lg: LG)
suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerDayCycleCommands() {
	ephemeralSubCommand(::DayArguments) {
		name = Lg.Day.Command.name
		description = Lg.Day.Command.description

		adminOnly {
			val force = arguments.force
			val kill = arguments.kill
			val botCache = lg.botCache
			val gameData = botCache.getGameData()
			val voteManager by inject<VoteManager>()

			if (gameData.phase.isDay) {
				respond { content = Lg.Day.Response.Error.alreadyDay.contextTranslate() }
				return@adminOnly
			}
			voteManager.createVillageVote()
			val oldWerewolfVote = voteManager.finishCurrentVote(PhaseType.NIGHT)
			val newVoteWerewolf = voteManager.createWerewolfVote()
			val config by inject<LgConfig>()
			val aliveRole = config.aliveRole.snowflake
			if (oldWerewolfVote?.votes?.isNotEmpty() == true) {
				when (val result = voteManager.calculateVoteResult(oldWerewolfVote, kill, force)) {
					is VoteResult.NoVotes -> return@adminOnly
					is VoteResult.Tie -> {
						sendAsWebhook(
							lg.bot,
							LgChannelType.LOUPS_VOTE.toId(),
							"ParalyaLG",
							getAsset("paralya_lg", lg.prefix)	) {
							content = Lg.DayCycle.Response.Other.equality.contextTranslate(
								result.players.joinToString(", ") { "<@${it.value}>" }
							)
						}
						newVoteWerewolf.apply {
							setChoices(result.toList())
							voteManager.putVote(this)
						}
						respond { content = Lg.DayCycle.Response.Other.secondVote.contextTranslate() }
						return@adminOnly
					}

					is VoteResult.Killed -> {
						guild!!.getMember(result.player).swapRoles(
							config.deadRole.snowflake,
							aliveRole,
							Lg.System.Permissions.PlayerKilled.reason.contextTranslate()
						)
						respond {
							content =
								Lg.DayCycle.Response.Success.killed.contextTranslate(guild!!.getMember(result.player).effectiveName)
						}
					}
				}
			}
			newVoteWerewolf.apply {
				setChoices(emptyList())
				voteManager.putVote(this)
			}
			botCache.nextPhase()
			DAY_CHANNELS.forEach { channelName ->
				botCache.getChannel(channelName)
					?.getTopChannel()
					?.addRolePermissions(aliveRole, Permission.ViewChannel, Permission.SendMessages)
			}
			// For each thread in the SUJET channel, unlock it
			botCache.getChannel(LgChannelType.SUJETS)?.activeThreads?.changeLockAll(false)
			botCache.getChannel(LgChannelType.LOUPS_CHAT)?.getMembersWithAccess()
				?.filterByRole(aliveRole)
				?.toList()?.forEach { member ->
					val reason = Lg.System.Permissions.Day.reason.contextTranslate()
					listOf(LgChannelType.LOUPS_VOTE, LgChannelType.LOUPS_CHAT).forEach { channelName ->
						botCache.getChannel(channelName)?.getTopChannel()?.apply {
							addMemberPermissions(member.id, Permission.ViewChannel, reason = reason)
							removeRolePermissions(member.id, Permission.SendMessages, reason = reason)
						}
					}
				}
			respond { content = Lg.Day.Response.success.contextTranslate() }
		}
	}
	ephemeralSubCommand(::NightArguments) {
		name = Lg.Night.Command.name
		description = Lg.Night.Command.description

		adminOnly {
			val force = arguments.force
			val kill = arguments.kill
			val botCache = lg.botCache
			val gameData = botCache.getGameData()
			val voteManager by inject<VoteManager>()
			if (gameData.phase.isNight) {
				respond { content = Lg.Night.Response.Error.alreadyNight.contextTranslate() }
				return@adminOnly
			}
			voteManager.createWerewolfVote()
			val oldVillageVote = voteManager.finishCurrentVote(PhaseType.DAY)
			val newVoteVillage = voteManager.createVillageVote()
			val config by inject<LgConfig>()
			val aliveRole = config.aliveRole.snowflake
			if (oldVillageVote?.votes?.isNotEmpty() == true) {
				val response = when (val result = voteManager.calculateVoteResult(oldVillageVote, kill, force)) {
					is VoteResult.NoVotes -> return@adminOnly
					is VoteResult.Tie -> {
						sendAsWebhook(
							lg.bot,
							LgChannelType.VOTES.toId(),
							"ParalyaLG",
							getAsset("paralya_lg", lg.prefix),
						) {
							content = Lg.DayCycle.Response.Other.equality.contextTranslate(
								result.players.joinToString(", ") { "<@${it.value}>" }
							)
						}
						newVoteVillage.apply {
							setChoices(result.players.toList())
							voteManager.putVote(this)
						}
						Lg.DayCycle.Response.Other.secondVote.contextTranslate()
					}

					is VoteResult.Killed -> {
						guild!!.getMember(result.player).swapRoles(
							config.deadRole.snowflake,
							aliveRole,
							Lg.System.Permissions.PlayerKilled.reason.contextTranslate()
						)
						Lg.DayCycle.Response.Success.killed.contextTranslate(
							guild!!.getMember(result.player).effectiveName
						)
					}
				}
				respond { content = response }
				return@adminOnly
			}
			newVoteVillage.apply {
				setChoices(emptyList())
				voteManager.putVote(this)
			}
			botCache.nextPhase()
			DAY_CHANNELS.forEach { channelName ->
				botCache.getChannel(channelName)
					?.getTopChannel()
					?.run {
						removeRolePermissions(aliveRole, Permission.ViewChannel, Permission.SendMessages)
						sendAsWebhook(lg.bot, this.id, "ParalyaLG", getAsset("paralya_lg", lg.prefix)) {
							content = Lg.System.separator.contextTranslate()
						}
					}

			}
			// For each thread in the SUJET channel, lock it
			botCache.getChannel(LgChannelType.SUJETS)?.activeThreads?.changeLockAll(true)
			botCache.getChannel(LgChannelType.LOUPS_CHAT)?.getMembersWithAccess()
				?.filterByRole(aliveRole)
				?.toList()?.forEach { member ->
					val reason = Lg.System.Permissions.Night.reason.contextTranslate()

					WOLF_CHANNELS.forEach { channelName ->
						botCache.getChannel(channelName)?.getTopChannel()?.apply {
							addMemberPermissions(member.id, Permission.ViewChannel, reason = reason)
							addMemberPermissions(member.id, Permission.SendMessages, reason = reason)
						}
					}
				}
			if (oldVillageVote?.corbeau != null) sendAsWebhook(
				lg.bot,
				LgChannelType.VOTES.toId(),
				"Corbeau",
				getAsset("\"\uD83D\uDC26\u200D⬛ Corbeau\"", lg.prefix)
			) {
				content = Lg.Night.Response.Other.corbeau.contextTranslate(oldVillageVote.corbeau.value)
			}
			respond { content = Lg.Night.Response.success.contextTranslate() }
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