package com.somil.jobportal.ai.backfill;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.ai.EmbeddingTarget;
import com.somil.jobportal.ai.index.EmbeddingIndexer;
import com.somil.jobportal.ai.index.Fakes;

import static org.junit.jupiter.api.Assertions.*;

class BackfillJobTests {
    private Fakes.Loader loader;
    private Fakes.RecordingEmbedder embedder;
    private BackfillJob job;

    @BeforeEach
    void setup() {
        loader = new Fakes.Loader();
        for (int id = 1; id <= 5; id++) loader.texts.put(id * 10, "job " + id);
        embedder = new Fakes.RecordingEmbedder(2);
        job = new BackfillJob(loader, new EmbeddingIndexer(loader, embedder, new Fakes.Store()));
    }

    private static BackfillJob.Options options(int fromId, int maxRows, boolean dryRun) {
        return new BackfillJob.Options(EmbeddingTarget.JOB, List.of(EmbeddingStrategy.JOB_FULL_V1), fromId, maxRows, 2, dryRun);
    }

    @Test
    void walksEveryRowPageByPage() {
        var outcome = job.run(options(0, 0, false));

        assertEquals(BackfillJob.OK, outcome.exitCode());
        assertEquals(5, outcome.result().embedded());
        assertEquals(50, outcome.lastCompletedId());
        assertEquals(3, embedder.calls.size());
    }

    @Test
    void secondRunIsFreeBecauseNothingChanged() {
        job.run(options(0, 0, false));
        embedder.calls.clear();

        var outcome = job.run(options(0, 0, false));

        assertEquals(5, outcome.result().unchanged());
        assertTrue(embedder.calls.isEmpty());
    }

    @Test
    void honoursFromIdAndMaxRows() {
        var outcome = job.run(options(20, 2, false));

        assertEquals(2, outcome.result().embedded());
        assertEquals(40, outcome.lastCompletedId());
    }

    @Test
    void stopsOnQuotaAndReportsWhereToResume() {
        embedder.failOnCall = 1;

        var outcome = job.run(options(0, 0, false));

        assertEquals(BackfillJob.QUOTA_EXHAUSTED, outcome.exitCode());
        assertEquals(20, outcome.lastCompletedId(), "ids 10 and 20 are stored; resume after 20");

        embedder.failOnCall = -1;
        var resumed = job.run(options(outcome.lastCompletedId(), 0, false));
        assertEquals(BackfillJob.OK, resumed.exitCode());
        assertEquals(3, resumed.result().embedded());
    }

    @Test
    void otherFailuresExitWithFailedCode() {
        embedder.failOnCall = 0;
        embedder.quota = false;
        assertEquals(BackfillJob.FAILED, job.run(options(0, 0, false)).exitCode());
    }

    @Test
    void dryRunSpendsNothing() {
        var outcome = job.run(options(0, 0, true));

        assertEquals(5, outcome.result().embedded());
        assertTrue(embedder.calls.isEmpty());
    }
}
