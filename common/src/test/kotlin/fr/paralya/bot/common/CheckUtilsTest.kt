package fr.paralya.bot.common

import dev.kord.core.event.Event
import dev.kordex.core.annotations.NotTranslated
import dev.kordex.core.checks.types.CheckContext
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(NotTranslated::class)
class CheckUtilsTest {

    private fun context(event: Event): CheckContext<Event> = CheckContext(event, Locale.ENGLISH)

    @Test
    suspend fun `isUser does nothing when check already failed`() {
        val ctx = context(mockk<Event>())
        ctx.fail("pre-existing failure")

        ctx.isUser()

        assertFalse(ctx.passed)
        assertEquals("pre-existing failure", ctx.message)
    }

    @Test
    suspend fun `isUser fails when the event type has no associated user`() {
        val event = mockk<Event>()
        val ctx = context(event)

        ctx.isUser()

        assertFalse(ctx.passed)
    }

    @Test
    suspend fun `isNotEphemeral does nothing when check already failed`() {
        val ctx = context(mockk<Event>())
        ctx.fail("pre-existing failure")

        ctx.isNotEphemeral()

        assertFalse(ctx.passed)
        assertEquals("pre-existing failure", ctx.message)
    }

    @Test
    suspend fun `isNotEphemeral leaves check untouched when there is no associated message`() {
        val event = mockk<Event>()
        val ctx = context(event)

        ctx.isNotEphemeral()

        assertTrue(ctx.passed)
    }
}
