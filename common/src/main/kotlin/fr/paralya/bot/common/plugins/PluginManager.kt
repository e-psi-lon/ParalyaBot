package fr.paralya.bot.common.plugins

import dev.kordex.core.ExtensibleBot
import dev.kordex.core.koin.KordExKoinComponent
import fr.paralya.bot.common.ApiVersion
import fr.paralya.bot.common.CommonModule
import dev.kordex.core.plugins.PluginManager as KordExPluginManager
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import org.pf4j.PluginRuntimeException
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.pf4j.PluginStateListener
import org.pf4j.PluginWrapper
import java.nio.file.Path

class PluginManager(roots: List<Path>, enabled: Boolean) : KordExPluginManager(roots, enabled), KordExKoinComponent {

    init {
        addPluginStateListener(PluginListener())
    }

    @Suppress("ThrowsCount")
    internal fun validatePlugin(wrapper: PluginWrapper, pluginPath: Path) {
        val classLoader = wrapper.pluginClassLoader
        val pluginClass = classLoader.loadClass(wrapper.descriptor.pluginClass)
        val pluginId = wrapper.pluginId
        logger.debug { "Verifying validity of the plugin at path $pluginPath with main class ${wrapper.descriptor.pluginClass}" }
        if (!Plugin::class.java.isAssignableFrom(pluginClass)) {
            throw PluginValidationException("Plugin $pluginId does not extend Plugin", pluginId)
        }

        val annotation = pluginClass.getAnnotation(ApiVersion::class.java) ?:
        throw PluginValidationException(
            "Plugin $pluginId is missing @ApiVersion annotation",
            pluginId
        )

        if (!versionManager.checkVersionConstraint(
                annotation.version, ">=${CommonModule.MIN_COMPATIBLE_VERSION}"
            )) throw PluginInvalidVersionException("Plugin $pluginId is not compatible with the current API version. " +
                "Minimum compatible version: ${CommonModule.MIN_COMPATIBLE_VERSION}", pluginId, annotation.version)

        if (!versionManager.checkVersionConstraint(
                annotation.version, "<=${CommonModule.API_VERSION}"
            )) throw PluginInvalidVersionException("Plugin $pluginId requires API version ${annotation.version}, " +
                "but the current API version is ${CommonModule.API_VERSION}", pluginId, annotation.version)

    }


    override fun loadPluginFromPath(pluginPath: Path): PluginWrapper {
        val wrapper = super.loadPluginFromPath(pluginPath)
        validatePlugin(wrapper, pluginPath)
        return wrapper
    }


    /**
     * Reloads a plugin.
     *
     * @param pluginId The ID of the plugin to be reloaded.
     * @param newPath The new path to the plugin. If null, the plugin will be reloaded from its current path.
     */
    fun reloadPlugin(pluginId: String, newPath: Path? = null): PluginReloadResult {
        val plugin = getPlugin(pluginId) ?: return OldPluginNotFound
        val pluginPath = plugin.pluginPath!! // PluginWrapper requires a path in its constructor
        val fullPath = if (pluginPath.isAbsolute) pluginPath else pluginsRoot.resolve(pluginPath)
        val reloadStrategy = createReloadStrategy(pluginId, fullPath, newPath ?: fullPath, logger)
        return reloadStrategy.reload()
    }

    // TODO: Consolidate handling and expose to public API
    private fun reloadPlugins() {
        unloadPlugins()
        loadPlugins()
        startPlugins()
    }

        fun tryStopPlugin(pluginId: String) = try {
            // Non-nullable enum
            // And if PF4J changes, we want to get a failure not a success holding null
            Result.success(stopPlugin(pluginId)!!)
        } catch (e: PluginRuntimeException) {
            Result.failure(e)
        }

        fun tryLoadAndStartPlugin(pluginPath: Path) = try {
            val fullPath = if (pluginPath.isAbsolute) pluginPath else pluginsRoot.resolve(pluginPath)
            val pluginEntry = plugins.entries.find { it.value.pluginPath == fullPath }
            val pluginId = if (pluginEntry != null) pluginEntry.key else loadPlugin(fullPath)
            // Non-nullable enum
            // And if PF4J changes, we want to get a failure not a success holding null
            Result.success(startPlugin(pluginId)!!)
        } catch (e: Exception) { // Untrusted start method
            Result.failure(e)
        }

    private inner class PluginListener : PluginStateListener, KordExKoinComponent {
        override fun pluginStateChanged(event: PluginStateEvent?) {
            event ?: return
            val bot = getKoin().getOrNull<ExtensibleBot>()
            if (bot == null) {
                logger.debug { "Plugin state changed before initialization of the bot itself" }
                return
            }
            logger.info { "Plugin ${event.plugin.pluginId} changed state to ${event.pluginState}" }
            if (event.pluginState == PluginState.STARTED) bot.kordRef.launch {
                bot.send(PluginReadyEvent(
                    event.plugin.pluginId, // Access to pluginState already required a non-null `plugin`
                    bot.kordRef.guilds.toSet()
                ))
            }
        }
    }
}
