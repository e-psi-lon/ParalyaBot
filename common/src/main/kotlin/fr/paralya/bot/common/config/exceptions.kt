package fr.paralya.bot.common.config

import fr.paralya.bot.common.ParalyaBotException
import io.konform.validation.ValidationError

open class BotConfigException(message: String) : ParalyaBotException(message)

class InvalidConfigException(className: String?, val errors: List<ValidationError>) : BotConfigException(
    "Configuration for $className is invalid due to the following errors: "
)
class MissingConfigException(path: String) : BotConfigException("No configuration found for path: $path")
