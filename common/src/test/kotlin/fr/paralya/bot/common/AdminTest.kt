package fr.paralya.bot.common

import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import fr.paralya.bot.common.config.BotConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminTest {

	companion object {
		// Fixed Discord snowflakes for testing
		private const val SNOWFLAKE1 = 661720242585600000UL  // 01/01/2020
		private const val SNOWFLAKE2 = 794354201395200000UL  // 01/01/2021
		private const val SNOWFLAKE3 = 926625772339200000UL  // 01/01/2022
		private const val SNOWFLAKE4 = 1058897343283200000UL // 01/01/2023
	}

	@AfterEach
	fun tearDown() {
		unmockkAll()
	}

	@Test
	fun `user is admin when ID matches config admins list`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1, SNOWFLAKE2),
			dmLogChannelId = SNOWFLAKE3,
			paralyaId = SNOWFLAKE4
		)
		val user = mockk<User> {
			every { id } returns SNOWFLAKE1.snowflake
		}

		// Act
		val result = user.isAdmin(config)

		// Assert
		assertTrue(result)
	}

	@Test
	fun `user is not admin when ID does not match config admins list`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)
		val user = mockk<User> {
			every { id } returns SNOWFLAKE4.snowflake
		}

		// Act
		val result = user.isAdmin(config)

		// Assert
		assertFalse(result)
	}

	@Test
	fun `null user is not admin`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)
		val user: User? = null

		// Act
		val result = user.isAdmin(config)

		// Assert
		assertFalse(result)
	}

	@Test
	fun `member is admin when ID matches config admins list`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1, SNOWFLAKE2),
			dmLogChannelId = SNOWFLAKE3,
			paralyaId = SNOWFLAKE4
		)
		val member = mockk<Member> {
			every { id } returns SNOWFLAKE2.snowflake
		}

		// Act
		val result = member.isAdmin(config)

		// Assert
		assertTrue(result)
	}

	@Test
	fun `member is not admin when ID does not match config admins list`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)
		val member = mockk<Member> {
			every { id } returns SNOWFLAKE4.snowflake
		}

		// Act
		val result = member.isAdmin(config)

		// Assert
		assertFalse(result)
	}

	@Test
	fun `null member is not admin`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)
		val member: Member? = null

		// Act
		val result = member.isAdmin(config)

		// Assert
		assertFalse(result)
	}

	@Test
	fun `admin check works with multiple admin IDs`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1, SNOWFLAKE2, SNOWFLAKE3, SNOWFLAKE4),
			dmLogChannelId = SNOWFLAKE1,
			paralyaId = SNOWFLAKE2
		)
		val user = mockk<User> {
			every { id } returns SNOWFLAKE3.snowflake
		}

		// Act
		val result = user.isAdmin(config)

		// Assert
		assertTrue(result)
	}

	@Test
	fun `admin check is case and value sensitive`() {
		// Arrange
		val config = BotConfig(
			token = "token",
			admins = listOf(SNOWFLAKE1),
			dmLogChannelId = SNOWFLAKE2,
			paralyaId = SNOWFLAKE3
		)
		val user = mockk<User> {
			every { id } returns (SNOWFLAKE1 - 1UL).snowflake // Off by one
		}

		// Act
		val result = user.isAdmin(config)

		// Assert
		assertFalse(result)
	}
}

