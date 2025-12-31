package fr.paralya.bot.common.plugins

import dev.kordex.core.plugins.KordExPlugin
import dev.kordex.i18n.Key
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.common.config.ValidatedConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import kotlin.getValue

abstract class Plugin: KordExPlugin() {
    abstract val name: String
    abstract val key: Key
    @PublishedApi
    internal var configDefined = false
    open val isGame = true

    override suspend fun setup() {
        prepareRegistration()
        onSetup()
    }

    /**
     * Register your plugin's config type by calling `define<T>()`.
     */
    protected abstract fun defineConfig()


    open suspend fun onSetup() {}
    open suspend fun onDelete() {}
    open suspend fun onStop() {}

    override fun delete() = runBlocking {
        kord.launch {
            removeAllRegistration()
            onDelete()
        }.join()
        super.delete()
    }

    override fun stop() = runBlocking {
        kord.launch {
            removeAllRegistration()
            onStop()
        }.join()
        super.stop()
    }

    private fun removeAllRegistration() {
        // val configManager by inject<ConfigManager>()
        // configManager.unregisterConfig(name) // Somehow find a way to unregister it
        val gameRegistry by inject<GameRegistry>()
        gameRegistry.unloadGameMode(name)
    }

    protected inline fun <reified T : ValidatedConfig>define() = register<T>(key).also { configDefined = true }

    private fun executeDefine() {
        defineConfig()
        if (!configDefined) throw PluginConfigurationException(
            "Config for plugin $name not defined. Please call define<T>() in your plugin's defineConfig() method."
        )
    }

    private fun prepareRegistration() {
        try {
            getKoin()
        } catch (_: IllegalStateException) {
            bot.logger.info { "Koin not started, registering $name config and game hook after Koin setup" }
            settings {
                hooks { afterKoinSetup { executeDefine() } }
            }
            return
        }
        bot.logger.info { "Koin already started, registering $name config and game" }
        executeDefine()
    }

    @PublishedApi
    internal inline fun <reified T : ValidatedConfig>register(key: Key) {
        registerConfig<T>()
        if (isGame) registerGame(key)
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