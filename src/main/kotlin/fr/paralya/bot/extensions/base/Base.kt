package fr.paralya.bot.extensions.base

import dev.kord.core.event.gateway.ReadyEvent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import org.slf4j.LoggerFactory

class Base: Extension() {
    override val name = "Base"
    private val logger = LoggerFactory.getLogger("Base")
    override suspend fun setup() {
        event<ReadyEvent> {
            action {
                logger.info("Bot connected to Discord as ${event.self.username}")
                logger.debug("Fetching channels from categories loup-garou and roles from loup-garou")
            }
        }
    }
}