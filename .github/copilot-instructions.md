# AI Coding Agent Instructions for ParalyaBot

**DISCLAIMER**: This file contains project-specific context for AI coding assistance. Only reference these instructions when working on ParalyaBot-specific features, architecture questions, or complex integrations. For simple syntax issues, debugging, or general programming questions, standard language knowledge is sufficient.

## Project Overview
ParalyaBot is a modular Discord bot built with **KordEx** (Kotlin Discord framework) using a multi-module Gradle architecture. The bot is designed for community management and Discord games for the Paralya server. Currently, the Werewolf (Loup-Garou) game is the only developed module, serving as a reference implementation for the planned plugin system.

### Current Architecture Status
**🔄 Transition Phase**: The project is currently between two architectural approaches:
- **Current**: Games are KordEx Extensions loaded directly in `ParalyaBot.kt`
- **Target**: Games will be KordEx Plugins with dynamic loading capabilities

**LG Module State**:
- ✅ `LG` class (Extension) - Currently active and loaded directly
- 🚧 `LgBotPlugin` class (Plugin) - Prepared but inactive, waiting for plugin system completion
- ✅ Plugin build configuration ready (`./gradlew :lg:exportToGames`)
- ✅ Configuration auto-registration prepared

**What This Means for Development**:
- **Bot Core** (`src/`): Manages extension loading and core services
- **Common Library** (`common/`): Stable infrastructure layer, no changes expected
- **Game Modules** (`lg/`): Follow Extension patterns now, Plugin patterns ready for future

## Architecture & Separation of Concerns

### Three-Layer Architecture

**1. Bot Core (`src/` directory) - Application Layer**
- **Entry Point**: `ParalyaBot.kt` with `main()` and `buildBot()` functions
- **Base Extension**: `extensions/base/Base.kt` - Core bot functionality (DM logging, game mode management)
- **Responsibilities**: 
  - Bot initialization and configuration
  - Extension registration and lifecycle management
  - Global bot services (error handling, presence, intents)
  - Core commands that aren't game-specific

**2. Common Library (`common/` directory) - Shared Infrastructure Layer**
- **Configuration**: `ConfigManager` with HOCON support, validation, and dynamic registration
- **Discord Utilities**: Webhook management, permissions, message utilities, role management, emoji handling
- **Game Infrastructure**: `GameRegistry` for runtime game mode management
- **Internationalization**: Type-safe translation system with hierarchical keys
- **Testing Infrastructure**: KoinTest extensions, MockK setup for comprehensive testing
- **Module Metadata**: `CommonModule` object for API versioning and compatibility checks
- **Emoji Support**: `EmojiUtils` for Discord emoji parsing and reaction management
- **Responsibilities**:
  - Provide reusable utilities for bot and game modules
  - Abstract Discord API complexities
  - Manage configuration lifecycle and validation
  - Enable consistent i18n patterns across modules
  - Ensure API compatibility between plugins and bot core

**3. Game Modules (`lg/` directory) - Domain Layer**
- **Game Logic**: Complete game implementations as KordEx Extensions
- **Game Data**: Domain-specific data models with cache patterns
- **Game Configuration**: Module-specific configuration classes extending `ValidatedConfig`
- **Plugin Architecture**: Each game has both Extension (current) and Plugin (future) classes
- **Responsibilities**:
  - Implement specific game rules and mechanics
  - Manage game state through DataCache patterns
  - Provide game-specific commands and event handlers
  - Handle game-specific Discord integrations (channels, roles, permissions)

### Module Dependencies
- **Root module** depends on: `common`, all game modules (currently `lg`)
- **Common module** has no internal dependencies (pure infrastructure)
- **Game modules** depend only on: `common` (never on other games or root)
- **Type-safe accessors**: Use `projects.common`, `projects.lg` in build files

## Implementation Patterns

### Bot Core Extension Architecture
The main bot uses KordEx Extensions for modular functionality:

```kotlin
// In ParalyaBot.kt - Bot initialization and DI setup
suspend fun buildBot(args: Array<String>): ExtensibleBot {
    val bot = ExtensibleBot(token) {
        extensions {
            add(::Base)  // Core bot functionality
            add(::LG)    // Game modules (temporary, will be plugin-loaded)
        }
        hooks {
            beforeKoinSetup {
                // Register infrastructure components
                loadModule {
                    singleOf(::ConfigManager) withOptions { named("configManager") }
                    singleOf(::GameRegistry) withOptions { named("registry") }
                }
                // Manual game config registration (temporary until plugin system)
                configManager.registerConfig<LgConfig>("lgConfig")
            }
        }
    }
}
```

**Base Extension Pattern**: Core bot functionality in `extensions/base/Base.kt`
- DM logging with webhook retransmission
- Game mode management commands (`/start_game`, `/stop_game`)
- Ready event handling and bot presence management

### Common Library Patterns

**Configuration Management**: HOCON-based with validation and dynamic registration
```kotlin
// Registering game configs dynamically (prepared for plugin system)
val configManager by inject<ConfigManager>()
configManager.registerConfig<LgConfig>("lgConfig")

// Configuration structure: bot { token, admins }, games { lg { rolesCategory, mainCategory } }
// Validation with Konform constraints on all config classes
```

**Discord Utilities Pattern**: Abstracted Discord API interactions
```kotlin
// Webhook management with asset integration
suspend fun getWebhook(channel: Snowflake, bot: ExtensibleBot, name: String): Webhook
suspend fun sendAsWebhook(bot: ExtensibleBot, channelId: Snowflake, username: String, avatar: String?, name: String, builder: WebhookMessageCreateBuilder.() -> Unit)

// Permission utilities for dynamic channel management
suspend fun setChannelPermissions(channel: TopGuildChannel, permissions: List<PermissionOverwrite>)

// Emoji utilities for Discord interactions
fun parseEmoji(emoji: String): ReactionEmoji  // Parse unicode/custom emojis
suspend fun Message.addReactions(emojis: List<String>)  // Add multiple reactions with rate limiting
fun ReactionEmoji.format(): String  // Format emojis for display
```

**Game Registry Pattern**: Runtime game mode management
```kotlin
class GameRegistry : KordExKoinComponent {
    fun registerGameMode(key: Key, gameMode: String)  // Register game for presence
    fun getGameMode(value: String): Pair<Key, String>  // Get translated game mode
    fun unloadGameMode(value: String)  // Remove game (for plugin system)
}

// Presence management extension
fun PresenceBuilder.gameMode(gameMode: Pair<Key, String>)
```

**Module Versioning Pattern**: API compatibility management (prepared for plugin system)
```kotlin
object CommonModule {
    const val API_VERSION = "1.0.0"         // Current API version
    const val MIN_COMPATIBLE_VERSION = "1.0.0"  // Minimum compatible version
}
// Will be used by plugins to ensure compatibility with bot core and common library
// Enables safe plugin loading and prevents version conflicts
```

### Game Module Patterns

**Extension-Based Architecture**: Each game is a complete KordEx Extension
```kotlin
class LG : Extension() {
    override val name = "LG"
    val botCache = kord.cache  // Access to KordEx cache
    val voteManager = VoteManager(botCache)  // Game-specific managers
    
    override suspend fun setup() {
        // Register with GameRegistry
        gameRegistry.registerGameMode(I18n.GameMode.lg, "lg")
        // Register data descriptions for caching
        kord.cache.register(GameData.description, VoteData.description)
        // Set up commands with domain organization
        publicSlashCommand { registerVotingCommands(); registerDayCycleCommands() }
    }
}
```

**Data Cache Pattern**: Immutable data with cache extension functions
```kotlin
// Data class with cache description
data class GameData(val state: LGState, val dayCount: Int, /* ... */) {
    companion object { val description = description<GameData, Snowflake>(GameData::id) }
    fun nextDay() = copy(state = LGState.DAY, dayCount = dayCount + 1)  // Immutable updates
}

// Cache extension functions for data access
suspend fun DataCache.getGameData() = query<GameData>().singleOrNull() ?: GameData()
suspend fun DataCache.updateGameData(modifier: (GameData) -> GameData) { 
    put(modifier(getGameData())) 
}
```

**Command Organization Pattern**: Domain-based command grouping
```kotlin
// Commands organized by domain with extension functions
suspend fun PublicSlashCommand<*, *>.registerVotingCommands() {
    ephemeralSubCommand(::VoteArguments) {
        name = Lg.Vote.Village.Command.name
        action { /* voting logic using cache extensions */ }
    }
}

suspend fun PublicSlashCommand<*, *>.registerDayCycleCommands() {
    ephemeralSubCommand { /* day/night cycle management */ }
}
```

## Plugin System Architecture (In Development)

### Current State vs Future Architecture
**Current Implementation** : 
- LG module loaded directly in `ParalyaBot.kt` via `add(::LG)`
- Manual config registration: `configManager.registerConfig<LgConfig>("lgConfig")`
- Game modules are Extensions, not Plugins

**Future Plugin Architecture**:
- `LgBotPlugin` class ready but unused (extends `KordExPlugin`)
- Plugin will auto-register configs and extensions
- Dynamic loading/unloading via `games/` directory
- `./gradlew :lg:exportToGames` builds plugin ZIP files

**Plugin Class Pattern** (prepared but inactive):
```kotlin
class LgBotPlugin : KordExPlugin() {
    override suspend fun setup() {
        // Auto-register configuration
        val configManager by inject<ConfigManager>()
        configManager.registerConfig<LgConfig>("lgConfig")
        // Auto-register extension
        extension(::LG)
    }
}
```

**Note**: The plugin system is KordEx-based but still in development. Current architecture bridges Extension and Plugin patterns.

## Development Workflow

### Build Commands
```bash
./gradlew build                    # Build all modules
./gradlew :lg:exportToGames       # Export LG module to games/ directory
./gradlew run --args="--dev"      # Run in development mode
```

### Testing
- Uses JUnit 5, Koin for DI (including in tests), MockK for mocking
- Test files follow patterns: `ModuleNameTest.kt` in `src/test/kotlin/`
- ByteBuddy agent configured for enhanced mocking capabilities

### Key Dependencies
- **KordEx**: Discord bot framework with i18n, commands, extensions
- **Kord**: Low-level Discord API wrapper  
- **Koin**: Dependency injection (used throughout the application and in testing)
- **Konform**: Configuration validation
- **Logback**: Logging with KotlinLogging facade

## Internationalization (i18n)

### Translation Structure
- **Generated Classes**: Type-safe translation classes: `fr.paralya.bot.I18n`, `fr.paralya.bot.lg.I18n`
- **Translation Files**: `src/main/resources/translations/{bundle}/strings.properties`
- **Default Locale**: French (`SupportedLocales.FRENCH`)
- **Bundle Organization**: Each module has its own translation bundle

**Bundle Structure**:
- Root: `paralyabot` bundle in `src/main/resources/translations/`
- Common: `paralyabot-common` bundle in `common/src/main/resources/translations/`
- LG Game: `paralyabot-lg` bundle in `lg/src/main/resources/translations/`

**Usage Patterns**:
```kotlin
// Static command names/descriptions
name = Lg.Vote.Village.Command.name
description = Lg.Vote.Village.Command.description

// Dynamic content
content = Messages.Error.channelNotFound.translateWithContext(channelType)
respond { content = I18n.StartGame.Response.success.translateWithContext(arguments.game) }
```

**Key Structure Examples**:
```properties
# Hierarchical structure with dots for nested properties
start-game.command.name=start_game
start-game.response.success=Le jeu {0} a été sélectionné.
messages.error.only-in-guild=Vous ne pouvez exécuter cette commande que sur un serveur.
```

## Game Development Patterns

### Werewolf Game Architecture (Reference Implementation)
The LG module serves as a reference implementation showcasing game development patterns:

**State Management**: 
- Immutable `GameData` with phase transitions (DAY/NIGHT cycles)
- Cache extension functions: `botCache.getGameData()`, `botCache.updateGameData { ... }`
- State enum: `LGState.DAY`, `LGState.NIGHT`

**Voting System**: 
- Centralized `VoteManager` handling village/werewolf votes and special roles (Corbeau/Raven)
- Immutable `VoteData` with cache patterns
- Vote state tracking and resolution

**Channel Management**: 
- Dynamic channel registration in `GameData.channels` map
- Permission management for different game phases
- Interview channels stored in `GameData.interviews` list

**Role System**: 
- Special abilities handled through game state and event listeners
- Role-specific behaviors in `Listeners.kt`

**Anonymous Messaging**: 
- Webhook system with rotating profile pictures for anonymity
- Asset-based avatars: `wolf_variant_1.webp`, `wolf_variant_2.webp`, `black_bird.webp`

### Discord Integration Patterns

**Common Library Utilities** (available to all games):
- **Permissions**: Dynamic channel permissions via `setChannelPermissions()`
- **Role Management**: Admin validation, role checking utilities
- **Message Utilities**: Webhook creation, message retransmission
- **Asset Management**: `getAsset()` function for game assets
- **Emoji Management**: `parseEmoji()`, `Message.addReactions()`, emoji formatting utilities

**Game-Specific Integration Examples** (from LG module):
- **Anonymous Communication**: Games can implement anonymous messaging using `sendAsWebhook()`
- **Event Handling**: Games register listeners for Discord events (`MessageCreateEvent`, etc.)
- **Channel Permissions**: Dynamic control of channel access during game phases
- **Role Assignment**: Game-specific role management for alive/dead players

### Data Flow Architecture
1. **Bot Layer**: Core commands modify presence, handle DM logging
2. **Game Layer**: Game commands modify state through DataCache, event listeners react to Discord events
3. **Common Layer**: Utilities handle Discord API interactions (permissions, webhooks, message handling)
4. **Configuration Layer**: Validates and provides channel/role mappings from HOCON config

## Debugging & Monitoring

### Logging
Use `KotlinLogging.logger` with contextual information:
```kotlin
val logger = KotlinLogging.logger("ComponentName")
logger.info { "Action completed: $details" }
```

**Custom Log Formatter**: The bot uses a custom `LogFormatter` class with advanced features:
- **Color-coded log levels**: DEBUG (blue), INFO (green), WARN (yellow), ERROR (red), CRITICAL (red background)
- **Intelligent logger name shortening**: Truncates long package names while preserving full names for errors
- **Enhanced exception formatting**: Detailed stack traces with cause chain formatting
- **Performance optimizations**: Logger name caching to reduce processing overhead
- **Configurable log levels**: Default INFO level, DEBUG available for development

### Error Handling
Bot has centralized error responses with i18n support. Commands should validate state before execution and provide meaningful error messages.

### Game State Inspection
Access game data through cache extensions:
- **LG Game Example**: `botCache.getGameData()`, `voteManager.getCurrentVote()`
- **General Pattern**: Custom cache extension functions for each game's data models
- **Cache Registration**: Remember to register data descriptions: `kord.cache.register(GameData.description)`

## Development Workflow

### Build System
**Root Module** (`build.gradle.kts`):
- Main application with shadow JAR for deployment
- Depends on all submodules: `implementation(projects.common)`, `implementation(projects.lg)`
- KordEx configuration with bot settings and i18n generation

**Module Dependencies**:
- All modules except `common` depend on `common`
- Games modules are isolated from each other
- Type-safe project accessors prevent circular dependencies
- **Plugin Optimization**: Uses `compileOnly` dependencies to significantly reduce plugin JAR sizes
- **Testing Dependencies**: MockK and Koin test dependencies properly scoped to test configurations

### Build Commands
```bash
./gradlew build                    # Build all modules
./gradlew shadowJar               # Create deployable JAR
./gradlew :lg:exportToGames       # Export LG module as plugin ZIP
./gradlew run --args="--dev"      # Run in development mode
```

### Testing Architecture
- **Framework**: JUnit 5 with KotlinTest assertions
- **Dependency Injection**: Koin for DI (including in tests with `KoinTest`)
- **Mocking**: MockK for mocking, ByteBuddy agent for enhanced mocking capabilities
- **Test Structure**: `src/test/kotlin/` with pattern `ModuleNameTest.kt`
- **Game Logic Testing**: LG module includes comprehensive tests for voting (`VoteManagerTest`), day/night cycles (`DayCycleTest`), and special roles (`SpecialRolesTest`)

**Test Example** (from ConfigManagerTest):
```kotlin
class ConfigManagerTest : KoinTest {
    @BeforeEach fun setup() { startKoin { } }
    @AfterEach fun tearDown() { stopKoin(); unmockkAll() }
    // Tests use dependency injection and mocking patterns
}
```

### Key Dependencies
- **KordEx**: Discord bot framework with i18n, commands, extensions
- **Kord**: Low-level Discord API wrapper  
- **Koin**: Dependency injection (used throughout application and testing)
- **Konform**: Configuration validation with custom constraints
- **TypeSafe Config**: HOCON configuration parsing
- **Logback**: Logging with KotlinLogging facade

## Common Pitfalls & Best Practices
- **Cache Management**: Always register data descriptions before use: `kord.cache.register(GameData.description)`
- **Async Operations**: Always use `suspend` functions for Discord operations
- **Configuration**: Validate configuration with Konform constraints, use `ValidatedConfig` interface
- **Translations**: Use type-safe translation keys (`I18n.Key.name`), never hardcoded strings
- **Guild Context**: Handle guild-only commands with null checks: `if (guild == null) return@action`
- **Admin Commands**: Use `adminOnly { }` extension from common for permission validation
- **Extension Functions**: Organize commands by domain using extension functions on command builders
- **Immutable State**: Prefer immutable data classes with copy functions for game state
- **Module Isolation**: Games should never depend on other games, only on `common`
- **Dependency Scoping**: Use `compileOnly` for plugin dependencies to reduce JAR size
- **Testing Setup**: Always clean up Koin and MockK in `@AfterEach`: `stopKoin(); unmockkAll()`
- **Emoji Handling**: Use `parseEmoji()` for consistent emoji parsing, `addReactions()` with rate limiting
- **Logging Levels**: Use appropriate log levels (INFO for production, DEBUG for development)
- **Version Compatibility**: Check `CommonModule.API_VERSION` for plugin compatibility (prepared for future plugin system)