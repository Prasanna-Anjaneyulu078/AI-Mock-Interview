package com.mockinterview.service;

import com.mockinterview.config.properties.Judge0Properties;
import com.mockinterview.entity.TestCase;
import com.mockinterview.exception.InvalidApiKeyException;
import com.mockinterview.exception.Judge0ForbiddenException;
import com.mockinterview.exception.Judge0QuotaExceededException;
import com.mockinterview.exception.SubscriptionExpiredException;
import com.mockinterview.util.ResilienceUtil;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Real Judge0 code-execution client (spec #10 — "Judge0 Reliability").
 *
 * <p>Submits source code, then polls for the result with a bounded timeout (no unbounded
 * waits). Transient failures are retried with backoff and an open circuit breaker prevents
 * cascading failures when Judge0 is down.
 *
 * <p>Reliability is graceful: when Judge0 is unconfigured, unreachable, or erroring,
 * {@link #execute} returns {@code null} so the caller can fall back to the existing
 * Gemini-based code evaluation (mirrors how {@code MurfService} degrades).
 *
 * <p>Test-case execution: the candidate's code is run once per {@link TestCase} — visible
 * cases first, then hidden — with each case's {@code input} as stdin. The trimmed stdout is
 * compared against the case's {@code expectedOutput}; the aggregated result reports
 * {@code passedTests}/{@code totalTests} and an overall {@code passed} flag.
 */
@Slf4j
@Service
public class Judge0Service {

    private static final long POLL_INTERVAL_MS = 2000;
    private static final int MAX_POLLS = 12;

    /** Map our frontend language ids to Judge0 language_ids. */
    private static final Map<String, Integer> LANG_IDS = Map.of(
            "javascript", 63,
            "python", 71,
            "java", 62,
            "cpp", 54,
            "c", 50,
            "c++", 54,
            "typescript", 74,
            "go", 60,
            "rust", 73
    );

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String rapidApiHost; // derived from baseUrl
    private final ResilienceUtil.CircuitBreaker circuitBreaker =
            new ResilienceUtil.CircuitBreaker(5, 30_000);
    private final RetryTemplate retryTemplate;

    public static class NonTransientJudge0Exception extends RuntimeException {
        public NonTransientJudge0Exception(String message, Throwable cause) { super(message, cause); }
    }

    public Judge0Service(@Qualifier("judge0RestTemplate") RestTemplate restTemplate,
                         Judge0Properties judge0Properties,
                         MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.baseUrl = judge0Properties.getUrl();
        this.apiKey = judge0Properties.getApiKey();
        this.circuitBreaker.setMetrics(meterRegistry, "judge0");
        
        // Extract the host portion for the X-RapidAPI-Host header (e.g. "judge0-ce.p.rapidapi.com")
        String host = "";
        try {
            if (this.baseUrl != null && !this.baseUrl.isBlank()) {
                host = new java.net.URI(this.baseUrl).getHost();
            }
        } catch (Exception ignored) {}
        this.rapidApiHost = host;
        
        this.retryTemplate = new RetryTemplate();
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(4000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(Exception.class, true);
        // Deterministic auth/quota/subscription failures must never be retried.
        retryableExceptions.put(InvalidApiKeyException.class, false);
        retryableExceptions.put(SubscriptionExpiredException.class, false);
        retryableExceptions.put(Judge0ForbiddenException.class, false);
        retryableExceptions.put(Judge0QuotaExceededException.class, false);
        retryableExceptions.put(NonTransientJudge0Exception.class, false);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(2, retryableExceptions, true);
        retryTemplate.setRetryPolicy(retryPolicy);
    }

    /**
     * Execute {@code code} in the given language against the supplied test cases and return
     * the aggregated result, or {@code null} if Judge0 is unavailable (caller should fall
     * back to AI eval).
     *
     * @param testCases visible + hidden cases; when empty/blank the code is run once as a
     *                  smoke test (compiles + runs) and {@code totalTests} is 0.
     */
    public Judge0Result execute(String code, String language, List<TestCase> testCases) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("⚠️ Judge0 not configured (app.ai.judge0.url) — falling back to AI code evaluation.");
            return null;
        }
        if (circuitBreaker.isOpen()) {
            log.warn("⚠️ Judge0 circuit breaker OPEN — falling back to AI code evaluation.");
            return null;
        }

        int langId = mapLang(language);
        Judge0Result result = new Judge0Result();

        if (testCases == null || testCases.isEmpty()) {
            // ── Smoke run: compile + execute once, no assertions ──
            result.setTotalTests(0);
            Judge0Run run = runOne(code, langId, "");
            if (run == null) {
                circuitBreaker.onFailure();
                return null;
            }
            circuitBreaker.onSuccess();
            result.setStdout(blankToEmpty(run.stdout()));
            result.setStderr(blankToEmpty(run.stderr()));
            result.setStatusDescription(run.statusDescription());
            result.setCompileOutput(run.compileOutput());
            result.setExecutionTime(run.time());
            result.setMemoryUsage(run.memory());
            result.setPassed(run.accepted());
            return result;
        }

        // ── Test-case run: visible first, then hidden ──
        List<TestCase> ordered = new ArrayList<>(testCases);
        ordered.sort((a, b) -> Boolean.compare(a.isHidden(), b.isHidden())); // visible (false) before hidden (true)

        int passed = 0;
        double maxTime = 0.0;
        double maxMem = 0.0;
        StringBuilder failureDetail = new StringBuilder();
        boolean transientFailure = false;
        
        List<Judge0Result.TestCaseDetail> testCaseDetails = new ArrayList<>();

        for (TestCase tc : ordered) {
            Judge0Run run = runOne(code, langId, tc.getInput());
            if (run == null) {
                transientFailure = true; // submit/poll failed entirely — no result to grade
                break;
            }
            maxTime = Math.max(maxTime, run.time());
            maxMem = Math.max(maxMem, run.memory());

            if (run.compileOutput() != null && !run.compileOutput().isBlank()) {
                result.setCompileOutput(run.compileOutput());
            }
            if (run.statusDescription() != null && !run.statusDescription().isBlank()) {
                result.setStatusDescription(run.statusDescription());
            }

            boolean ok = run.accepted() && outputsMatch(run.stdout(), tc.getExpectedOutput());
            if (ok) {
                passed++;
            } else if (failureDetail.length() == 0) {
                failureDetail.append("Test ")
                        .append(tc.getName() != null ? tc.getName() : "#" + (passed + 1))
                        .append(" failed. Expected: [")
                        .append(tc.getExpectedOutput() == null ? "" : tc.getExpectedOutput())
                        .append("] | Got: [")
                        .append(run.stdout() == null ? "" : run.stdout())
                        .append("]");
                if (run.stderr() != null && !run.stderr().isBlank()) {
                    failureDetail.append(" | stderr: ").append(run.stderr());
                }
            }

            Judge0Result.TestCaseDetail detail = Judge0Result.TestCaseDetail.builder()
                .name(tc.getName() != null ? tc.getName() : "Test Case " + (testCaseDetails.size() + 1))
                .passed(ok)
                .isHidden(tc.isHidden())
                .expectedOutput(tc.isHidden() ? "Hidden" : tc.getExpectedOutput())
                .actualOutput(tc.isHidden() ? "Hidden" : run.stdout())
                .time(run.time())
                .memory(run.memory())
                .build();
            testCaseDetails.add(detail);
        }

        result.setTotalTests(ordered.size());
        result.setPassedTests(passed);
        result.setPassed(passed == ordered.size() && !transientFailure);
        result.setExecutionTime(maxTime);
        result.setMemoryUsage(maxMem);
        result.setStderr(failureDetail.toString());
        result.setTestCaseResults(testCaseDetails);

        if (transientFailure) {
            circuitBreaker.onFailure();
            if (testCaseDetails.isEmpty()) {
                return null;
            }
        } else {
            circuitBreaker.onSuccess();
        }
        return result;
    }

    /** Submit + poll a single run. Returns {@code null} on transient failure. */
    private Judge0Run runOne(String code, int langId, String stdin) {
        String token;
        try {
            token = retryTemplate.execute(context -> submit(code, langId, stdin));
        } catch (com.mockinterview.exception.AIProviderException e) {
            throw e;
        } catch (NonTransientJudge0Exception e) {
            log.error("⚠️ Judge0 deterministic failure: {}", e.getMessage());
            throw new com.mockinterview.exception.AIProviderException("Judge0", 400, "BAD_REQUEST", "Execution failed: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("⚠️ Judge0 submit failed: {}", e.getMessage());
            throw new com.mockinterview.exception.AIProviderException("Judge0", 503, "CONNECTION_FAILURE", "Judge0 connection failed: " + e.getMessage(), null);
        }
        if (token == null) {
            throw new com.mockinterview.exception.AIProviderException("Judge0", 503, "CONNECTION_FAILURE", "Judge0 returned no token.", null);
        }
        Judge0Run run = poll(token);
        if (run == null) {
            throw new com.mockinterview.exception.AIProviderException("Judge0", 408, "TIMEOUT", "Judge0 execution timed out.", null);
        }
        return run;
    }

    private String submit(String code, int langId, String stdin) throws Exception {
        String url = baseUrl + "/submissions?base64_encoded=false&wait=false";
        Map<String, Object> body = new HashMap<>();
        body.put("source_code", code);
        body.put("language_id", langId);
        body.put("stdin", stdin != null ? stdin : "");

        HttpHeaders headers = buildRapidApiHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        logJudge0Request(url, headers, code, langId, stdin);

        try {
            var resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object token = resp.getBody().get("token");
                if (token != null) return token.toString();
            }
            throw new IllegalStateException("Judge0 submit returned no token (status " + resp.getStatusCode() + ")");
        } catch (HttpStatusCodeException hse) {
            int httpCode = hse.getStatusCode().value();
            String responseBody = hse.getResponseBodyAsString();
            log.error("Judge0 HTTP {} from {} — body: {}", httpCode, url, responseBody);
            throw classifyJudge0Error(httpCode, responseBody, hse);
        } catch (Exception e) {
            throw new RuntimeException("Judge0 Execution Failed", e);
        }
    }

    /** Logs request diagnostics so auth / URL / payload problems are visible without a live call. */
    private void logJudge0Request(String url, HttpHeaders headers, String code, int langId, String stdin) {
        boolean keyPresent = apiKey != null && !apiKey.isBlank();
        String maskedKey = keyPresent && apiKey.length() >= 4
                ? apiKey.substring(0, 4) + "…" : (keyPresent ? "***" : "<MISSING>");
        boolean hostPresent = rapidApiHost != null && !rapidApiHost.isBlank();
        if (!keyPresent || !hostPresent) {
            log.warn("⚠️ Judge0 request may fail auth: X-RapidAPI-Key={}, X-RapidAPI-Host={}",
                    keyPresent ? maskedKey : "<MISSING>", hostPresent ? rapidApiHost : "<MISSING>");
        } else {
            log.debug("Judge0 request → POST {} | key={} host={} | payload: source_code={}B language_id={} stdin={}B",
                    url, maskedKey, rapidApiHost,
                    code == null ? 0 : code.length(), langId, stdin == null ? 0 : stdin.length());
        }
    }

    private RuntimeException classifyJudge0Error(int httpCode, String responseBody, Throwable cause) {
        String lower = responseBody == null ? "" : responseBody.toLowerCase();
        if (httpCode == 403 || httpCode == 401) {
            if (lower.contains("invalid") || lower.contains("key") || lower.contains("unauthorized")) {
                return new InvalidApiKeyException(responseBody);
            }
            if (lower.contains("subscri") || lower.contains("not subscribed")
                    || lower.contains("inactive") || lower.contains("expired")) {
                return new SubscriptionExpiredException(responseBody);
            }
            return new Judge0ForbiddenException(responseBody);
        }
        if (httpCode == 429) {
            return new Judge0QuotaExceededException(responseBody);
        }
        if (httpCode == 408 || httpCode == 504) {
            com.mockinterview.exception.AIProviderException ex = new com.mockinterview.exception.AIProviderException(
                    "Judge0", httpCode, "TIMEOUT", "Judge0 connection timed out.", null);
            if (cause != null) ex.initCause(cause);
            return ex;
        }
        if (httpCode >= 400 && httpCode < 500) {
            return new NonTransientJudge0Exception(
                    "Non-retryable client error: " + httpCode + " | body=" + responseBody, cause);
        }
        // 5xx — surface as a 500 with a clear message (per agreed behavior).
        com.mockinterview.exception.AIProviderException ex = new com.mockinterview.exception.AIProviderException(
                "Judge0", 500, "JUDGE0_UNAVAILABLE", "Judge0 service unavailable.", null);
        if (cause != null) ex.initCause(cause);
        return ex;
    }

    /** Bounded poll loop — returns as soon as the run finishes, or null on timeout/failure. */
    private Judge0Run poll(String token) {
        String url = baseUrl + "/submissions/" + token + "?base64_encoded=false";
        HttpHeaders headers = buildRapidApiHeaders();

        for (int i = 0; i < MAX_POLLS; i++) {
            try {
                var resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) resp.getBody();
                Map<?, ?> statusObj = m.get("status") instanceof Map ? (Map<?, ?>) m.get("status") : null;
                Integer statusId = null;
                String statusDesc = null;
                if (statusObj != null) {
                    statusId = statusObj.get("id") instanceof Number ? ((Number) statusObj.get("id")).intValue() : null;
                    statusDesc = statusObj.get("description") instanceof String ? (String) statusObj.get("description") : null;
                }
                // 1 = In Queue, 2 = Processing → keep polling (bounded). 3 = Accepted, others = done/error.
                if (statusId != null && statusId <= 2) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }
                boolean accepted = statusId != null && statusId == 3;
                return new Judge0Run(
                        accepted,
                        asString(m.get("stdout")),
                        asString(m.get("stderr")),
                        statusDesc,
                        asString(m.get("compile_output")),
                        parseDouble(asString(m.get("time"))),
                        parseDouble(asString(m.get("memory")))
                );
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new com.mockinterview.exception.AIProviderException("Judge0", 503, "CONNECTION_FAILURE", "Judge0 poll interrupted.", null);
            } catch (HttpStatusCodeException hse) {
                int httpCode = hse.getStatusCode().value();
                log.error("Judge0 poll HTTP {} for token {} — body: {}", httpCode, token, hse.getResponseBodyAsString());
                throw classifyJudge0Error(httpCode, hse.getResponseBodyAsString(), hse);
            } catch (Exception e) {
                log.warn("⚠️ Judge0 poll error: {}", e.getMessage());
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ie2) {
                    Thread.currentThread().interrupt();
                    throw new com.mockinterview.exception.AIProviderException("Judge0", 503, "CONNECTION_FAILURE", "Judge0 poll interrupted.", null);
                }
            }
        }
        log.warn("Judge0 poll timed out after {} attempts for token {}", MAX_POLLS, token);
        return null;
    }

    private int mapLang(String language) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Language cannot be null or empty");
        }
        String lowerLang = language.toLowerCase();
        if (!LANG_IDS.containsKey(lowerLang)) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        return LANG_IDS.get(lowerLang);
    }

    /** Builds the common RapidAPI authentication headers. */
    private HttpHeaders buildRapidApiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-RapidAPI-Key", apiKey);
        }
        if (rapidApiHost != null && !rapidApiHost.isBlank()) {
            headers.set("X-RapidAPI-Host", rapidApiHost);
        }
        return headers;
    }

    private static boolean outputsMatch(String actual, String expected) {
        String a = actual == null ? "" : actual.strip();
        String e = expected == null ? "" : expected.strip();
        return a.equals(e);
    }

    // Obsolete with RetryTemplate configuration
    // private static boolean isRetryable(Exception e) { ... }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }

    private static double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Immutable result of a single Judge0 run. */
    private record Judge0Run(boolean accepted, String stdout, String stderr,
                             String statusDescription, String compileOutput, double time, double memory) {
    }
}
