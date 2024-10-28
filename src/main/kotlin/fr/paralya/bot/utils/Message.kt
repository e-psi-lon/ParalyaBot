package fr.paralya.bot.utils

import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.i18n.types.Key
import fr.paralya.bot.i18n.Translations.Modal

class Message: ModalForm() {
    override var title: Key = Modal.Message.title

    val message = paragraphText {
        label = Modal.Message.label
        placeholder = Modal.Message.placeholder
        maxLength = 2000
    }
}