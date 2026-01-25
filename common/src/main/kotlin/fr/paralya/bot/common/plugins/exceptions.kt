package fr.paralya.bot.common.plugins

import fr.paralya.bot.common.ParalyaBotException

open class PluginException(message: String) : ParalyaBotException(message)

class PluginConfigurationException(message: String) : PluginException(message)
