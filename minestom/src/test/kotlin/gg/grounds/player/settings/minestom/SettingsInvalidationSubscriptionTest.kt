package gg.grounds.player.settings.minestom

import io.nats.client.Nats
import java.time.Duration
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class SettingsInvalidationSubscriptionTest {
    @Test
    fun `invalidations fan out to every backend and stop closes subscriptions`() {
        GenericContainer(DockerImageName.parse("nats:2.15.1-alpine")).withExposedPorts(4222).use {
            server ->
            server.start()
            val url = "nats://${server.host}:${server.getMappedPort(4222)}"
            val first = LinkedBlockingQueue<UUID>()
            val second = LinkedBlockingQueue<UUID>()
            val a = SettingsInvalidationSubscription({ Nats.connect(url) }, { first.add(it) }, {})
            val b = SettingsInvalidationSubscription({ Nats.connect(url) }, { second.add(it) }, {})
            a.use {
                b.use {
                    a.start()
                    b.start()
                    Nats.connect(url).use { publisher ->
                        val id = UUID.randomUUID()
                        publisher.publish("player.settings.changed.$id", byteArrayOf())
                        publisher.flush(Duration.ofSeconds(2))
                        assertEquals(id, first.poll(3, TimeUnit.SECONDS))
                        assertEquals(id, second.poll(3, TimeUnit.SECONDS))
                        publisher.publish("player.settings.changed.invalid", byteArrayOf())
                        publisher.flush(Duration.ofSeconds(2))
                        a.close()
                        publisher.publish("player.settings.changed.$id", byteArrayOf())
                        publisher.flush(Duration.ofSeconds(2))
                        assertEquals(id, second.poll(3, TimeUnit.SECONDS))
                        assertNull(first.poll(150, TimeUnit.MILLISECONDS))
                    }
                }
            }
        }
    }

    @Test
    fun `a connection completing after shutdown is closed immediately`() {
        val connection = org.mockito.Mockito.mock(io.nats.client.Connection::class.java)
        lateinit var subscription: SettingsInvalidationSubscription
        subscription =
            SettingsInvalidationSubscription(
                {
                    subscription.close()
                    connection
                },
                {},
                {},
            )
        subscription.start()
        org.mockito.Mockito.verify(connection).close()
        org.mockito.Mockito.verify(connection, org.mockito.Mockito.never())
            .createDispatcher(
                org.mockito.ArgumentMatchers.any(io.nats.client.MessageHandler::class.java)
            )
    }
}
