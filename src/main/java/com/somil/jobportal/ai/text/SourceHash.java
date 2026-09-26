package com.somil.jobportal.ai.text;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.somil.jobportal.ai.EmbeddingStrategy;

/**
 * Fingerprint of everything that decides what a vector looks like: the strategy (and so its
 * task type), the model, the dimension and the exact text. If the stored hash equals this,
 * re-embedding would give the same vector, so the call is skipped and no quota is spent.
 * Changing any one of them, for example switching model, marks every old vector stale.
 */
public final class SourceHash {
    private SourceHash() { }

    public static String of(EmbeddingStrategy strategy, String model, int dimensions, String text) {
        String material = String.join("\u0000", strategy.id(), String.valueOf(strategy.taskType()), model,
                Integer.toString(dimensions), text);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", ex);
        }
    }
}
