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
		private val snowflake1 = 661720242585600000UL  // 01/01/2020
		private val snowflake2 = 794354201395200000UL  // 01/01/2021
		private val snowflake3 = 926625772339200000UL  // 01/01/2022
		private val snowflake4 = 1058897343283200000UL // 01/01/2023
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
			admins = listOf(snowflake1, snowflake2),
			dmLogChannelId = snowflake3,
			paralyaId = snowflake4
		)
		val user = mockk<User> {
			every { id } returns snowflake1.snowflake
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
			admins = listOf(snowflake1),
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
		)
		val user = mockk<User> {
			every { id } returns snowflake4.snowflake
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
			admins = listOf(snowflake1),
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
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
			admins = listOf(snowflake1, snowflake2),
			dmLogChannelId = snowflake3,
			paralyaId = snowflake4
		)
		val member = mockk<Member> {
			every { id } returns snowflake2.snowflake
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
			admins = listOf(snowflake1),
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
		)
		val member = mockk<Member> {
			every { id } returns snowflake4.snowflake
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
			admins = listOf(snowflake1),
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
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
			admins = listOf(snowflake1, snowflake2, snowflake3, snowflake4),
			dmLogChannelId = snowflake1,
			paralyaId = snowflake2
		)
		val user = mockk<User> {
			every { id } returns snowflake3.snowflake
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
			admins = listOf(snowflake1),
			dmLogChannelId = snowflake2,
			paralyaId = snowflake3
		)
		val user = mockk<User> {
			every { id } returns (snowflake1 - 1UL).snowflake // Off by one
		}

		// Act
		val result = user.isAdmin(config)

		// Assert
		assertFalse(result)
	}
}

