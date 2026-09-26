package com.somil.jobportal.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A named way of turning a job or a candidate into text before embedding it.
 *
 * <p>The id is stored with every vector and is part of the source hash, so editing how a
 * strategy builds its text must come with a new id (for example {@code job_full_v2}).
 * Old vectors then read as stale and the backfill re-embeds them.
 *
 * <p>The task type is sent to Gemini. Both sides use the same one because a job and a
 * candidate are compared with each other, not a short query against long documents.
 */
public enum EmbeddingStrategy {
    /** Title, job type, work mode, experience and the full plain-text description. */
    JOB_FULL_V1("job_full_v1", EmbeddingTarget.JOB, "SEMANTIC_SIMILARITY"),
    /** Title plus only the requirement-like sections of the description. */
    JOB_REQUIREMENTS_V1("job_req_v1", EmbeddingTarget.JOB, "SEMANTIC_SIMILARITY"),
    /** Headline, desired role, experience and skills. No resume text and no personal details. */
    CANDIDATE_PROFILE_V1("cand_profile_v1", EmbeddingTarget.CANDIDATE, "SEMANTIC_SIMILARITY");

    private final String id;
    private final EmbeddingTarget target;
    private final String taskType;

    EmbeddingStrategy(String id, EmbeddingTarget target, String taskType) {
        this.id = id;
        this.target = target;
        this.taskType = taskType;
    }

    public String id() { return id; }
    public EmbeddingTarget target() { return target; }
    public String taskType() { return taskType; }

    public static EmbeddingStrategy fromId(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.id.equals(wanted)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown embedding strategy '" + id
                        + "'. Known: " + Arrays.stream(values()).map(EmbeddingStrategy::id).toList()));
    }

    /** Parses configured ids and checks each one belongs to the expected target. */
    public static List<EmbeddingStrategy> parse(List<String> ids, EmbeddingTarget target) {
        List<EmbeddingStrategy> strategies = ids.stream().filter(s -> !s.isBlank()).map(EmbeddingStrategy::fromId).distinct().toList();
        for (EmbeddingStrategy strategy : strategies) {
            if (strategy.target != target) {
                throw new IllegalArgumentException("Strategy " + strategy.id + " embeds " + strategy.target
                        + ", not " + target);
            }
        }
        return strategies;
    }
}
