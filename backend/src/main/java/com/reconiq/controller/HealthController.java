package com.reconiq.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String applicationName;
    private final String version;

    public HealthController(@Value("${spring.application.name:reconiq-backend}") String applicationName,
                            @Value("${reconiq.version:1.0.0}") String version) {
        this.applicationName = applicationName;
        this.version = version;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", applicationName,
                "version", version,
                "timestamp", LocalDateTime.now().toString());
    }
}
