package com.somil.jobportal.eval;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somil.jobportal.ai.embedding.Embedder;
import com.somil.jobportal.ai.embedding.Embedding;
import com.somil.jobportal.ai.embedding.EmbeddingInput;

/** Each successful vector is durably cached before the next provider batch is requested. */
final class DiskEmbeddingCache implements Embedder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Embedder delegate;
    private final Path root;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong avoidedCalls = new AtomicLong();

    DiskEmbeddingCache(Embedder delegate, Path root) { this.delegate = delegate; this.root = root; }

    @Override public List<Embedding> embed(List<EmbeddingInput> inputs) {
        try {
            List<Embedding> result = new ArrayList<>(java.util.Collections.nCopies(inputs.size(), null));
            List<EmbeddingInput> misses = new ArrayList<>();
            List<Integer> positions = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                Path path = path(inputs.get(i));
                if (Files.exists(path)) {
                    CacheEntry entry = JSON.readValue(path.toFile(), CacheEntry.class);
                    if (!entry.model().equals(model()) || entry.dimensions() != dimensions()
                            || entry.values().length != dimensions() || !entry.hash().equals(hash(inputs.get(i).text())))
                        throw new IllegalStateException("Corrupt or stale eval embedding cache entry.");
                    result.set(i, new Embedding(entry.values(), model(), dimensions()));
                    hits.incrementAndGet();
                } else { misses.add(inputs.get(i)); positions.add(i); }
            }
            for (int start = 0; start < misses.size(); start += batchSize()) {
                int end = Math.min(start + batchSize(), misses.size());
                List<Embedding> vectors = delegate.embed(misses.subList(start, end));
                for (int j = start; j < end; j++) {
                    EmbeddingInput input = misses.get(j);
                    Embedding vector = vectors.get(j - start);
                    Path path = path(input);
                    Files.createDirectories(path.getParent());
                    Path temp = Files.createTempFile(path.getParent(), "vector-", ".tmp");
                    try {
                        JSON.writeValue(temp.toFile(), new CacheEntry(hash(input.text()), model(), dimensions(), vector.values()));
                        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } finally { Files.deleteIfExists(temp); }
                    result.set(positions.get(j), vector);
                }
            }
            avoidedCalls.addAndGet((inputs.size() + batchSize() - 1) / batchSize()
                    - (misses.size() + batchSize() - 1) / batchSize());
            return result;
        } catch (Exception ex) { throw new IllegalStateException("Eval embedding cache failed: " + ex.getClass().getSimpleName(), ex); }
    }

    long hits() { return hits.get(); }
    long avoidedCalls() { return avoidedCalls.get(); }
    boolean contains(EmbeddingInput input) { return Files.exists(path(input)); }
    @Override public String model() { return delegate.model(); }
    @Override public int dimensions() { return delegate.dimensions(); }
    @Override public int batchSize() { return delegate.batchSize(); }

    private Path path(EmbeddingInput input) { return root.resolve(model()).resolve(Integer.toString(dimensions())).resolve(hash(input.text()) + ".json"); }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private record CacheEntry(String hash, String model, int dimensions, float[] values) { }
}
