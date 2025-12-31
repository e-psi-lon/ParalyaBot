package fr.paralya.bot.lg

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.threads.ThreadChannelBehavior
import dev.kord.core.behavior.channel.threads.edit
import dev.kord.core.entity.Member
import kotlinx.coroutines.flow.Flow

/**
 * Sets the lock status of all threads in the flow to [lock].
 */
suspend fun Flow<ThreadChannelBehavior>.changeLockAll(lock: Boolean) {
	collect { thread ->
		thread.edit { locked = lock }
	}
}

/**
 * Applies a role change to a member (adding one role and removing another).
 */
suspend fun Member.swapRoles(
	addRoleId: Snowflake,
	removeRoleId: Snowflake,
	reason: String? = null
) {
	addRole(addRoleId, reason)
	removeRole(removeRoleId, reason)
}

fun String.truncate(maxLength: Int): String =
	if (length <= maxLength) this else take(maxLength - 3) + "..."