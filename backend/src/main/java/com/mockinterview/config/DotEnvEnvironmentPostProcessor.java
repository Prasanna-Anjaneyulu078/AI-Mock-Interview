package com.mockinterview.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads a local {@code .env} file into the Spring {@link ConfigurableEnvironment}
 * at startup, so that {@code application.yml} placeholders such as
 * {@code ${GEMINI_API_KEY:}} resolve from it.
 *
 * <p>Spring Boot does not read {@code .env} files natively; this bridges the gap
 * without pulling in an external dotenv dependency. Keys defined in the real OS
 * environment keep precedence (we add the source with the lowest priority),
 * matching the usual {@code .env}-overrides-nothing convention.</p>
 *
 * <p>The file location is resolved in this order:</p>
 * <ol>
 *   <li>{@code DOTENV_PATH} system property / environment variable (if set)</li>
 *   <li>{@code ./.env} relative to the working directory</li>
 *   <li>{@code ../.env} (handy when launched from a sibling module)</li>
 * </ol>
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource resource = resolveDotEnvResource(environment);
        if (resource == null || !resource.exists()) {
            return;
        }

        Map<String, Object> properties = parseDotEnv(resource);
        if (properties.isEmpty()) {
            return;
        }

        PropertySource<Map<String, Object>> source =
                new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
        // Add with lowest precedence: OS env / application.yml still win on collisions,
        // and the .env-only keys (e.g. GEMINI_API_KEY) are always available.
        environment.getPropertySources().addLast(source);
    }

    private Resource resolveDotEnvResource(ConfigurableEnvironment environment) {
        String explicit = environment.getProperty("DOTENV_PATH");
        if (explicit != null && !explicit.isBlank()) {
            return new FileSystemResource(explicit);
        }
        FileSystemResource local = new FileSystemResource(".env");
        if (local.exists()) {
            return local;
        }
        return new FileSystemResource("../.env");
    }

    private Map<String, Object> parseDotEnv(Resource resource) {
        Map<String, Object> properties = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = unquote(trimmed.substring(eq + 1).trim());
                if (!key.isEmpty()) {
                    properties.put(key, value);
                }
            }
        } catch (IOException e) {
            // Non-fatal: missing/unreadable .env simply means no extra properties.
            return properties;
        }
        return properties;
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    @Override
    public int getOrder() {
        // Run before application config is finalized so placeholders resolve.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
