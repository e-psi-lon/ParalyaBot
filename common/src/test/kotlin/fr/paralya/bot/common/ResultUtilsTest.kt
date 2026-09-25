package fr.paralya.bot.common

import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ResultUtilsTest {

    @Test
    fun `runCatchingException returns success when block succeeds`() {
        val result = runCatchingException { 42 }
        assertEquals(Result.success(42), result)
    }

    @Test
    fun `runCatchingException wraps a caught exception as failure`() {
        val exception = IllegalStateException("boom")
        val result = runCatchingException<Int> { throw exception }
        assertEquals(Result.failure(exception), result)
    }

    @Test
    fun `runCatchingException rethrows CancellationException instead of wrapping it`() {
        assertFailsWith<CancellationException> {
            runCatchingException<Int> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `runCatchingTypedException does not catch exceptions outside the typed bound`() {
        assertFailsWith<RuntimeException> {
            runCatchingTypedException<IllegalStateException, _> { throw RuntimeException("not the typed exception") }
        }
    }

    @Test
    fun `getExceptionOrNull returns null for a successful result`() {
        assertNull(Result.success(1).getExceptionOrNull())
    }

    @Test
    fun `getExceptionOrNull returns the exception for a failed result`() {
        val exception = IllegalStateException("boom")
        assertEquals(exception, Result.failure<Int>(exception).getExceptionOrNull())
    }

    @Test
    fun `getExceptionOrNull throws NonExceptionFailedResultException for a non-Exception throwable`() {
        assertFailsWith<NonExceptionFailedResultException> {
            Result.failure<Int>(OutOfMemoryError("simulated")).getExceptionOrNull()
        }
    }
}