package com.azki.reservation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class SwaggerUrlLoggerConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @EventListener(ApplicationStartedEvent.class)
    public void logSwaggerUiUrl() {
        String baseUrl = "http://localhost:" + serverPort + contextPath;
        String swaggerUrl = baseUrl + "/swagger-ui/index.html";

        log.info("");
        log.info("┌───────────────────────────────────────────────────┐");
        log.info("│                                                   │");
        log.info("│   API Documentation is available at:              │");
        log.info("│   {}", swaggerUrl);
        log.info("│                                                   │");
        log.info("└───────────────────────────────────────────────────┘");
        log.info("");
    }
}
