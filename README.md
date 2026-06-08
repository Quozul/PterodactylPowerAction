# CraftyPowerAction

A resource-saving Velocity plugin that automatically manages your Minecraft servers by starting them on demand and
shutting them down when idle, using the [Crafty](https://craftycontrol.com/) API or shell commands.

---

> [!IMPORTANT]  
> This is a fork of Quozul's [PterodactylPowerAction](https://github.com/Quozul/PterodactylPowerAction) made compatible with the Crafty API. Support is limited.

## Features

### 🔌 Energy & Resource Saving

Automatically shuts down empty servers after a configurable time with no players connected (default: 1 hour).

### 🚀 On-Demand Server Startup

Starts servers only when players connect, optimizing resource usage.

### 🔄 Seamless Player Experience

Redirects players to a lightweight waiting server during startup and transfers them automatically once the server is
ready.

### 🛡️ Kick Protection

Optionally redirects players to the waiting server when they are kicked from the backend server instead of disconnecting
them.

### 🔐 Whitelist Verification

Validate players against per-server whitelists before letting them join or start the server.

### 🧰 Flexible Implementation

Works with the Pterodactyl Panel API or via direct shell commands for self-hosted setups.

### ⚙️ Highly Configurable

Supports customizable shutdown delays, multiple server status check methods, and adjustable shutdown behavior on proxy
restart.  
👉 See the [Wiki](https://github.com/pelijot/CraftyPowerAction/wiki) for full details.

### 🌐 Multilingual Support

Automatically translates messages based on the client’s language (English, German, French).

### 🔍 Diagnostic Tools

Includes a built-in doctor command to validate configuration and help troubleshoot issues.

## How it Works

When a player tries to connect to an offline server, they're temporarily sent to your waiting server while
PterodactylPowerAction starts their requested destination. Once the server is ready, they're automatically transferred.
The plugin monitors player activity and shuts down empty servers to save resources.

![server-is-starting.png](docs/assets/server-is-starting.png)  
_Shader is Photon._

---

## Documentation

The documentation is available
on [the wiki of this GitHub repository](https://github.com/pelijot/CraftyPowerAction/wiki).

---

## Waiting/Limbo servers

Here is a small list of recommended lightweight servers software to use as waiting server:

- [PicoLimbo](https://github.com/Quozul/PicoLimbo)
- [NanoLimbo](https://www.spigotmc.org/resources/86198/)
- [Limbo](https://www.spigotmc.org/resources/82468/)

Note that the waiting server does not have to be a limbo server specifically, it can be any server as long as it is
always accessible. If you have a dedicated lobby server in your network, you can use that, no need for a dedicated limbo
server!

## Motivations

I'm running all my Minecraft servers with the Crafty Web Panel and haven't found any solution for it. Quozul's Project was perfect and simple enough for rewriting the API code to use Crafty's formats.
I don't have any real Java knowledge, though most of my rewrites were not that complicated.
I will disclose that I used AI for debugging errors.

---

## Contributing

Contributions are welcome! If you encounter any issues or have suggestions for improvement, please submit an issue or
pull request on GitHub. Make sure to follow the existing code style and include relevant tests.

1. Fork the repository.
2. Create a new branch `git checkout -b <branch-name>`.
3. Make changes and commit `git commit -m 'Add some feature'`.
4. Push to your fork `git push origin <branch-name>`.
5. Submit a pull request.
