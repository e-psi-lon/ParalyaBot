package fr.paralya.bot.extensions.lg

import dev.kord.core.behavior.execute
import dev.kord.core.entity.effectiveName
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.forms.ModalForm
import fr.paralya.bot.extensions.data.voteCorbeau
import fr.paralya.bot.extensions.data.voteVillage
import fr.paralya.bot.extensions.data.voteWereWolf
import fr.paralya.bot.i18n.Translations.Lg
import fr.paralya.bot.utils.getWebhook

suspend fun <A: Arguments, M: ModalForm>PublicSlashCommand<A, M>.registerVotingCommands(extension: LG) {
    group(Lg.Vote.Command.name) {
        description = Lg.Vote.Command.description
        ephemeralSubCommand(::VoteArguments) {
            name = Lg.Vote.Village.Command.name
            description = Lg.Vote.Village.Command.description
            action {

                val target = arguments.target
                val reason = arguments.reason
                if (channel.id.value == extension.channels["CORBEAU"]) {
                    if (extension.villageVotes.corbeau != 0.toULong()) {
                        respond { content = "Vous avez déjà voté en tant que corbeau !" }
                    }
                    extension.voteCorbeau(target.id.value)
                    respond { content = "Vous avez voté contre ${target.effectiveName}" }
                } else if (channel.id.value != extension.channels["VOTES"])
                    respond { content = "Vous ne pouvez pas voter ici !" }
                else if (extension.villageVotes.choices.isNotEmpty() && !extension.villageVotes.choices.contains(target.id.value))
                    respond { content = "Ce joueur n'est pas dans les choix disponibles !" }
                else if (!extension.villageVotes.isCurrent)
                    respond { content = "Aucun vote n'est actuellement en cours !" }
                else {
                    val alreadyVoted = extension.villageVotes.votes.containsKey(target.id.value)
                    extension.voteVillage(target)
                    respond {
                        content = "Vous avez voté contre ${target.effectiveName} !"
                    }
                    val webhook = extension.channels["VOTES"]?.let { it1 ->
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
                if (channel.id.value != extension.channels["LOUPS_VOTE"])
                    respond { content = "Vous ne pouvez pas voter ici !" }
                else if (extension.wereWolfVotes.choices.isNotEmpty() && !extension.wereWolfVotes.choices.contains(target.id.value))
                    respond { content = "Ce joueur n'est pas dans les choix disponibles !" }
                else if (!extension.villageVotes.isCurrent)
                    respond { content = "Aucun vote n'est actuellement en cours !" }
                else {
                    val alreadyVoted = extension.wereWolfVotes.votes.containsKey(target.id.value)
                    extension.voteWereWolf(target)
                    respond {
                        content = "Vous avez voté contre ${target.effectiveName} !"
                    }
                    val webhook = extension.channels["LOUP_VOTE"]?.let { it1 ->
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

private class VoteArguments: Arguments() {
    val target by user {
        name = Lg.Vote.Argument.Target.name
        description = Lg.Vote.Argument.Target.description
    }
    val reason by optionalString {
        name = Lg.Vote.Argument.Reason.name
        description = Lg.Vote.Argument.Reason.description
    }
}