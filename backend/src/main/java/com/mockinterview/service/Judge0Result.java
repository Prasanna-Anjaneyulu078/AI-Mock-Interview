package com.mockinterview.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated result of running a candidate's code through Judge0 against a set of test
 * cases. The field names match the API contract expected by the frontend:
 *
 * <pre>
 * {
 *   "stdout": "",
 *   "stderr": "",
 *   "executionTime": 0,
 *   "memoryUsage": 0,
 *   "passed": true,
 *   "passedTests": 0,
 *   "totalTests": 0
 * }
 * </pre>
 *
 * {@code passed} is true only when every test case passed. When no test cases are
 * supplied, the code is run once as a smoke test and {@code passed} reflects whether the
 * run was accepted (compiled and exited cleanly).
 *
 * {@code statusDescription} / {@code compileOutput} are diagnostic extras not part of the
 * required contract but useful for debugging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Judge0Result {

    private String stdout;
    private String stderr;
    private Double executionTime;   // seconds
    private Double memoryUsage;     // KB

    private boolean passed;
    private int passedTests;
    private int totalTests;

    private String statusDescription;
    private String compileOutput;
}
