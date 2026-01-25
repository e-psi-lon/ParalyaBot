package fr.paralya.bot.common.plugins

import dev.kordex.core.ExtensibleBot
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.plugins.PluginManager
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import org.pf4j.PluginState
import java.nio.file.Path

class PluginManager(roots: List<Path>, enabled: Boolean) : PluginManager(roots, enabled), KordExKoinComponent {


    override fun startPlugin(pluginId: String?): PluginState? {
        return super.startPlugin(pluginId).also {
            val bot by inject<ExtensibleBot>()
            bot.kordRef.launch {
                bot.send(PluginReadyEvent(
                    pluginId ?: error("Plugin id couldn't be found."),
                    bot.kordRef.guilds.toSet()
                ))
            }
        }
    }

    override fun stopPlugin(pluginId: String?): PluginState? {
        return super.stopPlugin(pluginId)
    }

    override fun deletePlugin(pluginId: String?): Boolean {
        return super.deletePlugin(pluginId)
    }

    fun reloadPlugin(pluginId: String?): PluginState? {
        return null
    }
}