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
 * - It has the minimum bit length (> 2^21)
 * - It doesn't overflow (< [ULong.MAX_VALUE])
 * - Its timestamp is not in the future (allowing 60 seconds for clock drift)
 *
 * Note: This only validates the structure, not that the ID exists in Discord or that it is entirely valid.
 * For more information, see the [Discord Snowflake](h²ttps://discord.com/developers/docs/reference#snowflakes)
 * documentation.
 */
fun ValidationBuilder<ULong>.appearsToBeSnowflake(displayName: String) =
	constrain("$displayName must be a valid Discord snowflake and it appears not to be") {
		it > (1UL shl 21) && it < ULong.MAX_VALUE
				&& ((it shr 22) + DISCORD_EPOCH) <= (System.currentTimeMillis() + 60_000).toULong()

	}