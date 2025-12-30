package fr.paralya.bot.common

import dev.kord.common.entity.DiscordUser
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.RoleBehavior
import dev.kord.core.cache.data.UserData
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.rest.Image
import dev.kordex.core.utils.any
import dev.kordex.core.utils.hasRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

/**
 * Retrieves an image asset from the specified path.
 *
 * @param path The path to the asset file (in the `assets` resource directory).
 * @param game An optional game to include in the asset path.
 * @return The image as an [Image] object.
 * @throws IllegalArgumentException if the resource is not found at the specified path.
 */
suspend fun getAsset(path: String, game: String? = null) =
    Image.raw(getResource("assets/${if (game != null) "$game/" else ""}$path.webp"), Image.Format.WEBP)

suspend fun getResource(path: String): ByteArray {
    val resource = object {}.javaClass.getResourceAsStream("/$path")
        ?: throw IllegalArgumentException("Resource at path /$path not found")
    return withContext(Dispatchers.IO) {
        resource.readAllBytes().also {
            resource.close()
        }
    }
}

/**
 * Converts a [DiscordUser] to a [User] using the provided [Kord] instance.
 */
fun DiscordUser?.asUser(kord: Kord) = this?.let { User(UserData.from(it), kord) }

/** Extension properties to convert various number types to a [Snowflake] */
val Number.snowflake get() = Snowflake(this.toLong())

/** @see [Number.snowflake] */
val ULong.snowflake get() = Snowflake(this)


/**
 * Filter a [Flow] of member objects to only include those who have a specific role.
 *
 * @param roleId The ID of the role to filter by.
 * @return A flow of [Member] objects who have the specified role.
 */
suspend fun Flow<Member>.filterByRole(roleId: Snowflake): Flow<Member> =
    filter { member -> member.roles.any { it.id == roleId } }

fun Flow<Member>.filterByRole(role: RoleBehavior): Flow<Member> = filter { it.hasRole(role) }
