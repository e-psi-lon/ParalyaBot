package fr.paralya.bot.common.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotConfigTest {
	companion object {
		// Fixed Discord snowflakes for testing
		private val snowflake1 = 661720242585600000UL // 01/01/2020
		private val snowflake2 = 794354201395200000UL // 01/01/2021
		private val snowflake3 = 926625772339200000UL // 01/01/2022
		private val snowflake4 = 1058897343283200000UL // 01/01/2023
	}

	@Test
	fun `valid bot config with all required fields passes validation`() {
		// Arrange
		val config = BotConfig(
			token = "valid-token",
			admins = listOf(snowflake1),
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
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
			admins = listOf(snowflake1),
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
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
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
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
			admins = listOf(snowflake1, snowflake2, snowflake3),
			dmLogChannelId = snowflake4,
			paralyaId = snowflake4
		)

		// Act
		val result = config.validate()

		// Assert
		assertTrue(result.isValid)
	}
}

