package com.mockinterview.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class SecurityConfigValidator {

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.refresh-secret}")
    private String jwtRefreshSecret;

    @PostConstruct
    public void validateSecurityConfig() {
        validateSecret("JWT Secret", jwtSecret);
        validateSecret("JWT Refresh Secret", jwtRefreshSecret);
    }

    private void validateSecret(String name, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(name + " is not configured. Please set the environment variable.");
        }

        if (secret.length() < 32) {
            throw new IllegalStateException(name + " must be at least 32 characters long for security.");
        }

        try {
            // Verify it can be decoded as base64 (our config expects base64 encoded keys)
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length < 32) {
                throw new IllegalStateException(name + " must decode to at least 256 bits (32 bytes) for HS256.");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " must be a valid Base64-encoded string. Current value: " + secret);
        }
    }
}