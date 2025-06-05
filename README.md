# ParalyaBot

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![JDK](https://img.shields.io/badge/JDK-21%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Discord](https://img.shields.io/badge/Discord-Paralya-7289DA.svg)](https://discord.gg/gQVxH7bGsP)

A Discord bot for the Paralya Discord server, built with Kotlin and KordEx.

## Overview

ParalyaBot is a modular Discord bot designed to facilitate community management and games for the Paralya community.
The current implementation includes a Werewolf (Loup-Garou) game module directly integrated in the core bot.
Once the plugin system is totally implemented, this game module, along with any potential other, will be moved to a dedicated plugin.

## Support

This bot is designed and supported only for use on the Paralya Discord server (ID: `883659215590277181`).
To get support, you can:
- Open an issue on the [GitHub repository](https://github.com/paralya/paralyabot/issues) (preferred method)
- DM me on Discord (`e_psi_lon`) 
- Send me a message through the [Paralya Discord server](https://discord.gg/gQVxH7bGsP) (only if none of the above methods work)

You are free to self-host under the terms of the AGPL-3.0 license, but functionality and support may not be guaranteed outside that context.

## Project Structure

```
paralya-bot/
├── build.gradle.kts          # Root build configuration
├── settings.gradle.kts       # Project settings
├── src/                      # Root module sources
│   └── main/
│       ├── kotlin/           # Main application code
│       └── resources/        # Root resources
├── common/                   # Common utilities module
│   ├── src/
│   │   ├── main/kotlin/      # Shared code
│   │   └── resources/        # Common resources
│   └── build.gradle.kts      # Common module build config
├── lg/                       # Werewolf (Loup-Garou) game module
│   ├── src/
│   │   └── main/kotlin/      # Game implementation
│   └── build.gradle.kts      # LG module build config
└── games/                    # Folder for installed game plugins
```

## Key Features

- Modular architecture for easy extension
- Werewolf game with full role support and game cycle management
- Command-based interface for easy interaction
- Internationalization support
- Configuration through HOCON files

## Requirements

- JDK 21 or newer
- Gradle (wrapper included)
- Discord Bot Token
- Discord server with appropriate permissions

## Installation

### For Users

- If you are an admin of the Paralya Discord server, you can directly ask me on Discord to get a pre-built JAR file.
- If you want to self-host for your own server, please follow the Developers instruction below to set up the bot. You'll need to edit some values and to have some basic knowledge of Kotlin and Gradle as well as a Discord bot 

### For Developers

1. Clone the repository:
   ```bash
   git clone https://github.com/paralya/paralyabot.git
   cd paralyabot
   ```
2. Create a `config.conf` file in the project root (see the Configuration section)
3. Build and run as described in the Building section

## Setup and Configuration

Create a `config.conf` file with the following structure:

```hocon
bot {
  token = "YOUR_DISCORD_BOT_TOKEN"
  admins = [YOUR_DISCORD_USER_ID, ANOTHER_ADMIN_USER_ID]
  dmLogChannelId = DM_LOG_CHANNEL_ID
  paralyaId = PARALYA_SERVER_ID
}

games {
  // LG is an example, other games can be added similarly
  lg {
    rolesCategory = ROLES_CATEGORY_ID
    mainCategory = MAIN_CATEGORY_ID
    aliveRole = ALIVE_ROLE_ID
    deadRole = DEAD_ROLE_ID
  }
}
```

### Discord Bot Setup

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Navigate to the "Bot" section and create a bot
4. Enable the necessary "Privileged Gateway Intents" (Message Content, Server Members, Presence)
5. Copy the bot token to your `config.conf` file
6. Use the OAuth2 URL Generator to invite the bot to your server with appropriate permissions

## Building

Build the entire project using Gradle:

```bash
./gradlew build
```

Build the deployable JAR:

```bash
./gradlew shadowJar
```

Export the Werewolf game plugin:

```bash
./gradlew :lg:exportToGames
```

## Running

Run the bot using:

```bash
java -jar build/libs/paralya-bot-1.0-SNAPSHOT.jar
```

For development, you can also run directly from Gradle:

```bash
./gradlew run
```

## Game Modules

### Werewolf (Loup-Garou)

A complete implementation of the Werewolf game for Discord. Features include:

- Day/night cycle management
- Role-based gameplay
- Voting system
- Special abilities for different roles
- Webhook-based anonymous messaging

See the LG module [README](lg/README.md) for more details on the Werewolf game implementation.

## Development

### Game Plugin Development Guide

To create a new game module, follow these steps:

1. Create a new module directory and configure `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("dev.kordex.gradle.kordex")
}

group = "fr.paralya.bot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("fr.paralya.bot:common:1.0-SNAPSHOT") // Replace with your common module version (this module isn't yet published)
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
package fr.paralya.bot.myplugin

import dev.kordex.core.plugins.KordExPlugin

class MyGamePlugin : KordExPlugin() {
    override suspend fun setup() {
		// Register commands
		// Event listeners
		// And more
	}
}
```

3. Create a configuration class if needed and ensure it implements `ValidatedConfig`:

```kotlin
@Serializable
data class MyGameConfig(
    var enabled: Boolean = true,
    var channelId: ULong = 0UL
) : ValidatedConfig {
    @Transient
    private val validator = Validation {
        MyGameConfig::channelId { appearsToBeSnowflake("Channel ID") }
    }

    override fun validate(): ValidationResult<MyGameConfig> {
        return validator(this)
    }
}
```

4. Set up internationalization by creating translation files in `src/main/resources/translations/`

5. Build and export your plugin in a built ZIP file to place in your local `games` directory:
```bash
./gradlew :yourgame:distZip
```

## Troubleshooting

### Common Issues

- **Configuration errors**: Verify your `config.conf` file has the correct format and valid IDs

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) — see the [LICENSE](LICENSE) file for details.
