package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.Query
import dev.kord.cache.api.query
import dev.kord.core.cache.idEq
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class DeserializedQuery<T : Any>(
    private val cache: DataCache,
    private val namespace: String,
    private var items: Flow<Pair<T, CachedData>>,
    private val clazz: KClass<T>,
    private val itemIdProperty: KProperty1<T, Any>? = null
) : Query<T> {
    override fun asFlow(): Flow<T> = items.map { it.first }

    override suspend fun remove() {
        items.collect { (_, cachedData) ->
            cache.query {
                idEq(CachedData::id, cachedData.id)
            }.remove()
        }
    }

    override suspend fun update(mapper: suspend (T) -> T) {
        items.collect { (item, cachedData) ->
            val newItem = mapper(item)
            cache.putSerialized(namespace, newItem, clazz, itemIdProperty)
            cache.query {
                idEq(CachedData::id, cachedData.id)
            }.remove()
        }
    }
}