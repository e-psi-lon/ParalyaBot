package fr.paralya.bot.lg

import dev.kord.common.entity.*
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.*
import dev.kord.rest.builder.message.embed
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.getCategory
import fr.paralya.bot.common.*
import fr.paralya.bot.common.i18n.Translations.Common
import fr.paralya.bot.lg.data.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.inject


private val requiredRoleChannels = listOf(
	LgChannelType.LOUPS_CHAT,
	LgChannelType.LOUPS_VOTE,
	LgChannelType.CORBEAU,
	LgChannelType.PETITE_FILLE,
)

private val requiredMainChannels = listOf(
	LgChannelType.ANNONCES_VILLAGE,
	LgChannelType.VOTES,
	LgChannelType.INTERVIEW,
	LgChannelType.SUJETS,
	LgChannelType.VILLAGE,
)

/**
 * Registers all event listeners for the Werewolf (Loup-Garou) game.
 *
 * This function sets up listeners for different Discord events like message creation,
 * message updates, message deletion, reactions, and bot ready event.
 */
suspend fun LG.registerListeners() {
	val lgConfig by inject<LgConfig>()
	val botConfig = inject<ConfigManager>().value.botConfig
	val dmChannelId = botConfig.dmLogChannelId.snowflake

	event<MessageCreateEvent> {
		action {
			val message = event.message

			if (message.channelId == botCache.getChannelId(LgChannelType.INTERVIEW) && message.author?.id in botCache.getInterviews()) {
				botCache.removeInterview(message.author!!.id)
				(message.channel as TopGuildChannel).addOverwrite(
					PermissionOverwrite.forMember(
						message.author!!.id,
						denied = Permissions(Permission.SendMessages)
					)
				)
			} else if (
				message.channelId == botCache.getChannelId(LgChannelType.LOUPS_CHAT) && !message.author.isAdmin(botConfig) &&
				message.author?.isBot == false && message.author?.isSelf == false
			) {
				val cached = botCache.getLastWerewolfMessageSender().value
				logger.debug { "Message sender is ${message.author?.id?.value} and cache is $cached" }
				if (message.author?.id != botCache.getLastWerewolfMessageSender()) {
					botCache.setLastWerewolfMessageSender(message.author!!.id)
					botCache.updateProfilePicture()
				}
				val wolfName = if (botCache.getProfilePictureState()) "🐺 Anonyme" else "🐺Anonyme"
				val wolfAvatar = if (botCache.getProfilePictureState()) "wolf_variant_2" else "wolf_variant_1"
				logger.debug { "Avatar is $wolfAvatar and name is $wolfName" }
				sendAsWebhook(
					bot,
					botCache.getChannelId(LgChannelType.PETITE_FILLE)!!,
					wolfName,
					getAsset(wolfAvatar, this@LG.prefix),
					"PF"
				) {
					content = message.content

					if (message.referencedMessage != null) embed {
						title = Common.Transmission.Reference.title.translateWithContext()
						description = message.referencedMessage!!.content
					}
				}
			} else if (message.channelId == botCache.getChannelId(LgChannelType.SUJETS)) {
				return@action
			}
		}

	}

	event<MessageUpdateEvent> {
		action {
			val oldMessage = event.old?.let { getCorrespondingMessage(MessageChannelBehavior(dmChannelId, kord), it) }
			val webhook = getWebhook(botCache.getChannelId(LgChannelType.PETITE_FILLE)!!, bot, "PF")
			val newMessage = event.message.asMessage()
			if (oldMessage != null) {
				try {
					webhook.token?.let {
						webhook.getMessage(it, oldMessage.id).edit {
							content = newMessage.content
							if (newMessage.referencedMessage != null) {
								embed {
									title = Common.Transmission.Reference.title.translateWithContext()
									description = newMessage.referencedMessage!!.content
								}
							} else {
								embeds?.clear()
							}
						}
					}
				} catch (e: Exception) {
					val wolfName = if (botCache.getProfilePictureState()) "🐺 Anonyme" else "🐺Anonyme"
					val wolfAvatar = if (botCache.getProfilePictureState()) "wolf_variant_2" else "wolf_variant_1"
					sendAsWebhook(bot, botCache.getChannelId(LgChannelType.PETITE_FILLE)!!, wolfName, wolfAvatar, "PF") {
						content = newMessage.content
						embed {
							title = Common.Transmission.Reference.title.translateWithContext()
							description = newMessage.referencedMessage!!.content
						}
						embed {
							title = Common.Transmission.Update.title.translateWithContext()
							description = oldMessage.content
						}
					}
				}
			}
		}
	}

	event<MessageDeleteEvent> {
		action {
			val oldMessage =
				event.message?.let { getCorrespondingMessage(MessageChannelBehavior(dmChannelId, kord), it) }
			if (oldMessage != null && event.message?.channelId == botCache.getChannelId(LgChannelType.PETITE_FILLE)) {
				val webhook = getWebhook(botCache.getChannelId(LgChannelType.PETITE_FILLE)!!, bot, "PF")
				try {
					webhook.token?.let { webhook.deleteMessage(it, oldMessage.id) }
				} catch (e: Exception) {
					logger.error(e) { "Error while deleting message" }
				}
			}
		}
	}

	event<ReactionAddEvent> {
		action {

		}
	}
	event<ReactionRemoveEvent> {
		action { }
	}

	event<ReadyEvent> {
		action {
			logger.debug { "Fetching channels from werewolf related categories" }
			val paralya = event.guilds.firstOrNull { it.id.value == botConfig.paralyaId }
				?: throw IllegalStateException("Paralya guild not found")

			val rolesChannels = collectChannelsFromCategory(lgConfig.rolesCategory.snowflake, paralya)
			val mainChannels = collectChannelsFromCategory(lgConfig.mainCategory.snowflake, paralya)

			logger.debug { "Found ${rolesChannels.size} channels in the roles category" }
			logger.debug { "Found ${mainChannels.size} channels in the main category" }

			// If the required channels are not found, throw an exception
			requiredRoleChannels.forEach { channelName ->
				if (rolesChannels[channelName.name] == null) {
					throw IllegalStateException("Channel $channelName not found in the roles category")
				}
			}
			requiredMainChannels.forEach { channelName ->
				if (mainChannels[channelName.name] == null) {
					throw IllegalStateException("Channel $channelName not found in the main category")
				}
			}

			(rolesChannels + mainChannels).forEach { (name, id) ->
				botCache.registerChannel(name, id)
			}
		}
	}
}

/**
 * Collects channels from a specified category in a guild.
 *
 * This function retrieves all channels in the given category and maps them to a map of channel names to their IDs.
 * It filters out any channels that do not have a valid name or ID. And formats their name to SCREAMING_SNAKE_CASE.
 *
 * @param categoryId The ID of the category to collect channels from.
 * @param guild The guild to collect channels from.
 * @return A map of channel names with their IDs.
 */
private suspend fun collectChannelsFromCategory(categoryId: Snowflake, guild: GuildBehavior): Map<String, Snowflake> {
	return guild.channels
		.filter { it.getCategory()?.id == categoryId }
		.map { channel ->
			val channelName = channel.name
				.replace(Regex("[^A-Za-z0-9_-]"), "")
				.removePrefix("-")
				.replace("-", "_")
				.uppercase()

			channelName to channel.id
		}
		.toList()
		.toMap()
		.filterKeys { it.isNotBlank() && it != "_".repeat(it.length) }
}
