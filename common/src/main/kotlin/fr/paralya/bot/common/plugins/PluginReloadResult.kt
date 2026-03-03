package fr.paralya.bot.common.plugins

sealed interface PluginReloadResult

sealed interface PluginReloadError : PluginReloadResult
object OldPluginNotFound : PluginReloadError
object OldPluginFallbackFailedToReload : PluginReloadError
object OldPluginReused : PluginReloadError