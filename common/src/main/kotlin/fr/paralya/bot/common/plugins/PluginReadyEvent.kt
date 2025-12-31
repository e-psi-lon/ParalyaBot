package fr.paralya.bot.common.plugins

import dev.kord.core.behavior.GuildBehavior
import dev.kordex.core.events.KordExEvent

class PluginReadyEvent(
    val guilds: Set<GuildBehavior>
) : KordExEvent
