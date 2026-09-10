package com.somil.jobportal.controller;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import com.somil.jobportal.util.JobContent;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import com.somil.jobportal.entity.*;
import com.somil.jobportal.services.*;

@Controller
public class JobSeekerApplyController {

    private final JobPostActivityService jobPostActivityService;
    private final UsersService usersService;
    private final JobSeekerApplyService jobSeekerApplyService;
    private final JobSeekerSaveService jobSeekerSaveService;
    private final RecruiterProfileService recruiterProfileService;
    private final JobSeekerProfileService jobSeekerProfileService;


    @Autowired
    public JobSeekerApplyController(JobPostActivityService jobPostActivityService, UsersService usersService, JobSeekerApplyService jobSeekerApplyService, JobSeekerSaveService jobSeekerSaveService, RecruiterProfileService recruiterProfileService, JobSeekerProfileService jobSeekerProfileService) {
        this.jobPostActivityService = jobPostActivityService;
        this.usersService = usersService;
        this.jobSeekerApplyService = jobSeekerApplyService;
        this.jobSeekerSaveService = jobSeekerSaveService;
        this.recruiterProfileService = recruiterProfileService;
        this.jobSeekerProfileService = jobSeekerProfileService;
    }

    @GetMapping("job-details-apply/{id}")
    public String display(@PathVariable("id") int id, Model model) {
        JobPostActivity job = jobPostActivityService.getOne(id);
        Object profile = usersService.getCurrentUserProfile();
        boolean recruiter = profile instanceof RecruiterProfile;
        boolean owner = recruiter && job.getPostedById() != null
                && ((RecruiterProfile) profile).getUserAccountId() == job.getPostedById().getUserId();
        model.addAttribute("recruiter", recruiter);
        model.addAttribute("owner", owner);
        model.addAttribute("applyList", owner ? jobSeekerApplyService.getJobCandidates(job) : List.of());
        model.addAttribute("alreadyApplied", false);
        model.addAttribute("alreadySaved", false);
        if (profile instanceof JobSeekerProfile seeker) {
            model.addAttribute("alreadyApplied", jobSeekerApplyService.getCandidatesJobs(seeker).stream()
                    .anyMatch(a -> Objects.equals(a.getJob().getJobPostId(), id)));
            model.addAttribute("alreadySaved", jobSeekerSaveService.getCandidatesJob(seeker).stream()
                    .anyMatch(a -> Objects.equals(a.getJob().getJobPostId(), id)));
        }
        String description = JobContent.safeHtml(job.getDescriptionOfJob());
        model.addAttribute("safeDescription", description.isBlank() ? "No description has been provided yet." : description);
        model.addAttribute("demoJob", job.getJobCompanyId() != null && Objects.toString(job.getJobCompanyId().getName(), "").endsWith("(Demo)"));
        String context = "Role: " + Objects.toString(job.getJobTitle(), "") + "\nRequirements: " + JobContent.plainText(description);
        model.addAttribute("aiContext", context.substring(0, Math.min(context.length(), 2400)));
        model.addAttribute("jobDetails", job);
        model.addAttribute("user", profile);
        return "job-details";
    }

    @PostMapping("job-details/apply/{id}")
    public String apply(@PathVariable("id") int id) {
        Object profile = usersService.getCurrentUserProfile();
        if (!(profile instanceof JobSeekerProfile seeker)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        JobPostActivity job = jobPostActivityService.getOne(id);
        jobSeekerApplyService.addNew(new JobSeekerApply(null, seeker, job, new Date(), null));
        return "redirect:/job-details-apply/" + id;
    }
}
