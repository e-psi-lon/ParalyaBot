# ParalyaBot - Werewolf (Loup-Garou) Module

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A fully-featured implementation of the Werewolf (Loup-Garou) game for Discord, built as a plugin for ParalyaBot.

## Overview

The Werewolf module provides a complete game system for running Werewolf games in Discord servers. 
It manages the game cycle, roles, voting, and special abilities, creating an immersive experience for players 
through Discord channels and webhooks.

## Features

- Complete day/night cycle management
- Role-based gameplay with special abilities
- Voting system for both villagers and werewolves
- Webhook-based anonymous messaging for werewolves
- Support for special roles (Loup Bavard, Corbeau, etc.)
- Admin commands for game management
- Interview system for player interrogation

## Game Roles

| Role          | Description                                             |
|---------------|---------------------------------------------------------|
| Villager      | Basic role with voting ability during the day           |
| Werewolf      | Can vote to eliminate a player during the night         |
| Loup Bavard   | A werewolf who must use a specific word in conversation |
| Corbeau       | Can add two additional votes to a player                |
| Petite Fille  | Can see werewolf chat (anonymized)                      |
| More roles... | Additional roles can be implemented                     |

## Commands

### Admin Commands

- `/lg jour` - Start a new day phase
- `/lg nuit` - Start a new night phase
- `/lg mort <player>` - Kill a player manually
- `/lg interview <player>` - Interview a player in the announcement channel
- `/lg notif <role>` - Send notifications to players with specific roles
- `/lg findujour <hour>` - Announce the real-time hour of the end of the day phase
- `/lg setrole <player> <role>` - Assign a special role to a player
- `/lg setmot <word>` - Set the word for the Loup Bavard role
- `/lg vote-reset` - Reset all current votes

### Player Commands

- `/lg vote village <player> [reason]` - Vote for a player during the day (villagers)
- `/lg vote loup <player> [reason]` - Vote for a player during the night (werewolves)
- `/lg unvote` - Cancel your vote
- `/lg vote-list` - View current votes

## Game Cycle

1. **Night Phase**
   - Villagers cannot speak
   - Werewolves can discuss and vote to kill a player
   - Special roles perform their night actions

2. **Day Phase**
   - Night vote results are applied (player is killed)
   - Villagers can discuss and vote to eliminate a suspected werewolf
   - Players can create and discuss topics in the subject channel

3. **Cycle repeats**

## Configuration

Configure the Werewolf game in the main `config.conf` file:

```hocon
games {
  lg {
    rolesCategory = ROLES_CATEGORY_ID       # Category for role-specific channels
    mainCategory = MAIN_CATEGORY_ID         # Main game category
    aliveRole = ALIVE_ROLE_ID               # Role for living players
    deadRole = DEAD_ROLE_ID                 # Role for eliminated players
  }
}
```

## Channel Structure

The game creates and manages several channels:

- **Village** — Main discussion channel during the day
- **Vote** — Channel for casting votes during the day
- **Loup-Chat** — Werewolf discussion channel (night only)
- **Loup-Vote** — Werewolf voting channel (night only)
- **Subject** — Channel for creating discussion topics for players to debate
- **Announcements** — Game announcements and events
- **Role-specific channels** — Channels for special roles

## Development

### Adding New Roles

1. Create a new role class
2. Implement the role's specific logic in one of the dedicated game cycle handlers
3. Update the game state management to include the new role
4. Add commands for the new role if necessary
5. Update the configuration to include the new role if needed

### Extending Game Mechanics

1. Modify the day/night cycle handlers in the appropriate files
2. Add new commands to the command registry
3. Update the game state management as needed

## Installation

The Werewolf module is built as a KordEx plugin and can be loaded into the ParalyaBot application.
Place the built Zip file, without renaming it, in your `games` directory, reload the plugins through the command and start playing!

> [!NOTE]
> If a previous version of the game is in your `games` directory, add the new version first, reload, and then remove the
> old version to avoid conflicts. The bot will automatically check for the latest version based on the file name.

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
