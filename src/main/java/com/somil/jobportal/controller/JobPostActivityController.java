package com.somil.jobportal.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.somil.jobportal.entity.JobCompany;
import com.somil.jobportal.entity.JobLocation;
import com.somil.jobportal.util.JobContent;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
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
                             @RequestParam(value = "days30", required = false) boolean days30,
                             @RequestParam(defaultValue = "all") String view,
                             @RequestParam(defaultValue = "relevance") String sort,
                             @RequestParam(defaultValue = "1") int page

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
        if (!List.of("relevance", "newest", "title", "applicants").contains(sort)) sort = "relevance";
        if (currentUserProfile instanceof RecruiterProfile && "relevance".equals(sort)) sort = "newest";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("jobPost", jobPost);

        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String currentUsername = authentication.getName();
            model.addAttribute("username", currentUsername);
            if (currentUserProfile instanceof RecruiterProfile recruiterProfile) {
                List<RecruiterJobsDto> recruiterJobs = jobPostActivityService.getRecruiterJobs(recruiterProfile.getUserAccountId());
                model.addAttribute("totalJobs", recruiterJobs.size());
                model.addAttribute("applicationCount", recruiterJobs.stream().mapToLong(j -> j.getTotalCandidates() == null ? 0 : j.getTotalCandidates()).sum());
                model.addAttribute("withApplicants", recruiterJobs.stream().filter(j -> j.getTotalCandidates() != null && j.getTotalCandidates() > 0).count());
                Set<Integer> matchingIds = jobPost.stream().map(JobPostActivity::getJobPostId).collect(Collectors.toSet());
                Comparator<RecruiterJobsDto> order = "title".equals(sort)
                        ? Comparator.comparing(j -> Objects.toString(j.getJobTitle(), ""), String.CASE_INSENSITIVE_ORDER)
                        : "applicants".equals(sort)
                        ? Comparator.comparing((RecruiterJobsDto j) -> Objects.requireNonNullElse(j.getTotalCandidates(), 0L)).reversed()
                        : Comparator.comparing(RecruiterJobsDto::getJobPostId).reversed();
                model.addAttribute("jobPost", recruiterJobs.stream().filter(j -> matchingIds.contains(j.getJobPostId())).sorted(order).toList());
                model.addAttribute("profileCompletion", completion(recruiterProfile.getFirstName(), recruiterProfile.getLastName(), recruiterProfile.getCompany(), recruiterProfile.getDesignation(), recruiterProfile.getCompanyWebsite(), recruiterProfile.getCompanyDescription()));
            } else if (currentUserProfile instanceof JobSeekerProfile jobSeekerProfile) {
                List<JobSeekerApply> jobSeekerApplyList = jobSeekerApplyService.getCandidatesJobs(jobSeekerProfile);
                List<JobSeekerSave> jobSeekerSaveList = jobSeekerSaveService.getCandidatesJob(jobSeekerProfile);
                model.addAttribute("totalJobs", jobPost.size());
                model.addAttribute("applicationCount", jobSeekerApplyList.size());
                model.addAttribute("savedCount", jobSeekerSaveList.size());
                model.addAttribute("profileCompletion", completion(jobSeekerProfile.getFirstName(), jobSeekerProfile.getLastName(), jobSeekerProfile.getCity(), jobSeekerProfile.getDesiredJobTitle(), jobSeekerProfile.getProfessionalHeadline(), jobSeekerProfile.getResume()));

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
                Comparator<JobPostActivity> order = "title".equals(sort)
                        ? Comparator.comparing(j -> Objects.toString(j.getJobTitle(), ""), String.CASE_INSENSITIVE_ORDER)
                        : Comparator.comparing(JobPostActivity::getPostedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(JobPostActivity::getJobPostId, Comparator.reverseOrder());
                if ("relevance".equals(sort)) {
                    java.util.Map<Integer, Integer> scores = jobPost.stream().collect(Collectors.toMap(
                            JobPostActivity::getJobPostId, j -> com.somil.jobportal.util.JobRelevance.score(jobSeekerProfile, j)));
                    order = Comparator.comparingInt((JobPostActivity j) -> scores.get(j.getJobPostId())).reversed().thenComparing(order);
                    model.addAttribute("rankingMessage", scores.values().stream().anyMatch(score -> score > 0)
                            ? "Best matches for your target role and skills appear first."
                            : "Showing the latest jobs. Add your target role and skills for better matches.");
                }
                model.addAttribute("jobPost", jobPost.stream()
                        .filter(j -> !"applied".equals(view) || Boolean.TRUE.equals(j.getIsActive()))
                        .filter(j -> !"saved".equals(view) || Boolean.TRUE.equals(j.getIsSaved()))
                        .sorted(order).toList());
            }
        }

        List<?> results = (List<?>) model.getAttribute("jobPost");
        int resultCount = results.size();
        int pages = Math.max(1, (resultCount + 11) / 12);
        int currentPage = Math.min(Math.max(1, page), pages);
        int start = (currentPage - 1) * 12;
        model.addAttribute("jobPost", results.subList(start, Math.min(start + 12, resultCount)));
        model.addAttribute("resultCount", resultCount);
        model.addAttribute("page", currentPage);
        model.addAttribute("totalPages", pages);
        model.addAttribute("rangeStart", resultCount == 0 ? 0 : start + 1);
        model.addAttribute("rangeEnd", Math.min(start + 12, resultCount));
        model.addAttribute("user", currentUserProfile);
        model.addAttribute("view", view);
        model.addAttribute("sort", sort);
        model.addAttribute("recruiter", currentUserProfile instanceof RecruiterProfile);

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

    @InitBinder("jobPostActivity")
    public void bindJobFields(WebDataBinder binder) {
        binder.setAllowedFields("jobPostId", "jobTitle", "jobType", "remote", "salary", "descriptionOfJob",
                "jobCompanyId.name", "jobLocationId.city", "jobLocationId.state", "jobLocationId.country");
    }

    @GetMapping("/dashboard/add")
    public String addJobs(Model model) {
        RecruiterProfile profile = requireRecruiter();
        JobPostActivity job = new JobPostActivity();
        job.setJobCompanyId(new JobCompany());
        job.getJobCompanyId().setName(profile.getCompany());
        job.setJobLocationId(new JobLocation());
        return editor(model, job, profile);
    }

    @PostMapping("/dashboard/addNew")
    public String addNew(@ModelAttribute("jobPostActivity") JobPostActivity submitted,
                         BindingResult binding, Model model) {
        RecruiterProfile profile = requireRecruiter();
        JobPostActivity existing = submitted.getJobPostId() == null ? null : jobPostActivityService.getOne(submitted.getJobPostId());
        if (existing != null) requireOwner(existing, profile);
        String error = validateJob(submitted);
        if (binding.hasErrors() || error != null) {
            model.addAttribute("formError", error == null ? "Please check the job details and try again." : error);
            return editor(model, submitted, profile);
        }
        // Preserve server-owned relationships, author and posting date on edits.
        JobPostActivity job = existing == null ? new JobPostActivity() : existing;
        if (existing == null) {
            job.setPostedById(usersService.getCurrentUser());
            job.setPostedDate(new Date());
            job.setJobCompanyId(new JobCompany());
            job.setJobLocationId(new JobLocation());
        }
        job.setJobTitle(submitted.getJobTitle().trim());
        job.setJobType(submitted.getJobType());
        job.setRemote(submitted.getRemote());
        job.setSalary(Objects.toString(submitted.getSalary(), "").trim());
        job.setDescriptionOfJob(JobContent.safeHtml(submitted.getDescriptionOfJob()));
        // Copy locations/companies into new records when edited so shared demo records are not overwritten.
        job.setJobCompanyId(new JobCompany(null, submitted.getJobCompanyId().getName().trim(), ""));
        job.setJobLocationId(new JobLocation(null, submitted.getJobLocationId().getCity().trim(),
                submitted.getJobLocationId().getState().trim(), submitted.getJobLocationId().getCountry().trim()));
        JobPostActivity saved = jobPostActivityService.addNew(job);
        return "redirect:/job-details-apply/" + saved.getJobPostId();
    }

    @GetMapping("dashboard/edit/{id}")
    public String editJob(@PathVariable("id") int id, Model model) {
        RecruiterProfile profile = requireRecruiter();
        JobPostActivity job = jobPostActivityService.getOne(id);
        requireOwner(job, profile);
        job.setDescriptionOfJob(JobContent.safeHtml(job.getDescriptionOfJob()));
        if ("Full-Time".equalsIgnoreCase(job.getJobType())) job.setJobType("Full-time");
        if ("Part-Time".equalsIgnoreCase(job.getJobType())) job.setJobType("Part-time");
        return editor(model, job, profile);
    }

    private String editor(Model model, JobPostActivity job, RecruiterProfile profile) {
        if (job.getJobCompanyId() == null) job.setJobCompanyId(new JobCompany());
        if (job.getJobLocationId() == null) job.setJobLocationId(new JobLocation());
        model.addAttribute("jobPostActivity", job);
        model.addAttribute("user", profile);
        model.addAttribute("recruiter", true);
        model.addAttribute("aiContext", "");
        return "add-jobs";
    }

    private RecruiterProfile requireRecruiter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "Recruiter".equals(a.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        Object profile = usersService.getCurrentUserProfile();
        if (!(profile instanceof RecruiterProfile recruiter)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return recruiter;
    }

    private void requireOwner(JobPostActivity job, RecruiterProfile profile) {
        if (job.getPostedById() == null || job.getPostedById().getUserId() != profile.getUserAccountId())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the posting recruiter can edit this job.");
    }

    private String validateJob(JobPostActivity job) {
        if (!validText(job.getJobTitle(), 160) || job.getJobCompanyId() == null || !validText(job.getJobCompanyId().getName(), 160))
            return "Enter a job title and company name (up to 160 characters each).";
        if (!List.of("Full-time", "Part-time", "Freelance", "Internship").contains(Objects.toString(job.getJobType(), ""))
                || !List.of("Remote-Only", "Office-Only", "Partial-Remote").contains(Objects.toString(job.getRemote(), "")))
            return "Choose an employment type and workplace arrangement.";
        JobLocation location = job.getJobLocationId();
        if (location == null || !validText(location.getCity(), 100) || !validText(location.getState(), 100) || !validText(location.getCountry(), 100))
            return "Enter a city, state or region, and country (up to 100 characters each).";
        if (Objects.toString(job.getSalary(), "").length() > 120) return "Keep the salary range within 120 characters.";
        if (job.getDescriptionOfJob() == null || job.getDescriptionOfJob().length() > 10000
                || JobContent.plainText(JobContent.safeHtml(job.getDescriptionOfJob())).length() < 30)
            return "Write a description with at least 30 characters of text, within the 10,000-character limit.";
        return null;
    }

    private boolean validText(String value, int max) {
        return StringUtils.hasText(value) && value.length() <= max;
    }

    private boolean isFullTime(String value) {
        return "Full-Time".equalsIgnoreCase(value) || "Full-time".equalsIgnoreCase(value);
    }

    private int completion(String... fields) {
        return (int) (Arrays.stream(fields).filter(StringUtils::hasText).count() * 100 / fields.length);
    }
}
