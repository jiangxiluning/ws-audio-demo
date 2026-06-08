package com.demo.orchestrator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.orchestrator.domain.AudioChunk;
import com.demo.orchestrator.exception.InvalidDurationException;

class AudioChunkPlannerTest {

    private final AudioChunkPlanner planner = new AudioChunkPlanner(new MediaProperties(
            "/tmp/ws-audio-demo", 10, 300, 90, 10, 30, "http://localhost:8090", 6, 32768));

    @Test
    void plan281s() {
        List<AudioChunk> chunks = planner.plan(281);
        assertEquals(4, chunks.size());
        assertEquals(90, chunks.get(0).durationSec());
        assertEquals(11, chunks.get(3).durationSec());
    }

    @Test
    void plan95sRebalance() {
        List<AudioChunk> chunks = planner.plan(95);
        assertEquals(2, chunks.size());
        assertEquals(85, chunks.get(0).durationSec());
        assertEquals(10, chunks.get(1).durationSec());
    }

    @Test
    void rejectsTooShort() {
        assertThrows(InvalidDurationException.class, () -> planner.plan(9));
    }
}
