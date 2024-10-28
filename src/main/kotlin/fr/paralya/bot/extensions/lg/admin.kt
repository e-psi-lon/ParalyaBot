package fr.paralya.bot.extensions.lg

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommandContext
import dev.kordex.core.commands.application.slash.PublicSlashCommandContext
import dev.kordex.core.components.forms.ModalForm
import fr.paralya.bot.ADMINS


suspend fun <A: Arguments, M: ModalForm>EphemeralSlashCommandContext<A, M>.adminOnly(action: suspend EphemeralSlashCommandContext<A, M>.() -> Unit) {
    if (ADMINS.contains(this.member?.id?.value)) {
        action()
        return
    }
    respond {
        content = "Vous n'avez pas les permissions nécessaires pour effectuer cette commande."
    }
}
suspend fun <A: Arguments, M: ModalForm>PublicSlashCommandContext<A, M>.adminOnly(action: suspend PublicSlashCommandContext<A, M>.() -> Unit) {
    if (ADMINS.contains(this.member?.id?.value)) {
        action()
        return
    }
    respond {
        content = "Vous n'avez pas les permissions nécessaires pour effectuer cette commande."
    }
    return
}