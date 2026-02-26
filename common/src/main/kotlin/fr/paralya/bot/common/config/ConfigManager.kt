package fr.paralya.bot.common.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.utils.loadModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.konform.validation.Validation
import io.konform.validation.ValidationResult
import io.konform.validation.onEach
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.serializer
import org.koin.core.module.Module
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.reflect.KClass

/**
 * Configuration manager for the bot.
 * Handles loading and managing the bot's configuration.
 * Allows for dynamic registration of game-specific configurations.
 *
 * @property configFile The file where the configuration is stored.
 * @property botConfig The core bot configuration.
 * @constructor Creates a new [ConfigManager] instance and automatically populates the base configuration.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class ConfigManager internal constructor(private val configFile: Path) : KordExKoinComponent {
	val logger = KotlinLogging.logger("ConfigManager")

	constructor() : this(Path(System.getenv("PARALYA_BOT_CONFIG_FILE") ?: "config.conf"))

	private var state: ConfigState = try {
		loadState()
	} catch (e: ConfigException) {
		logger.error(e) { "Failed to load config file on startup, cannot continue" }
		throw e
	}

	@PublishedApi
	internal val modules = mutableMapOf<String, Module>()


	// Core bot configuration, directly integrated into the ConfigManager
	val botConfig: BotConfig
		get() = state.botConfig
	/**
	 * Loads the configuration file into a [ConfigState] object
	 * If the file does not exist, it creates a default configuration file
	 * It also validates the given configuration
	 */
	private fun loadState(): ConfigState {
		if (!configFile.exists()) createDefaultConfig()
		val raw = ConfigFactory.parseFile(configFile.toFile())
		val botConfig = Hocon.decodeFromConfig<BotConfig>(getSubConfig("bot"))
		validateConfig(botConfig)
		return ConfigState(raw, botConfig)
	}

	fun reload() {
		state = try {
			loadState()
		} catch (e: ConfigException) {
			logger.warn(e) { "Failed to reload config file, keeping previous configuration" }
			state
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
		logger.warn { "Default config created at ${configFile.absolutePathString()}. Please fill in required values." }
	}

	/**
	 * Registers a game-specific configuration.
	 * This method allows for dynamic registration of configurations for different games.
	 * It uses Koin to manage the lifecycle of the configuration object and is completely type-safe.
	 *
	 * @param name The name of the configuration, used as a key in the config file.
	 */
	inline fun <reified T : ValidatedConfig> registerConfig(name: String) {
        val configObject = getConfigObject(T::class, name) ?: return
        modules[name] = loadModule(true) {
			single<T> { configObject } withOptions { named(name) }
		}
		logger.debug { "Config for $name registered successfully" }
	}

	fun unregisterConfig(name: String) {
		modules.remove(name)?.let {
			getKoin().unloadModules(listOf(it))
			logger.debug { "Koin module for $name unloaded successfully" }
		} ?: logger.warn { "No Koin module found for $name" }
	}

	@PublishedApi
    internal fun <T : ValidatedConfig> getConfigObject(clazz: KClass<T>, name: String): T? {
		val actualName = name.lowercase()
        logger.debug { "Registering config for $name at path games.$actualName with datatype ${clazz.simpleName}" }
        val configObject = try {
            Hocon.decodeFromConfig(clazz.serializer(), getSubConfig("games.$actualName"))
        } catch (e: IllegalArgumentException) {
            logger.error(e) {
                "Failed to find the configuration for name $name at path games.$actualName. " +
						"Please ensure it exists in the config file."
            }
            return null
        }
        validateConfig(configObject)
        return configObject
    }

	private fun getSubConfig(path: String): Config {
		if (!state.raw.hasPath(path))
			throw IllegalArgumentException("No configuration found for path: $path")
		return state.raw.getConfig(path)
	}

	private fun validateConfig(config: ValidatedConfig) {
		val result = config.validate()
		if (result.isValid) logger.info { "Configuration for ${config::class.simpleName} is valid." }
		else throw IllegalStateException(
			"Configuration for ${config::class.simpleName} is invalid due to the following errors: " +
					result.errors.joinToString("\n")
		)
	}
}


private class ConfigState(
	val raw: Config,
	val botConfig: BotConfig
)

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
	val token: String = "",
	val admins: List<ULong> = emptyList(),
	val dmLogChannelId: ULong = ULong.MIN_VALUE,
	val paralyaId: ULong = ULong.MIN_VALUE
) : ValidatedConfig {
	@Transient
	private val validator = Validation {
		BotConfig::token {
			defined() hint "Token must NOT be empty. Please provide it, it is a base requirement for the bot to work."
		}
		BotConfig::admins {
			defined() hint "Admins list must have at least one item"
			onEach {
				appearsToBeSnowflake("Admin ID") // Just to be sure that a real ID is provided
			}
		}
		BotConfig::dmLogChannelId {
			appearsToBeSnowflake("DM log channel ID")
		}
		BotConfig::paralyaId {
			appearsToBeSnowflake("Paralya guild ID")
		}
	}

	override fun validate(): ValidationResult<BotConfig> {
		return validator(this)
	}
}
