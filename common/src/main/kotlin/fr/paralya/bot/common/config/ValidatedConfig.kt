package fr.paralya.bot.common.config

import io.konform.validation.ValidationBuilder
import io.konform.validation.ValidationResult


private const val DISCORD_EPOCH = 1420070400000UL // Discord's epoch in milliseconds

/**
 * Interface for configuration classes that require validation.
 * Implementing classes should provide a [validate] method that returns a [ValidationResult].
 */
interface ValidatedConfig {
	fun validate(): ValidationResult<ValidatedConfig>
}


/**
 * Validates that a value appears to be a valid Discord snowflake by checking that:
 * - It has the minimum bit length (> 2^22)
 * - It doesn't overflow (< [ULong.MAX_VALUE])
 * - Its timestamp is not in the future (allowing 60 seconds for clock drift)
 *
 * Note: This only validates the structure, not that the ID exists in Discord or that it is entirely valid.
 * For more information, see the [Discord Snowflake](https://discord.com/developers/docs/reference#snowflakes)
 * documentation.
 */
@Suppress("MagicNumber")
fun ValidationBuilder<ULong>.appearsToBeSnowflake(displayName: String) =
	constrain("$displayName must be a valid Discord snowflake and it appears not to be") {
		it > (1UL shl 22) && it < ULong.MAX_VALUE
				&& ((it shr 22) + DISCORD_EPOCH) <= (System.currentTimeMillis() + 60_000).toULong()
	}

/**
 * Validates that a value is defined and not empty.
 *
 * The following rules are applied:
 * - For Strings: blank strings are considered empty.
 * - For Collections and Maps: empty collections/maps are considered empty.
 * - For Numbers, ULong, and UInt: zero values are considered empty.
 * - For Boolean: both true and false are considered defined (use `Boolean? = null` to represent undefined Boolean values).
 * - For null values: considered empty.
 * - **IMPORTANT:** Unknown types are considered defined by default.
 */
fun <T> ValidationBuilder<T>.defined(displayName: String = "Value") =
	constrain("$displayName must be defined and it appears not to be") {
	when (it) {
		is String -> it.isNotBlank()
		is Collection<*> -> it.isNotEmpty()
		is Map<*, *> -> it.isNotEmpty()
		is Number -> it.toDouble() != 0.0
		is ULong -> it != 0UL
		is UInt -> it != 0U
		// Boolean values (true or false) are considered defined
		// Use `Boolean? = null` in config classes to represent undefined Boolean values
		null -> false
		else -> true
	}
}
