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
import com.somil.jobportal.services.PasswordResetService;

@Controller
public class UsersController {

    private static final String RESET_TOKEN = "passwordResetToken";
    private static final String RESET_VERIFIED = "passwordResetVerified";

    private final UsersTypeService usersTypeService;
    private final EmailVerificationService verification;
    private final PasswordResetService passwordReset;

    @Autowired
    public UsersController(UsersTypeService usersTypeService, EmailVerificationService verification,
                           PasswordResetService passwordReset) {
        this.usersTypeService = usersTypeService;
        this.verification = verification;
        this.passwordReset = passwordReset;
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

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam(defaultValue = "") String email, HttpSession session, Model model) {
        try {
            String token = passwordReset.start(email);
            session.removeAttribute(RESET_VERIFIED);
            // Use a non-DB token when the email is unknown so the flow looks identical.
            session.setAttribute(RESET_TOKEN, token.isBlank() ? java.util.UUID.randomUUID().toString() : token);
            return "redirect:/forgot-password/verify";
        } catch (IllegalArgumentException | IllegalStateException error) {
            model.addAttribute("error", error.getMessage());
            return "forgot-password";
        }
    }

    @GetMapping("/forgot-password/verify")
    public String forgotPasswordVerifyPage(HttpSession session) {
        return session.getAttribute(RESET_TOKEN) == null ? "redirect:/forgot-password" : "forgot-password-verify";
    }

    @PostMapping("/forgot-password/verify")
    public String forgotPasswordVerify(@RequestParam(defaultValue = "") String code, HttpSession session, Model model) {
        try {
            passwordReset.verify((String) session.getAttribute(RESET_TOKEN), code.trim());
            session.setAttribute(RESET_VERIFIED, Boolean.TRUE);
            return "redirect:/forgot-password/new";
        } catch (PasswordResetService.InvalidCode error) {
            model.addAttribute("error", error.getMessage());
            return "forgot-password-verify";
        }
    }

    @PostMapping("/forgot-password/resend")
    public String forgotPasswordResend(HttpSession session, Model model) {
        try {
            passwordReset.resend((String) session.getAttribute(RESET_TOKEN));
            session.removeAttribute(RESET_VERIFIED);
            model.addAttribute("message", "A new code has been sent. Use the latest email and check your spam folder.");
        } catch (PasswordResetService.InvalidCode | IllegalArgumentException | IllegalStateException error) {
            model.addAttribute("error", error.getMessage());
        }
        return "forgot-password-verify";
    }

    @GetMapping("/forgot-password/new")
    public String forgotPasswordNewPage(HttpSession session) {
        if (session.getAttribute(RESET_TOKEN) == null || !Boolean.TRUE.equals(session.getAttribute(RESET_VERIFIED)))
            return "redirect:/forgot-password";
        return "forgot-password-new";
    }

    @PostMapping("/forgot-password/new")
    public String forgotPasswordNew(@RequestParam(defaultValue = "") String password,
                                    @RequestParam(defaultValue = "") String confirmPassword,
                                    HttpSession session, Model model) {
        if (session.getAttribute(RESET_TOKEN) == null || !Boolean.TRUE.equals(session.getAttribute(RESET_VERIFIED)))
            return "redirect:/forgot-password";
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "forgot-password-new";
        }
        try {
            passwordReset.resetPassword((String) session.getAttribute(RESET_TOKEN), password);
            session.removeAttribute(RESET_TOKEN);
            session.removeAttribute(RESET_VERIFIED);
            return "redirect:/login?reset=1";
        } catch (PasswordResetService.InvalidCode | IllegalArgumentException | IllegalStateException error) {
            model.addAttribute("error", error.getMessage());
            return "forgot-password-new";
        }
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
