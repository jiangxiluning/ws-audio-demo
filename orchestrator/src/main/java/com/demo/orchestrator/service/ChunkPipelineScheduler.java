package com.demo.orchestrator.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.demo.orchestrator.client.GrpcPcmClient;
import com.demo.orchestrator.config.MediaProperties;
import com.demo.orchestrator.domain.AudioChunk;
import com.demo.orchestrator.domain.ProcessJob;
import com.demo.orchestrator.domain.ProcessJob.State;
import com.demo.pcm.v1.ProcessSegment;

@Service
public class ChunkPipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChunkPipelineScheduler.class);

    private final FfmpegCodecService ffmpegCodecService;
    private final GrpcPcmClient grpcPcmClient;
    private final AudioStorageService storageService;
    private final ProcessJobService processJobService;
    private final MediaProperties properties;

    public ChunkPipelineScheduler(
            FfmpegCodecService ffmpegCodecService,
            GrpcPcmClient grpcPcmClient,
            AudioStorageService storageService,
            ProcessJobService processJobService,
            MediaProperties properties) {
        this.ffmpegCodecService = ffmpegCodecService;
        this.grpcPcmClient = grpcPcmClient;
        this.storageService = storageService;
        this.processJobService = processJobService;
        this.properties = properties;
    }

    public void runPipeline(ProcessJob job, WebSocketSession session) {
        job.setState(State.RUNNING);
        List<Path> chunkFiles = new ArrayList<>();
        long[] bytesSentTotal = {0};
        try {
            sendJson(session, Map.of(
                    "type", "session_meta",
                    "totalChunks", job.getChunks().size(),
                    "sourceDurationSec", job.getSourceDurationSec(),
                    "estimatedMergedBytes", 0));

            Path jobDir = storageService.jobDir(job.getJobId());

            for (AudioChunk chunk : job.getChunks()) {
                sendJson(session, Map.of(
                        "type", "chunk_start",
                        "chunkIndex", chunk.index(),
                        "chunkOffsetSec", chunk.offsetSec(),
                        "chunkDurationSec", chunk.durationSec()));

                byte[] pcm24k = ffmpegCodecService.decodeChunkToPcm24k(
                        job.getSourcePath(), chunk.offsetSec(), chunk.durationSec());
                if (pcm24k.length == 0) {
                    throw new IllegalStateException(
                            "ffmpeg decode returned empty PCM for " + job.getSourcePath()
                                    + " offset=" + chunk.offsetSec()
                                    + " duration=" + chunk.durationSec());
                }

                List<Path> segmentFiles = new ArrayList<>();
                int[] chunkBytesSent = {0};

                grpcPcmClient.processPcmStream(pcm24k, job.getGainDb(), chunk.durationSec(), segment -> {
                    try {
                        processSegment(
                                session, jobDir, chunk, segment, segmentFiles, chunkBytesSent, bytesSentTotal);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });

                Path chunkPath = jobDir.resolve("chunk_" + chunk.index() + ".ogg");
                ffmpegCodecService.concatOggFiles(segmentFiles, chunkPath);
                chunkFiles.add(chunkPath);

                sendJson(session, Map.of(
                        "type", "chunk_complete",
                        "chunkIndex", chunk.index(),
                        "chunkBytes", chunkBytesSent[0]));
            }

            Path merged = jobDir.resolve("full.ogg");
            ffmpegCodecService.concatOggFiles(chunkFiles, merged);
            job.setMergedPath(merged);
            job.setMergedBytes(Files.size(merged));
            job.setState(State.COMPLETED);

            sendJson(session, Map.of(
                    "type", "complete",
                    "totalDurationSec", job.getSourceDurationSec(),
                    "downloadUrl", "/api/v1/audio/download/" + job.getJobId(),
                    "mergedBytes", job.getMergedBytes(),
                    "mergedReady", true));
        } catch (Exception e) {
            log.error("Pipeline failed for job {}", job.getJobId(), e);
            job.setState(State.FAILED);
            job.setErrorMessage(e.getMessage());
            try {
                sendJson(session, Map.of("type", "error", "message", e.getMessage()));
            } catch (IOException ioException) {
                log.warn("Failed to send error frame", ioException);
            }
        }
    }

    private void processSegment(
            WebSocketSession session,
            Path jobDir,
            AudioChunk chunk,
            ProcessSegment segment,
            List<Path> segmentFiles,
            int[] chunkBytesSent,
            long[] bytesSentTotal)
            throws IOException {
        byte[] pcm48k = segment.getPcmS16Le().toByteArray();
        byte[] oggSegment;
        try {
            oggSegment = ffmpegCodecService.encodePcm48kToOggFlac(pcm48k);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg encode interrupted", e);
        }

        Path segmentPath = jobDir.resolve(
                "chunk_" + chunk.index() + "_seg_" + segment.getSegmentIndex() + ".ogg");
        Files.write(segmentPath, oggSegment);
        segmentFiles.add(segmentPath);

        streamBinary(session, chunk.index(), oggSegment, chunkBytesSent, bytesSentTotal);

        sendJson(session, Map.of(
                "type", "segment_complete",
                "chunkIndex", chunk.index(),
                "segmentIndex", segment.getSegmentIndex(),
                "segmentDurationSec", segment.getSegmentDurationSec(),
                "segmentBytes", oggSegment.length,
                "isLastInChunk", segment.getIsLast()));
    }

    private void streamBinary(
            WebSocketSession session,
            int chunkIndex,
            byte[] oggBytes,
            int[] chunkBytesSent,
            long[] bytesSentTotal)
            throws IOException {
        int frame = properties.streamChunkBytes();
        for (int i = 0; i < oggBytes.length; i += frame) {
            int end = Math.min(i + frame, oggBytes.length);
            byte[] slice = new byte[end - i];
            System.arraycopy(oggBytes, i, slice, 0, slice.length);
            session.sendMessage(new org.springframework.web.socket.BinaryMessage(slice));
            chunkBytesSent[0] += slice.length;
            bytesSentTotal[0] += slice.length;
            sendJson(session, Map.of(
                    "type", "progress",
                    "chunkIndex", chunkIndex,
                    "bytesSentInChunk", chunkBytesSent[0],
                    "chunkBytes", chunkBytesSent[0],
                    "bytesSentTotal", bytesSentTotal[0]));
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) throws IOException {
        session.sendMessage(new TextMessage(processJobService.toJson(payload)));
    }
}
