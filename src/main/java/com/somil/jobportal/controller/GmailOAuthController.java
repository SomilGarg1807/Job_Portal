package com.somil.jobportal.controller;

import com.somil.jobportal.services.GmailOAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GmailOAuthController {
    private static final String STATE_SESSION_KEY = "gmailOAuthState";
    private final GmailOAuthService gmailOAuth;

    public GmailOAuthController(GmailOAuthService gmailOAuth) {
        this.gmailOAuth = gmailOAuth;
    }

    @GetMapping("/oauth2/gmail/start")
    public String start(HttpSession session) {
        String state = gmailOAuth.newState();
        session.setAttribute(STATE_SESSION_KEY, state);
        return "redirect:" + gmailOAuth.authorizationUrl(state);
    }

    @GetMapping("/oauth2/callback/gmail")
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           HttpSession session,
                           Model model) {
        Object expected = session.getAttribute(STATE_SESSION_KEY);
        session.removeAttribute(STATE_SESSION_KEY);
        if (!(expected instanceof String value) || !value.equals(state)) {
            model.addAttribute("error", "OAuth state did not match. Start the Gmail connection again.");
            return "gmail-oauth-result";
        }
        try {
            model.addAttribute("refreshToken", gmailOAuth.exchange(code));
        } catch (IllegalArgumentException | IllegalStateException error) {
            model.addAttribute("error", error.getMessage());
        }
        return "gmail-oauth-result";
    }
}
