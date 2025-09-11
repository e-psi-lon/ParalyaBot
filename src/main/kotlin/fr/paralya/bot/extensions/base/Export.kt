package fr.paralya.bot.extensions.base

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.entity.Embed
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


suspend fun FollowupMessageCreateBuilder.addStringExport(channel: MessageChannelBehavior, guild: GuildBehavior?, messages: Flow<Message>, anonymous: Boolean) {
    val export = buildString {
        append("==============================================================\n")
        append("Serveur: ${guild?.asGuildOrNull()?.name ?: "Pas de nom de serveur"}")
        append("\nSalon: ${guild?.getChannel(channel.id)?.name ?: "Pas de nom de salon"}\n")
        append("==============================================================\n\n")
        var counter = 0
        var lastAuthor: String? = null
        var identity = false
        messages.collect { message ->
            val localDateTime = message.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
            val formattedDate = "%02d/%02d/%04d %02d:%02d".format(
                localDateTime.dayOfMonth,
                localDateTime.monthNumber,
                localDateTime.year,
                localDateTime.hour,
                localDateTime.minute
            )
            val author = if (anonymous) {
                if (identity) "Anonyme 1" else "Anonyme 2"
            } else {
                message.author?.tag ?: "Inconnu"
            }
            if ((message.author?.tag ?: "Inconnu") != lastAuthor) {
                append("[$formattedDate] $author\n")
                lastAuthor = message.author?.tag ?: "Inconnu"
                identity = !identity
            }
            append(message.content)
            appendCollection(message.attachments, "Pièce(s) jointe(s)") { it.url }
            appendCollection(message.embeds, "Embed(s)") { formatEmbed(it) }
            append("\n")
            counter++
        }
        append("==============================================================\n")
        append("$counter message(s) exportés")
        append("\n==============================================================\n")
    }
    addFile("export.txt", ChannelProvider { ByteReadChannel(export) })
}

private fun <T>StringBuilder.appendCollection(collection: Collection<T>, name: String, transformer: (T) -> String = { it.toString() }) {
    if (collection.isNotEmpty()) {
        append("{$name}\n")
        collection.forEach {
            append("${transformer(it)}\n")
        }
    }
}

private fun formatEmbed(embed: Embed): String {
    return buildString {
        append("Embed:\n")
        embed.title?.let { append("Titre: $it\n") }
        embed.description?.let { append("Déscription: $it\n") }
        if (embed.fields.isNotEmpty()) {
            append("Champs:\n")
            embed.fields.forEach { field ->
                append(" - ${field.name}: ${field.value}\n")
            }
        }
        embed.url?.let { append("URL: $it\n") }
        embed.color?.let { append("Couleur: #${it.rgb.toString(16).padStart(6, '0')}\n") }
        embed.footer?.let { footer ->
            append("Footer: ${footer.text}\n")
            footer.iconUrl?.let { append("URL de l'icône du footer: $it\n") }
        }
        embed.image?.let { image ->
            append("URL de l'image: ${image.url}\n")
        }
        embed.thumbnail?.let { thumbnail ->
            append("URL de la miniature: ${thumbnail.url}\n")
        }
        embed.author?.let { author ->
            append("Auteur: ${author.name}\n")
            author.url?.let { append("URL de l'auteur: $it\n") }
            author.iconUrl?.let { append("URL de l'icône de l'auteur: $it\n") }
        }
        append("Fin de l'Embed\n")
    }
}

suspend fun FollowupMessageCreateBuilder.addHtmlExport(channel: MessageChannelBehavior, guild: GuildBehavior?, messages: Flow<Message>, arguments: Base.ExportArguments) {}

private fun formatDate(snowflake: Snowflake): String {
    val dateTime = snowflake.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d %02d:%02d".format(
        dateTime.dayOfMonth, dateTime.monthNumber, dateTime.year, dateTime.hour, dateTime.minute
    )
}