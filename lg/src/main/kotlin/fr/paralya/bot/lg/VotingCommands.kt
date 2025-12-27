package fr.paralya.bot.lg

import dev.kord.core.entity.User
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommandContext
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.dm
import dev.kordex.i18n.Key
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.lg.data.LgChannelType
import fr.paralya.bot.lg.data.getChannelId
import fr.paralya.bot.lg.I18n as Lg
import fr.paralya.bot.lg.data.getGameData

/**
 * Registers the commands related to the voting mechanism in the game
 * This includes commands for voting during the day and night phases.
 * But also the command to retract a vote, shows the vote list and some admin commands.
 *
 * @receiver The instance of the [LG] extension that will handle the commands.
 */
context(lg: LG)
suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerVotingCommands() {
	group(Lg.Vote.Command.name) {
		description = Lg.Vote.Command.description
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Village.Command.name
			description = Lg.Vote.Village.Command.description
			action {
				handleVote(LGState.DAY, LgChannelType.VOTES, arguments.target, arguments.reason, true)
			}
		}
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Werewolf.Command.name
			description = Lg.Vote.Werewolf.Command.description
			action {
				handleVote(LGState.NIGHT, LgChannelType.LOUPS_VOTE, arguments.target, arguments.reason)
			}
		}
		ephemeralSubCommand {
			name = Lg.Vote.List.Command.name
			description = Lg.Vote.List.Command.description

			action {
				val state = validateVoteChannel(Lg.Vote.Response.Error.cantVoteHere) ?: return@action
				val vote = lg.voteManager.getCurrentVote(state)
				val isCorrectState = lg.botCache.getGameData().state == state
				if (vote?.votes.isNullOrEmpty() || !isCorrectState) {
					respond { content = Lg.Vote.List.Response.Error.noVotes.contextTranslate() }
					return@action
				}
				val voteCount = lg.voteManager.getVoteCount(vote)
				val votersByTarget = vote.votes.entries.groupBy({ it.value }, { it.key })
				respond {
					embed {
						title = Lg.Vote.List.Response.Success.Embed.title.contextTranslate()
						description = if (state == LGState.DAY)
							Lg.Vote.List.Response.Success.Embed.Description.day.contextTranslate()
						else Lg.Vote.List.Response.Success.Embed.Description.night.contextTranslate()
						voteCount.entries.sortedByDescending { it.value }.forEach { (target, count) ->
							field(guild!!.getMember(target).mention, inline = false) {
								val playerNameList = votersByTarget[target]?.map {
									guild!!.getMember(it).mention
								}?.sorted()?.joinToString(", ", prefix = "(", postfix = ")") ?: ""

								if (target == vote.corbeau) {
									Lg.Vote.List.Response.Success.Embed.Field.WithCorbeau.description
										.contextTranslate(count, playerNameList)
								} else Lg.Vote.List.Response.Success.Embed.Field.description
									.contextTranslate(count, playerNameList)
							}
						}
					}
				}
			}
		}
		ephemeralSubCommand {
			name = Lg.Vote.Reset.Command.name
			description = Lg.Vote.Reset.Command.description
			adminOnly {
				val state = validateVoteChannel(Lg.Vote.Response.Error.cantVoteHere) ?: return@adminOnly
				val vote = lg.voteManager.getCurrentVote(state)
				val votes = vote?.votes
				val isCorrectState = lg.botCache.getGameData().state == state
				if (votes.isNullOrEmpty() || !isCorrectState) {
					respond { content = Lg.Vote.Reset.Response.Error.noVotes.contextTranslate() }
					return@adminOnly
				}
				lg.voteManager.resetVotes(state)
				respond { content = Lg.Vote.Reset.Response.success.contextTranslate() }
			}
		}
	}
	ephemeralSubCommand(::UnvoteArguments) {
		name = Lg.Unvote.Command.name
		description = Lg.Unvote.Command.description

		action {
			val previousId = arguments.previousId?.snowflake
			val channelId = channel.id
			val voteManager = lg.voteManager
			val votes = lg.botCache.getChannelId(LgChannelType.VOTES)!!
			val voteLoups = lg.botCache.getChannelId(LgChannelType.LOUPS_VOTE)!!
			val voteCorbeau = lg.botCache.getChannelId(LgChannelType.CORBEAU)!!
			val voteChannels = listOf(votes, voteLoups, voteCorbeau)

			if (channelId !in voteChannels) {
				respond { content = Lg.Vote.Response.Error.cantVoteHere.contextTranslate() }
				return@action
			} else {
				when (channelId) {
					votes, voteLoups -> {
						val state = if (channelId == votes) LGState.DAY else LGState.NIGHT
						val currentVote = voteManager.getCurrentVote(state)
						val isCorrectState = lg.botCache.getGameData().state == state
						if (currentVote == null || !currentVote.votes.containsKey(user.id) || !isCorrectState) {
							respond { content = Lg.Unvote.Response.Error.noVote.contextTranslate() }
							return@action
						}
						voteManager.unvote(user.id)
						respond { content = Lg.Unvote.Response.success.contextTranslate() }
					}
					voteCorbeau -> {
						val currentVote = voteManager.getCurrentVote(LGState.DAY)
						val isCorrectState = lg.botCache.getGameData().state == LGState.NIGHT
						if (currentVote == null || currentVote.corbeau == 0.snowflake || !isCorrectState) {
							respond { content = Lg.Unvote.Response.Error.noVote.contextTranslate() }
							return@action
						}
						voteManager.unvoteCorbeau()
						respond { content = Lg.Unvote.Response.success.contextTranslate() }
					}
				}
			}
			if (previousId != null)
				channel.getMessage(previousId).delete(Lg.System.MessageDelete.VoteMessage.reason.contextTranslate())
		}
	}
	ephemeralSubCommand {
		name = Lg.MostVoted.Command.name
		description = Lg.MostVoted.Command.description

		adminOnly {
			val state = validateVoteChannel(Lg.MostVoted.Response.Error.cantUseHere)
				?: return@adminOnly
			if (state != LGState.DAY) {
				respond { content = Lg.MostVoted.Response.Error.onlyVillageVotes.contextTranslate() }
				return@adminOnly
			}
			val currentVote = lg.voteManager.getCurrentVote(state)
			if (currentVote?.votes.isNullOrEmpty()) {
				respond { content = Lg.MostVoted.Response.Error.noVotes.contextTranslate() }
				return@adminOnly
			}
			val voteCount = lg.voteManager.getVoteCount(currentVote)
			val maxVotes = voteCount.values.maxOrNull() ?: 0
			val mostVotedPlayers = voteCount.filter { it.value == maxVotes }.keys
			if (mostVotedPlayers.size > 1) {
				val members = mostVotedPlayers.map { playerId ->
					val member = guild!!.getMember(playerId)
					member.dm {
						content = Lg.MostVoted.Response.Success.tie.contextTranslate(maxVotes)
					}
					member
				}
				respond {
					content = Lg.MostVoted.Response.Success.sentTie.contextTranslate(
						members.joinToString(", ") { it.mention },
						maxVotes
					)
				}
			} else {
				val member = guild!!.getMember(mostVotedPlayers.first())
				member.dm {
					content = Lg.MostVoted.Response.Success.single.contextTranslate(maxVotes)
				}
				respond {
					content = Lg.MostVoted.Response.Success.sentSingle.contextTranslate(member.mention, maxVotes)
				}
			}
		}
	}
}

context(lg: LG)
suspend fun <C : EphemeralSlashCommandContext<*, *>> C.validateVoteChannel(errorMessage: Key): LGState? {
	val village = lg.botCache.getChannelId(LgChannelType.VILLAGE)!!
	val votes = lg.botCache.getChannelId(LgChannelType.VOTES)!!
	val loups = lg.botCache.getChannelId(LgChannelType.LOUPS_CHAT)!!
	val loupsVotes = lg.botCache.getChannelId(LgChannelType.LOUPS_VOTE)!!
	val dayChannels = setOf(village, votes)
	val nightChannels = setOf(loups, loupsVotes)

	return when (channel.id) {
		in dayChannels -> LGState.DAY
		in nightChannels -> LGState.NIGHT
		else -> {
			respond { content = errorMessage.contextTranslate() }
			null
		}
	}
}

context(lg: LG)
private suspend fun <A : Arguments, M : ModalForm> EphemeralSlashCommandContext<A, M>.handleVote(
	state: LGState,
	voteChannelType: LgChannelType,
	target: User,
	reason: String?,
	handleCorbeau: Boolean = false
) {
	val botCache = lg.botCache
	val voteManager = lg.voteManager
	val currentVote = voteManager.getCurrentVote(state)
	if (handleCorbeau && channel.id == botCache.getChannelId(LgChannelType.CORBEAU)) {
		if (currentVote?.corbeau != 0.snowflake) {
			respond { content = Lg.Vote.Response.Error.Corbeau.alreadyVoted.contextTranslate() }
			return
		}
		voteManager.voteCorbeau(target.id)
		respond { content = Lg.Vote.Response.Success.Corbeau.vote.contextTranslate(target.mention) }
		return
	}
	if (channel.id != botCache.getChannelId(voteChannelType)) {
		respond { content = Lg.Vote.Response.Error.cantVoteHere.contextTranslate() }
		return
	}
	if (currentVote == null) {
		respond { content = Lg.Vote.Response.Error.noCurrentVote.contextTranslate() }
		return
	}
	if (currentVote.choices.isNotEmpty() && target.id !in currentVote.choices) {
		respond { content = Lg.Vote.Response.Error.notInChoices.contextTranslate() }
		return
	}
	val alreadyVoted = user.id in currentVote.votes
	voteManager.vote(user.id, target)
	respond { content = Lg.Vote.Response.Success.vote.contextTranslate(target.mention) }
	sendAsWebhook(
		lg.bot,
		botCache.getChannelId(voteChannelType)!!,
		member?.asMember()?.effectiveName ?: "Inconnu",
		member?.asMember()?.avatar?.cdnUrl?.toUrl(),
		"votes"
	) {
		content = getVotePublicResponse(target, reason, alreadyVoted)
	}
}


/**
 * Gets the public response message for a vote.
 *
 * @param target The user being voted for.
 * @param reason An optional reason for the vote.
 * @param alreadyVoted Indicates if the user has already voted.
 * @return The translated message for the vote response.
 */
suspend fun TranslatableContext.getVotePublicResponse(
	target: User,
	reason: String? = null,
	alreadyVoted: Boolean = false,
) = when {
	alreadyVoted && reason != null ->
		Lg.Vote.Response.Success.Public.changeReason.contextTranslate(target.mention, reason)
	alreadyVoted -> Lg.Vote.Response.Success.Public.change.contextTranslate(target.mention)
	reason != null -> Lg.Vote.Response.Success.Public.voteReason.contextTranslate(target.mention, reason)
	else -> Lg.Vote.Response.Success.Public.vote.contextTranslate(target.mention)
}

/**
 * Arguments for the vote command.
 * This class defines the arguments that can be passed to the vote command.
 *
 * @property target The user to vote for.
 * @property reason An optional reason for the vote.
 */
private class VoteArguments : Arguments() {
	val target by user {
		name = Lg.Vote.Argument.Target.name
		description = Lg.Vote.Argument.Target.description
	}
	val reason by optionalString {
		name = Lg.Vote.Argument.Reason.name
		description = Lg.Vote.Argument.Reason.description
	}
}

private class UnvoteArguments : Arguments() {
	val previousId by optionalInt {
		name = Lg.Unvote.Argument.PreviousId.name
		description = Lg.Unvote.Argument.PreviousId.description
	}
}