package com.somil.jobportal.ai.store;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.somil.jobportal.ai.EmbeddingTarget;

/**
 * Native SQL over the embedding tables. Hibernate has no VECTOR type, so these tables are
 * not JPA entities and Hibernate never creates or alters them (see db/ai/V1__*.sql).
 *
 * <p>Vectors are sent as TiDB's text form, {@code [0.1,0.2,...]}, which TiDB converts to
 * VECTOR on insert. Table names come from {@link EmbeddingTarget} constants, never input.
 */
public class JdbcEmbeddingStore implements EmbeddingStore {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcEmbeddingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public Map<EmbeddingKey, String> hashes(EmbeddingTarget target, Collection<Integer> ids) {
        Map<EmbeddingKey, String> hashes = new HashMap<>();
        if (ids.isEmpty()) return hashes;
        String sql = "SELECT " + target.embeddingIdColumn() + " AS id, strategy, source_hash FROM "
                + target.embeddingTable() + " WHERE " + target.embeddingIdColumn() + " IN (:ids)";
        named.query(sql, Map.of("ids", ids), row -> {
            hashes.put(new EmbeddingKey(row.getInt("id"), row.getString("strategy")), row.getString("source_hash"));
        });
        return hashes;
    }

    @Override
    public void upsert(EmbeddingTarget target, List<StoredEmbedding> rows) {
        if (rows.isEmpty()) return;
        String sql = "INSERT INTO " + target.embeddingTable() + " (" + target.embeddingIdColumn()
                + ", strategy, model, dimensions, source_hash, source_chars, embedding, embedded_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE model = VALUES(model), dimensions = VALUES(dimensions),"
                + " source_hash = VALUES(source_hash), source_chars = VALUES(source_chars),"
                + " embedding = VALUES(embedding), embedded_at = VALUES(embedded_at)";
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.batchUpdate(sql, rows, rows.size(), (statement, row) -> {
            statement.setInt(1, row.id());
            statement.setString(2, row.strategy());
            statement.setString(3, row.model());
            statement.setInt(4, row.dimensions());
            statement.setString(5, row.sourceHash());
            statement.setInt(6, row.sourceChars());
            statement.setString(7, VectorText.format(row.vector()));
            statement.setTimestamp(8, now);
        });
    }
}
