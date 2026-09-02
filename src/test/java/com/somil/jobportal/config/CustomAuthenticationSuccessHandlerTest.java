package com.somil.jobportal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.RecruiterProfile;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.repository.JobSeekerProfileRepository;
import com.somil.jobportal.repository.RecruiterProfileRepository;
import com.somil.jobportal.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationSuccessHandlerTest {

    @Mock
    private UsersRepository usersRepository;
    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;
    @Mock
    private JobSeekerProfileRepository jobSeekerProfileRepository;

    private CustomAuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomAuthenticationSuccessHandler(
                usersRepository, recruiterProfileRepository, jobSeekerProfileRepository);
    }

    @Test
    void recruiterWithoutCompanyIsSentToProfileSetup() throws Exception {
        Users user = user(7, "recruiter@example.com");
        when(usersRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(recruiterProfileRepository.findById(7)).thenReturn(Optional.of(new RecruiterProfile(user)));

        MockHttpServletResponse response = authenticate(user.getEmail(), "Recruiter");

        assertThat(response.getRedirectedUrl()).isEqualTo("/recruiter-profile/?onboarding=true");
    }

    @Test
    void completeRecruiterIsSentToDashboard() throws Exception {
        Users user = user(8, "complete-recruiter@example.com");
        RecruiterProfile profile = new RecruiterProfile(user);
        profile.setCompany("Acme Technology");
        when(usersRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(recruiterProfileRepository.findById(8)).thenReturn(Optional.of(profile));

        MockHttpServletResponse response = authenticate(user.getEmail(), "Recruiter");

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard/");
    }

    @Test
    void incompleteJobSeekerIsSentToProfileSetup() throws Exception {
        Users user = user(9, "seeker@example.com");
        when(usersRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jobSeekerProfileRepository.findById(9)).thenReturn(Optional.of(new JobSeekerProfile(user)));

        MockHttpServletResponse response = authenticate(user.getEmail(), "Job Seeker");

        assertThat(response.getRedirectedUrl()).isEqualTo("/job-seeker-profile/?onboarding=true");
    }

    private MockHttpServletResponse authenticate(String email, String authority) throws Exception {
        User principal = new User(email, "password", List.of(new SimpleGrantedAuthority(authority)));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);
        return response;
    }

    private Users user(int id, String email) {
        Users user = new Users();
        user.setUserId(id);
        user.setEmail(email);
        return user;
    }
}
