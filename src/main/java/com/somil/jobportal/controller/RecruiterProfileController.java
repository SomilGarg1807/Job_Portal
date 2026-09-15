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
import com.somil.jobportal.services.FileStorageService;

@Controller
@RequestMapping("/recruiter-profile")
public class RecruiterProfileController {

    private final UsersRepository usersRepository;
    private final RecruiterProfileService recruiterProfileService;
    private final FileStorageService fileStorage;

    public RecruiterProfileController(UsersRepository usersRepository, RecruiterProfileService recruiterProfileService,
                                      FileStorageService fileStorage) {
        this.usersRepository = usersRepository;
        this.recruiterProfileService = recruiterProfileService;
        this.fileStorage = fileStorage;
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

        if (recruiterProfile.getCompany() != null) recruiterProfile.setCompany(recruiterProfile.getCompany().trim());
        if (recruiterProfile.getCompany() != null && recruiterProfile.getCompany().length() > 160) {
            model.addAttribute("error", "Company name must be 160 characters or fewer.");
            return "recruiter_profile";
        }

        if (!hasRequiredProfileFields(recruiterProfile)) {
            model.addAttribute("error", "Your name, company name, and headquarters location are required before you can continue.");
            model.addAttribute("onboarding", true);
            return "recruiter_profile";
        }

        if (recruiterProfile.getEmployeeCount() != null && recruiterProfile.getEmployeeCount() < 1) {
            model.addAttribute("error", "Employee count must be at least 1.");
            return "recruiter_profile";
        }

        FileStorageService.Upload photo = null;
        if (multipartFile != null && !multipartFile.isEmpty()) {
            try {
                photo = fileStorage.preparePhoto(multipartFile);
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
                return "recruiter_profile";
            }
            recruiterProfile.setProfilePhoto(photo.name());
        }
        RecruiterProfile savedUser = recruiterProfileService.addNew(recruiterProfile);
        if (photo != null) fileStorage.store(FileStorageService.RECRUITER_PHOTO, savedUser.getUserAccountId(), photo);

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
