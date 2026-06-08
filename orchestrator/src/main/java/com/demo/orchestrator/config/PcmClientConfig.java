package com.demo.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.demo.orchestrator.client.GrpcPcmClient;

@Configuration
public class PcmClientConfig {

    @Bean(destroyMethod = "close")
    GrpcPcmClient grpcPcmClient(MediaProperties properties) {
        return new GrpcPcmClient(properties);
    }
}
