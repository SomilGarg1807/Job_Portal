package com.somil.jobportal.ai.search;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Exact cosine search over the already filtered catalogue; no approximate index required. */
public class SemanticVectorRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public SemanticVectorRepository(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        var bounded = new org.springframework.jdbc.core.JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));
        bounded.setQueryTimeout(2);
        this.jdbc = new NamedParameterJdbcTemplate(bounded);
    }
    public Optional<String> candidate(int id, String hash, String model, int dimensions) {
        return jdbc.query("SELECT VEC_AS_TEXT(embedding) AS vector FROM ai_candidate_embedding "
                + "WHERE user_account_id=:id AND strategy='cand_profile_v1' AND source_hash=:hash "
                + "AND model=:model AND dimensions=:dims", Map.of("id", id, "hash", hash,
                "model", model, "dims", dimensions), (rs, n) -> rs.getString("vector")).stream().findFirst();
    }
    public List<Match> matches(Collection<Integer> ids, String vector, String model, int dimensions) {
        if (ids.isEmpty()) return List.of();
        List<Match> result = new ArrayList<>();
        List<Integer> all = new ArrayList<>(ids);
        for (int start = 0; start < all.size(); start += 500) {
            result.addAll(jdbc.query("SELECT job_post_id, strategy, source_hash, "
                    + "1 - VEC_COSINE_DISTANCE(embedding, CAST(:vector AS VECTOR)) AS similarity "
                    + "FROM ai_job_embedding WHERE job_post_id IN (:ids) AND model=:model AND dimensions=:dims "
                    + "AND strategy IN ('job_full_v1','job_req_v1')",
                    Map.of("ids", all.subList(start, Math.min(start + 500, all.size())), "vector", vector,
                            "model", model, "dims", dimensions),
                    (rs, n) -> new Match(rs.getInt("job_post_id"), rs.getString("strategy"),
                            rs.getString("source_hash"), rs.getDouble("similarity"))));
        }
        return result;
    }
    public record Match(int id, String strategy, String hash, double similarity) { }
}
