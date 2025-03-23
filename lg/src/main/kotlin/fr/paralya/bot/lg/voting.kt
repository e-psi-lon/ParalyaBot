package fr.paralya.bot.lg

import dev.kord.core.behavior.execute
import dev.kord.core.entity.effectiveName
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.forms.ModalForm
import fr.paralya.bot.common.getWebhook
import fr.paralya.bot.common.toSnowflake
import fr.paralya.bot.lg.data.getChannel
import fr.paralya.bot.lg.data.getCurrentVote
import fr.paralya.bot.lg.data.vote
import fr.paralya.bot.lg.data.voteCorbeau
import fr.paralya.bot.lg.i18n.Translations.Lg

suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerVotingCommands(extension: LG) {
	group(Lg.Vote.Command.name) {
		description = Lg.Vote.Command.description
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Village.Command.name
			description = Lg.Vote.Village.Command.description
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id == extension.botCache.getChannel("CORBEAU")) {
					if (extension.botCache.getCurrentVote(LGState.DAY)?.corbeau != 0.toSnowflake()) {
						respond { content = "Vous avez déjà voté en tant que corbeau !" }
					}
					extension.botCache.voteCorbeau(target.id)
					respond { content = "Vous avez voté contre ${target.effectiveName}" }
				} else if (channel.id != extension.botCache.getChannel("VOTES"))
					respond { content = "Vous ne pouvez pas voter ici !" }
				else if (extension.botCache.getCurrentVote(LGState.DAY)?.choices?.isNotEmpty() == true && extension.botCache.getCurrentVote(
						LGState.DAY
					)?.choices?.contains(target.id) != true
				)
					respond { content = "Ce joueur n'est pas dans les choix disponibles !" }
				else if (extension.botCache.getCurrentVote(LGState.DAY) == null)
					respond { content = "Aucun vote n'est actuellement en cours !" }
				else {
					val alreadyVoted =
						extension.botCache.getCurrentVote(LGState.DAY)?.votes?.containsKey(target.id) == true
					extension.botCache.vote(user.id, target)
					respond {
						content = "Vous avez voté contre ${target.effectiveName} !"
					}
					val webhook = extension.botCache.getChannel("VOTES")?.let { it1 ->
						getWebhook(
							it1,
							extension.bot,
							"votes"
						)
					}
					webhook?.execute(webhook.token!!) {
						content = if (alreadyVoted)
							"J'ai changé mon vote, je vote maintenant contre ${target.effectiveName}${if (reason != null) " car $reason" else ""}."
						else
							"Je vote contre ${target.effectiveName}${if (reason != null) " car $reason" else ""}."
						username = member?.asMember()?.effectiveName ?: "Inconnu"
						avatarUrl = member?.asMember()?.avatar?.cdnUrl?.toUrl()
					}
				}
			}
		}
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Werewolf.Command.name
			description = Lg.Vote.Werewolf.Command.description
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id != extension.botCache.getChannel("LOUPS_VOTE"))
					respond { content = "Vous ne pouvez pas voter ici !" }
				else if (extension.botCache.getCurrentVote(LGState.NIGHT)?.choices?.isNotEmpty() == true && extension.botCache.getCurrentVote(
						LGState.NIGHT
					)?.choices?.contains(target.id) != true
				)
					respond { content = "Ce joueur n'est pas dans les choix disponibles !" }
				else if (extension.botCache.getCurrentVote(LGState.NIGHT) == null)
					respond { content = "Aucun vote n'est actuellement en cours !" }
				else {
					val alreadyVoted =
						extension.botCache.getCurrentVote(LGState.NIGHT)?.votes?.containsKey(target.id) == true
					extension.botCache.vote(user.id, target)
					respond {
						content = "Vous avez voté contre ${target.effectiveName} !"
					}
					val webhook = extension.botCache.getChannel("LOUP_VOTE")?.let { it1 ->
						getWebhook(
							it1,
							extension.bot,
							"votes"
						)
					}
					webhook?.execute(webhook.token!!) {
						content = if (alreadyVoted)
							"J'ai changé mon vote, je vote maintenant contre ${target.effectiveName}${if (reason != null) " car $reason" else ""}."
						else
							"Je vote contre ${target.effectiveName}${if (reason != null) " car $reason" else ""}."
						username = member?.asMember()?.effectiveName ?: "Inconnu"
						avatarUrl = member?.asMember()?.avatar?.cdnUrl?.toUrl()
					}
				}
			}

		}
	}
}

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