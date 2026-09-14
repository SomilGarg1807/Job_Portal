package com.somil.jobportal.services;

import com.somil.jobportal.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AtsScoreServiceTests {
    private final AtsScoreService service = new AtsScoreService(null, null);

    private JobPostActivity job(String title, String description, Integer min, Integer max) {
        var job = new JobPostActivity(); job.setJobTitle(title); job.setDescriptionOfJob(description);
        job.setMinExperienceYears(min); job.setMaxExperienceYears(max);
        return job;
    }

    private JobSeekerProfile profile(String years, String... skills) {
        var profile = new JobSeekerProfile();
        profile.setTotalExperienceYears(years == null ? null : new BigDecimal(years));
        List<Skills> list = new ArrayList<>();
        for (String skill : skills) list.add(new Skills(null, skill, "Advanced", "2", profile));
        profile.setSkills(list);
        return profile;
    }

    @Test
    void matchingResumeScoresHigherThanUnrelatedResume() {
        var job = job("Java Backend Engineer", "<p>Build REST APIs with Java, Spring Boot and MySQL. Deploy on AWS with Docker.</p>", 1, 3);
        var strong = service.score(profile("2"), job,
                "Experience: Java backend engineer building REST APIs with Spring Boot, MySQL, Docker and AWS. Education: B.Tech. Skills: Java. Projects: payments API.");
        var weak = service.score(profile("0"), job, "Graphic designer skilled in Photoshop and branding.");
        assertThat(strong.score()).isGreaterThan(weak.score()).isGreaterThanOrEqualTo(75);
        assertThat(strong.matched()).contains("java", "spring boot", "mysql", "aws", "docker");
        assertThat(strong.matched()).doesNotContain("spring");
        assertThat(weak.missing()).contains("java", "aws");
        assertThat(strong.score()).isBetween(0, 100);
    }

    @Test
    void profileSkillsCountWhenResumeIsUnreadable() {
        var job = job("Python Developer", "Python, Django and PostgreSQL experience required.", null, null);
        var report = service.score(profile(null, "Python", "Django"), job, "");
        assertThat(report.resumeRead()).isFalse();
        assertThat(report.matched()).contains("python", "django");
        assertThat(report.missing()).contains("postgresql");
        assertThat(report.experienceScore()).isEqualTo(100);
    }

    @Test
    void fallsBackToFrequentTermsForNonTechnicalDescriptions() {
        var keywords = service.keywords("store manager inventory inventory merchandising customer customer customer staffing");
        assertThat(keywords).contains("customer", "inventory", "merchandising");
    }
}
