package fr.paralya.bot.common.plugins

import dev.kordex.core.plugins.PluginManager
import org.pf4j.PluginState
import java.nio.file.Path

class PluginManager(roots: List<Path>, enabled: Boolean) : PluginManager(roots, enabled) {


    override fun startPlugin(pluginId: String?): PluginState? {
        return super.startPlugin(pluginId)
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