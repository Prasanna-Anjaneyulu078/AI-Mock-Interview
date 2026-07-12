package com.mockinterview.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ensures that starter code shown to candidates contains only method/function
 * SIGNATURES with empty stubs — never full implementations or solution bodies.
 *
 * <p>The sanitizer works language-by-language and applies conservative regex
 * stripping to replace any non-trivial body with the canonical stub comment.
 * If parsing fails for a language, the safe empty stub is returned instead of
 * leaking the solution.
 */
public final class StarterCodeSanitizer {

    private StarterCodeSanitizer() {}

    /** Comment stub injected where a body was stripped. */
    private static final String JAVA_STUB    = "    // Write your code here\n";
    private static final String PY_STUB      = "    pass\n";
    private static final String JS_STUB      = "    // Write your code here\n";
    private static final String CPP_STUB     = "    // Write your code here\n";
    private static final String C_STUB       = "    /* Write your code here */\n";

    // Regex: any body content that is more than just whitespace/stub comments
    private static final Pattern HAS_REAL_CODE = Pattern.compile(
            "(?m)^\\s{2,}(?!//|#|pass|/\\*)[^\\s{}]",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Given a raw starterCode string (single-language), returns a version
     * containing only the method/function signature with an empty stub body.
     *
     * @param code raw code from AI
     * @param language language key e.g. "java", "python", "javascript", "c++", "c"
     * @return sanitized starter code
     */
    public static String sanitize(String code, String language) {
        if (code == null || code.isBlank()) return defaultStub(language);
        String lang = language == null ? "javascript" : language.toLowerCase().trim();
        try {
            return switch (lang) {
                case "java"       -> sanitizeJava(code);
                case "python"     -> sanitizePython(code);
                case "javascript",
                     "js",
                     "typescript",
                     "ts"         -> sanitizeJs(code);
                case "c++",
                     "cpp"        -> sanitizeCpp(code);
                case "c"          -> sanitizeC(code);
                default           -> sanitizeJs(code); // safe generic fallback
            };
        } catch (Exception e) {
            // Safety net: on any parsing failure return a clean stub
            return defaultStub(lang);
        }
    }

    /**
     * Sanitizes a JSON map of language→code entries (CodingModule flow).
     * The map is mutated in-place and the same reference is returned.
     */
    public static Map<String, Object> sanitizeMap(Map<String, Object> starterCodeMap) {
        if (starterCodeMap == null) return new LinkedHashMap<>();
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : starterCodeMap.entrySet()) {
            String lang = e.getKey();
            String raw  = e.getValue() instanceof String s ? s : "";
            clean.put(lang, sanitize(raw, lang));
        }
        return clean;
    }

    // ── Language-specific sanitizers ──────────────────────────────────────────

    private static String sanitizeJava(String code) {
        // Keep class/method declaration lines, replace body content
        return replaceBody(code, "{", "}", JAVA_STUB);
    }

    private static String sanitizePython(String code) {
        // For Python: keep "def ..." and "class ..." lines, truncate body to "pass"
        StringBuilder out = new StringBuilder();
        String[] lines = code.split("\n");
        boolean inDef = false;
        int defIndent = 0;
        for (String line : lines) {
            String trimmed = line.stripLeading();
            int indent = line.length() - trimmed.length();

            if (trimmed.startsWith("def ") || trimmed.startsWith("class ") || trimmed.startsWith("async def ")) {
                if (inDef && !out.isEmpty()) {
                    // Close previous def with pass
                    out.append(" ".repeat(defIndent + 4)).append("pass\n");
                }
                out.append(line).append("\n");
                inDef = true;
                defIndent = indent;
            } else if (inDef) {
                // Keep docstrings / type hints on next line, skip implementations
                if (trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")
                        || trimmed.startsWith("#") || trimmed.isBlank()) {
                    // allow comments and blank lines through only before first real code
                } else {
                    // Stop copying body — emit pass and exit
                    out.append(" ".repeat(defIndent + 4)).append("pass\n");
                    inDef = false;
                }
            }
        }
        if (inDef) out.append(" ".repeat(defIndent + 4)).append("pass\n");
        return out.isEmpty() ? defaultStub("python") : out.toString();
    }

    private static String sanitizeJs(String code) {
        return replaceBody(code, "{", "}", JS_STUB);
    }

    private static String sanitizeCpp(String code) {
        return replaceBody(code, "{", "}", CPP_STUB);
    }

    private static String sanitizeC(String code) {
        return replaceBody(code, "{", "}", C_STUB);
    }

    // ── Generic brace-pair body replacer ────────────────────────────────────

    /**
     * Finds the outermost function/method body and replaces its contents with
     * the stub if the body contains any real implementation statements.
     */
    private static String replaceBody(String code, String open, String close, String stub) {
        // Check if there is any substantive code in the body
        Matcher m = HAS_REAL_CODE.matcher(code);
        if (!m.find()) return code; // already a stub — return as-is

        // Find the first opening brace that starts a function body
        int depth = 0;
        int firstOpen = -1;
        int lastClose = -1;
        char[] chars = code.toCharArray();
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inString) {
                if (c == stringChar && (i == 0 || chars[i - 1] != '\\')) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; stringChar = c; continue; }
            if (c == '{') {
                depth++;
                if (depth == 1) firstOpen = i;
            } else if (c == '}') {
                if (depth == 1) lastClose = i;
                depth--;
            }
        }

        if (firstOpen < 0 || lastClose < 0) return code; // no balanced braces found
        // Rebuild: signature + open brace + stub + close brace
        String before = code.substring(0, firstOpen + 1); // includes '{'
        String after  = code.substring(lastClose);         // includes '}'
        return before + "\n" + stub + after;
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    private static String defaultStub(String language) {
        if (language == null) return "function solution() {\n" + JS_STUB + "}\n";
        return switch (language.toLowerCase()) {
            case "java"       -> "class Solution {\n    public void solve() {\n" + JAVA_STUB + "    }\n}\n";
            case "python"     -> "def solution():\n" + PY_STUB;
            case "c++", "cpp" -> "class Solution {\npublic:\n    void solve() {\n" + CPP_STUB + "    }\n};\n";
            case "c"          -> "void solution() {\n" + C_STUB + "}\n";
            default           -> "function solution() {\n" + JS_STUB + "}\n";
        };
    }
}
