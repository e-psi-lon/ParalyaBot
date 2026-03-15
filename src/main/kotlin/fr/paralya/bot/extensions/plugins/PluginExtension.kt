package fr.paralya.bot.extensions.plugins

import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import fr.paralya.bot.I18n
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.get
import fr.paralya.bot.common.plugins.Plugin
import fr.paralya.bot.common.plugins.PluginManager
import org.koin.core.component.inject

class PluginExtension : Extension() {
    override val name: String = "Plugins"

    val pluginManager by inject<PluginManager>()

    override suspend fun setup() {

        ephemeralSlashCommand {
            name = I18n.Plugins.Command.name
            description = I18n.Plugins.Command.description

            ephemeralSubCommand(::ReloadArguments) {
                name = I18n.Plugins.Reload.Command.name
                description = I18n.Plugins.Reload.Command.description

                adminOnly {

                }

            }

            ephemeralSubCommand {
                name = I18n.Plugins.List.Command.name
                description = I18n.Plugins.List.Command.description

                adminOnly {
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


    inner class ReloadArguments : Arguments() {
        val plugin by stringChoice {
            name = I18n.Plugins.Reload.Argument.Plugin.name
            description = I18n.Plugins.Reload.Argument.Plugin.description
            choices = get<PluginManager>().plugins.associate { plugin ->
                val pluginInstance = plugin.plugin as Plugin
                pluginInstance.key to pluginInstance.name
            }.toMutableMap()
        }
    }
}