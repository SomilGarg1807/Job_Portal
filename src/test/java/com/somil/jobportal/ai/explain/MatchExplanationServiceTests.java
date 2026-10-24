package com.somil.jobportal.ai.explain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.somil.jobportal.ai.explain.MatchExplanationService.Claim;
import com.somil.jobportal.ai.explain.MatchExplanationService.Draft;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.Skills;
import com.somil.jobportal.services.AiAssistantService.AssistantException;

class MatchExplanationServiceTests {
    static final String PROFILE = "Headline: Backend developer\nSkills: Java (3 years), Spring Boot";
    static final String JOB = "Job title: Java Engineer\nDescription: Build secure Java APIs and deploy services on AWS.";

    @Test void quotesMustAppearVerbatimIgnoringCaseAndSpacing() {
        assertThat(Groundedness.isQuoted("spring  BOOT", PROFILE)).isTrue();
        assertThat(Groundedness.isQuoted("\"deploy services on AWS.\"", JOB)).isTrue();
        assertThat(Groundedness.isQuoted("Kubernetes", JOB)).isFalse();
        assertThat(Groundedness.isQuoted("builds APIs securely", JOB)).as("paraphrase is not a quote").isFalse();
        assertThat(Groundedness.isQuoted("a", JOB)).as("too short to prove anything").isFalse();
        assertThat(Groundedness.isQuoted(null, JOB)).isFalse();
    }

    @Test void verifyDropsInventedAndMalformedClaims() {
        var draft = new Draft(List.of(
                new Claim("You use Java.", "Java (3 years)", "Build secure Java APIs"),
                new Claim("You know Kubernetes.", "Kubernetes", "deploy services on AWS"),
                new Claim("Invented job need.", "Spring Boot", "10 years of Rust")),
                List.of(new Claim("AWS is not shown in your profile.", "", "deploy services on AWS"),
                        new Claim("Contradiction.", "Java", "Build secure Java APIs")));
        var result = MatchExplanationService.verify(draft, PROFILE, JOB);
        assertThat(result.matches()).extracting(MatchExplanationService.Point::point).containsExactly("You use Java.");
        assertThat(result.gaps()).extracting(MatchExplanationService.Point::point).containsExactly("AWS is not shown in your profile.");
        assertThat(result.dropped()).isEqualTo(3);
    }

    @Test void verifyCapsPointsAndToleratesMissingLists() {
        var many = new ArrayList<Claim>();
        for (int i = 0; i < 8; i++) many.add(new Claim("Java " + i, "Java", "Java APIs"));
        var result = MatchExplanationService.verify(new Draft(many, null), PROFILE, JOB);
        assertThat(result.matches()).hasSize(MatchExplanationService.MAX_POINTS);
        assertThat(result.gaps()).isEmpty();
        assertThat(result.dropped()).as("points past the limit did not fail a check").isZero();
    }

    @Test void explainParsesStructuredOutputAndSendsOnlyMatchingFields() {
        var prompt = new AtomicReference<Prompt>();
        var service = new MatchExplanationService(ChatClient.create(reply(prompt, """
                {"matches":[{"point":"Your Java skill fits the API work.","profileQuote":"Java (Intermediate, 3 years)","jobQuote":"Build secure Java APIs"}],
                 "gaps":[{"point":"AWS deployment is not shown in your profile.","profileQuote":"","jobQuote":"deploy services on AWS"},
                         {"point":"Invented.","profileQuote":"","jobQuote":"lead a team of 20"}]}""")), "test-model");
        var result = service.explain(profile(), job());
        assertThat(result.matches()).hasSize(1);
        assertThat(result.gaps()).extracting(MatchExplanationService.Point::jobQuote).containsExactly("deploy services on AWS");
        assertThat(result.dropped()).isEqualTo(1);
        String sent = prompt.get().getContents();
        assertThat(sent).contains("Java", "Build secure Java APIs")
                .doesNotContain("Aarav", "aarav@example.invalid", "9999999999", "1800000", "resume.pdf");
    }

    @Test void explainFailsInsteadOfShowingNothingVerified() {
        var service = new MatchExplanationService(ChatClient.create(reply(new AtomicReference<>(), """
                {"matches":[{"point":"Made up.","profileQuote":"Rust","jobQuote":"Rust"}],"gaps":[]}""")), "test-model");
        assertThatThrownBy(() -> service.explain(profile(), job()))
                .isInstanceOfSatisfying(AssistantException.class, ex -> assertThat(ex.getStatus()).isEqualTo(502));
    }

    @Test void emptyProfileAndMissingKeyAreRejectedBeforeAnyCall() {
        var prompt = new AtomicReference<Prompt>();
        var service = new MatchExplanationService(ChatClient.create(reply(prompt, "{}")), "test-model");
        assertThatThrownBy(() -> service.explain(new JobSeekerProfile(), job()))
                .isInstanceOfSatisfying(AssistantException.class, ex -> assertThat(ex.getStatus()).isEqualTo(400));
        assertThat(prompt.get()).isNull();
        var unconfigured = new MatchExplanationService("", "test-model");
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.explain(profile(), job()))
                .isInstanceOfSatisfying(AssistantException.class, ex -> assertThat(ex.getStatus()).isEqualTo(503));
    }

    @Test void providerFailureIsReportedWithoutItsPayload() {
        ChatModel failing = p -> { throw new IllegalStateException("secret-key-123 leaked in provider message"); };
        var service = new MatchExplanationService(ChatClient.create(failing), "test-model");
        assertThatThrownBy(() -> service.explain(profile(), job()))
                .isInstanceOfSatisfying(AssistantException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(502);
                    assertThat(ex.getMessage()).doesNotContain("secret-key-123");
                });
    }

    private static ChatModel reply(AtomicReference<Prompt> captured, String json) {
        return prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        };
    }

    private static JobSeekerProfile profile() {
        var p = new JobSeekerProfile();
        p.setFirstName("Aarav"); p.setPhoneNumber("9999999999"); p.setExpectedCtc(new BigDecimal("1800000"));
        p.setResume("resume.pdf"); p.setProfessionalHeadline("Backend developer");
        p.setSkills(new ArrayList<>(List.of(new Skills(null, "Java", "Intermediate", "3", p))));
        return p;
    }

    private static JobPostActivity job() {
        var j = new JobPostActivity();
        j.setJobPostId(7); j.setJobTitle("Java Engineer");
        j.setDescriptionOfJob("<p>Build secure Java APIs and deploy services on AWS.</p>");
        return j;
    }
}
