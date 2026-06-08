package com.demo.orchestrator.client;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.pcm.v1.ProcessSegment;
import com.google.protobuf.ByteString;

/** Test double: 2× upsample in 10s segments without RTF sleep or gRPC. */
public class FakeGrpcPcmClient extends GrpcPcmClient {

    private static final int SEGMENT_BYTES_24K = 24000 * 2 * 10;

    public FakeGrpcPcmClient() {
        super("127.0.0.1:1");
    }

    @Override
    public void processPcmStream(
            byte[] pcm24k,
            double gainDb,
            double durationSec,
            java.util.function.Consumer<ProcessSegment> onSegment) {
        int segmentIndex = 0;
        for (int offset = 0; offset < pcm24k.length; offset += SEGMENT_BYTES_24K) {
            int end = Math.min(offset + SEGMENT_BYTES_24K, pcm24k.length);
            byte[] slice = new byte[end - offset];
            System.arraycopy(pcm24k, offset, slice, 0, slice.length);
            byte[] out = new byte[slice.length * 2];
            System.arraycopy(slice, 0, out, 0, slice.length);
            System.arraycopy(slice, 0, out, slice.length, slice.length);
            double segmentDuration = slice.length / (24000.0 * 2.0);
            boolean isLast = end >= pcm24k.length;
            onSegment.accept(ProcessSegment.newBuilder()
                    .setPcmS16Le(ByteString.copyFrom(out))
                    .setSegmentIndex(segmentIndex++)
                    .setSegmentDurationSec(segmentDuration)
                    .setIsLast(isLast)
                    .build());
        }
    }

    @Override
    public boolean checkHealth() {
        return true;
    }
}
