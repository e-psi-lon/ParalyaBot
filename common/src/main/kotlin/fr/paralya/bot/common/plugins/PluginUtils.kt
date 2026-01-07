package fr.paralya.bot.common.plugins

import dev.kordex.core.koin.KordExContext

inline fun <reified T : Plugin> getPluginInstance(): T = KordExContext.get().get<T>()