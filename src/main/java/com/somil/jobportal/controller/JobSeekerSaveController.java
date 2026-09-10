package com.somil.jobportal.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.JobSeekerSave;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.services.JobPostActivityService;
import com.somil.jobportal.services.JobSeekerProfileService;
import com.somil.jobportal.services.JobSeekerSaveService;
import com.somil.jobportal.services.UsersService;

@Controller
public class JobSeekerSaveController {

    private final UsersService usersService;
    private final JobSeekerProfileService jobSeekerProfileService;
    private final JobPostActivityService jobPostActivityService;
    private final JobSeekerSaveService jobSeekerSaveService;

    public JobSeekerSaveController(UsersService usersService, JobSeekerProfileService jobSeekerProfileService, JobPostActivityService jobPostActivityService, JobSeekerSaveService jobSeekerSaveService) {
        this.usersService = usersService;
        this.jobSeekerProfileService = jobSeekerProfileService;
        this.jobPostActivityService = jobPostActivityService;
        this.jobSeekerSaveService = jobSeekerSaveService;
    }

    @PostMapping("job-details/save/{id}")
    public String save(@PathVariable("id") int id) {
        Object profile = usersService.getCurrentUserProfile();
        if (!(profile instanceof JobSeekerProfile seeker))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        JobPostActivity job = jobPostActivityService.getOne(id);
        jobSeekerSaveService.addNew(new JobSeekerSave(null, seeker, job));
        return "redirect:/job-details-apply/" + id;
    }

    @GetMapping("saved-jobs/")
    public String savedJobs(Model model) {

        List<JobPostActivity> jobPost = new ArrayList<>();
        Object currentUserProfile = usersService.getCurrentUserProfile();

        List<JobSeekerSave> jobSeekerSaveList = jobSeekerSaveService.getCandidatesJob((JobSeekerProfile) currentUserProfile);
        for (JobSeekerSave jobSeekerSave : jobSeekerSaveList) {
            jobPost.add(jobSeekerSave.getJob());
        }

        model.addAttribute("jobPost", jobPost);
        model.addAttribute("user", currentUserProfile);

        return "saved-jobs";
    }
}