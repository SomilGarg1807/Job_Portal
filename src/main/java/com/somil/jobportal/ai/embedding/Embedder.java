package com.somil.jobportal.ai.embedding;

import java.util.List;

/** Turns texts into vectors. Results are in the same order as the inputs. */
public interface Embedder {
    List<Embedding> embed(List<EmbeddingInput> inputs);

    String model();

    int dimensions();

    int batchSize();
}
