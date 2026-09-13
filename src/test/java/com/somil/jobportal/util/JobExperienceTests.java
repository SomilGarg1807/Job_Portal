package com.somil.jobportal.util;

import com.somil.jobportal.entity.JobPostActivity;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class JobExperienceTests {
    @Test void readsExistingDescriptionsAndMatchesOverlappingRanges() {
        var junior = job("<p>Bring 0-1 years of relevant experience.</p>");
        var middle = job("<li>Bring 3–5 years of experience with Java.</li>");
        var senior = job("You need 12+ years of professional experience.");
        assertThat(JobExperience.filter(List.of(junior, middle, senior), "0-1")).containsExactly(junior);
        assertThat(JobExperience.filter(List.of(junior, middle, senior), "3-5")).containsExactly(middle);
        assertThat(JobExperience.filter(List.of(junior, middle, senior), "12+")).containsExactly(senior);
    }
    @Test void explicitFieldsOverrideOldDescriptionAndSupportZero() {
        var job = job("Previously required 5-8 years of experience.");
        job.setMinExperienceYears(0); job.setMaxExperienceYears(1);
        assertThat(JobExperience.label(job)).isEqualTo("0–1 years");
        assertThat(JobExperience.filter(List.of(job), "5-8")).isEmpty();
        job.setMinExperienceYears(3); job.setMaxExperienceYears(null);
        assertThat(JobExperience.label(job)).isEqualTo("3+ years");
    }
    @Test void unspecifiedExperienceIsNotInventedFromTitleOrSalary() {
        var job = job("Salary INR 12-18 LPA. Java 17 required.");
        job.setJobTitle("Junior Engineer");
        assertThat(JobExperience.range(job)).isNull();
        assertThat(JobExperience.filter(List.of(job), "0-1")).isEmpty();
        assertThat(JobExperience.filter(List.of(job), "unspecified")).containsExactly(job);
        assertThat(JobExperience.filter(List.of(job), "invalid")).containsExactly(job);
    }
    private JobPostActivity job(String description) {
        var job = new JobPostActivity(); job.setDescriptionOfJob(description); return job;
    }
}
