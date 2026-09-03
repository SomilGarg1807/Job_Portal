package com.somil.jobportal.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerApply;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.JobSeekerSave;
import com.somil.jobportal.entity.RecruiterJobsDto;
import com.somil.jobportal.entity.RecruiterProfile;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.services.JobPostActivityService;
import com.somil.jobportal.services.JobSeekerApplyService;
import com.somil.jobportal.services.JobSeekerSaveService;
import com.somil.jobportal.services.UsersService;

@Controller
public class JobPostActivityController {

    private final UsersService usersService;
    private final JobPostActivityService jobPostActivityService;
    private final JobSeekerApplyService jobSeekerApplyService;
    private final JobSeekerSaveService jobSeekerSaveService;

    @Autowired
    public JobPostActivityController(UsersService usersService, JobPostActivityService jobPostActivityService, JobSeekerApplyService jobSeekerApplyService, JobSeekerSaveService jobSeekerSaveService) {
        this.usersService = usersService;
        this.jobPostActivityService = jobPostActivityService;
        this.jobSeekerApplyService = jobSeekerApplyService;
        this.jobSeekerSaveService = jobSeekerSaveService;
    }

    @GetMapping("/dashboard/")
    public String searchJobs(Model model,
                             @RequestParam(value = "job", required = false) String job,
                             @RequestParam(value = "location", required = false) String location,
                             @RequestParam(value = "partTime", required = false) String partTime,
                             @RequestParam(value = "fullTime", required = false) String fullTime,
                             @RequestParam(value = "freelance", required = false) String freelance,
                             @RequestParam(value = "remoteOnly", required = false) String remoteOnly,
                             @RequestParam(value = "officeOnly", required = false) String officeOnly,
                             @RequestParam(value = "partialRemote", required = false) String partialRemote,
                             @RequestParam(value = "today", required = false) boolean today,
                             @RequestParam(value = "days7", required = false) boolean days7,
                             @RequestParam(value = "days30", required = false) boolean days30

    ) {

        model.addAttribute("partTime", Objects.equals(partTime, "Part-Time"));
        model.addAttribute("fullTime", isFullTime(fullTime));
        model.addAttribute("freelance", Objects.equals(freelance, "Freelance"));

        model.addAttribute("remoteOnly", Objects.equals(remoteOnly, "Remote-Only"));
        model.addAttribute("officeOnly", Objects.equals(officeOnly, "Office-Only"));
        model.addAttribute("partialRemote", Objects.equals(partialRemote, "Partial-Remote"));

        model.addAttribute("today", today);
        model.addAttribute("days7", days7);
        model.addAttribute("days30", days30);

        model.addAttribute("job", job);
        model.addAttribute("location", location);

        LocalDate searchDate = null;
        List<JobPostActivity> jobPost = null;
        boolean dateSearchFlag = true;
        boolean typeFilter = true;
        boolean remoteFilter = true;

        if (days30) {
            searchDate = LocalDate.now().minusDays(30);
        } else if (days7) {
            searchDate = LocalDate.now().minusDays(7);
        } else if (today) {
            searchDate = LocalDate.now();
        } else {
            dateSearchFlag = false;
        }

        if (partTime == null && fullTime == null && freelance == null) {
            partTime = "Part-Time";
            fullTime = "Full-Time";
            freelance = "Freelance";
            typeFilter = false;
        }

        if (officeOnly == null && remoteOnly == null && partialRemote == null) {
            officeOnly = "Office-Only";
            remoteOnly = "Remote-Only";
            partialRemote = "Partial-Remote";
            remoteFilter = false;
        }

        if (!dateSearchFlag && !typeFilter && !remoteFilter && !StringUtils.hasText(job) && !StringUtils.hasText(location)) {
            jobPost = jobPostActivityService.getAll();
        } else {
            jobPost = jobPostActivityService.search(job, location, Arrays.asList(partTime, fullTime, freelance),
                    Arrays.asList(remoteOnly, officeOnly, partialRemote), searchDate, typeFilter, remoteFilter);
        }

        Object currentUserProfile = usersService.getCurrentUserProfile();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("jobPost", jobPost);

        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String currentUsername = authentication.getName();
            model.addAttribute("username", currentUsername);
            if (currentUserProfile instanceof RecruiterProfile recruiterProfile
                    && !StringUtils.hasText(job) && !StringUtils.hasText(location)) {
                List<RecruiterJobsDto> recruiterJobs = jobPostActivityService.getRecruiterJobs(recruiterProfile.getUserAccountId());
                model.addAttribute("jobPost", recruiterJobs);
            } else if (currentUserProfile instanceof JobSeekerProfile jobSeekerProfile) {
                List<JobSeekerApply> jobSeekerApplyList = jobSeekerApplyService.getCandidatesJobs(jobSeekerProfile);
                List<JobSeekerSave> jobSeekerSaveList = jobSeekerSaveService.getCandidatesJob(jobSeekerProfile);

                boolean exist;
                boolean saved;

                for (JobPostActivity jobActivity : jobPost) {
                    exist = false;
                    saved = false;
                    for (JobSeekerApply jobSeekerApply : jobSeekerApplyList) {
                        if (Objects.equals(jobActivity.getJobPostId(), jobSeekerApply.getJob().getJobPostId())) {
                            jobActivity.setIsActive(true);
                            exist = true;
                            break;
                        }
                    }

                    for (JobSeekerSave jobSeekerSave : jobSeekerSaveList) {
                        if (Objects.equals(jobActivity.getJobPostId(), jobSeekerSave.getJob().getJobPostId())) {
                            jobActivity.setIsSaved(true);
                            saved = true;
                            break;
                        }
                    }

                    if (!exist) {
                        jobActivity.setIsActive(false);
                    }
                    if (!saved) {
                        jobActivity.setIsSaved(false);
                    }

                }
            }
        }

        model.addAttribute("user", currentUserProfile);

        return "dashboard";
    }

    @GetMapping("global-search/")
    public String globalSearch(Model model,
                               @RequestParam(value = "job", required = false) String job,
                               @RequestParam(value = "location", required = false) String location,
                               @RequestParam(value = "partTime", required = false) String partTime,
                               @RequestParam(value = "fullTime", required = false) String fullTime,
                               @RequestParam(value = "freelance", required = false) String freelance,
                               @RequestParam(value = "remoteOnly", required = false) String remoteOnly,
                               @RequestParam(value = "officeOnly", required = false) String officeOnly,
                               @RequestParam(value = "partialRemote", required = false) String partialRemote,
                               @RequestParam(value = "today", required = false) boolean today,
                               @RequestParam(value = "days7", required = false) boolean days7,
                               @RequestParam(value = "days30", required = false) boolean days30) {

        model.addAttribute("partTime", Objects.equals(partTime, "Part-Time"));
        model.addAttribute("fullTime", isFullTime(fullTime));
        model.addAttribute("freelance", Objects.equals(freelance, "Freelance"));

        model.addAttribute("remoteOnly", Objects.equals(remoteOnly, "Remote-Only"));
        model.addAttribute("officeOnly", Objects.equals(officeOnly, "Office-Only"));
        model.addAttribute("partialRemote", Objects.equals(partialRemote, "Partial-Remote"));

        model.addAttribute("today", today);
        model.addAttribute("days7", days7);
        model.addAttribute("days30", days30);

        model.addAttribute("job", job);
        model.addAttribute("location", location);

        LocalDate searchDate = null;
        List<JobPostActivity> jobPost = null;
        boolean dateSearchFlag = true;
        boolean typeFilter = true;
        boolean remoteFilter = true;

        if (days30) {
            searchDate = LocalDate.now().minusDays(30);
        } else if (days7) {
            searchDate = LocalDate.now().minusDays(7);
        } else if (today) {
            searchDate = LocalDate.now();
        } else {
            dateSearchFlag = false;
        }

        if (partTime == null && fullTime == null && freelance == null) {
            partTime = "Part-Time";
            fullTime = "Full-Time";
            freelance = "Freelance";
            typeFilter = false;
        }

        if (officeOnly == null && remoteOnly == null && partialRemote == null) {
            officeOnly = "Office-Only";
            remoteOnly = "Remote-Only";
            partialRemote = "Partial-Remote";
            remoteFilter = false;
        }

        if (!dateSearchFlag && !typeFilter && !remoteFilter && !StringUtils.hasText(job) && !StringUtils.hasText(location)) {
            jobPost = jobPostActivityService.getAll();
        } else {
            jobPost = jobPostActivityService.search(job, location, Arrays.asList(partTime, fullTime, freelance),
                    Arrays.asList(remoteOnly, officeOnly, partialRemote), searchDate, typeFilter, remoteFilter);
        }

        model.addAttribute("jobPost", jobPost);
        return "global-search";
    }

    @GetMapping("/dashboard/add")
    public String addJobs(Model model) {
        model.addAttribute("jobPostActivity", new JobPostActivity());
        model.addAttribute("user", usersService.getCurrentUserProfile());
        return "add-jobs";
    }

    @PostMapping("/dashboard/addNew")
    public String addNew(JobPostActivity jobPostActivity, Model model) {

        Users user = usersService.getCurrentUser();
        if (user != null) {
            jobPostActivity.setPostedById(user);
        }
        jobPostActivity.setPostedDate(new Date());
        model.addAttribute("jobPostActivity", jobPostActivity);
        JobPostActivity saved = jobPostActivityService.addNew(jobPostActivity);
        return "redirect:/dashboard/";
    }

    @GetMapping("dashboard/edit/{id}")
    public String editJob(@PathVariable("id") int id, Model model) {

        JobPostActivity jobPostActivity = jobPostActivityService.getOne(id);
        model.addAttribute("jobPostActivity", jobPostActivity);
        model.addAttribute("user", usersService.getCurrentUserProfile());
        return "add-jobs";
    }

    private boolean isFullTime(String value) {
        return "Full-Time".equalsIgnoreCase(value) || "Full-time".equalsIgnoreCase(value);
    }
}
