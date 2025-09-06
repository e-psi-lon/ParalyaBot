package fr.paralya.bot.lg

import dev.kord.core.entity.User
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.types.TranslatableContext
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.common.translateWithContext
import fr.paralya.bot.lg.data.LgChannelType
import fr.paralya.bot.lg.data.getChannelId
import fr.paralya.bot.lg.I18n.Lg
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
			val botCache = lg.botCache
			val voteManager = lg.voteManager
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id == botCache.getChannelId(LgChannelType.CORBEAU)) {
					if (voteManager.getCurrentVote(LGState.DAY)?.corbeau != 0.snowflake) {
						respond { content = Lg.Vote.Response.Error.Corbeau.alreadyVoted.translateWithContext() }
					}
					voteManager.voteCorbeau(target.id)
					respond {
						content = Lg.Vote.Response.Success.Corbeau.vote.translateWithContext(target.mention)
					}
				} else if (channel.id != botCache.getChannelId(LgChannelType.VOTES))
					respond { content = Lg.Vote.Response.Error.cantVoteHere.translateWithContext() }
				else if (voteManager.getCurrentVote(LGState.DAY)?.choices?.isNotEmpty() == true && voteManager.getCurrentVote(
						LGState.DAY
					)?.choices?.contains(target.id) != true
				)
					respond { content = Lg.Vote.Response.Error.notInChoices.translateWithContext() }
				else if (voteManager.getCurrentVote(LGState.DAY) == null)
					respond { content = Lg.Vote.Response.Error.noCurrentVote.translateWithContext() }
				else {
					val alreadyVoted =
						voteManager.getCurrentVote(LGState.DAY)?.votes?.containsKey(target.id) == true
					voteManager.vote(user.id, target)
					respond {
						content = Lg.Vote.Response.Success.vote.translateWithContext(target.mention)
					}
					sendAsWebhook(
						lg.bot,
						botCache.getChannelId(LgChannelType.VOTES)!!,
						member?.asMember()?.effectiveName ?: "Inconnu",
						member?.asMember()?.avatar?.cdnUrl?.toUrl(),
						"votes"
					) {
						content = getVotePublicResponse(target, reason, alreadyVoted)
					}
				}
			}
		}
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Werewolf.Command.name
			description = Lg.Vote.Werewolf.Command.description
			val botCache = lg.botCache
			val voteManager = lg.voteManager
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id != botCache.getChannelId(LgChannelType.LOUPS_VOTE))
					respond { content = Lg.Vote.Response.Error.cantVoteHere.translateWithContext() }
				else if (voteManager.getCurrentVote(LGState.NIGHT)?.choices?.isNotEmpty() == true && voteManager.getCurrentVote(
						LGState.NIGHT
					)?.choices?.contains(target.id) != true
				)
					respond { content = Lg.Vote.Response.Error.notInChoices.translateWithContext() }
				else if (voteManager.getCurrentVote(LGState.NIGHT) == null)
					respond { content = Lg.Vote.Response.Error.noCurrentVote.translateWithContext() }
				else {
					val alreadyVoted =
						voteManager.getCurrentVote(LGState.NIGHT)?.votes?.containsKey(target.id) == true
					voteManager.vote(user.id, target)
					respond {
						content = Lg.Vote.Response.Success.vote.translateWithContext(target.mention)
					}
					sendAsWebhook(
						lg.bot,
						botCache.getChannelId(LgChannelType.LOUPS_VOTE)!!,
						member?.asMember()?.effectiveName ?: "Inconnu",
						member?.asMember()?.avatar?.cdnUrl?.toUrl(),
						"votes"
					) {
						content = getVotePublicResponse(target, reason, alreadyVoted)
					}
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
                    respond { content = Lg.Vote.Response.Error.cantVoteHere.translateWithContext() }
                    return@action
                } else {
                    when (channelId) {
                        votes, voteLoups -> {
                            val state = if (channelId == votes) LGState.DAY else LGState.NIGHT
                            val currentVote = voteManager.getCurrentVote(state)
                            val isCorrectState = lg.botCache.getGameData().state == state
                            if (currentVote == null || !currentVote.votes.containsKey(user.id) || !isCorrectState) {
                                respond { content = Lg.Unvote.Response.Error.noVote.translateWithContext() }
                                return@action
                            }
                            voteManager.unvote(user.id)
                            respond { content = Lg.Unvote.Response.success.translateWithContext() }
                        }
                        voteCorbeau -> {
                            val currentVote = voteManager.getCurrentVote(LGState.DAY)
                            val isCorrectState = lg.botCache.getGameData().state == LGState.NIGHT
                            if (currentVote == null || currentVote.corbeau == 0.snowflake || !isCorrectState) {
                                respond { content = Lg.Unvote.Response.Error.noVote.translateWithContext() }
                                return@action
                            }
                            voteManager.unvoteCorbeau()
                            respond { content = Lg.Unvote.Response.success.translateWithContext() }
                        }
                    }
                }
                if (previousId != null)
                    channel.getMessage(previousId).delete(Lg.System.MessageDelete.VoteMessage.reason.translateWithContext())
            }
        }
        ephemeralSubCommand {
            name = Lg.VoteList.Command.name
            description = Lg.VoteList.Command.description

            action {
                val village = lg.botCache.getChannelId(LgChannelType.VILLAGE)!!
                val votes = lg.botCache.getChannelId(LgChannelType.VOTES)!!
                val loups = lg.botCache.getChannelId(LgChannelType.LOUPS_CHAT)!!
                val loupsVotes = lg.botCache.getChannelId(LgChannelType.LOUPS_VOTE)!!
                val dayChannels = setOf(village, votes)
                val nightChannels = setOf(loups, loupsVotes)
                if (channel.id !in dayChannels + nightChannels) {
                    respond { content = Lg.VoteList.Response.Error.cantSeeVotesHere.translateWithContext() }
                    return@action
                } else {
                    val state = if (channel.id in dayChannels) LGState.DAY else LGState.NIGHT
                    val vote = lg.voteManager.getCurrentVote(state)
                    val votes = vote?.votes
                    val isCorrectState = lg.botCache.getGameData().state == state
                    if (votes.isNullOrEmpty() || !isCorrectState) {
                        respond { content = Lg.VoteList.Response.Error.noVotes.translateWithContext() }
                        return@action
                    }
                    val votersByTarget = votes.entries.groupBy({ it.value }, { it.key })
                    val voteCountByTarget = votersByTarget.mapValues { it.value.size }.toMutableMap()
                    if (vote.corbeau != 0.snowflake && !voteCountByTarget.containsKey(vote.corbeau)) voteCountByTarget[vote.corbeau] = 2
                    respond {
                        embed {
                            title = Lg.VoteList.Response.Success.Embed.title.translateWithContext()
                            description = if (state == LGState.DAY) Lg.VoteList.Response.Success.Embed.Description.day.translateWithContext()
                                    else Lg.VoteList.Response.Success.Embed.Description.night.translateWithContext()

                            voteCountByTarget.entries.sortedByDescending { it.value }.forEach { (target, count) ->
                                field(guild!!.getMember(target).mention, inline = false) {
                                    val playerNameList = votersByTarget[target]?.map {
                                        guild!!.getMember(it).mention
                                    }?.sorted()?.joinToString(", ", prefix = "(", postfix = ")") ?: ""
                                    if (target == vote.corbeau) {
                                        Lg.VoteList.Response.Success.Embed.Field.WithCorbeau.description.translateWithContext(count + 2, playerNameList)
                                    } else {
                                        Lg.VoteList.Response.Success.Embed.Field.description.translateWithContext(count, playerNameList)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
) = if (alreadyVoted && reason != null)
	Lg.Vote.Response.Success.Public.changeReason.translateWithContext(target.mention, reason)
else if (alreadyVoted)
	Lg.Vote.Response.Success.Public.change.translateWithContext(target.mention)
else if (reason != null)
	Lg.Vote.Response.Success.Public.voteReason.translateWithContext(target.mention, reason)
else
	Lg.Vote.Response.Success.Public.vote.translateWithContext(target.mention)

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