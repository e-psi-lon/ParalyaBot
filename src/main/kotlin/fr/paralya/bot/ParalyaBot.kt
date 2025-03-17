package fr.paralya.bot

import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.put
import dev.kord.cache.api.query
import dev.kord.common.Color
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.i18n.SupportedLocales
import dev.kordex.core.utils.envOrNull
import fr.paralya.bot.extensions.lg.LG
import fr.paralya.bot.extensions.base.Base
import fr.paralya.bot.extensions.base.GameModes
import fr.paralya.bot.extensions.base.gameMode
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    val bot = ExtensibleBot(TOKEN) {
        val logger = LoggerFactory.getLogger("ParalyaBot")
        devMode = args.contains("--dev")
        logger.info("Starting bot in ${if (devMode) "development" else "production"} mode")
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

        presence { gameMode(GameModes.NONE) }

        errorResponse { message, type ->
            embed {
                title = "Erreur"
                description = "Une erreur est survenue: $message, de type: $type"
                color = Color(0xFF0000)
            }
        }
    }
    for (extension in bot.extensions) {
        bot.loadExtension(extension.value::class.qualifiedName!!)
    }
    bot.start()
}

val environmentVariables = listOf(
    "TOKEN",
    "ADMINS",
    "LG_ROLES_CATEGORY",
    "LG_MAIN_CATEGORY",
    "LG_ALIVE",
    "LG_DEAD"
)

suspend inline fun <reified T : Any>ExtensibleBot.get(noinline block: QueryBuilder<T>.() -> Unit): T? {
    return kordRef.cache.query<T> {
        block()
    }.singleOrNull()
}

suspend inline fun <reified T : Any>ExtensibleBot.set(value: T) {
    kordRef.cache.put(value)
}

private fun checkEnv() {
    val file = File(".env")
    if (!file.exists()) {
        file.createNewFile()
        file.writeText(environmentVariables.joinToString("\n") { "$it=" })
    } else {
        val lines = file.readLines()
        for (variable in environmentVariables) {
            if (lines.firstOrNull { it.startsWith("$variable=") } == null)
                file.appendText("$variable=\n")
        }
    }
}

private val TOKEN = envOrNull("TOKEN") ?: run {
    println("Please provide a token in the .env file in the same folder as the jar.")
    checkEnv()
    exitProcess(1)
}

val ADMINS = envOrNull("ADMINS")?.split(",")?.map { it.toULong() } ?: run {
    println("Please provide an admin list in the .env file in the same folder as the jar.")
    checkEnv()
    exitProcess(1)
}

val LG_ROLES_CATEGORY = envOrNull("LG_ROLES_CATEGORY")?.toULong()?: run {
    println("Please provide the id of the roles category (lg) in the .env file in the same folder as the jar.")
    checkEnv()
    exitProcess(1)
}

val LG_MAIN_CATEGORY = envOrNull("LG_MAIN_CATEGORY")?.toULong()?: run {
    println("Please provide the id of the main category (lg) in the .env file in the same folder as the jar.")
    checkEnv()
    exitProcess(1)
}

val LG_ALIVE = envOrNull("LG_ALIVE")?.toULong()?: run {
    println("Please provide the id of the alive role in the .env file in the same folder as the jar.")
    checkEnv()
    exitProcess(1)
}

val LG_DEAD = envOrNull("LG_DEAD")?.toULong()?: run {
    println("Please provide the id of the dead role in the .env file in the same folder as the jar.")
    checkEnv()
    exitProcess(1)
}
