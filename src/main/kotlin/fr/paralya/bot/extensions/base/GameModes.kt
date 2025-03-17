package fr.paralya.bot.extensions.base

import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.builder.PresenceBuilder
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.i18n.types.Key
import fr.paralya.bot.i18n.Translations

enum class GameModes: ChoiceEnum {
    NONE {
        override val readableName: Key
            get() = Translations.GameMode.none
    },
    LG {
        override val readableName: Key
            get() = Translations.GameMode.lg
    },
    BW {
        override val readableName: Key
            get() = Translations.GameMode.bw
    }
}

fun PresenceBuilder.gameMode(gameMode: GameModes) {
    if (gameMode == GameModes.NONE) {
        status = PresenceStatus.Idle
        this.watching("Paralya sans animation en cours...")
    } else {
        status = PresenceStatus.Online
        this.playing("une partie de ${gameMode.readableName.translate()}")
    }
}