package fr.paralya.bot.common.plugins

import java.nio.file.Path

internal open class PluginReloadStrategy(
    private val pluginManager: PluginManager,
    val oldPlugin: Path,
    val newPlugin: Path,
) {

    open fun reload(): PluginReloadResult? {
        return null
    }

}

/**
 * Internal helper factory to create a [PluginReloadStrategy] instance based on the current [PluginManager] instance.
 * Avoids explicit `this` at call site.
 */
internal fun PluginManager.createReloadStrategy(oldPluginPath: Path, newPluginPath: Path)=
    PluginReloadStrategy(this, oldPluginPath, newPluginPath)