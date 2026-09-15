package com.somil.jobportal.services;

import com.somil.jobportal.entity.*;
import com.somil.jobportal.repository.JobSeekerApplyRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;

@Service
public class CandidateAccessService {
    private final UsersService users;
    private final JobSeekerApplyRepository applications;
    private final JobSeekerProfileService profiles;
    private final FileStorageService storage;
    public CandidateAccessService(UsersService users, JobSeekerApplyRepository applications, JobSeekerProfileService profiles,
                                  FileStorageService storage) {
        this.users = users; this.applications = applications; this.profiles = profiles; this.storage = storage;
    }
    public JobSeekerProfile requireAccess(int candidateId) {
        Object viewer = users.getCurrentUserProfile();
        boolean own = viewer instanceof JobSeekerProfile seeker && Objects.equals(seeker.getUserAccountId(), candidateId);
        boolean recruiter = viewer instanceof RecruiterProfile profile
                && applications.existsByUserIdUserAccountIdAndJobPostedByIdUserId(candidateId, profile.getUserAccountId());
        if (!own && !recruiter) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return profiles.getOne(candidateId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    /** The candidate's current resume or photo, looked up by the name stored on their profile. */
    public StoredFile file(JobSeekerProfile profile, String filename) {
        String kind = Objects.equals(filename, profile.getResume()) ? FileStorageService.CANDIDATE_RESUME
                : Objects.equals(filename, profile.getProfilePhoto()) ? FileStorageService.CANDIDATE_PHOTO : null;
        if (kind == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return storage.find(kind, profile.getUserAccountId(), filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
