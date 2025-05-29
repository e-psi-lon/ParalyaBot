package fr.paralya.bot.common.config

import io.konform.validation.ValidationBuilder
import io.konform.validation.ValidationResult


/**
 * Interface for configuration classes that require validation.
 * Implementing classes should provide a [validate] method that returns a [ValidationResult].
 */
interface ValidatedConfig {
	fun validate(): ValidationResult<ValidatedConfig>
}
/**
 * Validates that the property is defined and not zero. It uses [name] to provide a user-friendly error message.
 * This is used to ensure that certain configuration values are defined and not zero (placeholder).
 */
fun ValidationBuilder<ULong>.defined(name: String) =
	constrain("$name must be defined with a real value, not a placeholder") { it != 0u.toULong() }