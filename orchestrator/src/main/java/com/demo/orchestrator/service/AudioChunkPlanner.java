package com.demo.orchestrator.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.orchestrator.domain.AudioChunk;
import com.demo.orchestrator.exception.InvalidDurationException;

@Service
public class AudioChunkPlanner {

    private final MediaProperties properties;

    public AudioChunkPlanner(MediaProperties properties) {
        this.properties = properties;
    }

    public List<AudioChunk> plan(double totalSec) {
        if (totalSec < properties.minDurationSec() || totalSec > properties.maxDurationSec()) {
            throw new InvalidDurationException(
                    "Duration must be between " + properties.minDurationSec() + " and " + properties.maxDurationSec());
        }

        List<Double> durations = new ArrayList<>();
        double remaining = totalSec;
        while (remaining > properties.maxChunkSec()) {
            durations.add(properties.maxChunkSec());
            remaining -= properties.maxChunkSec();
        }
        if (remaining > 0) {
            durations.add(remaining);
        }

        if (durations.size() > 1) {
            double last = durations.get(durations.size() - 1);
            if (last < properties.minChunkSec()) {
                double deficit = properties.minChunkSec() - last;
                int prevIdx = durations.size() - 2;
                durations.set(prevIdx, durations.get(prevIdx) - deficit);
                durations.set(durations.size() - 1, properties.minChunkSec());
            }
        }

        List<AudioChunk> chunks = new ArrayList<>();
        double offset = 0;
        for (int i = 0; i < durations.size(); i++) {
            double duration = durations.get(i);
            chunks.add(new AudioChunk(i, offset, duration));
            offset += duration;
        }
        return chunks;
    }
}
