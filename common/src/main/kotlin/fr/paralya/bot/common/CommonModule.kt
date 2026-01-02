package fr.paralya.bot.common


/**
 * You must add this annotation to every Plugin using the following snippet:
 * ```kt
 *  @ApiVersion(CommonModule.API_VERSION)
 *  class MyPlugin : KordExPlugin()
 *  ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiVersion(val version: String)