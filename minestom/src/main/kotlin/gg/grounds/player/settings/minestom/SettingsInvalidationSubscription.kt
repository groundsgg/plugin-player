package gg.grounds.player.settings.minestom

import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.Nats
import io.nats.client.Options
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/** Each backend receives every invalidation; a queue group would miss players on other hosts. */
internal class SettingsInvalidationSubscription(
    private val connect: () -> Connection,
    private val refresh: (UUID) -> Unit,
    private val reconcile: () -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var connection: Connection? = null

    fun start() {
        val opened = connect()
        synchronized(lock) {
            if (closed) {
                opened.close()
                return
            }
            try {
                opened
                    .createDispatcher { message ->
                        val id =
                            runCatching { UUID.fromString(message.subject.removePrefix(PREFIX)) }
                                .getOrNull()
                        if (id != null) synchronized(lock) { if (!closed) refresh(id) }
                    }
                    .subscribe("$PREFIX*")
                opened.flush(Duration.ofSeconds(3))
                connection = opened
            } catch (failure: Exception) {
                opened.close()
                throw failure
            }
        }
    }

    override fun close() {
        val owned =
            synchronized(lock) {
                closed = true
                connection.also { connection = null }
            }
        owned?.close()
    }

    companion object {
        private const val PREFIX = "player.settings.changed."

        fun fromEnvironment(
            refresh: (UUID) -> Unit,
            reconcile: () -> Unit,
        ): SettingsInvalidationSubscription {
            val builder =
                Options.Builder()
                    .server(System.getenv("NATS_URL") ?: "nats://nats.nats.svc.cluster.local:4222")
                    .connectionName("grounds-settings-sidebar")
                    .connectionTimeout(Duration.ofSeconds(3))
                    .maxReconnects(-1)
                    .connectionListener { _, event ->
                        if (event == ConnectionListener.Events.RECONNECTED) reconcile()
                    }
            val token =
                Path.of(System.getenv("GROUNDS_TOKEN_FILE") ?: "/var/run/secrets/grounds/token")
            if (Files.exists(token))
                builder.tokenSupplier { Files.readString(token).trim().toCharArray() }
            return SettingsInvalidationSubscription(
                { Nats.connect(builder.build()) },
                refresh,
                reconcile,
            )
        }
    }
}
