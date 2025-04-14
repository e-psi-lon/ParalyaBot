package fr.paralya.bot

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.LayoutBase
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import java.text.SimpleDateFormat
import java.util.*

class LogFormater : LayoutWrappingEncoder<ILoggingEvent>() {
	init {
		layout = object : LayoutBase<ILoggingEvent>() {
			private val format = "[%d{dd/MM/yyyy HH:mm:ss}] %highlight(%-5level) - %msg%ex\n"

			override fun doLayout(event: ILoggingEvent): String {
				return format
					.replace("%highlight(%-5level)", highlightLevel(event.level.toString()))
					.replace("%msg", event.formattedMessage)
					.replace("%logger", event.loggerName)
					.replace(
						"%d{dd/MM/yyyy HH:mm:ss}",
						SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date(event.timeStamp))
					).replace("%ex", event.throwableProxy?.let { formatException(it) } ?: "")
			}

			private fun highlightLevel(level: String): String {
				return when (level) {
					"DEBUG" -> "\u001B[34m$level\u001B[0m"  // Blue
					"INFO" -> "\u001B[32m$level\u001B[0m"   // Green
					"WARN" -> "\u001B[33m$level\u001B[0m"   // Yellow
					"ERROR" -> "\u001B[31m$level\u001B[0m"  // Red
					"CRITICAL" -> "\u001B[41m$level\u001B[0m" // Red background
					else -> level
				}
			}

			private fun formatException(throwableProxy: IThrowableProxy): String {
				val stackTrace = StringBuilder()
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