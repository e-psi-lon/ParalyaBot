package fr.paralya.bot.common

/**
 * Common module metadata, used to ensure correct versioning and compatibility
 * with `compileOnly` dependencies.
 */
object CommonModule {
    const val API_VERSION = "1.0.0"
    const val MIN_COMPATIBLE_VERSION = "1.0.0"
}


/**
 * You must add this annotation to every Plugin using the following snippet:
 * ```kt
 *  @ApiVersion(CommonModule.API_VERSION)
 *  class MyPlugin : KordExPlugin()
 *  ```
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiVersion(val version: String)