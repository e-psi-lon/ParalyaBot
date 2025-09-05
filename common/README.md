# ParalyaBot - Common Module

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

Core utilities and shared functionality for the ParalyaBot Discord bot.

## Overview

The Common module serves as the foundation for all other modules in the ParalyaBot project. 
It provides essential utilities, configuration management, and shared components that are used throughout the application.

## Features

- Configuration management with HOCON support and validation
- Discord utility functions for webhooks and permissions
- Command permission handling
- Internationalization support with structured key access
- Logging infrastructure
- Common data models and extensions

## Dependencies

- **Kord and KordEx**: Discord API wrapper and extension framework
- **Kotlin Serialization**: For configuration serialization
- **Logback**: For logging
- **Typesafe Config**: For HOCON configuration management
- **Koin**: For dependency injection

## Structure

```
common/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── fr/
│   │   │       └── paralya/
│   │   │           └── bot/
│   │   │               └── common/
│   │   │                   ├── config/                      # Configuration management
│   │   │                   │   ├── ConfigManager.kt         # Core configuration manager
│   │   │                   │   └── ValidatedConfig.kt       # Configuration validation
│   │   │                   ├── DiscordUtils.kt              # Discord utility functions
│   │   │                   └── I18n.kt                      # Generated internationalization (not present in the repo)
│   │   └── resources/
│   │       ├── assets/                                      # Bot assets/images
│   │       └── translations/
│   │           └── paralyabot-common/                       # Translation bundle
│   │               ├── strings_en.properties                # English translations
│   │               └── strings.properties                   # French translations (default)
│   └── test/
│       └── kotlin/                                          # Unit tests
└── build.gradle.kts                                         # Module build configuration
```

## Usage

### Configuration

The Common module provides a configuration system based on Typesafe Config (HOCON) with validation:

```kotlin
// Accessing the core bot configuration
val configManager = ConfigManager()

// Basic bot configuration access
val botToken = configManager.botConfig.token
val adminIds = configManager.botConfig.admins

// Registering a custom game configuration
@Serializable
data class MyGameConfig(
    var channelId: ULong = 0UL,
    var enabled: Boolean = false,
    var maxPlayers: Int = 10
) : ValidatedConfig {
    @Transient
    private val validator = Validation {
        MyGameConfig::channelId {
            appearsToBeSnowflake("Channel ID")
        }
        
        MyGameConfig::maxPlayers {
            minValue(2, "Maximum players must be at least 2")
            maxValue(30, "Maximum players cannot exceed 30")
        }
    }

    override fun validate(): ValidationResult<MyGameConfig> {
        return validator(this)
    }
}

// Register the configuration
configManager.registerConfig<MyGameConfig>("MyGameConfig")

// Retrieving a registered game configuration
val myGameConfig by inject<MyGameConfig>() // inject is only available in a KoinComponent context
println("Game enabled: ${myGameConfig.enabled}")
println("Maximum players: ${myGameConfig.maxPlayers}")
```

### Discord Utilities

Utility functions for common Discord operations:

```kotlin
// Get or create a webhook for a channel
val webhook = getWebhook(channelId, bot, "Webhook Name")

// Send a message using a webhook
sendAsWebhook(bot, channelId, "Custom Name", "https://example.com/avatar.png") {
    content = "Hello from webhook!"
    embed {
        title = "Important Announcement"
        description = "This is a webhook message with an embed!"
        color = Color(0x7289DA)
        footer {
            text = "Powered by ParalyaBot"
        }
        timestamp = Clock.System.now()
    }
}

// Managing channel permissions
val textChannel: TopGuildChannelBehavior // Obtained externally
// Get members with access to a channel
val members = textChannel.getMembersWithAccess()

// Filter members by role
val membersWithRole = members.filterByRole(roleId)

// Utility extensions
val snowflake = 123456789UL.snowflake
```

### Internationalization

The Common module provides a powerful internationalization system with type-safe access to translation keys:

#### Translation Files

Create translation files in `src/main/resources/translations/`:

```properties
# strings_en.properties
messages.error.channel_not_found=Channel {0} not found
messages.error.only_in_guild=This command can only be used in a guild
messages.success.game_started=Game started successfully in {0}

# strings.properties
messages.error.channel_not_found=Le salon {0} n'a pas été trouvé
messages.error.only_in_guild=Cette commande ne peut être utilisée que dans un serveur
messages.success.game_started=Partie démarrée avec succès dans {0}
```

#### Generated I18n Class

The KordEx plugin generates a type-safe I18n class from these files:

```kotlin
// Using the generated I18n class
// Simple translation
val errorMessage = I18n.Messages.Error.channelNotFoun

// Setting user language preference
event<MessageCreateEvent> {
    action {
        val userId = event.message.author?.id?.value ?: return@action
        respond {
            // Translation context is automatically set based on user locale
            content = I18n.Messages.Success.gameStarted.translateWithContext("game-channel") 
            
        }
    }
}
```

## Configuration Reference

The module uses a HOCON configuration file (`config.conf`):

```hocon
bot {
  token = ""                # Discord bot token (required)
  admins = []               # List of admin user IDs (required)
  dmLogChannelId = 0        # Channel ID for DM logging
  paralyaId = 0             # Paralya server ID
  defaultLocale = "fr"      # Default locale for translations
}

# Game-specific configurations
games {
  # Game configurations are defined under their lowercase name
  mygame {
    enabled = true
    channelId = 12345678901234567
    maxPlayers = 10
    # Other game-specific settings
  }
}
```

## Extension Development

### Initial setup

1. Create a new module directory and configure `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version KOTLIN_VERSION
    kotlin("plugin.serialization") version KOTLIN_VERSION
    id("dev.kordex.gradle.kordex")
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT" // Replace with your plugin version

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.common) // Reference to the Common module (if developing from this repo)
    implementation("fr.paralya.bot:common:$version") // Replace with your common module version (this module isn't yet published)
    // Add other dependencies as needed
}

kordex {
    plugins {
        id = "my-plugin-id" // Replace with your plugin ID
        version = getVersion()
        description = "My great plugin description" // Replace with your plugin description
        pluginClass = "fr.paralya.bot.my-game.MyGamePlugin" // Replace with your plugin class
    }
   
    i18n {
        classPackage = "fr.paralya.bot.my-game" // Replace with your plugin package
        className = "I18n"
        translationBundle = "paralyabot-my-game" // Replace with your translation bundle name
        publicVisibility = false
    }
}
```

2. Create the main plugin class:

```kotlin
@ApiVersion(CommonModule.VERSION) // This line is mandatory for the bot to load your plugin correctly
class MyGamePlugin : KordExPlugin() {
    override suspend fun setup() {
		// Register commands
		// Event listeners
		// And more
	}
}
```

### Adding New Configuration Types

1. Create a data class implementing `ValidatedConfig`:

```kotlin
@Serializable
data class MyCustomConfig(
    var option1: String = "default",
    var option2: Int = 0
) : ValidatedConfig {
    @Transient
    private val validator = Validation {
        MyCustomConfig::option1 {
            notEmpty("Option 1 cannot be empty")
        }
        
        MyCustomConfig::option2 {
            minValue(0, "Option 2 must be non-negative")
        }
    }

    override fun validate(): ValidationResult<MyCustomConfig> {
        return validator(this)
    }
}
```

2. Register your configuration with the `ConfigManager`:

```kotlin
configManager.registerConfig<MyCustomConfig>("MyCustomConfig")
```

3. Access your configuration in other parts of the application:

```kotlin
val myConfig by inject<MyCustomConfig>()
```

### Custom Discord Utilities

Add your utility functions to the appropriate class in a dedicated file:

```kotlin
// In a file like DiscordExtensions.kt
suspend fun TopGuildChannelBehavior.lockForRole(roleId: ULong) {
    editRolePermission(roleId.snowflake) {
        denied = Permissions {
            +Permission.SendMessages
        }
    }
}

suspend fun TopGuildChannelBehavior.unlockForRole(roleId: ULong) {
    editRolePermission(roleId.snowflake) {
        allowed = Permissions {
            +Permission.SendMessages
        }
    }
}
```

### Build and export

To build and export your newly created plugin, KordEx provides a Gradle task that packages your module into a ZIP file.
Run the following command in your terminal:

```bash
./gradlew :myplugin:distZip
```

## Troubleshooting

### Common Issues

- **Configuration validation errors**: Ensure your configuration file has valid values that meet validation requirements
- **Internationalization key missing**: Check that all translation keys exist in all language files
- **Dependency injection issues**: Verify that you've registered all components with Koin

## Contributing

Contributions to improve the Common module are welcome! Please ensure:

1. All new functionality has appropriate tests
2. Code follows the project's style guidelines
3. Documentation is updated to reflect changes

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [LICENSE](../LICENSE) file for details.