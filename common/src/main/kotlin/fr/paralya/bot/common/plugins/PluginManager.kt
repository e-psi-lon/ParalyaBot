package fr.paralya.bot.common.plugins

import dev.kordex.core.ExtensibleBot
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.plugins.PluginManager as KordExPluginManager
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.pf4j.PluginStateListener
import org.pf4j.PluginWrapper
import java.nio.file.Path

class PluginManager(roots: List<Path>, enabled: Boolean) : KordExPluginManager(roots, enabled), KordExKoinComponent {

    init {
        addPluginStateListener(PluginListener())
    }

    override fun deletePlugin(pluginId: String?): Boolean {
        return super.deletePlugin(pluginId)
    }

    fun reloadPlugin(pluginId: String?): PluginState? {
        return null
    }


    private inner class PluginListener : PluginStateListener, KordExKoinComponent {
        override fun pluginStateChanged(event: PluginStateEvent?) {
            event ?: return
            val bot = getKoin().getOrNull<ExtensibleBot>() ?: return logger.debug {
                "Plugin state changed before initialization of the bot itself"
            }
            logger.info { "Plugin ${event.plugin.pluginId} changed state to ${event.pluginState}" }
            if (event.pluginState == PluginState.STARTED) bot.kordRef.launch {
                bot.send(PluginReadyEvent(
                    event.plugin.pluginId, // Access to pluginState already required a non-null `plugin``
                    bot.kordRef.guilds.toSet()
                ))
            }
        }
    }
}
