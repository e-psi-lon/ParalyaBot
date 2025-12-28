package fr.paralya.bot.common.plugin

open class PluginException(message: String) : Exception(message)

class PluginConfigurationException(message: String) : PluginException(message)
