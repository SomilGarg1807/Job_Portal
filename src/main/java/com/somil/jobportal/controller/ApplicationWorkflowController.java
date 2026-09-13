package com.somil.jobportal.controller;

import com.somil.jobportal.entity.*;
import com.somil.jobportal.repository.JobSeekerApplyRepository;
import com.somil.jobportal.services.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Controller
public class ApplicationWorkflowController {
    private static final List<String> EXPERIENCE_OPTIONS = List.of("0-1", "1-3", "3-5", "5-8", "8-12", "12+");
    private final UsersService users;
    private final JobSeekerApplyRepository applications;
    private final ApplicationWorkflowService workflow;
    private final JobPostActivityService jobs;
    public ApplicationWorkflowController(UsersService users, JobSeekerApplyRepository applications,
                                         ApplicationWorkflowService workflow, JobPostActivityService jobs) {
        this.users = users; this.applications = applications; this.workflow = workflow; this.jobs = jobs;
    }
    @GetMapping("/applications")
    public String applications(@RequestParam(defaultValue = "1") int page, Model model) {
        Object profile = users.getCurrentUserProfile();
        if (!(profile instanceof JobSeekerProfile seeker)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var results = applications.findByUserIdOrderByApplyDateDescIdDesc(seeker, PageRequest.of(Math.max(0, page - 1), 12));
        if (results.getTotalPages() > 0 && page > results.getTotalPages())
            return "redirect:/applications?page=" + results.getTotalPages();
        model.addAttribute("results", results); model.addAttribute("user", profile); model.addAttribute("recruiter", false);
        return "applications";
    }
    @GetMapping("/recruiter/candidates")
    public String board(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "0") int jobId,
                        @RequestParam(required = false) ApplicationStatus status,
                        @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String experience, Model model) {
        Object profile = users.getCurrentUserProfile();
        if (!(profile instanceof RecruiterProfile recruiter)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        keyword = keyword.trim();
        if (keyword.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var ranges = Map.of("0-1", new int[]{0,1}, "1-3", new int[]{1,3}, "3-5", new int[]{3,5},
                "5-8", new int[]{5,8}, "8-12", new int[]{8,12}, "12+", new int[]{12,100});
        if (!ranges.containsKey(experience)) experience = "";
        int[] range = ranges.get(experience);
        BigDecimal minimum = range == null ? null : BigDecimal.valueOf(range[0]);
        BigDecimal maximum = range == null ? null : BigDecimal.valueOf(range[1]);
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        int index = Math.max(0, page - 1);
        var results = applications.searchBoard(recruiter.getUserAccountId(), jobId, status, minimum, maximum, keyword, pattern, PageRequest.of(index, 24));
        if (index >= results.getTotalPages() && results.getTotalPages() > 0)
            results = applications.searchBoard(recruiter.getUserAccountId(), jobId, status, minimum, maximum, keyword, pattern, PageRequest.of(results.getTotalPages()-1, 24));
        Map<ApplicationStatus, List<JobSeekerApply>> columns = new LinkedHashMap<>();
        for (var stage : ApplicationStatus.values()) columns.put(stage, results.getContent().stream().filter(a -> a.getStatus() == stage).toList());
        model.addAttribute("results", results); model.addAttribute("columns", columns);
        model.addAttribute("jobs", jobs.getRecruiterJobs(recruiter.getUserAccountId()));
        model.addAttribute("jobId", jobId); model.addAttribute("selectedStatus", status); model.addAttribute("keyword", keyword);
        model.addAttribute("experience", experience); model.addAttribute("experienceOptions", EXPERIENCE_OPTIONS);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("user", profile); model.addAttribute("recruiter", true);
        return "candidate-board";
    }
    @GetMapping("/applications/{id}")
    public String detail(@PathVariable int id, Model model) {
        Object profile = users.getCurrentUserProfile();
        var application = workflow.accessible(id, profile);
        model.addAttribute("jobApplication", application); model.addAttribute("timeline", workflow.timeline(application));
        model.addAttribute("user", profile); model.addAttribute("recruiter", workflow.owns(application, profile));
        model.addAttribute("statuses", ApplicationStatus.values());
        return "application-detail";
    }
    @PostMapping("/applications/{id}/update")
    public String update(@PathVariable int id, @RequestParam ApplicationStatus status,
                         @RequestParam(defaultValue = "") String notes, @RequestParam long revision, RedirectAttributes redirect) {
        try {
            workflow.update(id, users.getCurrentUserProfile(), status, notes, revision);
            redirect.addFlashAttribute("message", "Application updated. The candidate can see the status; your notes stay private.");
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.CONFLICT) throw ex;
            redirect.addFlashAttribute("error", "Someone updated this application. Review the latest status and notes before saving again.");
        }
        return "redirect:/applications/" + id;
    }
}
