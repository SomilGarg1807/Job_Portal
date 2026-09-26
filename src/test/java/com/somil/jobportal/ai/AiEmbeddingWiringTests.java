package com.somil.jobportal.ai;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.somil.jobportal.ai.index.EmbeddingIndexer;
import com.somil.jobportal.ai.index.EmbeddingUpdateQueue;
import com.somil.jobportal.ai.index.IndexResult;
import com.somil.jobportal.entity.JobCompany;
import com.somil.jobportal.entity.JobLocation;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.services.JobPostActivityService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** With embedding enabled, saving a job through the normal service queues a background refresh. */
@SpringBootTest(properties = {"app.ai.embedding.enabled=true", "app.ai.embedding.save-delay=0ms"})
class AiEmbeddingWiringTests {
    @MockitoBean EmbeddingIndexer indexer;
    @Autowired JobPostActivityService jobs;
    @Autowired EmbeddingUpdateQueue queue;

    @Test
    void savingAJobRefreshesItsEmbeddingInTheBackground() {
        when(indexer.index(any(), any(), any(), anyBoolean())).thenReturn(IndexResult.NONE);
        JobPostActivity job = new JobPostActivity();
        job.setJobTitle("Backend Engineer");
        job.setDescriptionOfJob("<p>Java</p>");
        job.setJobLocationId(new JobLocation(null, "Pune", "MH", "India"));
        job.setJobCompanyId(new JobCompany(null, "Acme", ""));

        int id = jobs.addNew(job).getJobPostId();

        verify(indexer, timeout(5000)).index(eq(EmbeddingTarget.JOB),
                eq(java.util.List.of(EmbeddingStrategy.JOB_FULL_V1, EmbeddingStrategy.JOB_REQUIREMENTS_V1)),
                argThat((Collection<Integer> ids) -> ids.contains(id)), eq(false));
        assertNotNull(queue);
    }
}
