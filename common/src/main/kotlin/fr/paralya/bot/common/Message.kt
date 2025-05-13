package fr.paralya.bot.common

import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.i18n.types.Key
import fr.paralya.bot.common.i18n.Translations.Modal


/**
 * Message is a modal form used to ask for input from the user.
 *
 *
 * @constructor Creates a new Message instance.
 *
 * @property title The title of the modal form.
 * @property message The message field of the modal form.
 */
class Message : ModalForm() {
	override var title: Key = Modal.Message.title

	val message = paragraphText {
		label = Modal.Message.label
		placeholder = Modal.Message.placeholder
		maxLength = 2000
	}
}