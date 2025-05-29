package fr.paralya.bot.lg.data

import fr.paralya.bot.common.config.ValidatedConfig
import fr.paralya.bot.common.config.greaterThanZero
import io.konform.validation.Validation
import io.konform.validation.ValidationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents the configuration for the Werewolf game.
 *
 * @property rolesCategory The ID of the category for roles.
 * @property mainCategory The ID of the main category.
 * @property aliveRole The ID of the role for alive players.
 * @property deadRole The ID of the role for dead players.
 */
@Serializable
data class LgConfig(
	var rolesCategory: ULong = 0u,
	var mainCategory: ULong = 0u,
	var aliveRole: ULong = 0u,
	var deadRole: ULong = 0u
)