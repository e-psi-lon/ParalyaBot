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


class ConfigManager: KordExKoinComponent {
	private lateinit var config: Config
	private val configFile = File("config.conf")

	// Core bot configuration, directly integrated into the ConfigManager
	val botConfig = BotConfig()

	init {
		loadConfig()
	}

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

	// Helper method to load config values into a data class
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

	private fun setProperty(obj: Any, propName: String, value: Any) {
		val property = obj::class.java.getDeclaredField(propName)
		property.isAccessible = true
		property.set(obj, value)
	}

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
 * @property paralyaId The ID of the Paralya guild.
 */
data class BotConfig(
	var token: String = "",
	var admins: List<ULong> = emptyList(),
	var dmLogChannelId: ULong = ULong.MIN_VALUE,
	var paralyaId: ULong = ULong.MIN_VALUE
)