package fr.paralya.bot

import dev.kord.common.Color
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.i18n.SupportedLocales
import dev.kordex.core.utils.getKoin
import dev.kordex.core.utils.loadModule
import fr.paralya.bot.common.ConfigManager
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.gameMode
import fr.paralya.bot.extensions.base.Base
import fr.paralya.bot.lg.LG
import fr.paralya.bot.lg.data.LgConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named

/**
 * Main entry point for the Paralya's Discord bot.
 *
 * This function gets the bot instance and starts it.
 *
 * @param args Command line arguments for the bot.
 */
suspend fun main(args: Array<String>) {
	val bot = buildBot(args)
	bot.start()
}


/**
 * Builds and configures the bot instance.
 *
 * @param args Command line arguments for the bot.
 * @return An instance of [ExtensibleBot], ready to be started.
 */
suspend fun buildBot(args: Array<String>): ExtensibleBot {
	val firstConfigManager = ConfigManager()
	val token = firstConfigManager.botConfig.token
	val bot = ExtensibleBot(token) {
		val logger = KotlinLogging.logger("ParalyaBot")
		devMode = if (!devMode) args.contains("--dev") else true
		logger.info { "Starting bot in ${if (devMode) "development" else "production"} mode" }
		extensions {
			add(::Base)
			add(::LG)
			help {
				enableBundledExtension = false
			}
		}

		@OptIn(PrivilegedIntent::class)
		intents {
			+Intent.GuildMembers
			+Intent.MessageContent
			+Intent.DirectMessages
		}

		i18n {
			defaultLocale = SupportedLocales.FRENCH
			applicationCommandLocale(SupportedLocales.FRENCH)
		}

		members { all() }

		presence { gameMode(GameRegistry.NONE) }

		errorResponse { message, type ->
			embed {
				title = "Erreur"
				description = "Une erreur est survenue: $message, de type: $type"
				color = Color(0xFF0000)
			}
		}

		hooks {
			beforeKoinSetup {
				loadModule {
					singleOf(::ConfigManager) withOptions {
						named("configManager")
						createdAtStart()
					}
					singleOf(::GameRegistry) withOptions {
						named("registry")
						createdAtStart()
					}
				}

				val configManager = getKoin().get<ConfigManager>(named("configManager"))
				configManager.registerConfig(::LgConfig, "lgConfig")
			}
		}
	}
	return bot
}
