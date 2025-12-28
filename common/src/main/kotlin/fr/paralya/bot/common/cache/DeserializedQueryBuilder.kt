package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.Query
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.query
import dev.kord.core.cache.idEq
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@PublishedApi
internal class DeserializedQueryBuilder<T : Any>(
    private val typeName: String,
    @PublishedApi
    internal val cache: DataCache,
    private val itemIdProperty: KProperty1<T, Any>? = null
) : QueryBuilder<T> {
    private val predicates = mutableListOf<(T) -> Boolean>()

    override fun <R> KProperty1<T, R>.predicate(predicate: (R) -> Boolean) {
        predicates.add { predicate(this.get(it)) }
    }

    override fun build(): Query<T> {
        throw UnsupportedOperationException("DeserializedQueryBuilder requires to know type at runtime. Use buildDeserialized<R>() instead.")
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    inline fun <reified R : Any>buildDeserialized(): Query<R> {
        return buildDeserialized(R::class as KClass<T>) as Query<R>
    }

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    @PublishedApi
    internal fun buildDeserialized(clazz: KClass<T>): Query<T> {
        val namespace = typeName.substringBefore(":")
        val key = typeName.substringAfter(":")

        val cachedDataFlow = cache.query<CachedData> {
            idEq(CachedData::namespace, namespace)
            idEq(CachedData::key, key)
        }.asFlow()
        val items = cachedDataFlow
            .map { cachedData ->
                val item = cbor.decodeFromByteArray(clazz.serializer(), cachedData.data)
                item to cachedData
            }
            .filter { (item, _) -> predicates.all { it(item) } }
        return DeserializedQuery(cache, namespace, items, clazz, itemIdProperty)
    }

}