package fr.paralya.bot.common

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageUtilsTest {

    private fun mockMessage(content: String, attachments: Set<Attachment>): Message {
        val message = mockk<Message>()
        every { message.content } returns content
        every { message.attachments } returns attachments
        return message
    }

    private fun mockAttachment(filename: String, size: Int, isSpoiler: Boolean): Attachment {
        val attachment = mockk<Attachment>()
        every { attachment.filename } returns filename
        every { attachment.size } returns size
        every { attachment.isSpoiler } returns isSpoiler
        return attachment
    }

    @Test
    fun `messages with different content are not similar`() {
        val msg1 = mockMessage("hello", emptySet())
        val msg2 = mockMessage("goodbye", emptySet())

        assertFalse(areMessagesSimilar(msg1, msg2))
    }

    @Test
    fun `messages with same content and no attachments are similar`() {
        val msg1 = mockMessage("hello", emptySet())
        val msg2 = mockMessage("hello", emptySet())

        assertTrue(areMessagesSimilar(msg1, msg2))
    }

    @Test
    fun `messages with same content and matching attachments are similar regardless of order`() {
        val a1 = mockAttachment("b.png", 100, false)
        val a2 = mockAttachment("a.png", 200, true)
        val msg1 = mockMessage("hello", setOf(a1, a2))
        val msg2 = mockMessage("hello", setOf(a2, a1)) // reversed order

        assertTrue(areMessagesSimilar(msg1, msg2))
    }

    @Test
    fun `messages with same content but different attachments are not similar`() {
        val a1 = mockAttachment("file.png", 100, false)
        val a2 = mockAttachment("file.png", 999, false) // different size
        val msg1 = mockMessage("hello", setOf(a1))
        val msg2 = mockMessage("hello", setOf(a2))

        assertFalse(areMessagesSimilar(msg1, msg2))
    }

    @Test
    fun `format returns the mention for a custom emoji`() {
        val emoji = ReactionEmoji.Custom(Snowflake(123uL), "pepe", isAnimated = false)

        assertEquals("<:pepe:123>", emoji.format())
    }

    @Test
    fun `format returns the animated mention for an animated custom emoji`() {
        val emoji = ReactionEmoji.Custom(Snowflake(123uL), "pepe", isAnimated = true)

        assertEquals("<a:pepe:123>", emoji.format())
    }

    @Test
    fun `format returns the raw unicode for a unicode emoji`() {
        val emoji = ReactionEmoji.Unicode("😀")

        assertEquals("😀", emoji.format())
    }
}