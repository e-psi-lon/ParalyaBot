package fr.paralya.bot.utils

import dev.kord.core.entity.Webhook
import dev.kord.rest.Image
import dev.kordex.core.ExtensibleBot
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

suspend fun getWebhook(channel: ULong, bot: ExtensibleBot, name: String): Webhook {
    val webhooks = bot.kordRef.rest.webhook.getChannelWebhooks(channel.toSnowflake())
    return (webhooks.firstOrNull { it.name == name } ?: bot.kordRef.rest.webhook.createWebhook(
        channel.toSnowflake(),
        name
    ) {
        val client = HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(true)
                }
            }
        }
        this.avatar = Image.fromUrl(client, getAssetLink("bot"))
        client.close()
    }).let {
        bot.kordRef.getWebhook(it.id)
    }
}

fun getAssetLink(name: String) =
    "https://raw.githubusercontent.com/e-psi-lon/ParalyaBot/main/src/main/resources/assets/$name.webp"

fun ULong.toSnowflake() = dev.kord.common.entity.Snowflake(this)
fun Long.toSnowflake() = dev.kord.common.entity.Snowflake(this)
fun Int.toSnowflake() = dev.kord.common.entity.Snowflake(this.toLong())