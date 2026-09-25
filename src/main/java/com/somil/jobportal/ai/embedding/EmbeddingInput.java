package com.somil.jobportal.ai.embedding;

/** One text to embed and the Gemini task type to embed it with (may be null). */
public record EmbeddingInput(String text, String taskType) {
}
