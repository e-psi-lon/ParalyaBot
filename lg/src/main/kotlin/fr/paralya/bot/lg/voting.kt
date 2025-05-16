package fr.paralya.bot.lg

import dev.kord.core.entity.User
import dev.kord.core.entity.effectiveName
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.types.TranslatableContext
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.common.translateWithContext
import fr.paralya.bot.lg.data.LgChannelType
import fr.paralya.bot.lg.data.getChannelId
import fr.paralya.bot.lg.data.getCurrentVote
import fr.paralya.bot.lg.i18n.Translations.Lg

/**
 * Registers the commands for voting in the game.
 * This includes commands for voting during the day and night phases.
 *
 * @receiver The instance of the [LG] extension that will handle the commands.
 */
context(LG)
suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerVotingCommands() {
	group(Lg.Vote.Command.name) {
		description = Lg.Vote.Command.description
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Village.Command.name
			description = Lg.Vote.Village.Command.description
			val botCache = this@LG.botCache
			val voteManager = this@LG.voteManager
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id == botCache.getChannelId(LgChannelType.CORBEAU)) {
					if (voteManager.getCurrentVote(LGState.DAY)?.corbeau != 0.snowflake) {
						respond { content = Lg.Vote.Response.Error.Corbeau.alreadyVoted.translateWithContext() }
					}
					voteManager.voteCorbeau(target.id)
					respond { content = Lg.Vote.Response.Success.Corbeau.vote.translateWithContext(target.effectiveName) }
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
						content = Lg.Vote.Response.Success.vote.translateWithContext(target.effectiveName)
					}
					sendAsWebhook(
						this@LG.bot,
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
			val botCache = this@LG.botCache
			val voteManager = this@LG.voteManager
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id != botCache.getChannelId(LgChannelType.LOUPS_VOTE))
					respond { content = Lg.Vote.Response.Error.cantVoteHere.translateWithContext() }
				else if (voteManager.getCurrentVote(LGState.NIGHT)?.choices?.isNotEmpty() == true && botCache.getCurrentVote(
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
						content = Lg.Vote.Response.Success.vote.translateWithContext(target.effectiveName)
					}
					sendAsWebhook(
						this@LG.bot,
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
Lg.Vote.Response.Success.Public.changeReason.translateWithContext(target.effectiveName, reason)
else if (alreadyVoted)
Lg.Vote.Response.Success.Public.change.translateWithContext(target.effectiveName)
else if (reason != null)
Lg.Vote.Response.Success.Public.voteReason.translateWithContext(target.effectiveName, reason)
else
Lg.Vote.Response.Success.Public.vote.translateWithContext(target.effectiveName)

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