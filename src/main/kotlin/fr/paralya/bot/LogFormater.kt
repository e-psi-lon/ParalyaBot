package fr.paralya.bot

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.LayoutBase
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.LinkedHashMap

class LogFormater : LayoutWrappingEncoder<ILoggingEvent>() {
	init {
		layout = object : LayoutBase<ILoggingEvent>() {
			private val format = "[%d{dd/MM/yyyy HH:mm:ss}] %highlight(%-5level) [%logger] - %msg%ex\n"
			private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
			private val date = Date()
			override fun doLayout(event: ILoggingEvent) = StringBuilder(format.length + 100).apply {
				append("[")
				append(dateFormat.format(Date(event.timeStamp)))
				append("] ")
				append(highlightLevel(event.level.toString()))
				append(" [")
				append(formatLoggerName(event))
				append("] - ")
				append(event.formattedMessage)
				event.throwableProxy?.let { append(formatException(it)) }
				append("\n")
			}.toString()


			private val loggerNameCache = object : LinkedHashMap<String, String>() {
				override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
					return size > 100 // Limit cache size to 1000 entries
				}
			}
			private fun formatLoggerName(log: ILoggingEvent): String {
				val loggerName = log.loggerName
				if (log.level.isGreaterOrEqual(Level.ERROR)) return loggerName

				return loggerNameCache.getOrPut(loggerName) {
					if (loggerName.length < 20) return@getOrPut loggerName
					val parts = loggerName.split(".")
					if (parts.size <= 2) loggerName else parts.last()
				}
			}

			private val levelMap = mapOf(
				"DEBUG" to "\u001B[34mDEBUG\u001B[0m",
				"INFO" to "\u001B[32mINFO\u001B[0m",
				"WARN" to "\u001B[33mWARN\u001B[0m",
				"ERROR" to "\u001B[31mERROR\u001B[0m",
				"CRITICAL" to "\u001B[41mCRITICAL\u001B[0m"
			)

			private fun highlightLevel(level: String): String {
				return levelMap[level] ?: level
			}

			/**
			 * Formats the exception stack trace to a human-readable string.
			 *
			 * @param throwableProxy The throwable proxy to format.
			 * @return The formatted stack trace.
			 */
			private fun formatException(throwableProxy: IThrowableProxy): String {
				val stackTrace = StringBuilder(256)
				stackTrace.append("\n\t").append(throwableProxy.className)
				if (throwableProxy.message != null) {
					stackTrace.append(": ").append(throwableProxy.message)
				}
				for (element in throwableProxy.stackTraceElementProxyArray) {
					stackTrace.append("\n\t\tat ").append(element)
				}
				var cause = throwableProxy.cause
				while (cause != null) {
					stackTrace.append("\nCaused by: ").append(cause.className)
					if (cause.message != null) {
						stackTrace.append(": ").append(cause.message)
					}
					for (element in cause.stackTraceElementProxyArray) {
						stackTrace.append("\n\t\tat ").append(element)
					}
					cause = cause.cause
				}

				return stackTrace.toString()
			}
		}
	}
}