package fr.paralya.bot

import dev.kord.common.Color
import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.i18n.SupportedLocales
import dev.kordex.core.utils.envOrNull
import fr.paralya.bot.extensions.lg.LG
import fr.paralya.bot.extensions.base.Base
import java.io.File
import kotlin.system.exitProcess

suspend fun main() {
    val bot = ExtensibleBot(TOKEN) {
        extensions {
            add(::Base)
            add(::LG)
            help {
                this.color { Color(0xAAFFAA) }
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

        presence {
            status = PresenceStatus.Idle
            this.watching("Paralya sans animation en cours")
        }

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

private fun checkEnv() {
    val file = File(".env")
    if (!file.exists()) {
        file.createNewFile()
        file.writeText("TOKEN=\n")
        file.writeText("ADMINS=\n")
        file.writeText("LG_ROLES_CATEGORY=\n")
        file.writeText("LG_MAIN_CATEGORY=\n")
    } else {
        val lines = file.readLines()
        if (lines.firstOrNull { it.startsWith("TOKEN=") } == null)
            file.writeText("TOKEN=\n")
        if (lines.firstOrNull { it.startsWith("ADMINS=") } == null)
            file.writeText("ADMINS=\n")
        if (lines.firstOrNull { it.startsWith("LG_ROLES_CATEGORY=") } == null)
            file.writeText("LG_ROLES_CATEGORY=\n")
        if (lines.firstOrNull { it.startsWith("LG_MAIN_CATEGORY=") } == null)
            file.writeText("LG_MAIN_CATEGORY=\n")
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
