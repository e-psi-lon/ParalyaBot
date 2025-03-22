package fr.paralya.bot.common

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Webhook
import dev.kord.rest.Image
import dev.kordex.core.ExtensibleBot
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

suspend fun getWebhook(channel: Snowflake, bot: ExtensibleBot, name: String): Webhook {
    val webhooks = bot.kordRef.rest.webhook.getChannelWebhooks(channel)
    return (webhooks.firstOrNull { it.name == name } ?: bot.kordRef.rest.webhook.createWebhook(
        channel,
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

fun ULong.toSnowflake() = Snowflake(this)
fun Long.toSnowflake() = Snowflake(this)
fun Int.toSnowflake() = Snowflake(this.toLong())