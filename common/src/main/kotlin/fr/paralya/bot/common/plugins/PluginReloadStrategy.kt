package fr.paralya.bot.common.plugins

import java.nio.file.Path

class PluginReloadStrategy(
    val oldPlugin: Path,
    val newPlugin: Path,
    val newPluginInstance: Plugin
) {

}
