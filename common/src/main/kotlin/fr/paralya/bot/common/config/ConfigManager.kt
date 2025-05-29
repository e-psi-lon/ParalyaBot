package fr.paralya.bot.common.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.utils.loadModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.serializer
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import java.io.File
import kotlin.system.exitProcess

/**
 * Configuration manager for the bot.
 * Handles loading and managing the bot's configuration.
 * Allows for dynamic registration of game-specific configurations.
 *
 * @property configFile The file where the configuration is stored.
 * @property config The loaded configuration object.
 * @property botConfig The core bot configuration.
 * @constructor Creates a new [ConfigManager] instance and automatically populates the base configuration.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class ConfigManager : KordExKoinComponent {
	private lateinit var config: Config
	private val configFile = File(System.getenv()["PARALYA_BOT_CONFIG_FILE"] ?: "config.conf")
	val logger = KotlinLogging.logger("ConfigManager")
	private val registeredConfigs = mutableListOf<String>()


	// Core bot configuration, directly integrated into the ConfigManager
	var botConfig: BotConfig

	init {
		loadConfig()
		botConfig = Hocon.decodeFromConfig(getSubConfig("bot"))
	}

	fun reloadConfig() {
		try {
			loadFile()
			// Then reload all registered configs
			val toRemove = mutableListOf<String>()
			registeredConfigs.forEach { name ->
				val config = try {
				 	getKoin().get<Any>(named(name))
				}
				catch (e: Exception) {
					logger.warn(e) { "Failed to get config for $name. Its related plugin might has been removed" }
					return@forEach
				}
				val configClass = config::class
				val configPath = "games.${name.removeSuffix("Config").lowercase()}"
				if (!this.config.hasPath(configPath)) {
					logger.warn { "No configuration found for $name at path $configPath. Removing it from registered configs." }
					toRemove.add(name)
					return@forEach
				}
				loadModule(true) {
					single {
						Hocon.decodeFromConfig(configClass.serializer(), getSubConfig(configPath))
					} withOptions { named(name) }
				}
			}
			registeredConfigs.removeAll(toRemove)
		} catch (e: Exception) {
			logger.error(e) { "Failed to reload the configuration" }
			exitProcess(1)
		}
	}

	/**
	 * Loads the configuration from the config file.
	 * If the config file does not exist, it creates a default one.
	 * It also loads the core bot configuration and any game-specific configurations.
	 */
	private fun loadConfig() {
		loadFile()
		botConfig = Hocon.decodeFromConfig(getSubConfig("bot"))
	}

	private fun loadFile() {
		if (!configFile.exists())
			createDefaultConfig()

		try {
			config = ConfigFactory.parseFile(configFile)
		} catch (e: Exception) {
			logger.error(e) { "Failed to load config file" }
			exitProcess(1)
		}
	}

	/**
	 * Creates a default configuration file with placeholders for required values.
	 * This method is called if the config file does not exist.
	 */
	private fun createDefaultConfig() {
		configFile.writeText(
			"""
            |bot {
            |    token = ""
            |    admins = []
			|    dmLogChannelId = 0
			|    paralyaId = 0
            |}
			|
            |# Game-specific configurations will be added here
            |games {
            |}
        """.trimMargin()
		)
		logger.warn { "Default config created at ${configFile.absolutePath}. Please fill in required values." }
		exitProcess(1)
	}

	/**
	 * Registers a game-specific configuration.
	 * This method allows for dynamic registration of configurations for different games.
	 * It uses Koin to manage the lifecycle of the configuration object and is completely type-safe.
	 *
	 * @param name The name of the configuration, used as a key in the config file.
	 */
	inline fun <reified T : Any> registerConfig(name: String) {
		logger.debug { "Registering config for $name at path games.${name.removeSuffix("Config")} with datatype ${T::class.simpleName}" }
		val configObject = try {
			Hocon.decodeFromConfig<T>(getSubConfig("games.${name.removeSuffix("Config").lowercase()}"))
		} catch (e: IllegalArgumentException) {
			logger.error(e) { "Failed to find the configuration for name $name at path games.${name.removeSuffix("Config").lowercase()}. Please ensure it exists in the config file." }
			exitProcess(1)
		}
		loadModule(true) {
			single<T> { configObject } withOptions { named(name) }
		}
		logger.debug { "Config for $name registered successfully" }
	}

	fun getSubConfig(path: String): Config {
		if (!config.hasPath(path))
			throw IllegalArgumentException("No configuration found for path: $path")
		return config.getConfig(path)
	}
}

/**
 * Data class representing the core bot configuration.
 *
 * @property token The bot token used for authentication.
 * @property admins A list of admin user IDs.
 * @property dmLogChannelId A channel ID to copy direct messages to.
 * @property paralyaId The ID of the Paralya guild. (can be changed for testing purposes)
 */
@Serializable
data class BotConfig(
	var token: String = "",
	var admins: List<ULong> = emptyList(),
	var dmLogChannelId: ULong = ULong.MIN_VALUE,
	var paralyaId: ULong = ULong.MIN_VALUE
)