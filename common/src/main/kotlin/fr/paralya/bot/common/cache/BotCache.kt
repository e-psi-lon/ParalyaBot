package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.Query
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.put
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@JvmName("enumEq")
fun <T : Any, E : Enum<E>> QueryBuilder<T>.idEq(property: KProperty1<T, E?>, value: E?) = property.eq(value)

@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal val cbor = Cbor {
    ignoreUnknownKeys = true
}

inline fun <reified T : Any> DataCache.querySerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: "unknown",
    noinline block: QueryBuilder<T>.() -> Unit = {}
): Query<T> {
    return querySerialized(T::class, namespace, itemIdProperty, typeKey, block)
}

fun <T : Any> DataCache.querySerialized(
    clazz: KClass<T>,
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: "unknown",
    block: QueryBuilder<T>.() -> Unit = {}
): Query<T> {
    val builder = DeserializedQueryBuilder("$namespace:$typeKey", this, itemIdProperty)
    builder.block()
    return builder.buildDeserialized(clazz)
}

suspend inline fun <reified T : Any> DataCache.putSerialized(namespace: String, item: T, itemId: KProperty1<T, Any>? = null) {
    putSerialized(namespace, item, T::class, itemId)
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
suspend fun <T : Any>DataCache.putSerialized(namespace: String, item: T, clazz: KClass<T>, itemId: KProperty1<T, Any>? = null) {
    val data = cbor.encodeToByteArray(clazz.serializer(), item)
    val cachedData = CachedData(
        namespace = namespace,
        key = clazz.simpleName ?: "unknown",
        data = data,
        itemId = itemId?.get(item)?.toString()
    )
    put(cachedData)
}

suspend inline fun <reified T : Any> DataCache.putSerializedAll(namespace: String, items: Flow<T>) = putSerializedAll(namespace, items, T::class)

suspend fun <T : Any>DataCache.putSerializedAll(namespace: String, items: Flow<T>, clazz: KClass<T>) =
    items.collect { putSerialized(namespace, it, clazz) }

suspend inline fun <reified T : Any> DataCache.removeSerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: "unknown",
    noinline block: QueryBuilder<T>.() -> Unit = {}
) = removeSerialized(T::class, namespace, itemIdProperty, typeKey, block)

suspend fun <T : Any>DataCache.removeSerialized(
    clazz: KClass<T>,
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: "unknown",
    block: QueryBuilder<T>.() -> Unit = {}
) = querySerialized(clazz, namespace, itemIdProperty, typeKey, block).remove()

suspend inline fun <reified T : Any> DataCache.updateSerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: "unknown",
    noinline block: QueryBuilder<T>.() -> Unit = {},
    noinline transform: suspend (T) -> T
) = updateSerialized(T::class, namespace, itemIdProperty, typeKey, block, transform)

suspend fun <T : Any> DataCache.updateSerialized(
    clazz: KClass<T>,
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: "unknown",
    block: QueryBuilder<T>.() -> Unit = {},
    transform: suspend (T) -> T
) = querySerialized(clazz, namespace, itemIdProperty, typeKey, block).update { transform(it) }

private val defaultCacheMutex = Mutex()

/**
 * Executes a cache operation atomically using a mutex.
 * @param mutex Optional mutex to use. If not provided, uses a default shared mutex.
 * @param operation The cache operation to execute atomically.
 */
suspend fun <T> DataCache.atomic(
    mutex: Mutex = defaultCacheMutex,
    operation: suspend DataCache.() -> T
): T = mutex.withLock { operation() }