# Configuration Guide

This document explains how to configure the PterodactylPowerAction plugin for Velocity, which allows you to
automatically start and stop Minecraft servers using either the Pterodactyl API or shell commands.

## Configuration File Overview

The configuration file uses YAML format and supports two main modes of operation:

- Pterodactyl API integration
- Shell command execution

## Basic Configuration Structure

```yaml
type: "pterodactyl"  # or "shell"
waiting_server_name: "limbo"  # Optional
ping_method: "ping"  # or "pterodactyl"
maximum_ping_duration: 60
shutdown_after_duration: 3600
redirect_to_waiting_server_on_kick: true
shutdown_behaviour: "shutdown_all"
```

## Configuration Options

### Common Settings

| Option                               | Description                                                                             | Default Value    | Possible Values                                   |
|--------------------------------------|-----------------------------------------------------------------------------------------|------------------|---------------------------------------------------|
| `type`                               | The method used to control servers                                                      | Required         | `"pterodactyl"`, `"shell"`                        |
| `waiting_server_name`                | The server players will be sent to while waiting for their destination server to start  | Optional         | Any server defined in `velocity.toml`, `null`     |
| `start_waiting_server_on_startup`    | Whether to automatically start the waiting server when the proxy starts if it's offline | `true`           | `true`, `false`                                   |
| `ping_method`                        | Method used to check if a server is running                                             | `"ping"`         | `"ping"`, `"pterodactyl"`                         |
| `maximum_ping_duration`              | Maximum time (in seconds) to wait for a server to respond                               | `60`             | Any positive integer                              |
| `shutdown_after_duration`            | Time (in seconds) after which an empty server will be shut down                         | `3600`           | Any positive integer                              |
| `redirect_to_waiting_server_on_kick` | Whether to redirect players to the waiting server when kicked from a backend server     | `false`          | `true`, `false`                                   |
| `shutdown_behaviour`                 | What to do with servers when the proxy shuts down                                       | `"shutdown_all"` | `"shutdown_all"`, `"shutdown_empty"`, `"nothing"` |

### Pterodactyl-Specific Settings

When `type` or `ping_method` is set to `"pterodactyl"`, the following settings are required:

```yaml
pterodactyl_api_key: "ptlc_xxx"
pterodactyl_client_api_base_url: "https://example.com/api/client"
servers:
  limbo: "21b8d887-7d47-4a0b-bbfe-4c4a6318dcf0" # Short form
  survival:
    identifier: "e25eccf6-2bc6-4264-b34a-c8ab02a5c986"
    whitelist: true
```

| Option                            | Description                                                |
|-----------------------------------|------------------------------------------------------------|
| `pterodactyl_api_key`             | Client API key from Pterodactyl panel                      |
| `pterodactyl_client_api_base_url` | Base URL for the Pterodactyl client API                    |
| `servers`                         | Mapping of Velocity server names to Pterodactyl server IDs |
| `whitelist`                       | Enable whitelist verification using `whitelist.json` file  |

Each server entry may either be a simple string containing the server's identifier or a map with an `identifier` field. When using the map form, an optional `whitelist` boolean can enable whitelist verification for that server.

### Shell-Specific Settings

When `type` is set to `"shell"`, the following structure is required:

```yaml
servers:
  server_name:
    working_directory: "/path/to/directory"  # Optional
    start: "command to start server"
    stop: "command to stop server"
    whitelist: true  # Optional
```

| Option              | Description                                               |
|---------------------|-----------------------------------------------------------|
| `working_directory` | Directory to run commands from (optional)                 |
| `start`             | Command to start the server                               |
| `stop`              | Command to stop the server                                |
| `whitelist`         | Enable whitelist verification using `whitelist.json` file |

## Shutdown Behavior Options

The `shutdown_behaviour` setting determines what happens to servers when the proxy shuts down:

| Value              | Description                                     |
|--------------------|-------------------------------------------------|
| `"nothing"`        | Keeps all servers running                       |
| `"shutdown_empty"` | Shuts down only servers with no players         |
| `"shutdown_all"`   | Shuts down all servers, even those with players |

## Ping Methods

The `ping_method` setting determines how server availability is checked:

| Value           | Description                                                                    |
|-----------------|--------------------------------------------------------------------------------|
| `"ping"`        | Uses Velocity's built-in ping mechanism (lighter and usually faster)           |
| `"pterodactyl"` | Uses the Pterodactyl API (may be more accurate but requires API configuration) |

**Important notes:**

- When using the `"pterodactyl"` ping method, you must include the waiting server's ID in the `servers` map
- The `"pterodactyl"` ping method is only compatible with the `"pterodactyl"` type and cannot be used with the `"shell"`
  type

## Waiting Server Configuration

The `waiting_server_name` setting determines where players are sent while waiting for their destination server to start:

- If set to a valid server name from your `velocity.toml`, players will be redirected to this server while waiting
- If set to `null` or any invalid server name, the waiting server feature will be disabled and players will be kicked
  from the network with a message that the server is starting instead of being redirected

The `start_waiting_server_on_startup` setting controls whether the plugin should automatically start the waiting server
when the proxy starts:

- If set to `true` (default), the plugin will check if the waiting server is offline during proxy startup and start it
  if needed
- If set to `false`, the plugin will not attempt to start the waiting server on proxy startup

## Player Handling

The `redirect_to_waiting_server_on_kick` setting determines what to do if the player gets kicked from the backend
server:

- If set to `true`, the player will be redirected to the waiting server
- If set to `false`, the player will be kicked from the network anytime the backend server refuses the connection

## Example Configurations

### Pterodactyl Example

```yaml
type: "pterodactyl"
pterodactyl_api_key: "ptlc_xxx"
pterodactyl_client_api_base_url: "https://panel.example.com/api/client"
servers:
  limbo: "21b8d887-7d47-4a0b-bbfe-4c4a6318dcf0"
  survival:
    identifier: "e25eccf6-2bc6-4264-b34a-c8ab02a5c986"
    whitelist: true
  creative:
    identifier: "f33b8741-e46c-4386-9281-05eaa2e88333"
waiting_server_name: "limbo"
ping_method: "pterodactyl"
maximum_ping_duration: 60
shutdown_after_duration: 3600
redirect_to_waiting_server_on_kick: false
shutdown_behaviour: "shutdown_empty"
```

### Shell Example

```yaml
type: "shell"
servers:
  survival:
    working_directory: "/home/minecraft/servers"
    start: "docker compose start survival"
    stop: "docker compose stop survival"
    whitelist: true
  creative:
    working_directory: "/home/minecraft/servers"
    start: "docker compose start creative"
    stop: "docker compose stop creative"
waiting_server_name: "limbo"
ping_method: "ping"
maximum_ping_duration: 60
shutdown_after_duration: 3600
redirect_to_waiting_server_on_kick: true
shutdown_behaviour: "shutdown_all"
```

## Notes and Warnings

- When using Pterodactyl behind a proxy (like CloudFlare DNS proxy), you may encounter connection issues.
- The `cd` command will not work in shell commands. Use the `working_directory` setting instead.
- Shell command mode has not been extensively tested on Windows.
- If you add a new server to `velocity.toml`, you'll need to reload Velocity's configuration separately.
