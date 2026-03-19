package fr.paralya.bot.extensions.plugins

import dev.kord.common.asJavaLocale
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.DISCORD_GREEN
import dev.kordex.core.DISCORD_RED
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.components.ComponentContainer
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.utils.suggestStringMap
import dev.kordex.i18n.Key
import dev.kordex.i18n.I18n as KI18n
import fr.paralya.bot.I18n
import fr.paralya.bot.botDeveloper
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.plugins.OldPluginFailedToDelete
import fr.paralya.bot.common.plugins.OldPluginFallbackFailedToLoad
import fr.paralya.bot.common.plugins.OldPluginNotFound
import fr.paralya.bot.common.plugins.OldPluginReusedAsFallback
import fr.paralya.bot.common.plugins.Plugin
import fr.paralya.bot.common.plugins.PluginManager
import fr.paralya.bot.common.plugins.PluginReloadError
import fr.paralya.bot.common.plugins.SuccessfulPluginReload
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.inject
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension


private const val CACHE_DURATION_MS = 1000L

class PluginExtension : Extension() {
    override val name: String = "Plugins"

    val pluginManager by inject<PluginManager>()
    private val pluginsPath = Path(System.getenv("PARALYA_BOT_PLUGINS_DIR") ?: "./plugins")


    private suspend fun ComponentContainer.exceptionButton(label: Key, title: String, exception: Exception) = ephemeralButton {
        this.label = label
        initialResponse {
            val stringException = exception.stackTraceToString()
            if (stringException.length < EmbedBuilder.Limits.description - 8) embed { // Account for the code block
                this.title = title
                description = "```\n$stringException\n```"
                color = DISCORD_RED
            } else {
                content = ""
                addFile(
                    "stacktrace.txt", ChannelProvider(stringException.length.toLong()) {
                        ByteReadChannel(stringException)
                    }
                )
            }
        }


    }

    override suspend fun setup() {

        ephemeralSlashCommand {
            name = I18n.Plugins.Command.name
            description = I18n.Plugins.Command.description

            ephemeralSubCommand(::ReloadArguments) {
                name = I18n.Plugins.Reload.Command.name
                description = I18n.Plugins.Reload.Command.description

                adminOnly(listOf(botDeveloper)) {
                    val reloadResult = pluginManager.reloadPlugin(
                        arguments.oldPluginId,
                        pluginsPath.resolve("${arguments.newPluginPath}.zip")
                    )

                    when (reloadResult) {
                        is SuccessfulPluginReload -> respond {
                            embed {
                                title = I18n.Plugins.Reload.Response.Success.Embed.title.contextTranslate()
                                description = I18n.Plugins.Reload.Response.Success.Embed.description.contextTranslate(arguments.oldPluginId, arguments.newPluginPath)
                                color = DISCORD_GREEN
                            }
                        }
                        is PluginReloadError -> {
                            val exception = reloadResult.exception
                            respond {
                                embed {
                                    title = I18n.Plugins.Reload.Response.Error.Embed.title.contextTranslate()
                                    color = DISCORD_RED
                                    description = when (reloadResult) {
                                        is OldPluginNotFound -> I18n.Plugins.Reload.Response.Error.OldPlugin.notFound.contextTranslate(arguments.oldPluginId)
                                        is OldPluginFailedToDelete -> I18n.Plugins.Reload.Response.Error.OldPlugin.failedToDelete.contextTranslate(arguments.oldPluginId)
                                        is OldPluginFallbackFailedToLoad -> I18n.Plugins.Reload.Response.Error.OldPlugin.fallbackFailed.contextTranslate(arguments.newPluginPath, arguments.oldPluginId)
                                        is OldPluginReusedAsFallback -> I18n.Plugins.Reload.Response.Error.OldPlugin.asFallback.contextTranslate(arguments.newPluginPath, arguments.oldPluginId)
                                    }
                                }
                                components {
                                    if (exception != null) exceptionButton(
                                        I18n.Plugins.Reload.Response.Error.Button.viewError,
                                        I18n.Plugins.Reload.Response.Error.Button.viewError.contextTranslate(arguments.oldPluginId),
                                        exception
                                    )
                                    if (reloadResult is OldPluginFallbackFailedToLoad) exceptionButton(
                                        I18n.Plugins.Reload.Response.Error.Button.secondError,
                                        I18n.Plugins.Reload.Response.Error.Button.secondError.contextTranslate(arguments.newPluginPath),
                                        reloadResult.fallbackException
                                    )
                                }
                            }
                        }
                    }
                }
            }



            ephemeralSubCommand {
                name = I18n.Plugins.List.Command.name
                description = I18n.Plugins.List.Command.description

                adminOnly(listOf(botDeveloper)) {
                    respond {
                        embed {
                            title = I18n.Plugins.List.Response.Embed.title.contextTranslate()
                            for (plugin in pluginManager.plugins) {
                                val pluginInstance = plugin.plugin as Plugin
                                field {
                                    name = pluginInstance.key.contextTranslate()
                                    value = "`${pluginInstance.version}`"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val mutex = Mutex()
    private var lastCacheTime = 0L
    private var cachedZipFiles: List<Path> = emptyList()
    private suspend fun getAvailableZipFiles(): List<Path> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now - lastCacheTime > CACHE_DURATION_MS) {
            cachedZipFiles = pluginsPath.listDirectoryEntries("*.zip")
            lastCacheTime = now
        }
        return cachedZipFiles
    }

    fun Arguments.pluginId(name: Key, description: Key) = string {
        this.name = name
        this.description = description
        validate {
            if (pluginManager.plugins.none { it.pluginId == value }) fail()
        }
        autoComplete {
            val effectiveLocale = (locale ?: guildLocale)?.asJavaLocale() ?: KI18n.defaultLocale
            suggestStringMap(
                pluginManager.plugins.associate { plugin ->
                    val pluginInstance = plugin.plugin as Plugin
                    pluginInstance.key.translateLocale(effectiveLocale) to pluginInstance.pluginId
                }
            )
        }
    }


    inner class ReloadArguments : Arguments() {
        val oldPluginId by pluginId(
            I18n.Plugins.Reload.Argument.Plugin.name,
            I18n.Plugins.Reload.Argument.Plugin.description
        )

        val newPluginPath: String by string {
            name = I18n.Plugins.Reload.Argument.NewPluginPath.name
            description = I18n.Plugins.Reload.Argument.NewPluginPath.description
            validate {
                if (value !in getAvailableZipFiles().map { it.nameWithoutExtension }) fail()
            }
            autoComplete {
                suggestStringMap(getAvailableZipFiles().associate { it.nameWithoutExtension to it.fileName.name })
            }
        }
    }
}
