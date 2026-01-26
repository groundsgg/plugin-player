# grounds-plugin-player

## Build

```bash
./gradlew build
```

## Configuration

The plugin requires the gRPC target to be provided via environment variable:

```bash
export PLAYER_PRESENCE_GRPC_TARGET="dns:///service-player.api.svc.cluster.local:9000"
```

Permissions use the same target by default but can be overridden:

```bash
export PERMISSIONS_GRPC_TARGET="dns:///service-player.api.svc.cluster.local:9000"
export PERMISSIONS_CACHE_REFRESH_SECONDS="30"
# Optional: gRPC target for permissions events (defaults to PERMISSIONS_GRPC_TARGET if unset)
export PERMISSIONS_EVENTS_GRPC_TARGET="dns:///service-player.api.svc.cluster.local:9000"
# Optional: unique identifier for this server instance when handling permissions events
export PERMISSIONS_EVENTS_SERVER_ID="velocity-1"
```

Messages are configured in `velocity/src/main/resources/messages.yml` (copied to the plugin data
directory on first run).

## Commands

```text
/permissions help
/permissions refresh [player|uuid]
/permissions player <player|uuid> info
/permissions player <player|uuid> check <permission>
/permissions player <player|uuid> refresh
/permissions player <player|uuid> permission add <permission> [duration]
/permissions player <player|uuid> permission remove <permission>
/permissions player <player|uuid> group add <group> [duration]
/permissions player <player|uuid> group remove <group>
/permissions group list
/permissions group <group> create
/permissions group <group> info
/permissions group <group> delete
/permissions group <group> permission add <permission> [duration]
/permissions group <group> permission remove <permission>
```

Durations use a single suffix: `30m`, `1h`, `7d`, `2w` (seconds `s`, minutes `m`, hours `h`, days `d`, weeks `w`).

## Development

Run in dev mode with live reload using DevSpace in a Kubernetes cluster:

```bash
cd velocity
devspace use namespace games
devspace dev
```

## License

Licensed under the GNU Affero General Public License v3.0
