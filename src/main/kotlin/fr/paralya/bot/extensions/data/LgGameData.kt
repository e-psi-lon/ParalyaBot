package fr.paralya.bot.extensions.data

import dev.kord.core.entity.User
import fr.paralya.bot.extensions.lg.LG

fun LG.changeState() {
	timeState = timeState.next()
}

fun LG.addChannel(name: String, id: ULong) {
	channels += name to id
}

fun LG.removeChannel(name: String) {
	channels -= name
}

fun LG.addInterview(id: ULong) {
	interviews += id
}

fun LG.removeInterview(id: ULong) {
	interviews -= id
}

fun LG.voteVillage(target: User) {
	villageVotes = villageVotes.vote(target)
}

fun LG.voteWereWolf(target: User) {
	wereWolfVotes = wereWolfVotes.vote(target)
}

fun LG.setVillageChoices(choices: List<ULong>) {
	villageVotes = villageVotes.setChoices(choices)
}

fun LG.setWereWolfChoices(choices: List<ULong>) {
	wereWolfVotes = wereWolfVotes.setChoices(choices)
}

fun LG.setCurrentVillage(isCurrent: Boolean) {
	villageVotes = villageVotes.setCurrent(isCurrent)
}

fun LG.setCurrentWereWolf(isCurrent: Boolean) {
	wereWolfVotes = wereWolfVotes.setCurrent(isCurrent)
}

fun LG.voteCorbeau(target: ULong) {
	villageVotes = villageVotes.voteCorbeau(target)
}

fun LG.addChannels(channels: Map<String, ULong>) {
	this.channels += channels
}