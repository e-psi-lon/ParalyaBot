package fr.paralya.bot.lg.data

import fr.paralya.bot.common.config.ValidatedConfig
import fr.paralya.bot.common.config.appearsToBeSnowflake
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
	val rolesCategory: ULong = 0u,
	val mainCategory: ULong = 0u,
	val aliveRole: ULong = 0u,
	val deadRole: ULong = 0u
) : ValidatedConfig {
	@Transient
	private val validator = Validation {
		LgConfig::rolesCategory {
			appearsToBeSnowflake("Roles category ID")
		}
		LgConfig::mainCategory {
			appearsToBeSnowflake("Main category ID")
		}
		LgConfig::aliveRole {
			appearsToBeSnowflake("Alive role ID")
		}
		LgConfig::deadRole {
			appearsToBeSnowflake("Dead role ID")
		}
	}

	override fun validate(): ValidationResult<LgConfig> =
		validator(this)
}