package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.threads.edit
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.utils.getTopChannel
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.common.*
import fr.paralya.bot.lg.data.*
import fr.paralya.bot.lg.i18n.Translations.Lg
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList

enum class LGState {
	DAY,
	NIGHT;

	fun next() = when (this) {
		DAY -> NIGHT
		NIGHT -> DAY
	}
}


suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerDayCycleCommands(extension: LG) {
	val botCache = extension.botCache
	ephemeralSubCommand(::DayArguments) {
		name = Lg.Day.Command.name
		description = Lg.Day.Command.description
		action {
			adminOnly {
				val force = arguments.force
				val kill = arguments.kill
				val gameData = botCache.getGameData()
				if (gameData.state == LGState.DAY) {
					respond { content = Lg.Day.Response.Error.alreadyDay.translateWithContext() }
					return@adminOnly
				}
				val newVote = botCache.getCurrentVote(LGState.DAY) ?: VoteData.createVillageVote(
					System.currentTimeMillis().toSnowflake()
				)
					.setCurrent(true)
				botCache.updateVote(newVote)
				val newVoteWerewolf = botCache.getCurrentVote(LGState.NIGHT) ?: VoteData.createWerewolfVote(
					System.currentTimeMillis().toSnowflake()
				)
					.setCurrent(true)
				val oldWerewolfVote = botCache.getCurrentVote(LGState.NIGHT)?.apply {
					setCurrent(false)
					botCache.updateVote(this)
				}
				botCache.updateVote(newVoteWerewolf)
				val config = getKoin().get<LgConfig>()
				val aliveRole = config.aliveRole.toSnowflake()
				if (oldWerewolfVote?.votes?.keys?.isNotEmpty() == true) {
					val voteCount = oldWerewolfVote.votes.values.groupingBy { it }.eachCount()
					val maxVote = voteCount.maxByOrNull { it.value }?.key
					val maxVotedPlayers = voteCount.filter { it.key == maxVote }.keys
					if (maxVotedPlayers.size > 1 && !force) {
						sendAsWebhook(
							extension.bot,
							botCache.getChannelId("LOUPS_VOTE")!!,
							"ParalyaLG",
							getAsset("lg")
						) {
							content =
								Lg.Day.Response.Other.equality.translateWithContext(maxVotedPlayers.joinToString(", ") { "<@${it.value}>" })
						}
						newVoteWerewolf.apply {
							setChoices(maxVotedPlayers.toList())
							botCache.updateVote(this)
						}
						respond {
							content = Lg.Day.Response.Other.secondVote.translateWithContext()
						}
						return@adminOnly
					} else if (maxVotedPlayers.size == 1 && kill) {
						val playerToKill = maxVotedPlayers.first()
						guild!!.getMember(playerToKill).apply {
							addRole(config.deadRole.toSnowflake(), Lg.System.Permissions.PlayerKilled.reason.translateWithContext())
							removeRole(config.aliveRole.toSnowflake(), Lg.System.Permissions.PlayerKilled.reason.translateWithContext())
						}
						respond {
							content = Lg.Day.Response.Success.killed.translateWithContext(guild!!.getMember(playerToKill).effectiveName)
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
				botCache.getChannel("LOUP_CHAT")?.getMembersWithAccess()
					?.filter { member -> member.hasRole(guild!!.getRole(aliveRole)) }
					?.toList()?.forEach { member ->
						val loupChat = botCache.getChannel("LOUP_CHAT")!!
						val loupVote = botCache.getChannel("LOUPS_VOTE")!!
						loupVote.apply {
							addMemberPermissions(member.id, Permission.ViewChannel, reason = Lg.System.Permissions.Day.reason.translateWithContext())
							removeMemberPermission(member.id, Permission.SendMessages, reason = Lg.System.Permissions.Day.reason.translateWithContext())
						}
						loupChat.apply {
							addMemberPermissions(member.id, Permission.ViewChannel, reason = Lg.System.Permissions.Day.reason.translateWithContext())
							removeMemberPermission(member.id, Permission.SendMessages, reason = Lg.System.Permissions.Day.reason.translateWithContext())
						}
					}
				respond {
					content = Lg.Day.Response.success.translateWithContext()
				}
			}
		}
	}
}

private class DayArguments : Arguments() {
	val force by defaultingBoolean {
		name = Lg.Day.Argument.Force.name
		description = Lg.Day.Argument.Force.description
		defaultValue = false
	}

	val kill by defaultingBoolean {
		name = Lg.Day.Argument.Kill.name
		description = Lg.Day.Argument.Kill.description
		defaultValue = true
	}
}

