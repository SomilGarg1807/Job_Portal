package com.somil.jobportal.services;

import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.somil.jobportal.ai.EmbeddingSourceChanged;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.repository.JobSeekerProfileRepository;
import com.somil.jobportal.repository.UsersRepository;

@Service
public class JobSeekerProfileService {

    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final UsersRepository usersRepository;
    private final ApplicationEventPublisher events;

    public JobSeekerProfileService(JobSeekerProfileRepository jobSeekerProfileRepository, UsersRepository usersRepository,
                                   ApplicationEventPublisher events) {
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.usersRepository = usersRepository;
        this.events = events;
    }

    public Optional<JobSeekerProfile> getOne(Integer id) {
        return jobSeekerProfileRepository.findById(id);
    }

    public JobSeekerProfile addNew(JobSeekerProfile jobSeekerProfile) {
        JobSeekerProfile saved = jobSeekerProfileRepository.save(jobSeekerProfile);
        // Refreshes the profile's embedding in the background when semantic matching is enabled.
        if (saved != null && saved.getUserAccountId() != null) events.publishEvent(EmbeddingSourceChanged.candidate(saved.getUserAccountId()));
        return saved;
    }

    public JobSeekerProfile getCurrentSeekerProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String currentUsername = authentication.getName();
            Users users = usersRepository.findByEmail(currentUsername).orElseThrow(() -> new UsernameNotFoundException("User not found"));
            Optional<JobSeekerProfile> seekerProfile = getOne(users.getUserId());
            return seekerProfile.orElse(null);
        } else return null;

    }
}