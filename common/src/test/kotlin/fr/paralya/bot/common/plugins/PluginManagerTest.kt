package fr.paralya.bot.common.plugins

import dev.kordex.i18n.Key
import fr.paralya.bot.common.ApiVersion
import fr.paralya.bot.common.CommonModule
import fr.paralya.bot.common.config.PrivateBotConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.pf4j.PluginWrapper
import kotlin.io.path.Path
import kotlin.test.assertFailsWith
import org.pf4j.Plugin as Pf4jPlugin

@ApiVersion(version = "0.0.1")
private class TooOldTestPlugin : Plugin() {
    override val name = "TooOldTestPlugin"
    override val key get() = Key("test.plugin.too-old")
    override fun defineConfig() = define<PrivateBotConfig>()
}

@ApiVersion(version = "999.0.0")
private class TooNewTestPlugin : Plugin() {
    override val name = "TooNewTestPlugin"
    override val key = Key("test.plugin.too-new")
    override fun defineConfig() = define<PrivateBotConfig>()
}

@ApiVersion(CommonModule.API_VERSION)
private class ValidTestPlugin : Plugin() {
    override val name: String = "ValidTestPlugin"
    override val key: Key = Key("test.plugin.valid")

    override fun defineConfig() = define<PrivateBotConfig>()
}

@ApiVersion(CommonModule.MIN_COMPATIBLE_VERSION)
private class MinCompatibleTestPlugin : Plugin() {
    override val name = "MinCompatibleTestPlugin"
    override val key = Key("test.plugin.min-compatible")
    override fun defineConfig() = define<PrivateBotConfig>()
}

private class UnannotatedTestPlugin : Plugin() {
    override val name: String = "UnannotatedTestPlugin"
    override val key: Key = Key("test.plugin.unannotated")

    override fun defineConfig() = define<PrivateBotConfig>()
}

private class NotAPluginAtAll : Pf4jPlugin()


class PluginManagerTest {

    private fun buildManager(): PluginManager =
        PluginManager(roots = listOf(Path("/fake/plugins")), enabled = true)

    private fun wrapperFor(clazz: Class<*>, id: String = "test-plugin"): PluginWrapper {
        val wrapper = mockk<PluginWrapper>(relaxed = true)
        val classLoader = mockk<ClassLoader>()
        every { wrapper.pluginClassLoader } returns classLoader
        every { wrapper.descriptor.pluginClass } returns clazz.name
        every { wrapper.pluginId } returns id
        every { classLoader.loadClass(clazz.name) } returns clazz
        return wrapper
    }

    @Test
    fun `validatePlugin throws when class does not extend Plugin`() {
        val manager = buildManager()
        val wrapper = wrapperFor(NotAPluginAtAll::class.java)

        assertFailsWith<PluginValidationException> {
            manager.validatePlugin(wrapper, Path("/fake/path"))
        }
    }

    @Test
    fun `validatePlugin throws when class is missing @ApiVersion`() {
        val manager = buildManager()
        val wrapper = wrapperFor(UnannotatedTestPlugin::class.java)

        assertFailsWith<PluginValidationException> {
            manager.validatePlugin(wrapper, Path("/fake/path"))
        }
    }

    @Test
    fun `validatePlugin throws when version is below minimum compatible`() {
        val manager = buildManager()
        val wrapper = wrapperFor(TooOldTestPlugin::class.java)

        assertFailsWith<PluginInvalidVersionException> {
            manager.validatePlugin(wrapper, Path("/fake/path"))
        }
    }

    @Test
    fun `validatePlugin throws when version exceeds current API version`() {
        val manager = buildManager()
        val wrapper = wrapperFor(TooNewTestPlugin::class.java)

        assertFailsWith<PluginInvalidVersionException> {
            manager.validatePlugin(wrapper, Path("/fake/path"))
        }
    }

    @Test
    fun `validatePlugin does not throw for a valid plugin at the API version upper boundary`() {
        val manager = buildManager()
        val wrapper = wrapperFor(ValidTestPlugin::class.java)

        manager.validatePlugin(wrapper, Path("/fake/path"))
    }

    @Test
    fun `validatePlugin does not throw for a valid plugin at the API version lower boundary`() {
        val manager = buildManager()
        val wrapper = wrapperFor(MinCompatibleTestPlugin::class.java)

        manager.validatePlugin(wrapper, Path("/fake/path"))
    }
}