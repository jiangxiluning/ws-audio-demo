package com.demo.orchestrator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.demo.orchestrator.client.FakeGrpcPcmClient;
import com.demo.orchestrator.client.GrpcPcmClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import(PipelineIntegrationTest.FakePcmConfig.class)
class PipelineIntegrationTest {

    private static final Path FLAC =
            Path.of("../test-fixtures/flac/tone_10s_24k_mono.flac").toAbsolutePath().normalize();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void requireFixtures() {
        assumeTrue(Files.isRegularFile(FLAC), "Run ./test-fixtures/generate.sh first");
        assumeTrue(hasFfmpeg(), "ffmpeg required on PATH");
    }

    @Test
    void uploadProcessStreamAndDownload() throws Exception {
        String uri = uploadFlac();
        Map<?, ?> process = startProcess(uri);
        String jobId = (String) process.get("jobId");
        String streamPath = (String) process.get("streamPath");

        awaitPipelineComplete(streamPath);

        ResponseEntity<byte[]> download = rest.exchange(
                "/api/v1/audio/download/" + jobId, HttpMethod.GET, null, byte[].class);
        assertEquals(HttpStatus.OK, download.getStatusCode());
        assertTrue(download.getBody().length > 0, "merged ogg should not be empty");
    }

    private String uploadFlac() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(FLAC.toFile()));

        ResponseEntity<Map> response =
                rest.exchange("/api/v1/audio/upload", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (String) response.getBody().get("uri");
    }

    private Map<?, ?> startProcess(String uri) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(Map.of("uri", uri, "gainDb", 6));
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/audio/process", HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return objectMapper.readValue(response.getBody(), Map.class);
    }

    private void awaitPipelineComplete(String streamPath) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        List<String> received = new CopyOnWriteArrayList<>();

        StandardWebSocketClient client = new StandardWebSocketClient();
        String wsUrl = "ws://localhost:" + port + streamPath;
        client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.add(message.getPayload());
                try {
                    Map<?, ?> frame = objectMapper.readValue(message.getPayload(), Map.class);
                    String type = (String) frame.get("type");
                    if ("complete".equals(type) || "error".equals(type)) {
                        if ("error".equals(type)) {
                            error.set((String) frame.get("message"));
                        }
                        complete.countDown();
                    }
                } catch (Exception e) {
                    error.set(e.getMessage());
                    complete.countDown();
                }
            }
        }, wsUrl).get(10, TimeUnit.SECONDS);

        assertTrue(complete.await(60, TimeUnit.SECONDS), "timeout; frames: " + received);
        if (error.get() != null) {
            throw new AssertionError("pipeline error: " + error.get());
        }
    }

    private static boolean hasFfmpeg() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @TestConfiguration
    static class FakePcmConfig {
        @Bean
        @Primary
        GrpcPcmClient grpcPcmClient() {
            return new FakeGrpcPcmClient();
        }
    }
}
