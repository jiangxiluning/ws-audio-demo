package com.demo.orchestrator.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.demo.orchestrator.client.GrpcPcmClient;
import com.demo.orchestrator.config.MediaProperties;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final GrpcPcmClient grpcPcmClient;
    private final MediaProperties properties;

    public HealthController(GrpcPcmClient grpcPcmClient, MediaProperties properties) {
        this.grpcPcmClient = grpcPcmClient;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        String pcmStatus = checkPcmService();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "pcmService", pcmStatus));
    }

    private String checkPcmService() {
        String target = properties.pcmGrpcTarget();
        if (target == null || target.isBlank()) {
            return "disabled";
        }
        try {
            return grpcPcmClient.checkHealth() ? "ok" : "unreachable";
        } catch (Exception e) {
            return "unreachable";
        }
    }
}
