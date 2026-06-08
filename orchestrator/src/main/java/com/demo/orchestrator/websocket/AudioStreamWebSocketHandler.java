package com.demo.orchestrator.websocket;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.demo.orchestrator.domain.ProcessJob;
import com.demo.orchestrator.domain.ProcessJob.State;
import com.demo.orchestrator.service.ChunkPipelineScheduler;
import com.demo.orchestrator.service.JobStore;
import com.demo.orchestrator.service.ProcessJobService;

@Component
public class AudioStreamWebSocketHandler extends TextWebSocketHandler {

    private static final ExecutorService PIPELINE_EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "audio-pipeline");
                t.setDaemon(true);
                return t;
            });

    private final JobStore jobStore;
    private final ProcessJobService processJobService;
    private final ChunkPipelineScheduler pipelineScheduler;

    public AudioStreamWebSocketHandler(
            JobStore jobStore, ProcessJobService processJobService, ChunkPipelineScheduler pipelineScheduler) {
        this.jobStore = jobStore;
        this.processJobService = processJobService;
        this.pipelineScheduler = pipelineScheduler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String jobId = extractJobId(session);
        ProcessJob job = processJobService.requireJob(jobId);
        if (job.getState() == State.EXPIRED) {
            session.sendMessage(new org.springframework.web.socket.TextMessage(
                    processJobService.toJson(Map.of("type", "error", "message", "Job expired"))));
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }
        if (!jobStore.markWsConnected(jobId)) {
            session.sendMessage(new org.springframework.web.socket.TextMessage(processJobService.toJson(
                    Map.of("type", "error", "message", "Job already has an active stream"))));
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }
        WebSocketSession sendSession =
                new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024);
        PIPELINE_EXECUTOR.execute(() -> pipelineScheduler.runPipeline(job, sendSession));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        jobStore.removeWs(extractJobId(session));
    }

    private String extractJobId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
