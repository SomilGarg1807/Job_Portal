package com.somil.jobportal.controller;

import java.util.Objects;
import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.somil.jobportal.entity.RecruiterProfile;
import com.somil.jobportal.entity.Users;
import com.somil.jobportal.repository.UsersRepository;
import com.somil.jobportal.services.RecruiterProfileService;
import com.somil.jobportal.util.FileUploadUtil;

@Controller
@RequestMapping("/recruiter-profile")
public class RecruiterProfileController {

    private final UsersRepository usersRepository;
    private final RecruiterProfileService recruiterProfileService;

    public RecruiterProfileController(UsersRepository usersRepository, RecruiterProfileService recruiterProfileService) {
        this.usersRepository = usersRepository;
        this.recruiterProfileService = recruiterProfileService;
    }

    @GetMapping("/")
    public String recruiterProfile(Model model,
                                   @RequestParam(value = "onboarding", required = false, defaultValue = "false") boolean onboarding) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String currentUsername = authentication.getName();
            Users users = usersRepository.findByEmail(currentUsername).orElseThrow(() -> new UsernameNotFoundException("Could not " + "found user"));
            Optional<RecruiterProfile> recruiterProfile = recruiterProfileService.getOne(users.getUserId());

            model.addAttribute("profile", recruiterProfile.orElseGet(() -> new RecruiterProfile(users)));

        }

        model.addAttribute("onboarding", onboarding);

        return "recruiter_profile";
    }

    @PostMapping("/addNew")
    public String addNew(RecruiterProfile recruiterProfile, @RequestParam("image") MultipartFile multipartFile, Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            String currentUsername = authentication.getName();
            Users users = usersRepository.findByEmail(currentUsername).orElseThrow(() -> new UsernameNotFoundException("Could not " + "found user"));
            recruiterProfile.setUserId(users);
            recruiterProfile.setUserAccountId(users.getUserId());
        }
        model.addAttribute("profile", recruiterProfile);

        if (!hasRequiredProfileFields(recruiterProfile)) {
            model.addAttribute("error", "Your name, company name, and headquarters location are required before you can continue.");
            model.addAttribute("onboarding", true);
            return "recruiter_profile";
        }

        if (recruiterProfile.getEmployeeCount() != null && recruiterProfile.getEmployeeCount() < 1) {
            model.addAttribute("error", "Employee count must be at least 1.");
            return "recruiter_profile";
        }

        String fileName = "";
        if (!multipartFile.isEmpty()) {
            fileName = StringUtils.cleanPath(Objects.requireNonNull(multipartFile.getOriginalFilename()));
            recruiterProfile.setProfilePhoto(fileName);
        }
        RecruiterProfile savedUser = recruiterProfileService.addNew(recruiterProfile);

        String uploadDir = "photos/recruiter/" + savedUser.getUserAccountId();
        if (!multipartFile.isEmpty()) {
            try {
                FileUploadUtil.saveFile(uploadDir, fileName, multipartFile);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return "redirect:/dashboard/";
    }

    private boolean hasRequiredProfileFields(RecruiterProfile profile) {
        return StringUtils.hasText(profile.getFirstName())
                && StringUtils.hasText(profile.getLastName())
                && StringUtils.hasText(profile.getCompany())
                && StringUtils.hasText(profile.getCountry())
                && StringUtils.hasText(profile.getState())
                && StringUtils.hasText(profile.getCity());
    }
}
