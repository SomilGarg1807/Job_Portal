package com.somil.jobportal;

import com.somil.jobportal.entity.*;
import com.somil.jobportal.repository.*;
import com.somil.jobportal.services.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.data.domain.Page;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hiring;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @Transactional
class HiringWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired UsersRepository users;
    @Autowired UsersTypeRepository types;
    @Autowired RecruiterProfileRepository recruiters;
    @Autowired JobSeekerProfileRepository seekers;
    @Autowired JobPostActivityRepository jobs;
    @Autowired JobSeekerApplyRepository applications;
    @Autowired ApplicationStatusEventRepository events;
    @Autowired ApplicationWorkflowService workflow;
    @Autowired EntityManager em;
    @MockitoBean AiAssistantService ai;
    Users owner, outsider, candidate, otherCandidate;
    RecruiterProfile recruiter;
    JobSeekerProfile seeker;
    JobPostActivity job;
    JobSeekerApply application;
    Users account(int role) {
        Users user = new Users(); user.setEmail(UUID.randomUUID()+"@example.invalid");
        user.setPassword("test-password-hash"); user.setActive(true); user.setUserTypeId(types.findById(role).orElseThrow());
        return users.saveAndFlush(user);
    }
    RecruiterProfile recruiter(Users user) {
        var p = new RecruiterProfile(user); p.setFirstName("Taylor"); p.setLastName("Reed"); p.setCompany("Northstar Studio");
        return recruiters.saveAndFlush(p);
    }
    JobSeekerProfile seeker(Users user, String name, String skill, String years) {
        var p = new JobSeekerProfile(user); p.setFirstName(name); p.setLastName("Example");
        p.setTotalExperienceYears(new BigDecimal(years)); p.setCity("Pune"); p.setCountry("India");
        p.setSkills(new ArrayList<>(List.of(new Skills(null, skill, "Intermediate", years, p))));
        return seekers.saveAndFlush(p);
    }
    JobPostActivity job(Users user, String title) {
        var j = new JobPostActivity(); j.setPostedById(user); j.setJobTitle(title); j.setPostedDate(new Date());
        j.setDescriptionOfJob("Build secure Java APIs and deploy services on AWS. Bring 1-3 years of experience.");
        j.setJobCompanyId(new JobCompany(null,"Northstar Studio","")); j.setJobLocationId(new JobLocation(null,"Pune","Maharashtra","India"));
        j.setJobType("Full-time"); j.setRemote("Partial-Remote");
        return jobs.saveAndFlush(j);
    }
    @BeforeEach void setup() {
        owner=account(1); outsider=account(1); candidate=account(2); otherCandidate=account(2);
        recruiter=recruiter(owner); recruiter(outsider);
        seeker=seeker(candidate,"Aarav","Java","2"); seeker(otherCandidate,"Maya","Python","6");
        job=job(owner,"Java Engineer");
        application=applications.saveAndFlush(new JobSeekerApply(null,seeker,job,new Date(),""));
        application.setRecruiterNotes("private hiring notes"); applications.saveAndFlush(application);
    }
    void preview(String name, String html) throws Exception {
        if (Boolean.getBoolean("dashboard.preview")) {Files.createDirectories(Path.of("target/dashboard-preview"));Files.writeString(Path.of("target/dashboard-preview/"+name+".html"),html);}
    }
    @Test void boardFiltersInDatabaseAndExcludesOtherRecruiters() throws Exception {
        applications.saveAndFlush(new JobSeekerApply(null,seekers.findById(otherCandidate.getUserId()).orElseThrow(),job,new Date(),""));
        applications.saveAndFlush(new JobSeekerApply(null,seeker,job(outsider,"Private other role"),new Date(),""));
        String html=mvc.perform(get("/recruiter/candidates").with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter")))
                .param("keyword","Java").param("experience","1-3").param("status","APPLIED"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Aarav")))
                .andExpect(content().string(not(containsString("Maya"))))
                .andExpect(content().string(not(containsString("Private other role"))))
                .andExpect(result -> assertThat(((Page<?>)result.getModelAndView().getModel().get("results")).getTotalElements()).isEqualTo(1))
                .andReturn().getResponse().getContentAsString();
        preview("candidate-board",html);
        mvc.perform(get("/recruiter/candidates").with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker")))).andExpect(status().isForbidden());
    }
    @Test void boardAndCandidateListsHaveBoundedDistinctPages() throws Exception {
        for (int i=0;i<24;i++) {
            var a=new JobSeekerApply(null,seeker,job(owner,"Platform Engineer " + i),new Date(),"");
            a.setStatus(ApplicationStatus.values()[i % ApplicationStatus.values().length]);
            applications.saveAndFlush(a);
        }
        var recruiterAuth=user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"));
        var first=mvc.perform(get("/recruiter/candidates").with(recruiterAuth)).andExpect(status().isOk()).andReturn();
        Page<?> firstPage=(Page<?>)first.getModelAndView().getModel().get("results");
        assertThat(firstPage.getNumberOfElements()).isEqualTo(24); assertThat(firstPage.getTotalElements()).isEqualTo(25);
        preview("candidate-board-full",first.getResponse().getContentAsString());
        var second=mvc.perform(get("/recruiter/candidates").param("page","2").with(recruiterAuth)).andExpect(status().isOk()).andReturn();
        Page<?> secondPage=(Page<?>)second.getModelAndView().getModel().get("results");
        assertThat(secondPage.getNumberOfElements()).isEqualTo(1);
        assertThat(firstPage.getContent()).doesNotContainAnyElementsOf((java.util.Collection)secondPage.getContent());
        var candidateAuth=user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"));
        var candidatePage=mvc.perform(get("/applications").with(candidateAuth)).andExpect(status().isOk()).andReturn();
        assertThat(((Page<?>)candidatePage.getModelAndView().getModel().get("results")).getNumberOfElements()).isEqualTo(12);
        mvc.perform(get("/job-seeker-profile/"+candidate.getUserId()).with(recruiterAuth)).andExpect(status().isOk());
    }
    @Test void statusHistoryPersistsWhileNotesStayPrivate() throws Exception {
        String path="/applications/"+application.getId()+"/update";
        mvc.perform(post(path).servletPath(path).with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"))).with(csrf())
                .param("status","SHORTLISTED").param("notes","private interview questions").param("revision","0")).andExpect(status().is3xxRedirection());
        em.flush(); em.clear();
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId())).hasSize(1);
        String html=mvc.perform(get("/applications/"+application.getId()).with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Shortlisted")))
                .andExpect(content().string(not(containsString("private interview questions"))))
                .andExpect(content().string(not(containsString("name=\"notes\""))))
                .andReturn().getResponse().getContentAsString();
        preview("application-candidate",html);
        html=mvc.perform(get("/applications/"+application.getId()).with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("private interview questions")))
                .andReturn().getResponse().getContentAsString(); preview("application-recruiter",html);
    }
    @Test void rememberMeKeepsUsersSignedInForSevenDaysWithoutThePassword() throws Exception {
        owner.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Secret#123"));
        users.saveAndFlush(owner);
        var signIn = mvc.perform(post("/login").param("username", owner.getEmail()).param("password", "Secret#123").param("remember-me", "on"))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().maxAge("remember-me", 7 * 24 * 60 * 60)).andReturn();
        mvc.perform(get("/recruiter/candidates").cookie(signIn.getResponse().getCookie("remember-me"))).andExpect(status().isOk());
        mvc.perform(get("/recruiter/candidates")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").param("username", owner.getEmail()).param("password", "Secret#123"))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().doesNotExist("remember-me"));
    }
    @Test void databaseHealthIsPublicAndQueriesTheDatabase() throws Exception {
        mvc.perform(get("/health/db")).andExpect(status().isOk()).andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.latencyMs").isNumber()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void atsScoreAndCandidateProfileRespectApplicationAccess() throws Exception {
        var recruiterAuth=user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"));
        var candidateAuth=user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"));
        var outsiderAuth=user(outsider.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"));
        String score="/applications/"+application.getId()+"/ats-score";
        for (var auth : List.of(recruiterAuth, candidateAuth))
            mvc.perform(get(score).with(auth)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.score").isNumber()).andExpect(jsonPath("$.matched[0]").value("java"));
        mvc.perform(get(score).with(outsiderAuth)).andExpect(status().isForbidden());
        String detail=mvc.perform(get("/applications/"+application.getId()).with(recruiterAuth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/applications/"+application.getId()+"/candidate")))
                .andExpect(content().string(containsString("Check ATS score"))).andReturn().getResponse().getContentAsString();
        preview("application-recruiter-ats",detail);
        String page="/applications/"+application.getId()+"/candidate";
        String html=mvc.perform(get(page).with(recruiterAuth)).andExpect(status().isOk())
                .andExpect(content().string(containsString("ATS SCORE FOR THIS JOB")))
                .andExpect(content().string(containsString("Aarav Example")))
                .andExpect(content().string(containsString("data-avatar-initial")))
                .andExpect(content().string(not(containsString("private hiring notes"))))
                .andReturn().getResponse().getContentAsString(); preview("candidate-profile",html);
        mvc.perform(get(page).with(candidateAuth)).andExpect(status().isForbidden());
        mvc.perform(get(page).with(outsiderAuth)).andExpect(status().isForbidden());
        mvc.perform(get("/resume-comparison/"+job.getJobPostId()+"/ats-score").with(candidateAuth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.band").isString());
        mvc.perform(get("/resume-comparison/"+job.getJobPostId()+"/ats-score").with(recruiterAuth)).andExpect(status().isForbidden());
        verifyNoInteractions(ai);
    }
    @Test void updatesRequireOwnershipAndCsrfAndRejectInvalidInput() throws Exception {
        String path="/applications/"+application.getId()+"/update";
        mvc.perform(post(path).servletPath(path).with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter")))
                .param("status","INTERVIEW").param("revision","0")).andExpect(status().isForbidden());
        for(Users viewer:List.of(outsider,candidate))
            mvc.perform(post(path).servletPath(path).with(user(viewer.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(viewer==candidate?"Job Seeker":"Recruiter"))).with(csrf())
                    .param("status","INTERVIEW").param("revision","0")).andExpect(status().isForbidden());
        mvc.perform(post(path).servletPath(path).with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"))).with(csrf())
                .param("status","INVALID").param("revision","0")).andExpect(status().isBadRequest());
        mvc.perform(post(path).servletPath(path).with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"))).with(csrf())
                .param("status","INTERVIEW").param("notes","x".repeat(3001)).param("revision","0")).andExpect(status().isBadRequest());
        assertThat(events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId())).isEmpty();
    }
    @Test void duplicateStatusDoesNotAddEventsAndStaleUpdatesCannotOverwrite() throws Exception {
        workflow.update(application.getId(),recruiter,ApplicationStatus.INTERVIEW,"first",0);
        workflow.update(application.getId(),recruiter,ApplicationStatus.INTERVIEW,"revised",1);
        assertThat(events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId())).hasSize(1);
        String path="/applications/"+application.getId()+"/update";
        mvc.perform(post(path).servletPath(path).with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter"))).with(csrf())
                .param("status","REJECTED").param("notes","stale").param("revision","0"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertThat(applications.findById(application.getId()).orElseThrow().getRecruiterNotes()).isEqualTo("revised");
    }
    @Test void candidatePagesPaginateAndPrivateResourcesRejectUnrelatedUsers() throws Exception {
        String html=mvc.perform(get("/applications").with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); preview("applications",html);
        mvc.perform(get("/applications").param("page","999").with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))))
                .andExpect(redirectedUrl("/applications?page=1"));
        for(String path:List.of("/applications/"+application.getId(),"/job-seeker-profile/"+candidate.getUserId(),
                "/photos/candidate/"+candidate.getUserId()+"/resume.pdf"))
            mvc.perform(get(path).with(user(outsider.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter")))).andExpect(status().isForbidden());
        mvc.perform(get("/job-seeker-profile/downloadResume").param("userID",String.valueOf(candidate.getUserId())).param("fileName","resume.pdf")
                .with(user(otherCandidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker")))).andExpect(status().isForbidden());
        mvc.perform(get("/applications/"+application.getId()).with(user(otherCandidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker")))).andExpect(status().isForbidden());
    }
    @Test void comparisonRequiresConsentAndUsesStoredRequirementsWithSharedRateLimit() throws Exception {
        String path="/resume-comparison/"+job.getJobPostId();
        String resume="Built Java APIs with Spring Boot, implemented unit tests and deployed services on AWS.";
        when(ai.isConfigured()).thenReturn(true); when(ai.compareResume(anyString(),anyString())).thenReturn("Evidence: Java APIs. Review deployment topics.");
        mvc.perform(post(path).servletPath(path).with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))).with(csrf()).param("resume",resume))
                .andExpect(status().isBadRequest()); verify(ai,never()).compareResume(anyString(),anyString());
        var session=new MockHttpSession();
        mvc.perform(post(path).servletPath(path).session(session).with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))).with(csrf()).param("resume",resume).param("consent","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Evidence: Java APIs. Review deployment topics."));
        verify(ai).compareResume(eq(resume),contains("Build secure Java APIs"));
        mvc.perform(post(path).servletPath(path).session(session).with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))).with(csrf()).param("resume",resume).param("consent","true"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post(path).servletPath(path).with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))).param("resume",resume).param("consent","true"))
                .andExpect(status().isForbidden());
    }
    @Test void comparisonPageDoesNotSendResumeWithoutSubmissionAndHandlesProviderFailure() throws Exception {
        String path="/resume-comparison/"+job.getJobPostId();
        String html=mvc.perform(get(path).with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString(); preview("resume-comparison",html); verifyNoInteractions(ai);
        mvc.perform(get(path).with(user(owner.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Recruiter")))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        when(ai.isConfigured()).thenReturn(true); when(ai.compareResume(anyString(),anyString())).thenThrow(new AiAssistantService.AssistantException(502,"Try again later."));
        mvc.perform(post(path).servletPath(path).with(user(candidate.getEmail()).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("Job Seeker"))).with(csrf())
                .param("resume","Relevant Java engineering experience and AWS deployment projects. Skills in SQL and testing.").param("consent","true"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message").value("Try again later."));
    }
}
