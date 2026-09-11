package com.somil.jobportal.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.somil.jobportal.entity.Users;
import com.somil.jobportal.entity.UsersType;
import com.somil.jobportal.services.UsersTypeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestParam;
import com.somil.jobportal.services.EmailVerificationService;

@Controller
public class UsersController {

    private final UsersTypeService usersTypeService;
    private final EmailVerificationService verification;

    @Autowired
    public UsersController(UsersTypeService usersTypeService, EmailVerificationService verification) {
        this.usersTypeService = usersTypeService;
        this.verification = verification;
    }

    @InitBinder
    public void registrationFields(WebDataBinder binder) {
        binder.setAllowedFields("email", "password", "userTypeId", "userTypeId.userTypeId");
    }

    @GetMapping("/register")
    public String register(Model model) {
        List<UsersType> usersTypes = usersTypeService.getAll();
        model.addAttribute("getAllTypes", usersTypes);
        model.addAttribute("user", new Users());
        return "register";
    }

    @PostMapping("/register/new")
    public String userRegistration(@Valid Users users, BindingResult binding, Model model, HttpSession session) {
        try {
            if (binding.hasErrors()) throw new IllegalArgumentException("Enter a valid email, password, and account type.");
            String token = verification.start(users);
            session.setAttribute("emailVerificationToken", token);
            return "redirect:/register/verify";
        } catch (IllegalArgumentException | IllegalStateException error) {
            model.addAttribute("error", error.getMessage());
            model.addAttribute("getAllTypes", usersTypeService.getAll());
            model.addAttribute("user", new Users());
            return "register";
        }
    }

    @GetMapping("/register/verify")
    public String verificationPage(HttpSession session) {
        return session.getAttribute("emailVerificationToken") == null ? "redirect:/register" : "verify-email";
    }

    @PostMapping("/register/verify")
    public String verify(@RequestParam(defaultValue = "") String code, HttpSession session, Model model) {
        try {
            verification.verify((String) session.getAttribute("emailVerificationToken"), code.trim());
            session.removeAttribute("emailVerificationToken");
            model.addAttribute("verified", true);
        } catch (EmailVerificationService.InvalidCode error) {
            model.addAttribute("error", error.getMessage());
        }
        return "verify-email";
    }

    @PostMapping("/register/resend")
    public String resend(HttpSession session, Model model) {
        try {
            verification.resend((String) session.getAttribute("emailVerificationToken"));
            model.addAttribute("message", "A new code has been sent. Use the latest email and check your spam folder.");
        } catch (EmailVerificationService.InvalidCode | IllegalArgumentException | IllegalStateException error) {
            model.addAttribute("error", error.getMessage());
        }
        return "verify-email";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        return "redirect:/";
    }
}
