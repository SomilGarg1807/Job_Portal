package com.somil.jobportal.controller;

import com.somil.jobportal.config.CustomAuthenticationSuccessHandler;
import com.somil.jobportal.config.WebSecurityConfig;
import com.somil.jobportal.entity.*;
import com.somil.jobportal.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({JobPostActivityController.class, AiAssistantController.class, HealthController.class})
@Import(WebSecurityConfig.class)
class DashboardTests {
    @Autowired MockMvc mvc;
    @MockitoBean UsersService users;
    @MockitoBean JobPostActivityService jobs;
    @MockitoBean JobSeekerApplyService applications;
    @MockitoBean JobSeekerSaveService saves;
    @MockitoBean AiAssistantService assistant;
    @MockitoBean CustomUserDetailsService details;
    @MockitoBean CustomAuthenticationSuccessHandler successHandler;

    private JobSeekerProfile seeker;
    private List<JobPostActivity> listings;

    @BeforeEach
    void setup() {
        seeker = new JobSeekerProfile();
        seeker.setFirstName("Somil");
        seeker.setLastName("Shah");
        seeker.setCity("Pune");
        seeker.setDesiredJobTitle("Software engineer");
        listings = List.of(job(1, "Frontend Developer", "Pixel Studio"), job(2, "Java Backend Engineer", "Northstar Labs"), job(3, "Product Designer", "Folio"));
        when(users.getCurrentUserProfile()).thenReturn(seeker);
        when(jobs.getAll()).thenReturn(listings);
        when(applications.getCandidatesJobs(seeker)).thenReturn(List.of(new JobSeekerApply(1, seeker, listings.get(1), new Date(), "")));
        when(saves.getCandidatesJob(seeker)).thenReturn(List.of(new JobSeekerSave(1, seeker, listings.get(0))));
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void seekerDashboardRendersRealCountsAndJobDetails() throws Exception {
        String html = mvc.perform(get("/dashboard/"))
                .andExpect(status().isOk()).andExpect(model().attribute("applicationCount", 1))
                .andExpect(model().attribute("savedCount", 1)).andExpect(model().attribute("profileCompletion", 66))
                .andExpect(content().string(containsString("Java Backend Engineer")))
                .andExpect(content().string(containsString("Create practice plan")))
                .andExpect(content().string(not(containsString("Review applicants"))))
                .andReturn().getResponse().getContentAsString();
        preview("seeker", html);
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void appliedAndSavedViewsFilterJobsWithoutChangingTotals() throws Exception {
        mvc.perform(get("/dashboard/").param("view", "applied"))
                .andExpect(status().isOk()).andExpect(model().attribute("jobPost", hasSize(1)))
                .andExpect(content().string(containsString("Java Backend Engineer")))
                .andExpect(content().string(not(containsString("Frontend Developer"))));
        mvc.perform(get("/dashboard/").param("view", "saved"))
                .andExpect(status().isOk()).andExpect(model().attribute("jobPost", hasSize(1)))
                .andExpect(content().string(containsString("Frontend Developer")))
                .andExpect(model().attribute("applicationCount", 1));
    }

    @Test @WithMockUser(authorities = "Recruiter")
    void recruiterSearchKeepsOwnershipAndCandidateCounts() throws Exception {
        var recruiter = new RecruiterProfile();
        recruiter.setUserAccountId(42);
        recruiter.setFirstName("Somil");
        recruiter.setCompany("Northstar Labs");
        when(users.getCurrentUserProfile()).thenReturn(recruiter);
        var owned = new RecruiterJobsDto(8L, 2, "Java Backend Engineer", listings.get(1).getJobLocationId(), listings.get(1).getJobCompanyId());
        when(jobs.getRecruiterJobs(42)).thenReturn(List.of(owned));
        when(jobs.search(any(), any(), anyList(), anyList(), any(), anyBoolean(), anyBoolean())).thenReturn(listings);
        String html = mvc.perform(get("/dashboard/").param("job", "Engineer").param("sort", "applicants"))
                .andExpect(status().isOk()).andExpect(model().attribute("jobPost", hasSize(1)))
                .andExpect(model().attribute("applicationCount", 8L))
                .andExpect(content().string(containsString("8 applicants")))
                .andExpect(content().string(not(containsString("Frontend Developer"))))
                .andExpect(content().string(containsString("Draft job description")))
                .andReturn().getResponse().getContentAsString();
        preview("recruiter", html);
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void sortsByTitleAndEscapesUserContent() throws Exception {
        seeker.setFirstName("<script>alert(1)</script>");
        mvc.perform(get("/dashboard/").param("sort", "title"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jobPost", contains(listings.get(0), listings.get(1), listings.get(2))))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void pagesTwelveJobsAndClampsOutOfRangeRequests() throws Exception {
        var many = java.util.stream.IntStream.rangeClosed(1, 26)
                .mapToObj(i -> job(i, "Developer " + i, "Example")).toList();
        when(jobs.getAll()).thenReturn(many);
        seeker.setDesiredJobTitle(null);
        String html = mvc.perform(get("/dashboard/").param("page", "2"))
                .andExpect(model().attribute("jobPost", hasSize(12)))
                .andExpect(model().attribute("resultCount", 26)).andExpect(model().attribute("totalPages", 3))
                .andExpect(model().attribute("rangeStart", 13)).andExpect(model().attribute("rangeEnd", 24))
                .andReturn().getResponse().getContentAsString();
        preview("paged", html);
        mvc.perform(get("/dashboard/").param("page", "999"))
                .andExpect(model().attribute("page", 3)).andExpect(model().attribute("jobPost", hasSize(2)));
        mvc.perform(get("/dashboard/").param("page", "-2"))
                .andExpect(model().attribute("page", 1));
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void ranksProfileMatchBeforeNewerJobsAndFallsBackWhenNothingMatches() throws Exception {
        var oldMatch = job(1, "Java Backend Engineer", "Example");
        oldMatch.setPostedDate(new Date(1000));
        var newer = job(2, "Product Designer", "Example");
        newer.setPostedDate(new Date(2000));
        when(jobs.getAll()).thenReturn(List.of(newer, oldMatch));
        seeker.setDesiredJobTitle("Java Backend Engineer");
        mvc.perform(get("/dashboard/"))
                .andExpect(model().attribute("jobPost", contains(oldMatch, newer)));
        seeker.setDesiredJobTitle("Veterinarian");
        mvc.perform(get("/dashboard/"))
                .andExpect(model().attribute("jobPost", contains(newer, oldMatch)))
                .andExpect(model().attribute("rankingMessage", containsString("latest jobs")));
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void emptyDashboardAndMissingOptionalFieldsRender() throws Exception {
        when(jobs.getAll()).thenReturn(List.of());
        when(users.getCurrentUserProfile()).thenReturn(new JobSeekerProfile());
        String html = mvc.perform(get("/dashboard/"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("different search")))
                .andReturn().getResponse().getContentAsString();
        preview("empty", html);
    }

    @Test
    void anonymousUsersCannotUseDashboardOrAi() throws Exception {
        mvc.perform(get("/dashboard/")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/ai/assist").contentType(MediaType.APPLICATION_JSON).content(request(true)))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(assistant);
    }

    @Test
    void healthIsPublicUncachedAndDoesNotCallDependencies() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().string("Cache-Control", "no-store"));
        // The servlet container suppresses HEAD bodies; MockMvc does not emulate that step.
        mvc.perform(head("/health")).andExpect(status().isOk());
        verifyNoInteractions(assistant);
        verify(users, never()).getCurrentUserProfile();
        verify(jobs, never()).getAll();
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void aiRequiresConsentAndReportsMissingConfiguration() throws Exception {
        mvc.perform(post("/api/ai/assist").header("X-Requested-With", "HotDevJobs")
                        .contentType(MediaType.APPLICATION_JSON).content(request(false)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/assist").header("X-Requested-With", "HotDevJobs")
                        .contentType(MediaType.APPLICATION_JSON).content(request(true)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message", containsString("GEMINI_API_KEY")));
        verify(assistant, never()).assist(anyString(), anyBoolean());
    }

    @Test @WithMockUser(authorities = "Recruiter")
    void aiUsesAuthenticatedRoleAndThrottlesRepeatRequests() throws Exception {
        when(assistant.isConfigured()).thenReturn(true);
        when(assistant.assist(anyString(), eq(true))).thenReturn("A reviewed draft");
        var session = new MockHttpSession();
        mvc.perform(post("/api/ai/assist").session(session).header("X-Requested-With", "HotDevJobs")
                        .contentType(MediaType.APPLICATION_JSON).content(request(true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("A reviewed draft"));
        mvc.perform(post("/api/ai/assist").session(session).header("X-Requested-With", "HotDevJobs")
                        .contentType(MediaType.APPLICATION_JSON).content(request(true)))
                .andExpect(status().isTooManyRequests());
        verify(assistant, times(1)).assist("Java developer with Spring Boot", true);
    }

    @Test @WithMockUser(authorities = "Job Seeker")
    void aiRejectsMissingHeaderAndOversizedInput() throws Exception {
        mvc.perform(post("/api/ai/assist").contentType(MediaType.APPLICATION_JSON).content(request(true)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/assist").header("X-Requested-With", "HotDevJobs")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"context\":\"" + "a".repeat(3001) + "\",\"consent\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assistant);
    }

    private String request(boolean consent) {
        return "{\"context\":\"Java developer with Spring Boot\",\"consent\":" + consent + "}";
    }

    private JobPostActivity job(int id, String title, String company) {
        var job = new JobPostActivity();
        job.setJobPostId(id);
        job.setJobTitle(title);
        job.setJobCompanyId(new JobCompany(id, company, ""));
        job.setJobLocationId(new JobLocation(id, "Pune", "Maharashtra", "India"));
        job.setJobType("Full-Time");
        job.setRemote("Partial-Remote");
        job.setSalary("INR 8–14 LPA");
        job.setPostedDate(new Date(1_780_000_000_000L + id * 86_400_000L));
        return job;
    }

    private void preview(String role, String html) throws Exception {
        if (Boolean.getBoolean("dashboard.preview")) {
            Path directory = Path.of("target", "dashboard-preview");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(role + ".html"), html);
        }
    }

    @Test @WithMockUser(authorities = "Recruiter")
    void jobEditorRendersAccessibleDescription() throws Exception {
        var recruiter = new RecruiterProfile();
        recruiter.setUserAccountId(42);
        recruiter.setFirstName("Alex");
        recruiter.setCompany("Northstar Labs");
        when(users.getCurrentUserProfile()).thenReturn(recruiter);
        preview("editor", mvc.perform(get("/dashboard/add"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("description-help")))
                .andReturn().getResponse().getContentAsString());
    }
}
