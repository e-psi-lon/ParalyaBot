package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.Query
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.put
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

@JvmName("enumEq")
fun <T : Any, E : Enum<E>> QueryBuilder<T>.idEq(property: KProperty1<T, E?>, value: E?) = property.eq(value)

@OptIn(ExperimentalSerializationApi::class)
private val cbor = Cbor {
    ignoreUnknownKeys = true
}

inline fun <reified T : Any> DataCache.querySerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: "unknown",
    noinline block: QueryBuilder<T>.() -> Unit = {}
): Query<T> {
    return querySerialized(T::class, namespace, serializer<T>(), itemIdProperty, typeKey, block)
}

@OptIn(ExperimentalSerializationApi::class)
fun <T : Any> DataCache.querySerialized(
    clazz: KClass<T>,
    namespace: String,
    serializer: KSerializer<T>,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: "unknown",
    block: QueryBuilder<T>.() -> Unit = {}
): Query<T> {
    val builder = DeserializedQueryBuilder("$namespace:$typeKey", clazz, this, itemIdProperty, serializer, cbor)
    builder.block()
    return builder.build()
}

suspend inline fun <reified T : Any> DataCache.putSerialized(
    namespace: String,
    item: T,
    itemId: KProperty1<T, Any>? = null
) {
    putSerialized(namespace, item, T::class, itemId, serializer<T>())
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun <T : Any>DataCache.putSerialized(
    namespace: String,
    item: T,
    clazz: KClass<T>,
    itemId: KProperty1<T, Any>? = null,
    serializer: KSerializer<T>
) {
    val data = cbor.encodeToByteArray(serializer, item)
    val cachedData = CachedData(
        namespace = namespace,
        key = clazz.simpleName ?: "unknown",
        data = data,
        itemId = itemId?.get(item)?.toString()
    )
    put(cachedData)
}

suspend inline fun <reified T : Any> DataCache.putSerializedAll(namespace: String, items: Flow<T>) =
    putSerializedAll(namespace, items, T::class, serializer<T>())

suspend fun <T : Any>DataCache.putSerializedAll(
    namespace: String,
    items: Flow<T>,
    clazz: KClass<T>,
    serializer: KSerializer<T>
) = items.collect { putSerialized(namespace, it, clazz, serializer = serializer) }

suspend inline fun <reified T : Any> DataCache.removeSerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: "unknown",
    noinline block: QueryBuilder<T>.() -> Unit = {}
) = removeSerialized(T::class, namespace, serializer<T>(), itemIdProperty, typeKey, block)

suspend fun <T : Any>DataCache.removeSerialized(
    clazz: KClass<T>,
    namespace: String,
    serializer: KSerializer<T>,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: "unknown",
    block: QueryBuilder<T>.() -> Unit = {}
) = querySerialized(clazz, namespace, serializer, itemIdProperty, typeKey, block).remove()

suspend inline fun <reified T : Any> DataCache.updateSerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: "unknown",
    noinline block: QueryBuilder<T>.() -> Unit = {},
    noinline transform: suspend (T) -> T
) = updateSerialized(T::class, namespace, serializer<T>(), itemIdProperty, typeKey, block, transform)

suspend fun <T : Any> DataCache.updateSerialized(
    clazz: KClass<T>,
    namespace: String,
    serializer: KSerializer<T>,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: "unknown",
    block: QueryBuilder<T>.() -> Unit = {},
    transform: suspend (T) -> T
) = querySerialized(clazz, namespace, serializer, itemIdProperty, typeKey, block).update { transform(it) }

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
