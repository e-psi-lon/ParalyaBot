package fr.paralya.bot.common.cache

import fr.paralya.bot.common.ParalyaBotException

open class CacheException(message: String, val element: String) : ParalyaBotException(message)
