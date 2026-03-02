package fr.paralya.bot.common.plugins

import dev.kordex.core.ExtensibleBot
import dev.kordex.core.koin.KordExKoinComponent
import fr.paralya.bot.common.ApiVersion
import fr.paralya.bot.common.CommonModule
import dev.kordex.core.plugins.PluginManager as KordExPluginManager
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.pf4j.PluginStateListener
import org.pf4j.PluginWrapper
import java.nio.file.Path

class PluginManager(roots: List<Path>, enabled: Boolean) : KordExPluginManager(roots, enabled), KordExKoinComponent {

    init {
        addPluginStateListener(PluginListener())
    }


    override fun loadPluginFromPath(pluginPath: Path): PluginWrapper {
        val wrapper = super.loadPluginFromPath(pluginPath)
        val classLoader = getPluginClassLoader(wrapper.pluginId)
        val pluginClass = classLoader.loadClass(wrapper.descriptor.pluginClass)
        logger.debug { "Verifying validity of the plugin at path $pluginPath with main class ${wrapper.descriptor.pluginClass}" }
        require(Plugin::class.java.isAssignableFrom(pluginClass)) {
            "Plugin ${wrapper.pluginId} does not extend Plugin"
        }

        val annotation = pluginClass.getAnnotation(ApiVersion::class.java)
        requireNotNull(annotation) {
            "Plugin ${wrapper.pluginId} is missing @ApiVersion annotation"
        }

        require(versionManager.checkVersionConstraint(
            annotation.version, ">=${CommonModule.MIN_COMPATIBLE_VERSION}"
        )) {
            "Plugin ${wrapper.pluginId} is not compatible with the current API version. " +
                    "Minimum compatible version: ${CommonModule.MIN_COMPATIBLE_VERSION}"
        }

        require(versionManager.checkVersionConstraint(
            annotation.version, "<=${CommonModule.API_VERSION}"
        )) {
            "Plugin ${wrapper.pluginId} requires API version ${annotation.version}, " +
                    "but the current API version is ${CommonModule.API_VERSION}"
        }

        return wrapper
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
