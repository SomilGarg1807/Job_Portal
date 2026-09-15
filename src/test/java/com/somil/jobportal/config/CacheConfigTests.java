package com.somil.jobportal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import com.somil.jobportal.entity.JobCompany;
import com.somil.jobportal.entity.JobLocation;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerApply;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.RecruiterJobsDto;
import com.somil.jobportal.repository.JobPostActivityRepository;
import com.somil.jobportal.repository.JobSeekerApplyRepository;
import com.somil.jobportal.services.JobPostActivityService;
import com.somil.jobportal.services.JobSeekerApplyService;

class CacheConfigTests {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(); }
        @Bean JobPostActivityRepository jobRepository() { return mock(JobPostActivityRepository.class); }
        @Bean JobSeekerApplyRepository applyRepository() { return mock(JobSeekerApplyRepository.class); }
        @Bean JobPostActivityService jobService(JobPostActivityRepository r) { return new JobPostActivityService(r); }
        @Bean JobSeekerApplyService applyService(JobSeekerApplyRepository r) { return new JobSeekerApplyService(r); }
    }

    private AnnotationConfigApplicationContext context;
    private JobPostActivityRepository jobRepository;
    private JobPostActivityService jobService;
    private JobSeekerApplyService applyService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        jobRepository = context.getBean(JobPostActivityRepository.class);
        jobService = context.getBean(JobPostActivityService.class);
        applyService = context.getBean(JobSeekerApplyService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void jobCountIsCachedUntilAJobIsSaved() {
        when(jobRepository.count()).thenReturn(5L, 6L);

        assertThat(jobService.countAll()).isEqualTo(5L);
        assertThat(jobService.countAll()).isEqualTo(5L);
        verify(jobRepository, times(1)).count();

        jobService.addNew(new JobPostActivity());

        assertThat(jobService.countAll()).isEqualTo(6L);
        verify(jobRepository, times(2)).count();
    }

    @Test
    void recruiterJobsAreCachedPerRecruiterUntilSomeoneApplies() {
        when(jobRepository.getRecruiterJobs(42)).thenReturn(List.of());

        jobService.getRecruiterJobs(42);
        jobService.getRecruiterJobs(42);
        verify(jobRepository, times(1)).getRecruiterJobs(42);

        jobService.getRecruiterJobs(7);
        verify(jobRepository, times(1)).getRecruiterJobs(7);

        applyService.addNew(new JobSeekerApply(null, new JobSeekerProfile(), new JobPostActivity(), new Date(), null));

        jobService.getRecruiterJobs(42);
        verify(jobRepository, times(2)).getRecruiterJobs(42);
    }

    @Test
    void redisSerializersRoundTripCachedValues() {
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder();
        new CacheConfig().redisCacheCustomizer().customize(builder);

        RedisCacheConfiguration count = builder.getCacheConfigurationFor(CacheConfig.JOB_COUNT).orElseThrow();
        ByteBuffer countJson = count.getValueSerializationPair().write(57L);
        assertThat(count.getValueSerializationPair().read(countJson)).isEqualTo(57L);

        RedisCacheConfiguration jobs = builder.getCacheConfigurationFor(CacheConfig.RECRUITER_JOBS).orElseThrow();
        RecruiterJobsDto dto = new RecruiterJobsDto(3L, 11, "Java Developer",
                new JobLocation(1, "Delhi", "Delhi", "India"), new JobCompany(2, "Acme", ""));
        Object restored = jobs.getValueSerializationPair().read(jobs.getValueSerializationPair().write(List.of(dto)));

        assertThat(restored).asList().singleElement().isInstanceOfSatisfying(RecruiterJobsDto.class, r -> {
            assertThat(r.getTotalCandidates()).isEqualTo(3L);
            assertThat(r.getJobTitle()).isEqualTo("Java Developer");
            assertThat(r.getJobLocationId().getCity()).isEqualTo("Delhi");
            assertThat(r.getJobCompanyId().getName()).isEqualTo("Acme");
        });
    }
}
