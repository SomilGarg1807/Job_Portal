package com.somil.jobportal.ai.embedding;

/** A unit-length vector together with the model and dimension that produced it. */
public record Embedding(float[] values, String model, int dimensions) {
}
