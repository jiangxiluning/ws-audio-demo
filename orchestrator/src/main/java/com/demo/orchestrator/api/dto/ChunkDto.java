package com.demo.orchestrator.api.dto;

import com.demo.orchestrator.domain.AudioChunk;

public record ChunkDto(int index, double offsetSec, double durationSec) {

    public static ChunkDto from(AudioChunk chunk) {
        return new ChunkDto(chunk.index(), chunk.offsetSec(), chunk.durationSec());
    }
}
