package fr.paralya.bot.common

@RequiresOptIn(
    message = "This is internal bot infrastructure. Do not call from plugins.",
    level = RequiresOptIn.Level.ERROR
)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class InternalBotApi
