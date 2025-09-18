package fr.paralya.bot.extensions.base

import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.DiscordMessage
import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.MessageType
import dev.kord.common.entity.optional.Optional
import dev.kord.core.Kord
import dev.kord.core.KordObject
import dev.kord.core.cache.data.UserData
import dev.kord.core.entity.Message
import dev.kord.rest.route.Route
import dev.kord.rest.route.Route.ChannelId
import dev.kord.rest.route.Route.MessageId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Collections
import java.util.WeakHashMap


@RequiresOptIn("This accessor relies on a blocking operation. For coroutines, consider a suspending function if available.", RequiresOptIn.Level.WARNING)
annotation class BlockingAccessor

private val callBackingField = Collections.synchronizedMap<Message, Call?>(WeakHashMap())
private val logger = KotlinLogging.logger("DiscordCall")

private val CustomMessageGet by lazy {
    val routeClass = Route::class.java
    val constructor = routeClass.getDeclaredConstructor(
        HttpMethod::class.java,
        String::class.java,
        DeserializationStrategy::class.java,
        Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType
    )
    constructor.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    constructor.newInstance(
        HttpMethod.Get,
        "/channels/$ChannelId/messages/$MessageId",
        CustomDiscordMessage.serializer(),
        true,
        true
    ) as Route<CustomDiscordMessage>
}


/**
 * This extension function allows getting the [call] property of a [Message] entity.
 * Since Kord doesn't expose the `call` field in the [Message] entity, this function fetches the message
 * from Discord API and returns a [Call] object if the message is a call.
 *
 * @see Call
 * @return The [Call] object if the message is a call, or `null` if it's not.
 */
@KordExperimental
@KordUnsafe
suspend fun Message.fetchCall(): Call? = callBackingField.getOrPut(this) {
    if (type == MessageType.Call) return@getOrPut null
    try {
        val discord = kord.rest.unsafe(CustomMessageGet) {
            keys[ChannelId] = channelId
            keys[MessageId] = id
        }.call
        discord.value?.toData()?.let { Call(it, kord) }
    } catch (e: ReflectiveOperationException) {
        logger.error(e) { "Failed to fetch call property for message $id in channel $channelId due to route creation failure." }
        null

    } catch (e: Exception) {
        logger.error(e) { "Failed to fetch call property for message $id in channel $channelId due to an unknown error." }
        null
    }
}.also { if (it == null) callBackingField.remove(this) }


/**
 * This extension property requires a fetch to Discord API. Therefore, a network operation is performed.
 * Be aware that the first access to this property will require a blocking network operation.
 * If you want to avoid blocking, consider using [fetchCall] directly.
 *
 * @see fetchCall
 */
@BlockingAccessor
@KordUnsafe
@KordExperimental

val Message.call: Call?
    get() = callBackingField.getOrElse(this) {
        runBlocking {
            fetchCall()
        }
    }


data class Call(val data: CallData, override val kord: Kord): KordObject {
    val participants: List<UserData> get() = data.participants
    val endedTimestamp: Optional<String> get() = data.endedTimestamp

}

@Serializable
data class DiscordCall(
    val participants: List<DiscordUser>,
    @SerialName("ended_timestamp") val endedTimestamp: Optional<String> = Optional.Missing()
)

@Serializable
data class CallData(
    val participants: List<UserData>,
    val endedTimestamp: Optional<String> = Optional.Missing()
) {
    companion object {
        fun from(entity: DiscordCall) = with(entity) {
            CallData(
                participants.map { UserData.from(it) },
                endedTimestamp
            )
        }
    }
}

fun DiscordCall.toData() = CallData.from(this)


/**
 * A full copy of the data structure isn't needed. The deserialization protocol
 * already handles missing fields in the data structure by ignoring them.
 * Therefore, only the call we desire is needed.
 *
 * @see DiscordMessage
 * @see fetchCall
 */
@Serializable
private data class CustomDiscordMessage(
    val call: Optional<DiscordCall> = Optional.Missing()
)
