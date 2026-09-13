package com.somil.jobportal.services;

import com.somil.jobportal.entity.*;
import com.somil.jobportal.repository.JobSeekerApplyRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.io.IOException;
import java.util.Objects;

@Service
public class CandidateAccessService {
    private final UsersService users;
    private final JobSeekerApplyRepository applications;
    private final JobSeekerProfileService profiles;
    public CandidateAccessService(UsersService users, JobSeekerApplyRepository applications, JobSeekerProfileService profiles) {
        this.users = users; this.applications = applications; this.profiles = profiles;
    }
    public JobSeekerProfile requireAccess(int candidateId) {
        Object viewer = users.getCurrentUserProfile();
        boolean own = viewer instanceof JobSeekerProfile seeker && Objects.equals(seeker.getUserAccountId(), candidateId);
        boolean recruiter = viewer instanceof RecruiterProfile profile
                && applications.existsByUserIdUserAccountIdAndJobPostedByIdUserId(candidateId, profile.getUserAccountId());
        if (!own && !recruiter) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return profiles.getOne(candidateId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public Path file(JobSeekerProfile profile, String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path base = Path.of("photos", "candidate", String.valueOf(profile.getUserAccountId())).toAbsolutePath().normalize();
        Path file = base.resolve(filename).normalize();
        try {
            if (!file.startsWith(base) || !Files.isRegularFile(file) || !file.toRealPath().startsWith(base.toRealPath()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        return file;
    }
}
