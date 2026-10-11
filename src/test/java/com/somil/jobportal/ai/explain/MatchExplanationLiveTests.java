package com.somil.jobportal.ai.explain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.Skills;

/** Calls the real Gemini API. Opt in with -Dlive.gemini=true and GEMINI_API_KEY set; CI never runs it. */
class MatchExplanationLiveTests {
    @Test void realModelQuotesSurviveVerification() {
        String key = System.getenv("GEMINI_API_KEY");
        assumeTrue(Boolean.getBoolean("live.gemini") && key != null && !key.isBlank(), "live Gemini test not enabled");
        var profile = new JobSeekerProfile();
        profile.setProfessionalHeadline("Backend developer building payment APIs");
        profile.setDesiredJobTitle("Java Backend Engineer");
        profile.setSkills(new ArrayList<>(List.of(new Skills(null, "Java", "Advanced", "4", profile),
                new Skills(null, "Spring Boot", "Intermediate", "3", profile), new Skills(null, "PostgreSQL", "Intermediate", "2", profile))));
        var job = new JobPostActivity();
        job.setJobPostId(1); job.setJobTitle("Senior Java Engineer"); job.setJobType("Full-time"); job.setRemote("Remote-Only");
        job.setMinExperienceYears(3); job.setMaxExperienceYears(6);
        job.setDescriptionOfJob("<p>Design and build Spring Boot microservices for our billing platform.</p>"
                + "<h3>Requirements</h3><ul><li>Strong Java and Spring Boot</li><li>Experience with Kafka event streaming</li>"
                + "<li>Hands-on Kubernetes and AWS</li><li>SQL databases such as PostgreSQL</li></ul>");
        var service = new MatchExplanationService(key, System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite"));
        var result = service.explain(profile, job);
        System.out.println("LIVE RESULT " + result);
        assertThat(result.matches()).isNotEmpty();
        assertThat(result.gaps()).isNotEmpty();
    }
}
