package fr.paralya.bot.common.plugins

import dev.kordex.core.koin.KordExContext.unloadKoinModules
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.plugins.KordExPlugin
import dev.kordex.core.plugins.PluginManager
import dev.kordex.i18n.Key
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.common.config.ValidatedConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.pf4j.PluginWrapper
import kotlin.getValue
import kotlin.lazy

abstract class Plugin: KordExPlugin() {
    abstract val name: String
    abstract val key: Key
    @PublishedApi
    internal var configDefined = false
    open val isGame = true
    @PublishedApi
    internal val components = mutableListOf<Module>()

    val plugin: PluginWrapper? by lazy {
        val pluginManager by inject<PluginManager>()
        pluginManager.whichPlugin(this::class.java)
    }

    val pluginId: String by lazy {
        plugin?.pluginId ?: error("Plugin ${this::class.simpleName} identifier couldn't be found.")
    }

    override suspend fun setup() {
        prepareRegistration()
        onSetup()
        try {
            loadKoinModules(components)
        } catch (_: IllegalStateException) {
            bot.logger.info { "Koin not started, loading $name components after Koin setup" }
            settings {
                hooks { afterKoinSetup { loadKoinModules(components) } }
            }
        }
    }

    protected inline fun <reified T : KordExKoinComponent> registerComponent(noinline constructor: () -> T) {
        val module = module {
            singleOf<T>(constructor) {
                createdAtStart()
            }
        }
        components.add(module)
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
        val configManager by inject<ConfigManager>()
        configManager.unregisterConfig(name)
        val gameRegistry by inject<GameRegistry>()
        gameRegistry.unloadGameMode(name)
        unloadKoinModules(components)
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