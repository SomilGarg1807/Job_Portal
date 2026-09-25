package com.somil.jobportal.ai.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.text.EmbeddingTexts;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.repository.JobPostActivityRepository;
import com.somil.jobportal.repository.JobSeekerProfileRepository;

/**
 * Loads entities through the existing repositories inside a short read-only transaction
 * (profile skills are lazy), and builds their text before the transaction ends. The Gemini
 * call happens afterwards, so no database connection is held while waiting on the network.
 */
public class JpaEmbeddingSourceLoader implements EmbeddingSourceLoader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate readOnly;
    private final JobPostActivityRepository jobs;
    private final JobSeekerProfileRepository profiles;
    private final int maxChars;

    public JpaEmbeddingSourceLoader(JdbcTemplate jdbc, TransactionTemplate readOnly, JobPostActivityRepository jobs,
                                    JobSeekerProfileRepository profiles, int maxChars) {
        this.jdbc = jdbc;
        this.readOnly = readOnly;
        this.jobs = jobs;
        this.profiles = profiles;
        this.maxChars = maxChars;
    }

    @Override
    public List<Integer> idsAfter(EmbeddingTarget target, int afterId, int limit) {
        return jdbc.queryForList("SELECT " + target.sourceIdColumn() + " FROM " + target.sourceTable()
                + " WHERE " + target.sourceIdColumn() + " > ? ORDER BY " + target.sourceIdColumn() + " LIMIT ?",
                Integer.class, afterId, limit);
    }

    @Override
    public List<SourceText> load(EmbeddingTarget target, List<EmbeddingStrategy> strategies, Collection<Integer> ids) {
        return readOnly.execute(status -> {
            List<SourceText> texts = new ArrayList<>();
            if (target == EmbeddingTarget.JOB) {
                for (JobPostActivity job : jobs.findAllById(ids)) {
                    for (EmbeddingStrategy strategy : strategies) {
                        texts.add(new SourceText(job.getJobPostId(), strategy, EmbeddingTexts.job(strategy, job, maxChars)));
                    }
                }
            } else {
                for (JobSeekerProfile profile : profiles.findAllById(ids)) {
                    for (EmbeddingStrategy strategy : strategies) {
                        texts.add(new SourceText(profile.getUserAccountId(), strategy,
                                EmbeddingTexts.candidate(strategy, profile, maxChars)));
                    }
                }
            }
            return texts;
        });
    }
}
