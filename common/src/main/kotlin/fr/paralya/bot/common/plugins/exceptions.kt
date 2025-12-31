package fr.paralya.bot.common.plugins

open class PluginException(message: String) : Exception(message)

class PluginConfigurationException(message: String) : PluginException(message)
