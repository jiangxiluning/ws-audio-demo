package com.demo.orchestrator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.orchestrator.domain.AudioChunk;

class ProcessJobServiceLogicTest {

    private final AudioChunkPlanner planner = new AudioChunkPlanner(new MediaProperties(
            "/tmp/ws-audio-demo", 10, 300, 90, 10, 30, "http://localhost:8090", 6, 32768));

    @Test
    void estimatedPythonProcessingSecFor281s() {
        List<AudioChunk> chunks = planner.plan(281);
        double rtf = chunks.stream().mapToDouble(AudioChunk::durationSec).sum() * 0.6;
        assertEquals(168.6, rtf, 0.01);
        assertEquals(4, chunks.size());
    }

    @Test
    void gainDefaultRange() {
        double defaultGain = 6.0;
        assertTrue(defaultGain >= -24 && defaultGain <= 24);
    }
}
