package fr.paralya.bot.common.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotConfigTest {
	companion object {
		// Fixed Discord snowflakes for testing
		private const val SNOWFLAKE1 = 661720242585600000UL // 01/01/2020
		private const val SNOWFLAKE2 = 794354201395200000UL // 01/01/2021
		private const val SNOWFLAKE3 = 926625772339200000UL // 01/01/2022
		private const val SNOWFLAKE4 = 1058897343283200000UL // 01/01/2023
	}

	@Test
	fun `valid bot config with all required fields passes validation`() {
		// Arrange
		val config = BotConfig(
			token = "valid-token",
			admins = listOf(SNOWFLAKE1),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)

		// Act
		val result = config.validate()

		// Assert
		assertTrue(result.isValid)
	}

	@Test
	fun `config fails validation when token is empty`() {
		// Arrange
		val config = BotConfig(
			token = "",
			admins = listOf(SNOWFLAKE1),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)

		// Act
		val result = config.validate()

		// Assert
		assertFalse(result.isValid)
	}

	@Test
	fun `config fails validation when admins list is empty`() {
		// Arrange
		val config = BotConfig(
			token = "valid-token",
			admins = emptyList(),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)

		// Act
		val result = config.validate()

		// Assert
		assertFalse(result.isValid)
	}

	@Test
	fun `config succeeds with multiple valid admin IDs`() {
		// Arrange
		val config = BotConfig(
			token = "valid-token",
			admins = listOf(SNOWFLAKE1, SNOWFLAKE2, SNOWFLAKE3),
			dmLogChannelId = SNOWFLAKE4,
			paralyaId = SNOWFLAKE4
		)

		// Act
		val result = config.validate()

		// Assert
		assertTrue(result.isValid)
	}
}

