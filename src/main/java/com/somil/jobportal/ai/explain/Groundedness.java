package com.somil.jobportal.ai.explain;

import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic check that a quoted phrase really appears in the text the model was given.
 * Comparison ignores case, whitespace runs and surrounding punctuation, nothing more: a
 * paraphrase is not a quote, and a quote that cannot be found is treated as invented.
 */
public final class Groundedness {
    static final int MIN_QUOTE_CHARS = 3;

    private Groundedness() { }

    public static boolean isQuoted(String quote, String source) {
        String needle = normalize(quote);
        return needle.length() >= MIN_QUOTE_CHARS && normalize(source).contains(needle);
    }

    static String normalize(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s\"'“”‘’.,;:]+|[\\s\"'“”‘’.,;:]+$", "");
    }
}
