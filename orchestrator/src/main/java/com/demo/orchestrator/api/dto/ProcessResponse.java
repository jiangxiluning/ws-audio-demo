package com.demo.orchestrator.api.dto;

import java.util.List;

public record ProcessResponse(
        String jobId,
        double sourceDurationSeconds,
        int totalChunks,
        List<ChunkDto> chunks,
        double estimatedProcessingSeconds,
        String streamPath) {
}
