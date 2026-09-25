package com.somil.jobportal.ai.text;

import org.junit.jupiter.api.Test;

import com.somil.jobportal.ai.EmbeddingStrategy;

import static org.junit.jupiter.api.Assertions.*;

class SourceHashTests {
    private static final String BASE = SourceHash.of(EmbeddingStrategy.JOB_FULL_V1, "gemini-embedding-001", 768, "Java");

    @Test
    void sameInputsGiveSameHash() {
        assertEquals(BASE, SourceHash.of(EmbeddingStrategy.JOB_FULL_V1, "gemini-embedding-001", 768, "Java"));
        assertEquals(64, BASE.length());
    }

    @Test
    void changingTextModelDimensionOrStrategyMakesVectorStale() {
        assertNotEquals(BASE, SourceHash.of(EmbeddingStrategy.JOB_FULL_V1, "gemini-embedding-001", 768, "Java "));
        assertNotEquals(BASE, SourceHash.of(EmbeddingStrategy.JOB_FULL_V1, "gemini-embedding-2", 768, "Java"));
        assertNotEquals(BASE, SourceHash.of(EmbeddingStrategy.JOB_FULL_V1, "gemini-embedding-001", 1536, "Java"));
        assertNotEquals(BASE, SourceHash.of(EmbeddingStrategy.JOB_REQUIREMENTS_V1, "gemini-embedding-001", 768, "Java"));
    }
}
