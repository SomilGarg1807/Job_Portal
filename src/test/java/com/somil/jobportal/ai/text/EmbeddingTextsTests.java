package com.somil.jobportal.ai.text;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.entity.JobCompany;
import com.somil.jobportal.entity.JobLocation;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.Skills;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingTextsTests {
    private static final String DESCRIPTION = "<p>We build payment APIs.</p>"
            + "<h3>Requirements</h3><ul><li>4+ years of Java</li><li>Spring Boot</li></ul>"
            + "<p><strong>Benefits</strong></p><p>Free lunch</p>"
            + "<p>Nice to have:</p><p>Kafka</p>";

    private static JobPostActivity job() {
        JobPostActivity job = new JobPostActivity();
        job.setJobTitle("Backend Engineer");
        job.setJobType("Full-time");
        job.setRemote("Remote-Only");
        job.setSalary("40 LPA");
        job.setMinExperienceYears(3);
        job.setMaxExperienceYears(5);
        job.setDescriptionOfJob(DESCRIPTION);
        job.setJobLocationId(new JobLocation(1, "Pune", "MH", "India"));
        job.setJobCompanyId(new JobCompany(1, "Acme Payments", ""));
        return job;
    }

    @Test
    void fullJobTextHasRoleFieldsAndPlainDescriptionButNoLocationSalaryOrCompany() {
        String text = EmbeddingTexts.job(EmbeddingStrategy.JOB_FULL_V1, job(), 6000);

        assertTrue(text.startsWith("Job title: Backend Engineer\n"));
        assertTrue(text.contains("Employment type: Full-time"));
        assertTrue(text.contains("Work mode: Remote-Only"));
        assertTrue(text.contains("Experience: 3–5 years"));
        assertTrue(text.contains("We build payment APIs. Requirements 4+ years of Java"));
        assertFalse(text.contains("<"));
        for (String excluded : List.of("Pune", "India", "40 LPA", "Acme")) assertFalse(text.contains(excluded), excluded);
    }

    @Test
    void requirementsTextKeepsOnlyRequirementSections() {
        String text = EmbeddingTexts.job(EmbeddingStrategy.JOB_REQUIREMENTS_V1, job(), 6000);

        assertTrue(text.contains("Requirements: 4+ years of Java Spring Boot\nKafka"), text);
        assertFalse(text.contains("payment APIs"));
        assertFalse(text.contains("Free lunch"));
        assertFalse(text.contains("Employment type"));
    }

    @Test
    void requirementsFallBackToBulletsThenWholeDescription() {
        assertEquals("Java\nSQL", EmbeddingTexts.requirements("<p>About us</p><ul><li>Java</li><li>SQL</li></ul>"));
        assertEquals("Just a paragraph.", EmbeddingTexts.requirements("<p>Just a paragraph.</p>"));
    }

    @Test
    void jobWithoutExperienceOmitsTheLine() {
        JobPostActivity job = job();
        job.setMinExperienceYears(null);
        job.setMaxExperienceYears(null);
        assertFalse(EmbeddingTexts.job(EmbeddingStrategy.JOB_FULL_V1, job, 6000).contains("Experience"));
    }

    @Test
    void candidateTextUsesProfessionalFieldsOnly() {
        JobSeekerProfile profile = new JobSeekerProfile();
        profile.setFirstName("Asha");
        profile.setLastName("Rao");
        profile.setPhoneNumber("+91 99999 99999");
        profile.setCity("Pune");
        profile.setExpectedCtc(new BigDecimal("3000000"));
        profile.setWorkAuthorization("Citizen");
        profile.setResume("resume.pdf");
        profile.setProfessionalHeadline("Backend engineer building payment systems");
        profile.setDesiredJobTitle("Senior Java Developer");
        profile.setTotalExperienceYears(new BigDecimal("4.50"));
        profile.setEmploymentType("Full-time");
        profile.setRemotePreference("Remote");
        profile.setSkills(List.of(new Skills(1, "Java", "Advanced", "4", profile), new Skills(2, "Kafka", "", "", profile),
                new Skills(3, " ", "Beginner", "1", profile)));

        String text = EmbeddingTexts.candidate(EmbeddingStrategy.CANDIDATE_PROFILE_V1, profile, 6000);

        assertEquals("""
                Headline: Backend engineer building payment systems
                Desired role: Senior Java Developer
                Total experience: 4.5 years
                Employment type: Full-time
                Work mode: Remote
                Skills: Java (Advanced, 4 years); Kafka""", text);
        for (String excluded : List.of("Asha", "Rao", "99999", "Pune", "3000000", "Citizen", "resume")) {
            assertFalse(text.contains(excluded), excluded);
        }
    }

    @Test
    void candidateWithNothingDescriptiveIsSkipped() {
        JobSeekerProfile profile = new JobSeekerProfile();
        profile.setFirstName("Asha");
        profile.setTotalExperienceYears(BigDecimal.TEN);
        assertEquals("", EmbeddingTexts.candidate(EmbeddingStrategy.CANDIDATE_PROFILE_V1, profile, 6000));
    }

    @Test
    void wrongTargetIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingTexts.job(EmbeddingStrategy.CANDIDATE_PROFILE_V1, job(), 6000));
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingTexts.candidate(EmbeddingStrategy.JOB_FULL_V1, new JobSeekerProfile(), 6000));
    }

    @Test
    void truncatesAtWordBoundary() {
        assertEquals("alpha beta", EmbeddingTexts.truncate("alpha beta gamma", 12));
        assertEquals("short", EmbeddingTexts.truncate("short", 12));
    }
}
