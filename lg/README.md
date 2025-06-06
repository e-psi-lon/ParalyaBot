# ParalyaBot - Werewolf (Loup-Garou) Module

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

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

## Game Setup Guide

### Prerequisites

1. Discord server with appropriate permissions for the bot
2. Configured categories and roles (see the [configuration](#Configuration) section)
3. At least four players recommended (though testing can be done with fewer)

## Game Roles

| Role         | Team    | Description                                       | Special Abilities                                   |
|--------------|---------|---------------------------------------------------|-----------------------------------------------------|
| Villager     | Village | Basic role with voting ability during the day     | Day voting only                                     |
| Werewolf     | Wolves  | Can vote to eliminate a player during the night   | Night voting, wolf-chat access                      |
| Loup Bavard  | Wolves  | A special werewolf with a communication challenge | Must use a specific word in conversation each day   |
| Corbeau      | Village | Can mark a player to receive additional votes     | Adds two votes to a player of choice during the day |
| Petite Fille | Village | Can secretly observe werewolf discussions         | Can see werewolf chat (anonymized)                  |

## Commands

### Admin Commands

- `/lg jour` - Start a new day phase
- `/lg nuit` - Start a new night phase
- `/lg mort <player>` - Kill a player manually
- `/lg interview <player>` - Interview a player in the announcement channel
- `/lg notif <role>` - Send notifications to players with specific roles
- `/lg findujour <hour>` - Announce the real-time hour of the end of the day phase
- `/lg vote-reset` - Reset all current votes

### Player Commands

- `/lg vote village <player>` - Vote for a player during the day (villagers)
- `/lg vote loup <player>` - Vote for a player during the night (werewolves)
- `/lg unvote` - Cancel your vote
- `/lg vote-list` - View current votes
- `/lg role` - View your assigned role and abilities (DM only)

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

### Server Setup

Before starting the game, ensure you have:

1. Created a category for the main game channels
2. Created a category for role-specific channels
3. Created "Alive" and "Dead" roles for player management
4. Added these IDs to your configuration file

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

### Extending Game Mechanics

1. Modify the day/night cycle handlers in the appropriate files
2. Add new commands to the command registry
3. Update the game state management as needed

## Troubleshooting

### Common Issues

- **Permissions Problems**: If players can't see certain channels, verify role permissions and ensure the bot has the "Manage Channels" and "Manage Roles" permissions
- **Role Ability Issues**: Check that players are using their abilities at the correct time (day/night)
- **Bot Not Responding**: Verify that the bot has the required permissions and intents

### Problem Solving

- **Game Stuck in Phase**: Use the appropriate admin command (`/lg jour` or `/lg nuit`) to manually advance the game
- **Incorrect Player Death**: Use `/lg mort` to manually manage player deaths if automatic processing fails
- **Reset Votes**: If voting seems stuck, use `/lg vote-reset` to clear all current votes

## Installation

The Werewolf module is built as a KordEx plugin and can be loaded into the ParalyaBot application.
Place the built Zip file, without renaming it, in your `games` directory, reload the plugins through the command and start playing!

> [!NOTE]
> If a previous version of the game is in your `games` directory, add the new version first, reload, and then remove the
> old version to avoid conflicts. The bot will automatically check for the latest version based on the file name.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) — see the [LICENSE](../LICENSE) file for details.