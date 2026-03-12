package fr.paralya.bot.common

import dev.kordex.core.checks.failed
import dev.kordex.core.checks.passed
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.checks.userFor
import io.github.oshai.kotlinlogging.KotlinLogging

public suspend fun CheckContext<*>.isUser() {
    if (!passed) {
        return
    }

    val logger = KotlinLogging.logger("fr.paralya.bot.common.isUser")
    val user = userFor(event)?.asUserOrNull()

    if (user == null) {
        logger.failed("Event did not concern a user.")

        fail()
    } else if (!user.isBot) {
        logger.passed()

        pass()
    }
}
