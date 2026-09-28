package com.somil.jobportal.ai.search;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.somil.jobportal.ai.*;
import com.somil.jobportal.ai.embedding.*;
import com.somil.jobportal.ai.text.*;
import com.somil.jobportal.entity.*;

class SemanticJobSearchTests {
    static AiEmbeddingProperties properties() {
        return new AiEmbeddingProperties(true, "test", "model", 2, 50, 0, Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(2), 6000, Duration.ofSeconds(3),
                List.of("job_full_v1", "job_req_v1"), List.of("cand_profile_v1"));
    }
    final SemanticVectorRepository repository = mock(SemanticVectorRepository.class);
    final Embedder embedder = mock(Embedder.class);
    final SemanticJobSearch search = new SemanticJobSearch(repository, embedder, properties(), .55);

    JobPostActivity job(int id) {
        var job = new JobPostActivity(); job.setJobPostId(id); job.setJobTitle("Java engineer");
        job.setDescriptionOfJob("Build backend services"); return job;
    }
    SemanticVectorRepository.Match match(JobPostActivity job, double similarity) {
        String text = EmbeddingTexts.job(EmbeddingStrategy.JOB_FULL_V1, job, 6000);
        return new SemanticVectorRepository.Match(job.getJobPostId(), "job_full_v1",
                SourceHash.of(EmbeddingStrategy.JOB_FULL_V1, "model", 2, text), similarity);
    }
    void queryWorks() {
        when(embedder.embed(anyList())).thenReturn(List.of(new Embedding(new float[]{1, 0}, "model", 2)));
    }
    @Test void rejectsStaleLowScoringAndIneligibleVectorsAndCachesQuery() {
        queryWorks(); var current = job(1); var stale = job(2); var weak = job(3);
        var old = match(stale, .99); stale.setJobTitle("Designer");
        when(repository.matches(anyCollection(), anyString(), eq("model"), eq(2)))
                .thenReturn(List.of(match(current, .8), old, match(weak, .2), match(job(999), 1)));
        assertEquals(Map.of(1, .8), search.search("backend services", List.of(current, stale, weak)));
        assertEquals(Map.of(1, .8), search.search("backend services", List.of(current, stale, weak)));
        verify(embedder, times(1)).embed(List.of(new EmbeddingInput("backend services", "SEMANTIC_SIMILARITY")));
    }
    @Test void providerFailureFallsBackAndOpensCircuit() {
        when(embedder.embed(anyList())).thenThrow(new RuntimeException("quota"));
        assertTrue(search.search("backend", List.of(job(1))).isEmpty());
        assertTrue(search.search("frontend", List.of(job(1))).isEmpty());
        verify(embedder, times(1)).embed(anyList());
    }
    @Test void vectorDatabaseFailureFallsBack() {
        queryWorks(); when(repository.matches(anyCollection(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("unsupported SQL"));
        assertTrue(search.search("backend", List.of(job(1))).isEmpty());
    }
    @Test void recommendationsUseCurrentProfileHashWithoutCallingGemini() {
        var profile = new JobSeekerProfile(); profile.setUserAccountId(5); profile.setDesiredJobTitle("Backend engineer");
        String hash = SourceHash.of(EmbeddingStrategy.CANDIDATE_PROFILE_V1, "model", 2,
                EmbeddingTexts.candidate(EmbeddingStrategy.CANDIDATE_PROFILE_V1, profile, 6000));
        when(repository.candidate(5, hash, "model", 2)).thenReturn(Optional.of("[1,0]"));
        var job = job(1);
        when(repository.matches(anyCollection(), eq("[1,0]"), eq("model"), eq(2))).thenReturn(List.of(match(job, .9)));
        assertEquals(Map.of(1, .9), search.recommendations(profile, List.of(job)));
        profile.setDesiredJobTitle("Designer");
        assertTrue(search.recommendations(profile, List.of(job)).isEmpty());
        verifyNoInteractions(embedder);
    }
    @Test void blankAndOversizedQueriesNeverCallProvider() {
        assertTrue(search.search(" ", List.of(job(1))).isEmpty());
        assertTrue(search.search("x".repeat(501), List.of(job(1))).isEmpty());
        verifyNoInteractions(embedder, repository);
    }
}
