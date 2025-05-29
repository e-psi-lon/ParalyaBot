package fr.paralya.bot.common

import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.builder.PresenceBuilder
import dev.kordex.core.i18n.types.Key
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.koin.test.KoinTest
import kotlin.test.Test
import kotlin.test.assertEquals


class GameRegistryTest : KoinTest {


	private val gameRegistry = GameRegistry()

	@Test
	fun `register a game mode`() {
		val key = Key("game-mode.test")
		val gameMode = "Test Game Mode"
		gameRegistry.registerGameMode(key, gameMode)
		val result = gameRegistry.getGameMode(gameMode)
		assertEquals(key, result.first, "Expected key to be $key got ${result.first} instead")
		assertEquals(gameMode, result.second, "Expected game mode to be $gameMode got ${result.second} instead")
	}

	@Test
	fun `get all game modes`() {
		val key = Key("game-mode.test")
		val gameMode = "Test Game Mode"
		gameRegistry.registerGameMode(key, gameMode)
		val key2 = Key("game-mode.test2")
		val gameMode2 = "Test Game Mode 2"
		gameRegistry.registerGameMode(key2, gameMode2)
		val result = gameRegistry.getGameModes()
		assertEquals(2, result.size, "Expected size to be 2 got ${result.size} instead")
		assertEquals(key, result.keys.first(), "Expected key to be $key got ${result.keys.first()} instead")
		assertEquals(
			gameMode,
			result.values.first(),
			"Expected game mode to be $gameMode got ${result.values.first()} instead"
		)
		assertEquals(key2, result.keys.last(), "Expected key to be $key2 got ${result.keys.last()} instead")
		assertEquals(
			gameMode2,
			result.values.last(),
			"Expected game mode to be $gameMode2 got ${result.values.last()} instead"
		)
	}


	@Test
	fun `unload a game mode`() {
		val key = Key("game-mode.test")
		val gameMode = "Test Game Mode"
		gameRegistry.registerGameMode(key, gameMode)
		gameRegistry.unloadGameMode(gameMode)
		val result = gameRegistry.getGameModes()
		assertEquals(0, result.size, "Expected size to be 0 got ${result.size} instead")
	}

	@Test
	fun `get non-existent game mode returns NONE`() {
		val result = gameRegistry.getGameMode("NonExistentGameMode")
		assertEquals(GameRegistry.NONE, result, "Expected NONE for non-existent game mode")
	}

	@Test
	fun `register game mode with existing key overwrites previous value`() {
		val key = Key("game-mode.test")
		val gameMode1 = "Test Game Mode 1"
		val gameMode2 = "Test Game Mode 2"

		gameRegistry.registerGameMode(key, gameMode1)
		gameRegistry.registerGameMode(key, gameMode2)

		val result = gameRegistry.getGameMode(gameMode2)
		assertEquals(key, result.first, "Key should match")
		assertEquals(gameMode2, result.second, "Game mode should be updated to the new value")

		// Check that old value is no longer accessible
		val oldResult = gameRegistry.getGameMode(gameMode1)
		assertEquals(GameRegistry.NONE, oldResult, "Old game mode should not be accessible")
	}

	@Test
	fun `register duplicate game mode with different key`() {
		val key1 = Key("game-mode.test1")
		val key2 = Key("game-mode.test2")
		val gameMode = "Same Game Mode"

		gameRegistry.registerGameMode(key1, gameMode)
		gameRegistry.registerGameMode(key2, gameMode)

		// When retrieving by value, it should return the first registered key
		val result = gameRegistry.getGameMode(gameMode)
		assertEquals(key1, result.first, "Should return the first key registered with this value")
	}

	@Test
	fun `unload non-existent game mode doesn't throw exception`() {
		// This should not throw an exception
		try {
			gameRegistry.unloadGameMode("NonExistentGameMode")
		} catch (e: Exception) {
			throw AssertionError("Unloading non-existent game mode should not throw an exception but did: ${e.message}")
		}
	}

	@Test
	fun `empty registry returns correct values`() {
		val emptyMap = gameRegistry.getGameModes()
		assertEquals(0, emptyMap.size, "Empty registry should return empty map")

		val nonExistentResult = gameRegistry.getGameMode("anything")
		assertEquals(GameRegistry.NONE, nonExistentResult, "Non-existent game mode should return NONE")
	}

	@Test
	fun `presence builder sets idle status and watching message when NONE game mode`() {
		val presenceBuilder = mockk<PresenceBuilder>(relaxed = true)
		var capturedStatus: PresenceStatus? = null
		var capturedWatchingName: String? = null

		// Setup property capture for status
		every { presenceBuilder.status = capture(slot()) } answers {
			capturedStatus = firstArg()
		}

		// Setup method capture for watching
		every { presenceBuilder.watching(capture(slot())) } answers {
			capturedWatchingName = firstArg()
		}

		presenceBuilder.gameMode(GameRegistry.NONE)

		assertEquals(PresenceStatus.Idle, capturedStatus, "Status should be Idle for NONE game mode")
		assertEquals(
			"Paralya sans animation en cours...",
			capturedWatchingName,
			"Should set watching message for NONE game mode"
		)
	}

	@Test
	fun `presence builder sets online status and playing message with game name for regular game mode`() {
		val presenceBuilder = mockk<PresenceBuilder>(relaxed = true)
		val key = mockk<Key>()
		val gameMode = "Test Game Mode"
		val translatedKey = "Translated Game Mode"
		var capturedStatus: PresenceStatus? = null
		var capturedPlayingName: String? = null

		// Mock the translation function
		every { key.translate() } returns translatedKey

		// Setup property capture for status
		every { presenceBuilder.status = capture(slot()) } answers {
			capturedStatus = firstArg()
		}

		// Setup method capture for playing
		every { presenceBuilder.playing(capture(slot())) } answers {
			capturedPlayingName = firstArg()
		}

		presenceBuilder.gameMode(key to gameMode)

		assertEquals(PresenceStatus.Online, capturedStatus, "Status should be Online for regular game mode")
		assertEquals(
			"une partie de ${key.translate()}",
			capturedPlayingName,
			"Should set playing message with translated game name"
		)
	}
}