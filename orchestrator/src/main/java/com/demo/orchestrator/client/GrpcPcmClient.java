package com.demo.orchestrator.client;

import java.util.Iterator;
import java.util.function.Consumer;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.pcm.v1.HealthCheckRequest;
import com.demo.pcm.v1.PcmProcessorGrpc;
import com.demo.pcm.v1.ProcessRequest;
import com.demo.pcm.v1.ProcessSegment;
import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class GrpcPcmClient implements AutoCloseable {

    private static final int MAX_MESSAGE_BYTES = 16 * 1024 * 1024;

    private final ManagedChannel channel;
    private final PcmProcessorGrpc.PcmProcessorBlockingStub stub;

    public GrpcPcmClient(MediaProperties properties) {
        this(properties.pcmGrpcTarget());
    }

    /** For test doubles that override {@link #processPcmStream}. */
    GrpcPcmClient(String target) {
        this.channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .maxInboundMessageSize(MAX_MESSAGE_BYTES)
                .build();
        this.stub = PcmProcessorGrpc.newBlockingStub(channel)
                .withMaxInboundMessageSize(MAX_MESSAGE_BYTES);
    }

    public void processPcmStream(
            byte[] pcm24k, double gainDb, double durationSec, Consumer<ProcessSegment> onSegment) {
        ProcessRequest request = ProcessRequest.newBuilder()
                .setPcmS16Le(ByteString.copyFrom(pcm24k))
                .setGainDb(gainDb)
                .setDurationSec(durationSec)
                .setInputSampleRate(24000)
                .setOutputSampleRate(48000)
                .build();
        try {
            Iterator<ProcessSegment> segments = stub.processStream(request);
            while (segments.hasNext()) {
                onSegment.accept(segments.next());
            }
        } catch (StatusRuntimeException e) {
            throw new IllegalStateException("PCM gRPC failed: " + e.getStatus(), e);
        }
    }

    public boolean checkHealth() {
        try {
            return "ok".equals(stub.check(HealthCheckRequest.getDefaultInstance()).getStatus());
        } catch (StatusRuntimeException e) {
            return false;
        }
    }

    @Override
    public void close() {
        channel.shutdown();
    }
}
