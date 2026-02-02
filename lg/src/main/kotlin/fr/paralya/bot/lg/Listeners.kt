package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.channel.TopGuildChannelBehavior
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.*
import dev.kordex.core.checks.inChannel
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
		check { inChannel(LgChannelType.LOUPS_CHAT.toId()) }
		action { relayService.onMessageSent(WEBHOOK_PF_NAME, LgChannelType.PETITE_FILLE.toId(), true) }
	}

	event<MessageCreateEvent> {
		check { inChannel(LgChannelType.DATE_MYSTERE.toId()) }
		action { relayService.onMessageSent(WEBHOOK_CUPIDON_NAME, LgChannelType.CUPIDON.toId(), false) }
	}

	event<MessageCreateEvent> {
		check {
			inChannel(LgChannelType.INTERVIEW.toId())
			passIf { event.message.author?.id in botCache.getInterviews() }
		}
		action {
			botCache.removeInterview(event.message.author!!.id)
			(event.message.channel as TopGuildChannelBehavior)
				.removeMemberPermission(event.message.author!!.id, Permission.SendMessages)
		}
	}

	event<MessageCreateEvent> {
		check { inChannel(LgChannelType.SUJETS.toId()) }
		action {
			val reasonText = I18n.System.Topics.creation.contextTranslate()
			(event.message.channel as TextChannel).startPublicThreadWithMessage(event.message.id, event.message.content.truncate(100)) {
				reason = reasonText
			}
			event.message.addReactions(
				"🟢".toReaction(),
				"🤔".toReaction(),
				"🔴".toReaction()
			)
		}
	}

	event<MessageUpdateEvent> {
		check { inChannel(LgChannelType.LOUPS_CHAT.toId()) }
		action { relayService.onMessageUpdate(WEBHOOK_PF_NAME, LgChannelType.PETITE_FILLE.toId(), true) }
	}

	event<MessageUpdateEvent> {
		check { inChannel(LgChannelType.DATE_MYSTERE.toId()) }
		action { relayService.onMessageUpdate(WEBHOOK_CUPIDON_NAME, LgChannelType.CUPIDON.toId(), false) }
	}

	event<MessageDeleteEvent> {
		check { inChannel(LgChannelType.LOUPS_CHAT.toId()) }
		action { relayService.onMessageDelete(WEBHOOK_PF_NAME, LgChannelType.PETITE_FILLE.toId()) }
	}

	event<MessageDeleteEvent> {
		check { inChannel(LgChannelType.DATE_MYSTERE.toId()) }
		action { relayService.onMessageDelete(WEBHOOK_CUPIDON_NAME, LgChannelType.CUPIDON.toId()) }
	}

	event<ReactionAddEvent> {
		check { inChannel(LgChannelType.LOUPS_CHAT.toId()) }
		action { relayService.onReactionAdd(WEBHOOK_PF_NAME,
			LgChannelType.PETITE_FILLE.toId(), isAnonymous = true
		) }
	}

	event<ReactionAddEvent> {
		check { inChannel(LgChannelType.DATE_MYSTERE.toId()) }
		action { relayService.onReactionAdd(WEBHOOK_CUPIDON_NAME,
			LgChannelType.CUPIDON.toId(), isAnonymous = false
		) }
	}

	event<ReactionRemoveEvent> {
		check { inChannel(LgChannelType.LOUPS_CHAT.toId()) }
		action { relayService.onReactionRemove(WEBHOOK_PF_NAME,
			LgChannelType.PETITE_FILLE.toId(), isAnonymous = true
		) }
	}
	event<ReactionRemoveEvent> {
		check { inChannel(LgChannelType.DATE_MYSTERE.toId()) }
		action { relayService.onReactionRemove(WEBHOOK_CUPIDON_NAME,
			LgChannelType.CUPIDON.toId(), isAnonymous = false
		) }
	}

	event<ReadyEvent> {
		action {
			handleReadyEvent(event.guilds, botConfig, lgConfig)
		}
	}

	event<PluginReadyEvent> {
		check { passIf { pluginRef.pluginId == event.pluginId } }
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
