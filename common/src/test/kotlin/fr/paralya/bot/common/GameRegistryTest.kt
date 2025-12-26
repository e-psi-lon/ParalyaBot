package fr.paralya.bot.common

import dev.kordex.i18n.I18n
import dev.kordex.i18n.Key
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class GameRegistryTest {

	@BeforeEach
	fun startup() {
		I18n.defaultLocale = Locale.FRENCH
	}

	@AfterEach
	fun tearDown() {
		unmockkAll()
	}

	@Test
	fun `registerGameMode stores game mode with key`() {
		// Arrange
		val registry = GameRegistry()
		val key = Key("game-mode.test")
		val gameMode = "Test Game"

		// Act
		registry.registerGameMode(key, gameMode)
		val result = registry.getGameMode(gameMode)

		// Assert
		assertEquals(key, result.first)
		assertEquals(gameMode, result.second)
	}

	@Test
	fun `getGameModes returns all registered modes`() {
		// Arrange
		val registry = GameRegistry()
		val key1 = Key("game-mode.test1")
		val gameMode1 = "Game 1"
		val key2 = Key("game-mode.test2")
		val gameMode2 = "Game 2"

		// Act
		registry.registerGameMode(key1, gameMode1)
		registry.registerGameMode(key2, gameMode2)
		val modes = registry.getGameModes()

		// Assert
		assertEquals(2, modes.size)
		assertEquals(gameMode1, modes[key1])
		assertEquals(gameMode2, modes[key2])
	}

	@Test
	fun `unloadGameMode removes registered mode`() {
		// Arrange
		val registry = GameRegistry()
		val key = Key("game-mode.test")
		val gameMode = "Test Game"
		registry.registerGameMode(key, gameMode)

		// Act
		registry.unloadGameMode(gameMode)

		// Assert
		assertEquals(GameRegistry.NONE, registry.getGameMode(gameMode))
		assertEquals(0, registry.getGameModes().size)
	}

	@Test
	fun `getGameMode returns NONE for non-existent mode`() {
		// Arrange
		val registry = GameRegistry()

		// Act
		val result = registry.getGameMode("NonExistent")

		// Assert
		assertEquals(GameRegistry.NONE, result)
	}

	@Test
	fun `registerGameMode with existing key overwrites previous value`() {
		// Arrange
		val registry = GameRegistry()
		val key = Key("game-mode.test")
		registry.registerGameMode(key, "Old Game")

		// Act
		registry.registerGameMode(key, "New Game")

		// Assert
		assertEquals(GameRegistry.NONE, registry.getGameMode("Old Game"))
		assertEquals(key to "New Game", registry.getGameMode("New Game"))
	}

	@Test
	fun `unloadGameMode with non-existent mode does not throw exception`() {
		// Arrange
		val registry = GameRegistry()

		// Act & Assert - should not throw
		registry.unloadGameMode("NonExistent")
	}

	@Test
	fun `getGameModes returns empty map for empty registry`() {
		// Arrange
		val registry = GameRegistry()

		// Act
		val modes = registry.getGameModes()

		// Assert
		assertEquals(0, modes.size)
	}

	@Test
	fun `unloadGameMode with NONE game mode does nothing`() {
		// Arrange
		val registry = GameRegistry()

		// Act
		registry.unloadGameMode("none")

		// Assert - should not throw, registry should remain empty
		assertEquals(0, registry.getGameModes().size)
	}

	@Test
	fun `multiple game modes can be registered and retrieved independently`() {
		// Arrange
		val registry = GameRegistry()
		val testKey = Key("game-mode.test")
		val werewolfKey = Key("game-mode.werewolf")
		val testGame = "TestGame"
		val werewolfGame = "Werewolf"

		// Act
		registry.registerGameMode(testKey, testGame)
		registry.registerGameMode(werewolfKey, werewolfGame)

		// Assert
		assertEquals(testKey to testGame, registry.getGameMode(testGame))
		assertEquals(werewolfKey to werewolfGame, registry.getGameMode(werewolfGame))
		assertEquals(2, registry.getGameModes().size)
	}

	@Test
	fun `unloadGameMode followed by new registration with same name works`() {
		// Arrange
		val registry = GameRegistry()
		val key1 = Key("game-mode.v1")
		val key2 = Key("game-mode.v2")
		val gameName = "TestGame"

		// Act
		registry.registerGameMode(key1, gameName)
		registry.unloadGameMode(gameName)
		registry.registerGameMode(key2, gameName)

		// Assert
		assertEquals(key2 to gameName, registry.getGameMode(gameName))
	}
}

