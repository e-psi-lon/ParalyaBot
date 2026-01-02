package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.TopGuildChannelBehavior
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.*
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.getCategory
import dev.kordex.core.utils.toReaction
import fr.paralya.bot.common.*
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.common.config.BotConfig
import fr.paralya.bot.common.plugins.PluginReadyEvent
import fr.paralya.bot.lg.data.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.koin.core.component.inject


private val requiredRoleChannels = listOf(
	LgChannelType.LOUPS_CHAT,
	LgChannelType.LOUPS_VOTE,
	LgChannelType.CORBEAU,
	LgChannelType.PETITE_FILLE,
	LgChannelType.CUPIDON,
	LgChannelType.DATE_MYSTERE
)

private val requiredMainChannels = listOf(
	LgChannelType.ANNONCES_VILLAGE,
	LgChannelType.VOTES,
	LgChannelType.INTERVIEW,
	LgChannelType.SUJETS,
	LgChannelType.VILLAGE,
)

private const val WEBHOOK_PF_NAME = "PF"
private const val WEBHOOK_CUPIDON_NAME = "Cupidon"

/**
 * Registers all event listeners for the Werewolf (Loup-Garou) game.
 *
 * This function sets up listeners for different Discord events like message creation,
 * message updates, message deletion, reactions, and bot ready event.
 */
suspend fun LG.registerListeners() {
	val lgConfig by inject<LgConfig>()
	val botConfig = inject<ConfigManager>().value.botConfig
	val relayService by inject<LgRelayService>()

	event<MessageCreateEvent> {
		action {
			val message = event.message
			when (message.channelId) {
				LgChannelType.LOUPS_CHAT.toId() ->
					relayService.onMessageSent(WEBHOOK_PF_NAME,
						LgChannelType.PETITE_FILLE.toId()!!, false
					)
				LgChannelType.INTERVIEW.toId() -> {
					if (message.author?.id in botCache.getInterviews()) {
						botCache.removeInterview(message.author!!.id)
						(message.channel as TopGuildChannelBehavior)
							.removeMemberPermission(message.author!!.id, Permission.SendMessages)
					}
				}

				LgChannelType.DATE_MYSTERE.toId() ->
					relayService.onMessageSent(WEBHOOK_CUPIDON_NAME,
						LgChannelType.CUPIDON.toId()!!, false
					)

				LgChannelType.SUJETS.toId() -> {
					val reasonText = I18n.System.Topics.creation.contextTranslate()
					(message.channel as TextChannel).startPublicThreadWithMessage(message.id, message.content.truncate(100)) {
						reason = reasonText
					}
					message.addReactions(
						"🟢".toReaction(),
						"🤔".toReaction(),
						"🔴".toReaction()
					)
				}
			}
		}

	}

	event<MessageUpdateEvent> {
		action {
			when (event.message.channelId) {
				LgChannelType.LOUPS_CHAT.toId() ->
					relayService.onMessageUpdate(WEBHOOK_PF_NAME,
						LgChannelType.PETITE_FILLE.toId()!!, false
					)
				LgChannelType.DATE_MYSTERE.toId() ->
					relayService.onMessageUpdate(WEBHOOK_CUPIDON_NAME,
						LgChannelType.CUPIDON.toId()!!, false
					)
			}
		}
	}

	event<MessageDeleteEvent> {
		action {
			when (event.message?.channelId) {
				LgChannelType.LOUPS_CHAT.toId() ->
					relayService.onMessageDelete(WEBHOOK_PF_NAME, LgChannelType.PETITE_FILLE.toId())
				LgChannelType.DATE_MYSTERE.toId() ->
					relayService.onMessageDelete(WEBHOOK_CUPIDON_NAME, LgChannelType.CUPIDON.toId())
				else -> return@action
			}
		}
	}

	event<ReactionAddEvent> {
		action {
			when (event.message.channelId) {
				LgChannelType.LOUPS_CHAT.toId() ->
					relayService.onReactionAdd(
						WEBHOOK_PF_NAME,
						LgChannelType.PETITE_FILLE.toId()!!, false
					)
				LgChannelType.DATE_MYSTERE.toId() ->
					relayService.onReactionAdd(
						WEBHOOK_CUPIDON_NAME,
						LgChannelType.CUPIDON.toId()!!, false
					)
				else -> return@action
			}
		}
	}
	event<ReactionRemoveEvent> {
		action {
			when (event.message.channelId) {
				LgChannelType.LOUPS_CHAT.toId() ->
					relayService.onReactionRemove(WEBHOOK_PF_NAME,
						LgChannelType.PETITE_FILLE.toId()!!, isAnonymous = false
					)
				LgChannelType.DATE_MYSTERE.toId() ->
					relayService.onReactionRemove(WEBHOOK_CUPIDON_NAME,
						LgChannelType.CUPIDON.toId()!!, isAnonymous = false
					)
				else -> return@action
			}
		}
	}

	event<ReadyEvent> {
		action {
			handleReadyEvent(event.guilds, botConfig, lgConfig)
		}
	}

	event<PluginReadyEvent> {
		action {
			if (pluginRef.pluginId == event.pluginId) handleReadyEvent(event.guilds, botConfig, lgConfig)
		}
	}
}

private suspend fun LG.handleReadyEvent(guilds: Set<GuildBehavior>, botConfig: BotConfig, lgConfig: LgConfig) {
	logger.debug { "Fetching channels from werewolf related categories" }
	val paralya = guilds.firstOrNull { it.id.value == botConfig.paralyaId }
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
