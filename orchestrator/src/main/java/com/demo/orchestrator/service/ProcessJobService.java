package com.demo.orchestrator.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.demo.orchestrator.config.MediaProperties;
import com.demo.orchestrator.domain.AudioChunk;
import com.demo.orchestrator.domain.ProcessJob;
import com.demo.orchestrator.domain.StoredAudio;
import com.demo.orchestrator.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProcessJobService {

    private static final Logger log = LoggerFactory.getLogger(ProcessJobService.class);

    private final AudioStorageService storageService;
    private final AudioValidationService validationService;
    private final AudioChunkPlanner chunkPlanner;
    private final JobStore jobStore;
    private final MediaProperties properties;
    private final ObjectMapper objectMapper;

    public ProcessJobService(
            AudioStorageService storageService,
            AudioValidationService validationService,
            AudioChunkPlanner chunkPlanner,
            JobStore jobStore,
            MediaProperties properties,
            ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.validationService = validationService;
        this.chunkPlanner = chunkPlanner;
        this.jobStore = jobStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ProcessJob createJob(String uri, Double gainDb) throws IOException, InterruptedException {
        StoredAudio stored = storageService.resolve(uri);
        double duration = stored.durationSeconds() > 0
                ? stored.durationSeconds()
                : validationService.probeDurationSeconds(java.nio.file.Path.of(stored.path()));
        validationService.validateDuration(duration);
        List<AudioChunk> chunks = chunkPlanner.plan(duration);
        double effectiveGain = gainDb != null ? gainDb : properties.defaultGainDb();
        if (effectiveGain < -24 || effectiveGain > 24) {
            throw new IllegalArgumentException("Gain must be between -24 and 24 dB");
        }
        String jobId = UUID.randomUUID().toString();
        ProcessJob job = new ProcessJob(jobId, uri, Path.of(stored.path()), duration, effectiveGain, chunks);
        jobStore.put(job);
        scheduleExpiry(jobId);
        return job;
    }

    private void scheduleExpiry(String jobId) {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(properties.wsConnectTimeoutSec() * 1000L);
                jobStore.expireIfAwaiting(jobId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public ProcessJob requireJob(String jobId) {
        return jobStore.get(jobId).orElseThrow(() -> new ResourceNotFoundException("Unknown job: " + jobId));
    }

    public String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
