# grounds-plugin-player

## Build

```bash
./gradlew build
```

## Configuration

The plugin requires the address of service-player. A proxy that cannot reach presence
cannot decide whether a player may join, so this is required rather than defaulted:

```bash
export PLAYER_SERVICE_URL="http://service-player.api.svc.cluster.local:9000"
```

The scheme may be omitted (`service-player.api.svc.cluster.local:9000`), matching how
the deploy sets the other service URLs; `http://` is assumed.

Calls carry the projected workload token from `GROUNDS_TOKEN_FILE`
(default `/var/run/secrets/grounds/token`). With no token file present — local dev
against a service running `grounds.auth.enabled=false` — requests go out unauthenticated.

Optional heartbeat configuration:

```bash
export PLAYER_PRESENCE_HEARTBEAT_SECONDS="30"
export PLAYER_SESSIONS_TTL="90s"
```

`PLAYER_PRESENCE_HEARTBEAT_SECONDS` is automatically clamped to a safe value based on
`PLAYER_SESSIONS_TTL` (`max = ttl / 3`) to reduce false stale-session cleanup.

Messages are configured in `velocity/src/main/resources/messages.yml` (copied to the plugin data
directory on first run).

## Development

Run in dev mode with live reload using DevSpace in a Kubernetes cluster:

```bash
cd velocity
devspace use namespace games
devspace dev
```

## License

Licensed under the GNU Affero General Public License v3.0
