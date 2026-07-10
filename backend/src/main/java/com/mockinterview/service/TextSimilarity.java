package com.mockinterview.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lightweight text-similarity utilities used to prevent near-duplicate
 * interview questions (within a batch, across an interview, and across a
 * candidate's past/same-resume interviews).
 *
 * We use Jaccard similarity over a stop-word-filtered token set. True
 * duplicates (re-phrasings of the same question) score high; genuinely
 * distinct questions that merely share domain vocabulary score low.
 */
public final class TextSimilarity {

    private TextSimilarity() {
    }

    // Words that carry little discriminative value for question similarity.
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "for", "with",
            "is", "are", "was", "were", "be", "been", "being", "you", "your", "what",
            "how", "why", "do", "does", "did", "can", "could", "would", "should",
            "explain", "describe", "tell", "me", "about", "difference", "between",
            "vs", "use", "using", "used", "have", "has", "had", "this", "that",
            "these", "those", "it", "its", "as", "at", "by", "from", "into", "than",
            "then", "so", "if", "our", "their"
    );

    /**
     * Jaccard similarity in [0,1] between two question strings.
     * 1.0 = identical token sets, 0.0 = disjoint.
     */
    public static double jaccard(String a, String b) {
        Set<String> sa = tokens(a);
        Set<String> sb = tokens(b);
        if (sa.isEmpty() && sb.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(sa);
        intersection.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * @return true if {@code candidate} is a duplicate of any entry in
     * {@code existing} (exact match or similarity above {@code threshold}).
     */
    public static boolean isDuplicate(String candidate, Set<String> existing, double threshold) {
        if (candidate == null) {
            return true;
        }
        String norm = normalize(candidate);
        for (String e : existing) {
            if (e.equalsIgnoreCase(norm)) {
                return true;
            }
            if (jaccard(candidate, e) > threshold) {
                return true;
            }
        }
        return false;
    }

    /** Lower-cased, trimmed, stop-word-filtered token set. */
    public static Set<String> tokens(String s) {
        if (s == null || s.isBlank()) {
            return Set.of();
        }
        // Keep letters, digits and '+' (for "C++"); drop '.'/'#' so that
        // "configuration." and "configuration" tokenise identically.
        return Arrays.stream(s.toLowerCase().split("[^a-z0-9+]+"))
                .filter(t -> t.length() > 1)
                .filter(t -> !STOPWORDS.contains(t))
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
