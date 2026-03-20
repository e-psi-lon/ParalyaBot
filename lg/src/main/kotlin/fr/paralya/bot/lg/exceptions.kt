package fr.paralya.bot.lg

import fr.paralya.bot.common.plugins.PluginException

open class LgException(message: String) : PluginException(message)

class LgChannelNotFoundException(message: String) : LgException(message)