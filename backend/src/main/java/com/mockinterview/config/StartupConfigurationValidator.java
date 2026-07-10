package com.mockinterview.config;

import com.mockinterview.config.properties.AssemblyAiProperties;
import com.mockinterview.service.ai.AIProviderRouter;
import com.mockinterview.config.properties.Judge0Properties;
import com.mockinterview.config.properties.MurfProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Prints a human-readable configuration report at startup (after the Spring context
 * has refreshed). Each AI provider is marked {@code ✓ configured} or
 * {@code ⚠ disabled} based on whether its required settings are present; the
 * multipart upload limit is also confirmed.
 *
 * <p>Note: JWT secret validation is handled separately by
 * {@link com.mockinterview.config.SecurityConfigValidator} via {@code @PostConstruct};
 * if the secret is missing the application fails to start <em>before</em> this report
 * runs, which is the desired fail-fast behaviour.
 */
@Component
public class StartupConfigurationValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigurationValidator.class);

    private final AIProviderRouter aiProvider;
    private final AssemblyAiProperties assemblyAi;
    private final MurfProperties murf;
    private final Judge0Properties judge0;

    @Value("${spring.servlet.multipart.max-file-size:1MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:1MB}")
    private String maxRequestSize;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.security.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.security.jwt.refresh-secret:}")
    private String jwtRefreshSecret;

    public StartupConfigurationValidator(AIProviderRouter aiProvider, AssemblyAiProperties assemblyAi,
                                         MurfProperties murf, Judge0Properties judge0) {
        this.aiProvider = aiProvider;
        this.assemblyAi = assemblyAi;
        this.murf = murf;
        this.judge0 = judge0;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("prod".equalsIgnoreCase(activeProfile)) {
            if (!aiProvider.isHealthy()) {
                throw new IllegalStateException("Gemini API key is required in production.");
            }
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalStateException("JWT_SECRET is required in production.");
            }
            if (jwtRefreshSecret == null || jwtRefreshSecret.isBlank()) {
                throw new IllegalStateException("JWT_REFRESH_SECRET is required in production.");
            }
        }

        log.info("════════════════════════════════════════════════════════");
        log.info("  AI Provider & Configuration Startup Report");
        log.info("════════════════════════════════════════════════════════");

        boolean aiOk = aiProvider.isHealthy();
        if (aiOk) {
            log.info("  {} AI Providers: ENABLED", flag(true));
            log.info("    Model: {}", aiProvider.getActiveProvider());
        } else {
            log.info("  {} AI Providers: DISABLED", flag(false));
            log.info("    Reason: Invalid API key format");
        }

        boolean assemblyOk = assemblyAi.isConfigured();
        log.info("  {} AssemblyAI: {}", flag(assemblyOk), assemblyOk ? "ENABLED" : "DISABLED");

        boolean murfOk = murf.isConfigured();
        log.info("  {} Murf: {}", flag(murfOk), murfOk ? "ENABLED" : "DISABLED");

        boolean judge0Ok = judge0.isConfigured();
        if (judge0Ok) {
            log.info("  {} Judge0: ENABLED", flag(true));
            log.info("    Execution: ENABLED");
            log.info("    URL: {}", judge0.getUrl());
        } else {
            log.info("  {} Judge0: DISABLED", flag(false));
            log.info("    Reason: Invalid URL");
        }

        boolean multipartOk = isAtLeast10Mb(maxFileSize) && isAtLeast10Mb(maxRequestSize);
        log.info("  {} Multipart uploads configured (max-file: {}, max-request: {})",
                flag(multipartOk), maxFileSize, maxRequestSize);

        if (!aiOk) {
            log.warn("================================================");
            log.warn("WARNING");
            log.warn("AI INTERVIEW MODE IS DISABLED");
            log.warn("");
            log.warn("Dynamic Questions : OFF");
            log.warn("Resume Analysis   : OFF");
            log.warn("AI Scoring        : OFF");
            log.warn("Follow-Ups        : OFF");
            log.warn("Adaptive Engine   : OFF");
            log.warn("");
            log.warn("System will use fallback questions.");
            log.warn("================================================");
        } else {
            log.info("================================================");
            log.info("AI INTERVIEW MODE ENABLED");
            log.info("");
            log.info("Dynamic Questions : ON");
            log.info("Resume Analysis   : ON");
            log.info("AI Scoring        : ON");
            log.info("Follow-Ups        : ON");
            log.info("Adaptive Engine   : ON");
            log.info("================================================");
        }

        log.info("════════════════════════════════════════════════════════");
    }

    private String flag(boolean ok) {
        return ok ? "✓" : "⚠"; // ✓ / ⚠
    }

    private boolean isAtLeast10Mb(String size) {
        try {
            String s = size.toUpperCase().trim();
            if (s.endsWith("MB")) {
                return Double.parseDouble(s.substring(0, s.length() - 2)) >= 10.0;
            }
            if (s.endsWith("KB")) {
                return Double.parseDouble(s.substring(0, s.length() - 2)) >= 10_240.0;
            }
            if (s.endsWith("GB")) {
                return true;
            }
        } catch (Exception ignored) {
            // unparseable -> treat as not configured
        }
        return false;
    }
}

