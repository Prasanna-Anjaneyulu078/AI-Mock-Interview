package com.mockinterview.util;



/**
 * Lightweight, dependency-free resilience helpers used to harden outbound calls to
 * external AI providers (Gemini, AssemblyAI, Murf) without pulling in a circuit-breaker
 * library. Provides:
 *  <ul>
 *    <li>{@link #retry} — exponential backoff retry, skipping non-retryable errors.</li>
 *    <li>{@link CircuitBreaker} — a simple sliding-window circuit breaker.</li>
 *  </ul>
 */
public final class ResilienceUtil {

    private ResilienceUtil() {
    }

    // Retry functionality has been migrated to Spring RetryTemplate.

    /**
     * Minimal circuit breaker: opens after {@code failureThreshold} consecutive failures,
     * half-opens after {@code cooldownMs}, and closes again on success. All state changes
     * are synchronized because a single breaker instance is shared across requests.
     */
    public static class CircuitBreaker {
        private final int failureThreshold;
        private final long cooldownMs;
        private int consecutiveFailures = 0;
        private long openedAt = 0;
        private boolean open = false;
        
        private io.micrometer.core.instrument.MeterRegistry registry;
        private String name;

        public CircuitBreaker(int failureThreshold, long cooldownMs) {
            this.failureThreshold = failureThreshold;
            this.cooldownMs = cooldownMs;
        }

        public void setMetrics(io.micrometer.core.instrument.MeterRegistry registry, String name) {
            this.registry = registry;
            this.name = name;
            
            if (registry != null && name != null) {
                io.micrometer.core.instrument.Gauge.builder("circuit.breaker.state", this, cb -> cb.isOpen() ? 1.0 : 0.0)
                        .tag("name", name)
                        .description("Circuit breaker state (1=open, 0=closed)")
                        .register(registry);
            }
        }
        
        public synchronized boolean allowRequest() {
            if (!open) {
                return true;
            }
            if (System.currentTimeMillis() - openedAt >= cooldownMs) {
                open = false; // half-open: allow one trial request
                consecutiveFailures = 0;
                return true;
            }
            return false;
        }

        public synchronized void onSuccess() {
            open = false;
            consecutiveFailures = 0;
            if (registry != null && name != null) {
                registry.counter("circuit.breaker.calls", "name", name, "result", "success").increment();
            }
        }

        public synchronized void onFailure() {
            consecutiveFailures++;
            if (registry != null && name != null) {
                registry.counter("circuit.breaker.calls", "name", name, "result", "failure").increment();
            }
            if (consecutiveFailures >= failureThreshold) {
                open = true;
                openedAt = System.currentTimeMillis();
            }
        }

        public synchronized boolean isOpen() {
            return open;
        }
    }
}
