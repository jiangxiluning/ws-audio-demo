package com.demo.orchestrator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadProcessIntegrationTest {

    private static final Path FLAC =
            Path.of("../test-fixtures/flac/tone_10s_24k_mono.flac").toAbsolutePath().normalize();

    @Autowired
    private TestRestTemplate rest;

    @BeforeAll
    static void requireFixtures() {
        assumeTrue(Files.isRegularFile(FLAC), "Run ./test-fixtures/generate.sh first");
    }

    @Test
    void uploadAndProcessReturnsChunkPlan() {
        String uri = uploadFlac();
        ResponseEntity<Map> process = startProcess(uri);

        assertEquals(HttpStatus.OK, process.getStatusCode());
        Map<?, ?> body = process.getBody();
        assertEquals(1, body.get("totalChunks"));
        assertEquals(10.0, body.get("sourceDurationSeconds"));
        assertEquals(6.0, body.get("estimatedProcessingSeconds"));
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

    private ResponseEntity<Map> startProcess(String uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"uri\":\"" + uri + "\",\"gainDb\":6}", headers);
        return rest.exchange("/api/v1/audio/process", HttpMethod.POST, entity, Map.class);
    }
}
