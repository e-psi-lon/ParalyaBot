package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.Query
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.query
import dev.kord.core.cache.idEq
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@OptIn(ExperimentalSerializationApi::class)
internal class DeserializedQueryBuilder<T : Any>(
    private val typeName: String,
    private val clazz: KClass<T>,
    private val cache: DataCache,
    private val itemIdProperty: KProperty1<T, Any>? = null,
    private val serializer: KSerializer<T>,
    private val cbor: Cbor
) : QueryBuilder<T> {
    private val predicates = mutableListOf<(T) -> Boolean>()

    override fun <R> KProperty1<T, R>.predicate(predicate: (R) -> Boolean) {
        predicates.add { predicate(this.get(it)) }
    }

    override fun build(): Query<T> {
        val namespace = typeName.substringBefore(":")
        val key = typeName.substringAfter(":")

        val cachedDataFlow = cache.query<CachedData> {
            idEq(CachedData::namespace, namespace)
            idEq(CachedData::key, key)
        }.asFlow()
        val items = cachedDataFlow
            .map { cachedData ->
                val item = cbor.decodeFromByteArray(serializer, cachedData.data)
                item to cachedData
            }
            .filter { (item, _) -> predicates.all { it(item) } }
        return DeserializedQuery(cache, namespace, items, clazz, itemIdProperty, serializer)
    }
}
