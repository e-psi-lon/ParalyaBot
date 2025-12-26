package fr.paralya.bot.common.config

import io.konform.validation.Validation
import io.konform.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomValidationRulesTest {

	companion object {
		// Fixed Discord snowflakes for testing
		private val snowflake = 661720242585600000UL  // 01/01/2020
	}
	data class StringConfig(val value: String = "") : ValidatedConfig {
		override fun validate(): ValidationResult<ValidatedConfig> {
			return Validation<StringConfig> {
				StringConfig::value { defined() }
			}(this)
		}
	}

	data class ListConfig(val items: List<String> = emptyList()) : ValidatedConfig {
		override fun validate(): ValidationResult<ValidatedConfig> {
			return Validation<ListConfig> {
				ListConfig::items { defined() }
			}(this)
		}
	}

	data class SnowflakeConfig(val id: ULong = 0UL) : ValidatedConfig {
		override fun validate(): ValidationResult<ValidatedConfig> {
			return Validation<SnowflakeConfig> {
				SnowflakeConfig::id { appearsToBeSnowflake("Test ID") }
			}(this)
		}
	}

	data class MultiFieldConfig(
		val value: String = "",
		val count: Int = 0,
		val items: List<String> = emptyList(),
		val snowflakeId: ULong = 0UL
	) : ValidatedConfig {
		override fun validate(): ValidationResult<ValidatedConfig> {
			return Validation<MultiFieldConfig> {
				MultiFieldConfig::value { defined() }
				MultiFieldConfig::count { constrain("count must be positive") { it > 0 } }
				MultiFieldConfig::items { defined() }
				MultiFieldConfig::snowflakeId { appearsToBeSnowflake("Test Snowflake ID") }
			}(this)
		}
	}


	@Test
	fun `defined constraint works for non-empty string`() {
		val config = StringConfig(value = "test")
		assertTrue(config.validate().isValid)
	}

	@Test
	fun `defined constraint fails for empty string`() {
		val config = StringConfig(value = "")
		assertFalse(config.validate().isValid)
	}

	@Test
	fun `defined constraint works for non-empty list`() {
		val config = ListConfig(items = listOf("a", "b"))
		assertTrue(config.validate().isValid)
	}

	@Test
	fun `defined constraint fails for empty list`() {
		val config = ListConfig(items = emptyList())
		assertFalse(config.validate().isValid)
	}


	@Test
	fun `appearsToBeSnowflake validates valid Discord snowflake`() {
		val config = SnowflakeConfig(id = snowflake)
		assertTrue(config.validate().isValid)
	}

	@Test
	fun `appearsToBeSnowflake rejects invalid snowflake`() {
		val config = SnowflakeConfig(id = 100UL)
		assertFalse(config.validate().isValid)
	}

	@Test
	fun `validation with all valid values passes`() {
		val config = MultiFieldConfig(
			value = "test",
			count = 10,
			items = listOf("item1", "item2"),
			snowflakeId = snowflake
		)
		assertTrue(config.validate().isValid)
	}

	@Test
	fun `validation with multiple failures reports all errors`() {
		val config = MultiFieldConfig(
			value = "",
			count = -5,
			items = emptyList(),
			snowflakeId = 100UL
		)
		val result = config.validate()
		assertFalse(result.isValid)
		assertTrue(result.errors.size >= 3)
	}
}

