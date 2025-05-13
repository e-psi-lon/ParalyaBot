package fr.paralya.bot.lg

import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommandContext
import dev.kordex.core.commands.application.slash.PublicSlashCommandContext
import dev.kordex.core.commands.application.slash.SlashCommand
import dev.kordex.core.commands.application.slash.SlashCommandContext
import dev.kordex.core.components.forms.ModalForm
import fr.paralya.bot.common.BotConfig
import fr.paralya.bot.common.ConfigManager
import org.koin.core.component.inject

/**
 * Extension function to register a command that admins can only execute.
 *
 * This function takes a lambda action that will be executed
 * only if the user is an admin.
 * It works as a wrapper around the existing command registration
 *
 * @param C The type of the [SlashCommandContext].
 * @param A The type of the [Arguments].
 * @param M The type of the [ModalForm].
 * @param action The action to be executed if the user is an admin.
 */
fun <C: SlashCommandContext<*, A, M>, A: Arguments, M : ModalForm>SlashCommand<C, A, M>.adminOnly(action: suspend C.(M?) -> Unit) {
	action { modal ->
		val configManager by inject<ConfigManager>()
		if (configManager.botConfig.admins.contains(this.member?.id?.value)) {
			action(modal)
		} else {
			when (this) {
				is PublicSlashCommandContext<*, *> -> respond { content = "Vous n'avez pas les permissions nécessaires pour effectuer cette commande." }
				is EphemeralSlashCommandContext<*, *> -> respond { content = "Vous n'avez pas les permissions nécessaires pour effectuer cette commande." }
			}
		}
	}
}

/**
 * Extension function to check if a user is an admin.
 *
 * @param config The [BotConfig] instance containing the list of admin IDs.
 * @return true if the user is an admin, false otherwise.
 */
fun User?.isAdmin(config: BotConfig): Boolean {
	return this != null && config.admins.contains(this.id.value)
}

/**
 * Extension function to check if a member is an admin.
 *
 * @param config The [BotConfig] instance containing the list of admin IDs.
 * @return true if the member is an admin, false otherwise.
 */
fun Member?.isAdmin(config: BotConfig): Boolean {
	return this != null && config.admins.contains(this.id.value)
}
