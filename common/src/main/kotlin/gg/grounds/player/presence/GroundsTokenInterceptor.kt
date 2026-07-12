package gg.grounds.player.presence

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import java.nio.file.Files
import java.nio.file.Path

/**
 * Attaches the projected ServiceAccount JWT as `Authorization: Bearer ...` to every outgoing call.
 *
 * service-player runs with `grounds.auth.enabled=true` and rejects tokenless calls with
 * UNAUTHENTICATED. The Grounds charts project a short-lived token (audience `grounds-services`)
 * into the proxy pod and point [TOKEN_FILE_ENV] at it; kubelet rotates the file, so it is re-read
 * per call rather than cached.
 *
 * With no token file present (local dev against a service running `grounds.auth.enabled=false`) the
 * call goes out without the header.
 */
class GroundsTokenInterceptor(private val tokenLoader: () -> String? = ::loadTokenFromFile) :
    ClientInterceptor {

    override fun <ReqT : Any, RespT : Any> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        val delegate = next.newCall(method, callOptions)
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(delegate) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                tokenLoader()?.let { headers.put(AUTHORIZATION, "Bearer $it") }
                super.start(responseListener, headers)
            }
        }
    }

    companion object {
        const val TOKEN_FILE_ENV = "GROUNDS_TOKEN_FILE"
        const val DEFAULT_TOKEN_PATH = "/var/run/secrets/grounds/token"

        private val AUTHORIZATION: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

        private fun loadTokenFromFile(): String? {
            val path = Path.of(System.getenv(TOKEN_FILE_ENV) ?: DEFAULT_TOKEN_PATH)
            return try {
                if (Files.exists(path)) Files.readString(path).trim().ifEmpty { null } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
