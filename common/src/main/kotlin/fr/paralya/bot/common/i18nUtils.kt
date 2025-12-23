package fr.paralya.bot.common

import dev.kordex.core.types.TranslatableContext
import dev.kordex.i18n.Key

/**
 * Translates a key with the locale of the current translation context.
 *
 * @param replacements Optional replacements to include in the translation.
 * @return The translated string.
 */
context(ctx: TranslatableContext)
suspend fun Key.contextTranslate(vararg replacements: Any?) =
    translateLocale(ctx.getLocale(), *replacements)

/**
 * Translates a key with the locale of the current translation context.
 *
 * @param replacements Optional replacements array to include in the translation.
 * @return The translated string.
 */
context(ctx: TranslatableContext)
suspend fun Key.contextTranslateArray(replacements: Array<Any?>) =
    translateArrayLocale(ctx.getLocale(), replacements)

/**
 * Translates a key with the locale of the current translation context.
 *
 * @param replacements Optional named replacements to include in the translation.
 * @return The translated string.
 */
context(ctx: TranslatableContext)
suspend fun Key.contextTranslateNamed(vararg replacements: Pair<String, Any?>) =
    translateNamedLocale(ctx.getLocale(), *replacements)
