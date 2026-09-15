package com.somil.jobportal.controller;

import com.somil.jobportal.config.*;
import com.somil.jobportal.entity.*;
import com.somil.jobportal.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobSeekerApplyController.class)
@Import(WebSecurityConfig.class)
class PublicJobDetailsTests {
    @Autowired MockMvc mvc;
    @MockitoBean JobPostActivityService jobs;
    @MockitoBean UsersService users;
    @MockitoBean JobSeekerApplyService applications;
    @MockitoBean JobSeekerSaveService saves;
    @MockitoBean RecruiterProfileService recruiters;
    @MockitoBean JobSeekerProfileService seekers;
    @MockitoBean CustomUserDetailsService details;
    @MockitoBean CustomAuthenticationSuccessHandler success;
    JobPostActivity job;
    @BeforeEach void setup() {
        job = new JobPostActivity(); job.setJobPostId(7); job.setJobTitle("Java Engineer");
        job.setJobCompanyId(new JobCompany(1,"Example Company",""));
        job.setJobLocationId(new JobLocation(1,"Pune","Maharashtra","India"));
        job.setDescriptionOfJob("<p>Build APIs with Java. Bring 1-3 years of relevant experience.</p><script>alert('bad')</script>");
        var publisher = new Users(); publisher.setUserId(42); publisher.setEmail("private@example.com"); publisher.setPassword("private-password");
        job.setPostedById(publisher);
        when(jobs.getOne(7)).thenReturn(job);
    }
    @Test void guestsCanReadSanitizedDescriptionWithoutPrivateControls() throws Exception {
        String html = mvc.perform(get("/job-details-apply/7"))
                .andExpect(status().isOk()).andExpect(model().attribute("guest", true))
                .andExpect(content().string(containsString("Build APIs with Java")))
                .andExpect(content().string(containsString("Sign in to apply")))
                .andExpect(content().string(not(containsString("/job-details/apply/7"))))
                .andExpect(content().string(not(containsString("applicants-card"))))
                .andExpect(content().string(not(containsString("id=\"ai-form\""))))
                .andExpect(content().string(not(containsString("private-password"))))
                .andExpect(content().string(not(containsString("private@example.com"))))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andReturn().getResponse().getContentAsString();
        verifyNoInteractions(users, applications, saves);
        if (Boolean.getBoolean("dashboard.preview")) {
            Files.createDirectories(Path.of("target/dashboard-preview"));
            Files.writeString(Path.of("target/dashboard-preview/guest-job.html"), html);
        }
    }
    @Test void guestsCannotApplySaveEditOrReadProfiles() throws Exception {
        for (String path : List.of("/job-details/apply/7", "/job-details/save/7", "/dashboard/addNew"))
            mvc.perform(post(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/job-seeker-profile/42")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(applications, saves);
    }
    @Test void onlyPostingRecruiterCanSeeApplicantSection() throws Exception {
        var profile = new RecruiterProfile(); profile.setUserAccountId(43); profile.setFirstName("Alex");
        when(users.getCurrentUserProfile()).thenReturn(profile);
        mvc.perform(get("/job-details-apply/7").with(user("other").authorities(() -> "Recruiter")))
                .andExpect(status().isOk()).andExpect(model().attribute("owner", false))
                .andExpect(content().string(not(containsString("applicants-card"))));
        verifyNoInteractions(applications);
        profile.setUserAccountId(42);
        when(applications.getJobCandidates(job)).thenReturn(List.of());
        mvc.perform(get("/job-details-apply/7").with(user("owner").authorities(() -> "Recruiter")))
                .andExpect(status().isOk()).andExpect(model().attribute("owner", true))
                .andExpect(content().string(containsString("applicants-card")));
    }
    @Test void postingRecruiterSeesPageWithApplicants() throws Exception {
        var profile = new RecruiterProfile(); profile.setUserAccountId(42); profile.setFirstName("Alex");
        when(users.getCurrentUserProfile()).thenReturn(profile);
        var named = new JobSeekerProfile(); named.setUserAccountId(5); named.setFirstName("Priya");
        var unnamed = new JobSeekerProfile(); unnamed.setUserAccountId(6);
        when(applications.getJobCandidates(job)).thenReturn(List.of(
                new JobSeekerApply(1, named, job, new java.sql.Timestamp(System.currentTimeMillis()), null),
                new JobSeekerApply(2, unnamed, job, null, null),
                new JobSeekerApply(3, null, job, new java.util.Date(), null)));
        mvc.perform(get("/job-details-apply/7").with(user("owner").authorities(() -> "Recruiter")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Priya")))
                .andExpect(content().string(containsString("/applications/2/candidate")))
                .andExpect(content().string(not(containsString("/job-seeker-profile/6"))));
    }
    @Test void missingPublicJobReturns404() throws Exception {
        when(jobs.getOne(999)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/job-details-apply/999")).andExpect(status().isNotFound());
    }
}
