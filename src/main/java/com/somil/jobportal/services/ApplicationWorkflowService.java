package com.somil.jobportal.services;

import com.somil.jobportal.entity.*;
import com.somil.jobportal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class ApplicationWorkflowService {
    private final JobSeekerApplyRepository applications;
    private final ApplicationStatusEventRepository events;
    public ApplicationWorkflowService(JobSeekerApplyRepository applications, ApplicationStatusEventRepository events) {
        this.applications = applications; this.events = events;
    }
    public JobSeekerApply accessible(int id, Object profile) {
        var application = applications.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean candidate = profile instanceof JobSeekerProfile seeker && Objects.equals(seeker.getUserAccountId(), application.getUserId().getUserAccountId());
        if (!candidate && !owns(application, profile)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return application;
    }
    public boolean owns(JobSeekerApply application, Object profile) {
        return profile instanceof RecruiterProfile recruiter && application.getJob().getPostedById() != null
                && Objects.equals(recruiter.getUserAccountId(), application.getJob().getPostedById().getUserId());
    }
    public List<ApplicationStatusEvent> timeline(JobSeekerApply application) {
        List<ApplicationStatusEvent> timeline = new ArrayList<>();
        timeline.add(new ApplicationStatusEvent(application, ApplicationStatus.APPLIED,
                application.getApplyDate()));
        timeline.addAll(events.findByApplicationIdOrderByOccurredAtAscIdAsc(application.getId()));
        return timeline;
    }
    @Transactional
    public void update(int id, Object profile, ApplicationStatus status, String notes, long revision) {
        var application = applications.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!owns(application, profile)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (status == null || notes == null || notes.length() > 3000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (revision != application.getWorkflowRevision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This application changed. Reload it before saving.");
        if (application.getStatus() != status) {
            application.setStatus(status);
            events.save(new ApplicationStatusEvent(application, status, new Date()));
        }
        application.setRecruiterNotes(notes.trim());
        application.setWorkflowRevision(revision + 1);
        applications.save(application);
    }
}
