package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataEntryCache
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.data.description
import dev.kord.cache.redis.RedisEntryCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedisFallbackEntryCacheTest {
    @Test
    suspend fun `put falls back to secondary cache when redis throws IllegalStateException`()  {
        val redis = mockk<RedisEntryCache<SomeType, String>>()
        val fallback = mockk<DataEntryCache<SomeType>>(relaxed = true)
        val description = description(SomeType::id)
        val cache = RedisFallbackEntryCache(redis, fallback, description, mutableSetOf())

        coEvery { redis.put(any<SomeType>()) } throws IllegalStateException("nope")

        cache.put(SomeType())

        coVerify(exactly = 1) { fallback.put(any<SomeType>()) }
    }

    @Test
    suspend fun `put marks type incompatible after a redis failure`() {
        val redis = mockk<RedisEntryCache<SomeType, String>>()
        val fallback = mockk<DataEntryCache<SomeType>>(relaxed = true)
        val description = description(SomeType::id)
        val incompatible = mutableSetOf<KClass<*>>()
        val cache = RedisFallbackEntryCache(redis, fallback, description, incompatible)

        coEvery { redis.put(any<SomeType>()) } throws IllegalStateException("nope")

        cache.put(SomeType())

        assertTrue(description.klass in incompatible)
    }

    @Test
    suspend fun `put skips redis entirely once type is already marked incompatible`() {
        val redis = mockk<RedisEntryCache<SomeType, String>>()
        val fallback = mockk<DataEntryCache<SomeType>>(relaxed = true)
        val description = description(SomeType::id)
        // pre-seeded, no need to trigger the exception path first
        val incompatible = mutableSetOf<KClass<*>>(description.klass)
        val cache = RedisFallbackEntryCache(redis, fallback, description, incompatible)

        cache.put(SomeType())

        coVerify(exactly = 0) { redis.put(any<SomeType>()) }
        coVerify(exactly = 1) { fallback.put(any<SomeType>()) }
    }

    @Test
    fun `query uses redis when type is not marked incompatible`() {
        val redis = mockk<RedisEntryCache<SomeType, String>>()
        val fallback = mockk<DataEntryCache<SomeType>>(relaxed = true)
        val description = description(SomeType::id)
        val redisQuery = mockk<QueryBuilder<SomeType>>()
        every { redis.query() } returns redisQuery
        val cache = RedisFallbackEntryCache(redis, fallback, description, mutableSetOf())

        val result = cache.query()

        verify(exactly = 1) { redis.query() }
        verify(exactly = 0) { fallback.query() }
        assertEquals(redisQuery, result)
    }

    @Test
    fun `query uses fallback when type is marked incompatible`() {
        val redis = mockk<RedisEntryCache<SomeType, String>>()
        val fallback = mockk<DataEntryCache<SomeType>>(relaxed = true)
        val description = description(SomeType::id)
        val cache = RedisFallbackEntryCache(redis, fallback, description, mutableSetOf(description.klass))

        cache.query()

        verify(exactly = 0) { redis.query() }
        verify(exactly = 1) { fallback.query() }
    }
}
