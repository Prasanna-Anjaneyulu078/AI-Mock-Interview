package com.mockinterview.controller;

import com.mockinterview.service.ProviderHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System Health", description = "Endpoints for system health checks")
public class HealthController {

    private final ProviderHealthService providerHealthService;

    public HealthController(ProviderHealthService providerHealthService) {
        this.providerHealthService = providerHealthService;
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health", description = "Returns the configuration and health status of external providers.")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(providerHealthService.getHealthStatus());
    }
}
