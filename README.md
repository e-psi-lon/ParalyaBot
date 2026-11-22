# ParalyaBot

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![JDK](https://img.shields.io/badge/JDK-25%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html)
[![Discord](https://img.shields.io/badge/Discord-Paralya-7289DA.svg)](https://discord.gg/gQVxH7bGsP)

> [!IMPORTANT]
> This repository is being migrated to a self-hosted [Forgejo](https://git.e-psi-lon.dev/e-psi-lon/ParalyaBot). The GitHub repository will remain available as a mirror for reference, but new development will occur there.

A modular Discord bot for the Paralya community, built with Kotlin and KordEx.

ParalyaBot keeps its core Discord integration and shared infrastructure in the main application, while first-party features are distributed as plugins allowing for runtime hot-replacement/updates of the said features. It uses Redis-compatible storage for external cache data and HOCON for bot and plugin configuration.

## Features

- **Core Discord commands**: start or stop a configured game, export channel history as text or partially supported HTML, relay direct messages to a configured log channel, and report system monitoring.
- **Werewolf/Loup-Garou plugin**: game setup, day/night cycles, role channels, voting, vote withdrawal, and vote administration.
- **First-party plugin system**: plugins register configuration, Koin components, cache entries, extensions, and game modes through shared interfaces.
- **Plugin lifecycle commands**: host operators can list, start, stop, and reload plugin ZIP distributions.
- **Redis-compatible caching**: externalizes cached data outside the bot process, with in-memory fallback for data that cannot use the external cache.
- **French-first internationalization**: generated translation bundles are used throughout the bot and plugins.

## Project structure

| Path | Purpose |
| --- | --- |
| `bot/` | Main executable, core extensions, monitoring, and plugin lifecycle commands |
| `common/` | Shared configuration, cache, plugin API, Discord utilities, and tests |
| `lg/` | Werewolf/Loup-Garou game plugin |
| `ai/` | AI plugin module; its implementation is currently a scaffold |
| `sta/` | Reserved plugin module; currently contains translation assets only |
| `deps/` | Shared runtime/development dependency bundle |
| `build-logic/` | Shared Gradle convention plugins |
| `cache/` | Go implementation of the Redis-compatible cache service |
| `nix/` | Nix development shell, package builds, container images, and helper commands |

The repository is a Gradle multi-project build. Plugin distributions are loaded dynamically at runtime.

`container/` is a git-ignored runtime mount used for `config.conf` and plugin ZIP files. It's meant for local or Docker execution.

## Requirements

- JDK 25
- A Discord application and bot token
- A Discord server where the bot has slash-command and channel permissions
- Redis-compatible storage for external cached data

The Gradle wrapper is included, so a separate Gradle installation is not required.

## Discord application setup

1. Create an application in the [Discord Developer Portal](https://discord.com/developers/applications).
2. Add a bot to the application and copy its token.
3. Enable **Server Members Intent** and **Message Content Intent** under Privileged Gateway Intents.
4. Invite the bot with the `applications.commands` scope and the permissions required by the features you enable.

Plugin configuration and required Discord roles/categories/channels are described below by plugin rather than in this base setup.

## Configuration

The core configuration uses HOCON. By default, the bot reads `config.conf` from the working directory. Set `PARALYA_BOT_CONFIG_FILE` to use another path.

```hocon
bot {
    token = "YOUR_DISCORD_BOT_TOKEN"
    admins = [ 123456789012345678 ]
    dmLogChannelId = 123456789012345678
    paralyaId = 123456789012345678
}

games {
    example_game {
        # Plugin-specific configuration
    }
}
```

- `bot.token`: Discord bot token.
- `bot.admins`: Discord user IDs allowed to use administrator-restricted commands.
- `bot.dmLogChannelId`: channel used to mirror DMs and DM edits/deletions.
- `bot.paralyaId`: guild ID targeted by the application.
- `games.<name>`: plugin-specific configuration registered by a plugin.

The Werewolf plugin, for example, expects `games.lg` values for category and role IDs. Configuration is validated at startup and on reload.

Runtime environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PARALYA_BOT_CONFIG_FILE` | `config.conf` | HOCON configuration path |
| `PARALYA_BOT_PLUGINS_DIR` | `./plugins` | Directory containing plugin ZIP distributions |
| `KORD_CACHE_URL` | `redis://localhost:6379` | Redis-compatible cache URL |
| `BOT_DEVELOPER_ID` | none | Discord user ID with access to plugin lifecycle commands |

## Running with Docker

The dedicated **`docker`** branch contains the ready-to-use Docker Compose deployment. It already includes `container/`, `container/plugins/`, and [`docker-compose.yml`](https://github.com/e-psi-lon/ParalyaBot/blob/docker/docker-compose.yml).

```bash
git fetch origin docker
git switch docker
```

Create `container/config.conf`, then place plugin ZIPs in `container/plugins/`:

```text
ParalyaBot/
├── docker-compose.yml
└── container/
    ├── config.conf
    └── plugins/
        └── lg-0.1.0.zip
```

From the branch root:

```bash
docker compose pull
docker compose up -d
docker compose logs -f paralyabot
```

The container reads `config.conf` from `/app/external/config.conf` and plugin ZIPs from `/app/external/plugins`. Plugin files can be listed, started, stopped, or reloaded through the host-oriented plugin commands.

## Development with Nix

Nix provides the recommended development environment. It supplies JDK 25, Go, `golangci-lint`, project-local `.jdk` and `.goroot` links for IDE configuration, and project/version environment variables.

With direnv installed, `.envrc` enters the flake automatically when you enter the repository:

```bash
direnv allow
```

Otherwise:

```bash
nix develop
```

The main development shell also provides `pbot`, a helper for building and running the bot, cache, and plugins and for dependency management:

```bash
pbot
pbot build
pbot up
pbot bot build
pbot bot up
pbot cache up
pbot plugin build lg
pbot plugin deploy lg
pbot snapshot-check
```

`pbot plugin deploy lg` builds the `lg` plugin and copies it into `container/plugins/`. `pbot up` builds and starts the cache before the bot. Docker or Podman is required by the container helpers.

The lower-level Gradle wrapper is available when a specific build task is needed but note that it won't provide environment variables, the cache or containerisation:

```bash
./gradlew test
./gradlew check
./gradlew build
./gradlew lg:exportToPluginsDir
```

Set `KORD_CACHE_URL` when Redis-compatible storage is not available at `redis://localhost:6379`.

## Support and self-hosting

This bot is designed and supported primarily for the Paralya Discord server (ID: `883659215590277181`). It can be self-hosted under the terms of the AGPL-3.0 license, but behavior and support outside that community are not guaranteed.

To get help:

- Open an issue on the [GitHub repository](https://github.com/e-psi-lon/ParalyaBot/issues).
- DM `e_psi_lon` on Discord.
- Ask in the [Paralya Discord server](https://discord.gg/gQVxH7bGsP) if the other methods do not work.

## Contributing

1. Fork the repository and create a focused feature branch.
2. Follow the existing Kotlin, KordEx, Koin, and Gradle conventions.
3. Add or update tests for behavior changes where appropriate.
4. Open a pull request with a concise description of the change.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See [LICENSE](LICENSE) for the full text.
