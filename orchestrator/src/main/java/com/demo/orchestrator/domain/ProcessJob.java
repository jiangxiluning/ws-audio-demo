package com.demo.orchestrator.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class ProcessJob {

    public enum State {
        AWAITING_WS, RUNNING, COMPLETED, FAILED, EXPIRED
    }

    private final String jobId;
    private final String uri;
    private final Path sourcePath;
    private final double sourceDurationSec;
    private final double gainDb;
    private final List<AudioChunk> chunks;
    private final Instant createdAt;
    private volatile State state = State.AWAITING_WS;
    private volatile Path mergedPath;
    private volatile long mergedBytes;
    private volatile String errorMessage;

    public ProcessJob(
            String jobId,
            String uri,
            Path sourcePath,
            double sourceDurationSec,
            double gainDb,
            List<AudioChunk> chunks) {
        this.jobId = jobId;
        this.uri = uri;
        this.sourcePath = sourcePath;
        this.sourceDurationSec = sourceDurationSec;
        this.gainDb = gainDb;
        this.chunks = List.copyOf(chunks);
        this.createdAt = Instant.now();
    }

    public String getJobId() {
        return jobId;
    }

    public String getUri() {
        return uri;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public double getSourceDurationSec() {
        return sourceDurationSec;
    }

    public double getGainDb() {
        return gainDb;
    }

    public List<AudioChunk> getChunks() {
        return chunks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Path getMergedPath() {
        return mergedPath;
    }

    public void setMergedPath(Path mergedPath) {
        this.mergedPath = mergedPath;
    }

    public long getMergedBytes() {
        return mergedBytes;
    }

    public void setMergedBytes(long mergedBytes) {
        this.mergedBytes = mergedBytes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public double estimatedPythonProcessingSec() {
        return chunks.stream().mapToDouble(AudioChunk::durationSec).sum() * 0.6;
    }
}
