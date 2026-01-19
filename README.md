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
