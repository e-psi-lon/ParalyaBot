package fr.paralya.bot.common.cache

import dev.kord.cache.api.data.DataDescription
import dev.kord.cache.api.data.description
import dev.kord.cache.api.delegate.DelegatingDataCache
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BotCacheTest {
    @Test
    suspend fun `unregister removes a registered description`()  {
        val cache = DelegatingDataCache { }
        val description: DataDescription<SomeType, String> = description(SomeType::id)
        cache.register(description)
        assertNotNull(cache.getEntry<SomeType>(description.type))

        (cache as DelegatingDataCache).unregister(description)
        assertNull(cache.getEntry<SomeType>(description.type))
    }

}