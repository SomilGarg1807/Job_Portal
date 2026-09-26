package com.somil.jobportal.ai.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.embedding.Embedder;
import com.somil.jobportal.ai.embedding.Embedding;
import com.somil.jobportal.ai.embedding.EmbeddingException;
import com.somil.jobportal.ai.embedding.EmbeddingInput;
import com.somil.jobportal.ai.store.EmbeddingKey;
import com.somil.jobportal.ai.store.EmbeddingStore;
import com.somil.jobportal.ai.store.StoredEmbedding;

/** In-memory stand-ins so indexing logic can be tested without a database or Gemini. */
public final class Fakes {
    private Fakes() { }

    /** Source texts keyed by id; the same text is returned for every strategy, prefixed by its id. */
    public static class Loader implements EmbeddingSourceLoader {
        public final TreeMap<Integer, String> texts = new TreeMap<>();

        @Override
        public List<Integer> idsAfter(EmbeddingTarget target, int afterId, int limit) {
            return texts.tailMap(afterId, false).keySet().stream().limit(limit).toList();
        }

        @Override
        public List<SourceText> load(EmbeddingTarget target, List<EmbeddingStrategy> strategies, Collection<Integer> ids) {
            List<SourceText> result = new ArrayList<>();
            for (int id : ids) {
                if (!texts.containsKey(id)) continue;
                for (EmbeddingStrategy strategy : strategies) {
                    String text = texts.get(id);
                    result.add(new SourceText(id, strategy, text.isBlank() ? text : strategy.id() + ":" + text));
                }
            }
            return result;
        }
    }

    public static class RecordingEmbedder implements Embedder {
        public final List<List<String>> calls = new ArrayList<>();
        public int failOnCall = -1;
        public boolean quota = true;
        private final int batchSize;

        public RecordingEmbedder(int batchSize) { this.batchSize = batchSize; }

        @Override
        public List<Embedding> embed(List<EmbeddingInput> inputs) {
            if (calls.size() == failOnCall) throw new EmbeddingException("quota", quota);
            calls.add(inputs.stream().map(EmbeddingInput::text).toList());
            return inputs.stream().map(input -> new Embedding(new float[]{input.text().length(), 1}, model(), dimensions())).toList();
        }

        public int textsEmbedded() { return calls.stream().mapToInt(List::size).sum(); }

        @Override public String model() { return "fake-model"; }
        @Override public int dimensions() { return 2; }
        @Override public int batchSize() { return batchSize; }
    }

    public static class Store implements EmbeddingStore {
        public final Map<EmbeddingKey, StoredEmbedding> rows = new HashMap<>();
        public int upserts;

        @Override
        public Map<EmbeddingKey, String> hashes(EmbeddingTarget target, Collection<Integer> ids) {
            Map<EmbeddingKey, String> hashes = new HashMap<>();
            rows.forEach((key, row) -> { if (ids.contains(key.id())) hashes.put(key, row.sourceHash()); });
            return hashes;
        }

        @Override
        public void upsert(EmbeddingTarget target, List<StoredEmbedding> batch) {
            upserts++;
            batch.forEach(row -> rows.put(new EmbeddingKey(row.id(), row.strategy()), row));
        }
    }
}
