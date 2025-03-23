package fr.paralya.bot.lg

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.components.forms.ModalForm
import fr.paralya.bot.lg.i18n.Translations.Lg

enum class LGState {
	DAY,
	NIGHT;

	fun next(): LGState {
		return when (this) {
			DAY -> NIGHT
			NIGHT -> DAY
		}
	}
}


suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerDayCycleCommands(extension: LG) {
	ephemeralSubCommand(::DayArguments) {
		name = Lg.Day.Command.name
		description = Lg.Day.Command.description
		action {
			adminOnly {
				val force = arguments.force
				val kill = arguments.kill

			}
		}
	}
}

private class DayArguments : Arguments() {
	val force by defaultingBoolean {
		name = Lg.Day.Argument.Force.name
		description = Lg.Day.Argument.Force.description
		defaultValue = false

	}
	val kill by defaultingBoolean {
		name = Lg.Day.Argument.Kill.name
		description = Lg.Day.Argument.Kill.description
		defaultValue = true
	}
}

