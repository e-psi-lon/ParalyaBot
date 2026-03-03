package fr.paralya.bot.common.plugins

import fr.paralya.bot.common.ParalyaBotException

open class PluginException(message: String) : ParalyaBotException(message)

open class PluginValidationException(message: String, val pluginId: String) : PluginException(message)
class PluginInvalidVersionException(message: String, pluginId: String, val version: String) : PluginValidationException(message, pluginId)

class PluginConfigurationException(message: String) : PluginException(message)
