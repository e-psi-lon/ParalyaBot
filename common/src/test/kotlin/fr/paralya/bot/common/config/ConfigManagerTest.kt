package fr.paralya.bot.common.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ConfigManagerTest : KoinTest {

	@TempDir
	lateinit var tempDir: Path

	companion object {
		// Fixed Discord snowflakes for testing
		private val snowflake1 = 661720242585600000UL  // 01/01/2020
		private val snowflake2 = 794354201395200000UL  // 01/01/2021
		private val snowflake3 = 926625772339200000UL  // 01/01/2022
		private val snowflake4 = 1058897343283200000UL // 01/01/2023
	}

	@BeforeEach
	fun setup() {
		startKoin { }
	}

	@AfterEach
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun `creates default config when file does not exist`() {
		// Arrange
		val nonExistentPath = tempDir.resolve("non-existent.conf")

		// Act
		ConfigManager(nonExistentPath)

		// Assert
		assertTrue(nonExistentPath.toFile().exists())
		val content = nonExistentPath.toFile().readText()
		assertTrue(content.contains("bot {"))
		assertTrue(content.contains("token = \"\""))
		assertTrue(content.contains("games {"))
	}

	@Test
	fun `loads existing valid configuration`() {
		// Arrange
		val configFile = tempDir.resolve("config.conf")
		configFile.writeText(
			"""
			|bot {
			|    token = "test-token-123"
			|    admins = [$snowflake1, $snowflake2]
			|    dmLogChannelId = $snowflake3
			|    paralyaId = $snowflake4
			|}
			|games {}
			""".trimMargin()
		)

		// Act
		val configManager = ConfigManager(configFile)

		// Assert
		assertEquals("test-token-123", configManager.botConfig.token)
		assertEquals(2, configManager.botConfig.admins.size)
	}

	@Test
	fun `throws when bot config has no token`() {
		// Arrange
		val configFile = tempDir.resolve("config.conf")
		configFile.writeText(
			"""
			|bot {
			|    token = ""
			|    admins = [$snowflake1]
			|    dmLogChannelId = $snowflake2
			|    paralyaId = $snowflake3
			|}
			|games {}
			""".trimMargin()
		)

		// Act & Assert
		assertFails { ConfigManager(configFile) }
	}

	@Test
	fun `throws when bot config has no admins`() {
		// Arrange
		val configFile = tempDir.resolve("config.conf")
		configFile.writeText(
			"""
			|bot {
			|    token = "test-token"
			|    admins = []
			|    dmLogChannelId = $snowflake1
			|    paralyaId = $snowflake2
			|}
			|games {}
			""".trimMargin()
		)

		// Act & Assert
		assertFails { ConfigManager(configFile) }
	}

	@Test
	fun `throws when required bot section is missing`() {
		// Arrange
		val configFile = tempDir.resolve("config.conf")
		configFile.writeText("games {}")

		// Act & Assert
		assertFails { ConfigManager(configFile) }
	}

	@Test
	fun `throws when snowflake IDs are invalid`() {
		// Arrange
		val configFile = tempDir.resolve("config.conf")
		configFile.writeText(
			"""
			|bot {
			|    token = "test-token"
			|    admins = [100]
			|    dmLogChannelId = 999
			|    paralyaId = $snowflake1
			|}
			|games {}
			""".trimMargin()
		)

		// Act & Assert
		assertFails { ConfigManager(configFile) }
	}

	@Test
	fun `loads config with multiple valid admin IDs`() {
		// Arrange
		val configFile = tempDir.resolve("config.conf")
		configFile.writeText(
			"""
			|bot {
			|    token = "test-token"
			|    admins = [$snowflake1, $snowflake2, $snowflake3]
			|    dmLogChannelId = $snowflake4
			|    paralyaId = $snowflake4
			|}
			|games {}
			""".trimMargin()
		)

		// Act
		val configManager = ConfigManager(configFile)

		// Assert
		assertEquals(3, configManager.botConfig.admins.size)
	}
}

