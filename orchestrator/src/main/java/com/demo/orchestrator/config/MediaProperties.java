package com.demo.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media")
public record MediaProperties(
        String storageDir,
        double minDurationSec,
        double maxDurationSec,
        double maxChunkSec,
        double minChunkSec,
        int wsConnectTimeoutSec,
        String pcmGrpcTarget,
        double defaultGainDb,
        int streamChunkBytes
) {
}
