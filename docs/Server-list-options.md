# Server List Options

The following configuration options control how server status is displayed in the Minecraft multiplayer menu. It is recommended to set Velocity's `ping-passthrough` option to `all` when using these settings.

| Option | Description | Default | Values |
|--------|-------------|---------|--------|
| `state_ping` | Replace the players/ping text with server state when offline or starting | `false` | `true`, `false` |
| `cache_motd` | Cache the backend MOTD and show it while the server is offline | `false` | `true`, `false` |
| `state_motd` | Show server state in the MOTD when offline or starting (disables `cache_motd`) | `false` | `true`, `false` |
