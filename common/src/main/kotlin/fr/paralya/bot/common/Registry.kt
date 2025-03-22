package fr.paralya.bot.common

import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.builder.PresenceBuilder
import dev.kordex.core.i18n.types.Key
import fr.paralya.bot.common.i18n.Translations

val GAME_MODE_NONE = Translations.GameMode.none to "none"

class Registry {
    private val gameModes = mutableMapOf<Key, String>()

    fun registerGameMode(key: Key, gameMode: String) {
        gameModes[key] = gameMode
    }

    fun getGameMode(value: String) = if (gameModes.containsValue(value)) {
        gameModes.filterValues { it == value }.keys.first() to value
    } else {
        GAME_MODE_NONE
    }

    fun getGameModes(): MutableMap<Key, String> {
        return gameModes
    }

    fun unloadGameMode(value: String) {
        gameModes.remove(gameModes.filterValues { it == value }.keys.first())
    }
}

fun PresenceBuilder.gameMode(gameMode: Pair<Key, String>) {
    if (gameMode == GAME_MODE_NONE) {
        status = PresenceStatus.Idle
        this.watching("Paralya sans animation en cours...")
    } else {
        status = PresenceStatus.Online
        this.playing("une partie de ${gameMode.first.translate()}")
    }
}