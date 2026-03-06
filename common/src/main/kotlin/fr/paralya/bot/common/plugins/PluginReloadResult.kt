package fr.paralya.bot.common.plugins

import org.pf4j.PluginRuntimeException

sealed interface PluginReloadResult
object SuccessfulPluginReload : PluginReloadResult

sealed class PluginReloadError(val exception: Exception?) : PluginReloadResult

object OldPluginNotFound : PluginReloadError(null)
class OldPluginFailedToDelete(exception: PluginRuntimeException? = null) : PluginReloadError(exception)
class OldPluginFallbackFailedToLoad(exception: Exception, val fallbackException: Exception) : PluginReloadError(exception)
class OldPluginReused(exception: Exception) : PluginReloadError(exception)