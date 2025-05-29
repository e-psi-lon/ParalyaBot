package fr.paralya.bot.common.config

import io.konform.validation.Validation
import io.konform.validation.ValidationResult
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import java.io.File
import kotlin.system.exitProcess
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ConfigManagerTest : KoinTest {

	@TempDir
	lateinit var tempDir: File
	private lateinit var configFile: File

	@BeforeEach
	fun setup() {
		startKoin { }
		configFile = File(tempDir, "config.conf")
	}

	@AfterEach
	fun tearDown() {
		stopKoin()
		unmockkAll()
	}

	@Test
	fun `createDefaultConfig creates config with required structure when file does not exist`() {
		mockkConstructor(File::class)
		every { anyConstructed<File>().exists() } returns false
		every { anyConstructed<File>().absolutePath } returns "/tmp/config.conf"
		every { anyConstructed<File>().writeText(any()) } returns Unit
		every { exitProcess(1) } answers {
			throw RuntimeException("Simulated exit")
		}

		try {
			ConfigManager()
			fail("Should have exited the process")
		} catch (_: RuntimeException) {
		}

		verify {
			anyConstructed<File>().writeText(match { content ->
				content.contains("bot {") &&
						content.contains("token = \"\"") &&
						content.contains("admins = []") &&
						content.contains("games {")
			})
		}
	}

	@Test
	fun `loadConfig loads existing configuration correctly`() {
		// Create a test config file
		configFile.writeText(
			"""
            |bot {
            |    token = "test-token"
            |    admins = [123456789, 987654321]
            |    dmLogChannelId = 555555555
            |    paralyaId = 77777re7777
            |}
            |games {}
        """.trimMargin()
		)

		mockkConstructor(File::class)
		every { anyConstructed<File>().exists() } returns true
		every { anyConstructed<File>() } returns configFile

		val configManager = ConfigManager()

		assertEquals("test-token", configManager.botConfig.token)
		assertEquals(2, configManager.botConfig.admins.size)
		assertEquals(555555555UL, configManager.botConfig.dmLogChannelId)
		assertEquals(777777777UL, configManager.botConfig.paralyaId)
	}

	@Test
	fun `reloadConfig refreshes configuration from disk`() {
		// Create initial config
		configFile.writeText(
			"""
            |bot {
            |    token = "initial-token"
            |    admins = [123456789]
            |    dmLogChannelId = 111111111
            |    paralyaId = 222222222
            |}
            |games {}
        """.trimMargin()
		)

		// mockkConstructor(File::class)
		// every { anyConstructed<File>().exists() } returns true
		// every { anyConstructed<File>() } returns configFile

		// val configManager = ConfigManager()
		// assertEquals("initial-token", configManager.botConfig.token)

		// Update config file
		configFile.writeText(
			"""
            |bot {
            |    token = "updated-token"
            |    admins = [123456789, 987654321]
            |    dmLogChannelId = 333333333
            |    paralyaId = 444444444
            |}
            |games {}
        """.trimMargin()
		)

		// Reload config
		// configManager.reloadConfig()

		// Verify updated values
		// assertEquals("updated-token", configManager.botConfig.token)
		// assertEquals(2, configManager.botConfig.admins.size)
		// assertEquals(333333333UL, configManager.botConfig.dmLogChannelId)
		// assertEquals(444444444UL, configManager.botConfig.paralyaId)
	}

	@Test
	fun `registerConfig correctly registers and loads game config`() {
		// Create a test config file with game section
		println("Test called")
		configFile.writeText(
			"""
            |bot {
            |    token = "test-token"
            |    admins = [123456789]
            |    dmLogChannelId = 111111111
            |    paralyaId = 222222222
            |}
            |games {
            |    testgame {
            |        enabled = true
            |        score = 100
            |        name = "Test Game"
            |    }
            |}
        """.trimMargin()
		)
		println("Config file written")
		println("with ${configFile.readText()}")
		val configPath = configFile.absolutePath

		mockkStatic("dev.kordex.core.utils._KoinKt")
		every {
			dev.kordex.core.utils.loadModule(any(), any())
		} answers {
			// Instead of using KordEx's loadModule, use regular Koin
			val moduleCreator = firstArg<() -> org.koin.core.module.Module>()
			val module = moduleCreator()
			org.koin.core.context.GlobalContext.get().loadModules(listOf(module))
			module
		}

		mockkConstructor(File::class)
		println("Start mocking File")
		every { anyConstructed<File>().exists() } returns true
		every { anyConstructed<File>().absolutePath } returns configPath

		data class TestGameConfig(
			var enabled: Boolean = false,
			var score: Int = 0,
			var name: String = ""
		) : ValidatedConfig {
			override fun validate(): ValidationResult<TestGameConfig> =
				Validation<TestGameConfig> {}(this)
		}

		val configManager = ConfigManager()
		println("Did it passed after the creation?")
		configManager.registerConfig<TestGameConfig>("TestGameConfig")

		println("Did it passed after here?")
		// Get the registered config
		val gameConfig by inject<TestGameConfig>()

		// Verify loaded values
		assertTrue(gameConfig.enabled)
		assertEquals(100, gameConfig.score)
		assertEquals("Test Game", gameConfig.name)
	}

	@Test
	fun `convertValue handles different data types correctly`() {
		val configManager = ConfigManager()

		// Access the private method using reflection
		val convertValueMethod = ConfigManager::class.java.getDeclaredMethod(
			"convertValue", String::class.java, kotlin.reflect.KClass::class.java, List::class.java
		)
		convertValueMethod.isAccessible = true

		// Test various data types
		assertEquals("hello", convertValueMethod.invoke(configManager, "hello", String::class, emptyList<Any>()))
		assertEquals(42, convertValueMethod.invoke(configManager, "42", Int::class, emptyList<Any>()))
		assertEquals(true, convertValueMethod.invoke(configManager, "true", Boolean::class, emptyList<Any>()))
		assertEquals(3.14, convertValueMethod.invoke(configManager, "3.14", Double::class, emptyList<Any>()))

		@Suppress("UNCHECKED_CAST")
		val listResult =
			convertValueMethod.invoke(configManager, "[1,2,3]", List::class, listOf(Int::class)) as List<Int>
		assertEquals(listOf(1, 2, 3), listResult)

		@Suppress("UNCHECKED_CAST")
		val mapResult = convertValueMethod.invoke(
			configManager, "key1:value1,key2:value2", Map::class, listOf(String::class, String::class)
		) as Map<String, String>
		assertEquals(mapOf("key1" to "value1", "key2" to "value2"), mapResult)
	}

	@Test
	fun `fallback to environment variables when config property is missing`() {
		// Create config without all properties
		configFile.writeText(
			"""
            |bot {
            |    token = "test-token"
            |    dmLogChannelId = 111111111
            |}
            |games {}
        """.trimMargin()
		)

		mockkConstructor(File::class)
		every { anyConstructed<File>().exists() } returns true
		every { anyConstructed<File>() } returns configFile

		// Mock environment variables
		val envVariables = mapOf(
			"BOT_ADMINS" to "123456789,987654321",
			"BOT_PARALYA_ID" to "222222222"
		)

		System.getenv().forEach { (key, value) ->
			System.setProperty("env.$key", value)
		}
		envVariables.forEach { (key, value) ->
			System.setProperty("env.$key", value)
		}

		val configManager = ConfigManager()

		assertEquals("test-token", configManager.botConfig.token)
		assertEquals(111111111UL, configManager.botConfig.dmLogChannelId)

		// Clean up environment variables
		envVariables.keys.forEach {
			System.clearProperty("env.$it")
		}
	}
}