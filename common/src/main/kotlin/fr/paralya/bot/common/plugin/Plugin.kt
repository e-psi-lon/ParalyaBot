package fr.paralya.bot.common.plugin

import dev.kordex.core.plugins.KordExPlugin
import dev.kordex.i18n.Key
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.common.config.ValidatedConfig
import org.koin.core.component.inject
import kotlin.getValue

abstract class Plugin: KordExPlugin() {
    abstract val name: String
    abstract val key: Key
    @PublishedApi
    internal var configDefined = false
    override suspend fun setup() {
        prepareRegistration()
        onSetup()
    }

    /**
     * Register your plugin's config type by calling `define<T>()`.
     */
    abstract fun defineConfig()
    abstract suspend fun onSetup()

    inline fun <reified T : ValidatedConfig>define() = register<T>(key).also { configDefined = true }

    private fun executeDefine() {
        defineConfig()
        if (!configDefined) throw PluginConfigurationException(
            "Config for plugin $name not defined. Please call define<T>() in your plugin's defineConfig() method."
        )
    }

    fun prepareRegistration() {
        try {
            getKoin()
            bot.logger.info { "Koin already started, registering $name config and game" }
            executeDefine()
        } catch (_: IllegalStateException) {
            bot.logger.info { "Koin not started, registering $name config and game hook after Koin setup" }
            settings {
                hooks { afterKoinSetup { executeDefine() } }
            }
        }
    }

    @PublishedApi
    internal inline fun <reified T : ValidatedConfig>register(key: Key) {
        registerConfig<T>()
        registerGame(key)
    }

    @PublishedApi
    internal inline fun <reified T : ValidatedConfig>registerConfig() {
        val configManager by inject<ConfigManager>()
        configManager.registerConfig<T>(name)
    }

    @PublishedApi
    internal fun registerGame(key: Key) {
        val gameRegistry by inject<GameRegistry>()
        gameRegistry.registerGameMode(key, name)

    }

}