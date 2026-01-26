package gg.grounds.permissions

import gg.grounds.grpc.BaseGrpcClient
import gg.grounds.grpc.permissions.PermissionsChangeEvent
import gg.grounds.grpc.permissions.PermissionsEventsServiceGrpc
import gg.grounds.grpc.permissions.SubscribePermissionsChangesRequest
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver

class GrpcPermissionsEventsClient
private constructor(
    channel: ManagedChannel,
    private val stub: PermissionsEventsServiceGrpc.PermissionsEventsServiceStub,
) : BaseGrpcClient(channel) {
    fun subscribe(
        request: SubscribePermissionsChangesRequest,
        observer: StreamObserver<PermissionsChangeEvent>,
    ) {
        stub.subscribePermissionsChanges(request, observer)
    }

    companion object {
        fun create(target: String): GrpcPermissionsEventsClient {
            val channel = createChannel(target)
            val stub = PermissionsEventsServiceGrpc.newStub(channel)
            return GrpcPermissionsEventsClient(channel, stub)
        }
    }
}
