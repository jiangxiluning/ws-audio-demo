package com.demo.orchestrator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.demo.orchestrator.websocket.AudioStreamWebSocketHandler;

@Configuration
@EnableConfigurationProperties(MediaProperties.class)
@EnableWebSocket
public class AppConfig implements WebSocketConfigurer, WebMvcConfigurer {

    private final AudioStreamWebSocketHandler audioStreamWebSocketHandler;

    public AppConfig(AudioStreamWebSocketHandler audioStreamWebSocketHandler) {
        this.audioStreamWebSocketHandler = audioStreamWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioStreamWebSocketHandler, "/ws/v1/stream/{jobId}").setAllowedOrigins("*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*");
    }
}
