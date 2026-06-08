package com.demo.orchestrator.api.dto;

public record UploadResponse(String uri, double durationSeconds, String originalFormat) {
}
