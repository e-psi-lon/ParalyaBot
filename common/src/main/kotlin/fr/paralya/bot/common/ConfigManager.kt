package fr.paralya.bot.common

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueType
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.utils.envOrNull
import dev.kordex.core.utils.loadModule
import org.koin.core.component.inject
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.system.exitProcess

/**
 * Configuration manager for the bot.
 * Handles loading and managing the bot's configuration.
 * Allows for dynamic registration of game-specific configurations.
 *
 * @property configFile The file where the configuration is stored.
 * @property config The loaded configuration object.
 * @property botConfig The core bot configuration.
 * @constructor Creates a new ConfigManager instance.
 */
class ConfigManager: KordExKoinComponent {
	private lateinit var config: Config
	private val configFile = File("config.conf")

	// Core bot configuration, directly integrated into the ConfigManager
	val botConfig = BotConfig()

	init {
		loadConfig()
	}

	/**
	 * Loads the configuration from the config file.
	 * If the config file does not exist, it creates a default one.
	 * It also loads the core bot configuration and any game-specific configurations.
	 */
	private fun loadConfig() {
		if (!configFile.exists()) {
			createDefaultConfig()
		}

		try {
			config = ConfigFactory.parseFile(configFile)
			loadConfigSection(botConfig, "bot")
		} catch (e: Exception) {
			println("Error loading configuration: ${e.message}")
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
            bot {
                token = ""
                admins = []
            }

            # Game-specific configurations will be added here
            games {
            }
        """.trimIndent()
		)

		println("Default config created at ${configFile.absolutePath}. Please fill in required values.")
		exitProcess(1)
	}

	/**
	 * Registers a game-specific configuration.
	 * This method allows for dynamic registration of configurations for different games.
	 * It uses Koin to manage the lifecycle of the configuration object and is completely type-safe.
	 *
	 * @param configBuilder A lambda function that builds the configuration object.
	 * @param name The name of the configuration, used as a key in the config file.
	 */
	inline fun <reified T : Any> registerConfig(crossinline configBuilder: () -> T, name: String) {
		loadModule {
			singleOf(configBuilder) withOptions {
				named(name)
				createdAtStart()
			}
		}
		val config by inject<T>()
		loadConfigSection(config, "games.${name.removeSuffix("Config").lowercase()}")
	}

	/**
	 * Loads a configuration section into the provided object.
	 * This method uses reflection to set properties on the object based on the loaded configuration.
	 *
	 * @param configObject The object to load the configuration into.
	 * @param path The path in the config file where the configuration is located.
	 */
	fun <T : Any> loadConfigSection(configObject: T, path: String) {
		val cls = configObject::class

		cls.memberProperties.forEach { prop ->
			try {
				val propName = prop.name
				val fullPath = "$path.$propName"

				// Check if config has this path
				if (config.hasPath(fullPath)) {
					// To set the property, we need to first get its value
					setProperty(configObject, propName, loadConfigValue(config, fullPath))
				} else {
					// Fallback to environment variables
					val envVar = fullPath.replace('.', '_').uppercase()
					val envValue = envOrNull(envVar)

					if (envValue != null) {
						val convertedValue = convertEnvValue(envValue, prop.returnType.classifier as KClass<*>)
						setProperty(configObject, propName, convertedValue)
					}
				}
			} catch (e: Exception) {
				println("Error loading property ${prop.name}: ${e.message}")
			}
		}
	}

	/**
	 * Sets a property on the given object using reflection.
	 * This method is used to set properties dynamically based on the loaded configuration.
	 *
	 * @param obj The object whose property is to be set.
	 * @param propName The name of the property to set.
	 * @param value The value to set the property to.
	 */
	private fun setProperty(obj: Any, propName: String, value: Any) {
		val property = obj::class.java.getDeclaredField(propName)
		property.isAccessible = true
		property.set(obj, value)
	}

	/**
	 * Converts an environment variable value to the appropriate type.
	 * This method is used to convert string values from environment variables to their respective types.
	 *
	 * @param value The string value from the environment variable.
	 * @param type The type to convert the value to.
	 * @return The converted value.
	 */
	private fun convertEnvValue(value: String, type: KClass<*>): Any {
		return when (type) {
			String::class -> value
			Int::class -> value.toInt()
			Long::class -> value.toLong()
			ULong::class -> value.toULong()
			Boolean::class -> value.toBoolean()
			List::class -> value.split(",")
			else -> throw IllegalArgumentException("Unsupported type: $type")
		}
	}

	/**
	 * Loads a configuration value from the config file.
	 * This method handles different types of values (list, boolean, number, string, object).
	 *
	 * @param config The config object.
	 * @param path The path in the config file where the value is located.
	 * @return The loaded value.
	 */
	private fun loadConfigValue(config: Config, path: String): Any {
		val value = config.getValue(path)
		return when (value.valueType()) {
			ConfigValueType.LIST -> config.getList(path)
			ConfigValueType.BOOLEAN -> config.getBoolean(path)
			ConfigValueType.NUMBER -> config.getNumber(path)
			ConfigValueType.STRING -> config.getString(path)
			ConfigValueType.OBJECT -> config.getObject(path)
			ConfigValueType.NULL -> config.getObject(path)
			null -> throw IllegalArgumentException("Unsupported type for config value")
		}
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
data class BotConfig(
	var token: String = "",
	var admins: List<ULong> = emptyList(),
	var dmLogChannelId: ULong = ULong.MIN_VALUE,
	var paralyaId: ULong = ULong.MIN_VALUE
)