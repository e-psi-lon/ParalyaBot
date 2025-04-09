package fr.paralya.bot.common

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.TopGuildChannelBehavior
import dev.kord.core.behavior.channel.editMemberPermission
import dev.kord.core.behavior.channel.editRolePermission


suspend fun TopGuildChannelBehavior.addRolePermission(
	id: Snowflake,
	permission: Permission,
	reason : String? = null,
) {
	editRolePermission(id) {
		allowed = allowed.plus(permission)
		denied = denied.minus(permission)
		this.reason = reason
	}
}

suspend fun TopGuildChannelBehavior.addMemberPermission(
	id: Snowflake,
	permission: Permission,
	reason : String? = null,
) {
	editMemberPermission(id) {
		allowed = allowed.plus(permission)
		denied = denied.minus(permission)
		this.reason = reason
	}
}

suspend fun TopGuildChannelBehavior.addRolePermissions(
	id: Snowflake,
	vararg permissions: Permission,
	reason : String? = null
) {
	editRolePermission(id) {
		allowed = allowed.plus(Permissions(permissions.toSet()))
		denied = denied.minus(Permissions(permissions.toSet()))
		this.reason = reason
	}
}

suspend fun TopGuildChannelBehavior.addMemberPermissions(
	id: Snowflake,
	vararg permissions: Permission,
	reason : String? = null
) {
	editMemberPermission(id) {
		allowed = allowed.plus(Permissions(permissions.toSet()))
		denied = denied.minus(Permissions(permissions.toSet()))
		this.reason = reason
	}
}

suspend fun TopGuildChannelBehavior.removeRolePermission(
	id: Snowflake,
	permission: Permission,
	reason : String? = null,
) {
	editRolePermission(id) {
		allowed = allowed.minus(permission)
		denied = denied.plus(permission)
		this.reason = reason
	}
}

suspend fun TopGuildChannelBehavior.removeMemberPermission(
	id: Snowflake,
	permission: Permission,
	reason : String? = null,
) {
	editMemberPermission(id) {
		allowed = allowed.minus(permission)
		denied = denied.plus(permission)
		this.reason = reason
	}
}

suspend fun TopGuildChannelBehavior.removeRolePermissions(
	id: Snowflake,
	vararg permissions: Permission,
	reason : String? = null
) {
	editRolePermission(id) {
		allowed = allowed.minus(Permissions(permissions.toSet()))
		denied = denied.plus(Permissions(permissions.toSet()))
		this.reason = reason
	}
}

suspend fun TopGuildChannelBehavior.removeMemberPermissions(
	id: Snowflake,
	vararg permissions: Permission,
	reason : String? = null
) {
	editMemberPermission(id) {
		allowed = allowed.minus(Permissions(permissions.toSet()))
		denied = denied.plus(Permissions(permissions.toSet()))
		this.reason = reason
	}
}