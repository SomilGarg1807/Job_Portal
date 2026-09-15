package com.somil.jobportal.controller;

import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.services.*;
import com.somil.jobportal.services.AiAssistantService.AssistantException;
import com.somil.jobportal.util.JobContent;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@Controller
@RequestMapping("/resume-comparison")
public class ResumeComparisonController {
    private final UsersService users;
    private final JobPostActivityService jobs;
    private final CandidateAccessService files;
    private final ResumeTextService resumes;
    private final AiAssistantService ai;
    private final AtsScoreService ats;
    public ResumeComparisonController(UsersService users, JobPostActivityService jobs, CandidateAccessService files,
                                      ResumeTextService resumes, AiAssistantService ai, AtsScoreService ats) {
        this.users = users; this.jobs = jobs; this.files = files; this.resumes = resumes; this.ai = ai; this.ats = ats;
    }
    @GetMapping("/{id}/ats-score") @ResponseBody
    public ResponseEntity<AtsScoreService.AtsReport> atsScore(@PathVariable int id) {
        var profile = seeker();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ats.score(profile, jobs.getOne(id)));
    }
    private JobSeekerProfile seeker() {
        if (!(users.getCurrentUserProfile() instanceof JobSeekerProfile profile))
            throw new AssistantException(403, "Sign in as a job seeker to compare your own resume.");
        return profile;
    }
    @GetMapping("/{id}")
    public String page(@PathVariable int id, Model model, HttpServletResponse response) {
        var profile = seeker();
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("job", jobs.getOne(id)); model.addAttribute("user", profile); model.addAttribute("recruiter", false);
        String text = "";
        String hint = "Paste relevant experience, projects and skills from your resume below.";
        if (profile.getResume() != null && profile.getResume().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try {
                text = resumes.extract(files.file(profile, profile.getResume()).getData());
                hint = text.isBlank() ? "This PDF has no readable text. Paste your resume text below; scanned PDFs are not supported."
                        : "Text loaded from your profile resume. Check the extraction and remove contact details before continuing.";
            } catch (java.io.IOException | RuntimeException ex) {
                hint = "We could not read this resume. Use a text PDF up to 3 MB and 10 pages, or paste the relevant text below.";
            }
        }
        model.addAttribute("resumeText", text); model.addAttribute("resumeHint", hint);
        return "resume-comparison";
    }
    @PostMapping("/{id}") @ResponseBody
    public Map<String, String> compare(@PathVariable int id, @RequestParam String resume,
                                      @RequestParam(defaultValue = "false") boolean consent,
                                      HttpSession session, HttpServletResponse response) {
        seeker(); response.setHeader("Cache-Control", "no-store");
        if (!consent || resume.trim().length() < 50 || resume.length() > 12000)
            throw new AssistantException(400, "Enter 50-12,000 characters and agree to send the reviewed text to Gemini.");
        var job = jobs.getOne(id);
        if (!ai.isConfigured()) throw new AssistantException(503, "AI is not connected yet. The site owner needs to configure GEMINI_API_KEY.");
        String requirements = "Role: " + job.getJobTitle() + "\nExperience: " + job.getExperienceLabel()
                + "\n" + JobContent.plainText(Objects.toString(job.getDescriptionOfJob(), ""));
        requirements = requirements.substring(0, Math.min(requirements.length(), 16000));
        synchronized (session) {
            long now = System.currentTimeMillis();
            Long last = (Long) session.getAttribute("aiLastRequest");
            if (last != null && now - last < 30000) throw new AssistantException(429, "Please wait 30 seconds between AI requests.");
            session.setAttribute("aiLastRequest", now);
        }
        return Map.of("text", ai.compareResume(resume.trim(), requirements));
    }
    @ExceptionHandler(AssistantException.class) @ResponseBody
    public ResponseEntity<Map<String,String>> error(AssistantException ex) {
        return ResponseEntity.status(ex.getStatus()).header("Cache-Control", "no-store").body(Map.of("message", ex.getMessage()));
    }
}
