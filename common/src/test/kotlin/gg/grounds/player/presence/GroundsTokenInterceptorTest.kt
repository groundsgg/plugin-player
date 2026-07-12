package gg.grounds.player.presence

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import java.io.InputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GroundsTokenInterceptorTest {

    @Test
    fun attachesBearerTokenWhenTokenIsAvailable() {
        val channel = CapturingChannel()

        GroundsTokenInterceptor(tokenLoader = { "jwt-abc" })
            .interceptCall(METHOD, CallOptions.DEFAULT, channel)
            .start(NoopListener(), Metadata())

        assertEquals("Bearer jwt-abc", channel.capturedHeaders?.get(AUTHORIZATION))
    }

    @Test
    fun sendsNoAuthorizationHeaderWhenTokenIsMissing() {
        val channel = CapturingChannel()

        GroundsTokenInterceptor(tokenLoader = { null })
            .interceptCall(METHOD, CallOptions.DEFAULT, channel)
            .start(NoopListener(), Metadata())

        assertFalse(channel.capturedHeaders?.containsKey(AUTHORIZATION) ?: true)
    }

    private class CapturingChannel : Channel() {
        var capturedHeaders: Metadata? = null

        override fun authority(): String = "test"

        override fun <ReqT : Any, RespT : Any> newCall(
            methodDescriptor: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
        ): ClientCall<ReqT, RespT> =
            object : ClientCall<ReqT, RespT>() {
                override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                    capturedHeaders = headers
                }

                override fun request(numMessages: Int) = Unit

                override fun cancel(message: String?, cause: Throwable?) = Unit

                override fun halfClose() = Unit

                override fun sendMessage(message: ReqT) = Unit
            }
    }

    private class NoopListener : ClientCall.Listener<String>()

    private companion object {
        val AUTHORIZATION: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

        val MARSHALLER =
            object : MethodDescriptor.Marshaller<String> {
                override fun stream(value: String): InputStream = value.byteInputStream()

                override fun parse(stream: InputStream): String = stream.reader().readText()
            }

        val METHOD: MethodDescriptor<String, String> =
            MethodDescriptor.newBuilder(MARSHALLER, MARSHALLER)
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("gg.grounds.player.Test/Call")
                .build()
    }
}
