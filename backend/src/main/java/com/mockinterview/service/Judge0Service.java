package com.mockinterview.service;

import com.mockinterview.config.properties.Judge0Properties;
import com.mockinterview.entity.TestCase;
import com.mockinterview.util.ResilienceUtil;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                         Judge0Properties judge0Properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = judge0Properties.getUrl();
        this.apiKey = judge0Properties.getApiKey();
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
            System.err.println("⚠️ Judge0 not configured (app.ai.judge0.url) — falling back to AI code evaluation.");
            return null;
        }
        if (circuitBreaker.isOpen()) {
            System.err.println("⚠️ Judge0 circuit breaker OPEN — falling back to AI code evaluation.");
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

        for (TestCase tc : ordered) {
            Judge0Run run = runOne(code, langId, tc.getInput());
            if (run == null) {
                transientFailure = true; // submit/poll failed entirely — no result to grade
                break;
            }
            maxTime = Math.max(maxTime, run.time());
            maxMem = Math.max(maxMem, run.memory());

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
        }

        result.setTotalTests(ordered.size());
        result.setPassedTests(passed);
        result.setPassed(passed == ordered.size() && !transientFailure);
        result.setExecutionTime(maxTime);
        result.setMemoryUsage(maxMem);
        result.setStderr(failureDetail.toString());

        if (transientFailure) {
            circuitBreaker.onFailure();
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
        } catch (NonTransientJudge0Exception e) {
            System.err.println("⚠️ Judge0 deterministic failure: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("⚠️ Judge0 submit failed: " + e.getMessage());
            return null;
        }
        if (token == null) {
            return null;
        }
        return poll(token);
    }

    private String submit(String code, int langId, String stdin) throws Exception {
        String url = baseUrl + "/submissions?base64_encoded=false&wait=false";
        Map<String, Object> body = new HashMap<>();
        body.put("source_code", code);
        body.put("language_id", langId);
        body.put("stdin", stdin != null ? stdin : "");

        HttpHeaders headers = buildRapidApiHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            var resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object token = resp.getBody().get("token");
                if (token != null) return token.toString();
            }
            throw new IllegalStateException("Judge0 submit returned no token (status " + resp.getStatusCode() + ")");
        } catch (HttpStatusCodeException hse) {
            int httpCode = hse.getStatusCode().value();
            if (httpCode >= 400 && httpCode < 500 && httpCode != HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new NonTransientJudge0Exception("Non-retryable client error: " + httpCode, hse);
            }
            throw hse;
        }
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
                return null;
            } catch (Exception e) {
                System.err.println("⚠️ Judge0 poll error: " + e.getMessage());
                return null;
            }
        }
        System.err.println("⚠️ Judge0 poll timed out after " + MAX_POLLS + " attempts — falling back to AI evaluation.");
        return null;
    }

    private int mapLang(String language) {
        if (language == null) return LANG_IDS.getOrDefault("python", 71);
        return LANG_IDS.getOrDefault(language.toLowerCase(), 71);
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
