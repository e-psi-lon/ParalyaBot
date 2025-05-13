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
import fr.paralya.bot.lg.data.getChannelId
import fr.paralya.bot.lg.data.getCurrentVote
import fr.paralya.bot.lg.data.vote
import fr.paralya.bot.lg.data.voteCorbeau
import fr.paralya.bot.lg.i18n.Translations.Lg

context(LG)
suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerVotingCommands() {
	group(Lg.Vote.Command.name) {
		description = Lg.Vote.Command.description
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Village.Command.name
			description = Lg.Vote.Village.Command.description
			val botCache = this@LG.botCache
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id == botCache.getChannelId("CORBEAU")) {
					if (botCache.getCurrentVote(LGState.DAY)?.corbeau != 0.toSnowflake()) {
						respond { content = "Vous avez déjà voté en tant que corbeau !" }
					}
					botCache.voteCorbeau(target.id)
					respond { content = "Vous avez voté contre ${target.effectiveName}" }
				} else if (channel.id != botCache.getChannelId("VOTES"))
					respond { content = "Vous ne pouvez pas voter ici !" }
				else if (botCache.getCurrentVote(LGState.DAY)?.choices?.isNotEmpty() == true && botCache.getCurrentVote(
						LGState.DAY
					)?.choices?.contains(target.id) != true
				)
					respond { content = "Ce joueur n'est pas dans les choix disponibles !" }
				else if (botCache.getCurrentVote(LGState.DAY) == null)
					respond { content = "Aucun vote n'est actuellement en cours !" }
				else {
					val alreadyVoted =
						this@LG.botCache.getCurrentVote(LGState.DAY)?.votes?.containsKey(target.id) == true
					this@LG.botCache.vote(user.id, target)
					respond {
						content = "Vous avez voté contre ${target.effectiveName} !"
					}
					val webhook = botCache.getChannelId("VOTES")?.let { it1 ->
						getWebhook(
							it1,
							this@LG.bot,
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
			val botCache = this@LG.botCache
			action {
				val target = arguments.target
				val reason = arguments.reason
				if (channel.id != botCache.getChannelId("LOUPS_VOTE"))
					respond { content = "Vous ne pouvez pas voter ici !" }
				else if (botCache.getCurrentVote(LGState.NIGHT)?.choices?.isNotEmpty() == true && botCache.getCurrentVote(
						LGState.NIGHT
					)?.choices?.contains(target.id) != true
				)
					respond { content = "Ce joueur n'est pas dans les choix disponibles !" }
				else if (botCache.getCurrentVote(LGState.NIGHT) == null)
					respond { content = "Aucun vote n'est actuellement en cours !" }
				else {
					val alreadyVoted =
						botCache.getCurrentVote(LGState.NIGHT)?.votes?.containsKey(target.id) == true
					botCache.vote(user.id, target)
					respond {
						content = "Vous avez voté contre ${target.effectiveName} !"
					}
					val webhook = botCache.getChannelId("LOUP_VOTE")?.let { it1 ->
						getWebhook(
							it1,
							this@LG.bot,
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