package com.somil.jobportal.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.RecruiterProfile;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.repository.JobSeekerProfileRepository;
import com.somil.jobportal.repository.RecruiterProfileRepository;
import com.somil.jobportal.repository.UsersRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UsersRepository usersRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;

    public CustomAuthenticationSuccessHandler(UsersRepository usersRepository,
                                              RecruiterProfileRepository recruiterProfileRepository,
                                              JobSeekerProfileRepository jobSeekerProfileRepository) {
        this.usersRepository = usersRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        boolean hasJobSeekerRole = authentication.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("Job Seeker"));
        boolean hasRecruiterRole = authentication.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("Recruiter"));

        Users user = usersRepository.findByEmail(username).orElse(null);

        if (hasRecruiterRole && user != null) {
            RecruiterProfile profile = recruiterProfileRepository.findById(user.getUserId()).orElse(null);
            if (profile == null || !StringUtils.hasText(profile.getCompany())) {
                response.sendRedirect("/recruiter-profile/?onboarding=true");
                return;
            }
        }

        if (hasJobSeekerRole && user != null) {
            JobSeekerProfile profile = jobSeekerProfileRepository.findById(user.getUserId()).orElse(null);
            if (isIncomplete(profile)) {
                response.sendRedirect("/job-seeker-profile/?onboarding=true");
                return;
            }
        }

        if (hasRecruiterRole || hasJobSeekerRole) {
            response.sendRedirect("/dashboard/");
        }
    }

    private boolean isIncomplete(JobSeekerProfile profile) {
        return profile == null
                || !StringUtils.hasText(profile.getFirstName())
                || !StringUtils.hasText(profile.getLastName())
                || !StringUtils.hasText(profile.getCountry())
                || !StringUtils.hasText(profile.getState())
                || !StringUtils.hasText(profile.getCity())
                || !StringUtils.hasText(profile.getWorkAuthorization())
                || !StringUtils.hasText(profile.getEmploymentType());
    }
}
