package fr.paralya.bot.common.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueType
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.utils.envOrNull
import dev.kordex.core.utils.loadModule
import io.github.oshai.kotlinlogging.KotlinLogging
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
 * @constructor Creates a new [ConfigManager] instance and automatically populates the base configuration.
 */
class ConfigManager : KordExKoinComponent {
	private lateinit var config: Config
	private val configFile = File("config.conf")
	private val logger = KotlinLogging.logger("ConfigManager")
	private val registeredConfigs = mutableListOf<String>()


	// Core bot configuration, directly integrated into the ConfigManager
	val botConfig = BotConfig()

	init {
		loadConfig()
	}

	fun reloadConfig() {
		try {
			config = ConfigFactory.parseFile(configFile)
			loadConfigSection(botConfig, "bot")
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
				loadConfigSection(config, "games.${name.removeSuffix("Config").lowercase()}")
			}
			registeredConfigs.removeAll(toRemove)
		} catch (e: Exception) {
			logger.error(e) { "Failed to reload config file" }
			exitProcess(1)
		}
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

		loadConfigSection(config, "games.${name.removeSuffix("Config").lowercase()}", name)
	}

	/**
	 * Loads a configuration section into the provided object.
	 * This method uses reflection to set properties on the object based on the loaded configuration.
	 *
	 * @param configObject The object to load the configuration into.
	 * @param path The path in the config file where the configuration is located.
	 */
	fun <T : Any> loadConfigSection(configObject: T, path: String, name: String? = null) {
		val cls = configObject::class
		name?.let { registeredConfigs.add(it) }

		cls.memberProperties.forEach { prop ->
			try {
				val propName = prop.name
				val fullPath = "$path.$propName"
				val propTypeArgs = prop.returnType.arguments.map { it.type!!.classifier as KClass<*> }

				// Check if config has this path
				if (config.hasPath(fullPath)) {
					// To set the property, we need to first get its value
					setProperty(configObject,
						propName,
						convertValue(
							loadConfigValue(config, fullPath).toString(),
							prop.returnType.classifier as KClass<*>,
							propTypeArgs
						)
					)
				} else {
					// Fallback to environment variables
					val envVar = fullPath.replace('.', '_').uppercase()
					val envValue = envOrNull(envVar)

					if (envValue != null) {
						val convertedValue = convertValue(envValue, prop.returnType.classifier as KClass<*>, propTypeArgs)
						setProperty(configObject, propName, convertedValue)
					}
				}
			} catch (e: Exception) {
				logger.error(e) { "Error loading property ${prop.name}" }
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
	private fun setProperty(obj: Any, propName: String, value: Any?) {
		val property = obj::class.java.getDeclaredField(propName)
		property.isAccessible = true
		property.set(obj, value)
	}

	/**
	 * Converts a variable value to the appropriate type.
	 * This method is used to convert string values from variables to their respective types.
	 *
	 * @param value The string value of a variable.
	 * @param type The type to convert the value to.
	 * @param parameters The list of parameters for generic types.
	 * @return The converted value.
	 */
	private fun convertValue(value: String, type: KClass<*>, parameters: List<KClass<*>> = emptyList()): Any? {
		// Handle null values
		if (value.isBlank() || value.equals("null", ignoreCase = true)) {
			// For collection types, return empty collections rather than null
			return when (type) {
				List::class -> emptyList<Any>()
				Map::class -> emptyMap<Any, Any>()
				Set::class -> emptySet<Any>()
				else -> null
			}
		}
		return when (type) {
			String::class -> value
			Int::class -> value.toInt()
			Long::class -> value.toLong()
			ULong::class -> value.toLong().toULong()
			Boolean::class -> value.toBoolean()
			Float::class -> value.toFloat()
			Double::class -> value.toDouble()
			Short::class -> value.toShort()
			Byte::class -> value.toByte()
			Char::class -> if (value.length == 1) value[0] else throw IllegalArgumentException("Cannot convert '$value' to Char")
			List::class -> {
				value.split(",").map {
					if (parameters.isNotEmpty()) {
						// Get nested parameters if any
						val nestedParams = if (parameters[0] == List::class || parameters[0] == Map::class) {
							parameters[0].typeParameters.map { it1 -> it1 as KClass<*> }
						} else emptyList()
						convertValue(it.trim(), parameters[0], nestedParams)
					} else it.trim()
				}
			}
			Map::class -> {
				value.split(",").associate {
					val parts = it.split(":", limit = 2)
					if (parts.size != 2) throw IllegalArgumentException("Invalid map entry format: $it")

					val (key, mapVal) = parts

					if (parameters.size >= 2) {
						// Get nested parameters for key and value if any
						val keyNestedParams = if (parameters[0] == List::class || parameters[0] == Map::class) {
							parameters[0].typeParameters.map { it1 -> it1 as KClass<*> }
						} else emptyList()

						val valueNestedParams = if (parameters[1] == List::class || parameters[1] == Map::class) {
							parameters[1].typeParameters.map { it1 -> it1 as KClass<*> }
						} else emptyList()

						convertValue(key.trim(), parameters[0], keyNestedParams) to
								convertValue(mapVal.trim(), parameters[1], valueNestedParams)
					} else key.trim() to mapVal.trim()
				}
			}
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
	private fun loadConfigValue(config: Config, path: String): Any? {
		val value = config.getValue(path)
		return when (value.valueType()) {
			ConfigValueType.LIST -> config.getList(path).unwrapped()
			ConfigValueType.BOOLEAN -> config.getBoolean(path)
			ConfigValueType.NUMBER -> config.getNumber(path)
			ConfigValueType.STRING -> config.getString(path)
			ConfigValueType.OBJECT -> config.getObject(path).unwrapped()
			ConfigValueType.NULL -> if (config.getIsNull(path)) null else throw IllegalArgumentException("How a value supposed to be null is not null? What the hell did you do?")
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