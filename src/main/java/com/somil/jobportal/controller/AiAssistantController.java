package com.somil.jobportal.controller;

import com.somil.jobportal.services.AiAssistantService;
import com.somil.jobportal.services.AiAssistantService.AssistantException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {
    private final AiAssistantService assistant;

    public AiAssistantController(AiAssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping(value = "/assist", consumes = "application/json", produces = "application/json")
    public Map<String, String> assist(@Valid @RequestBody AssistantRequest request,
                                      @RequestHeader(value = "X-Requested-With", defaultValue = "") String requestedWith,
                                      Authentication authentication, HttpSession session) {
        // Require a non-simple request header; cross-origin forms cannot trigger provider usage.
        if (!"HotDevJobs".equals(requestedWith)) {
            throw new AssistantException(403, "Please use the assistant form on your dashboard.");
        }
        boolean recruiter = hasRole(authentication, "Recruiter");
        if (!recruiter && !hasRole(authentication, "Job Seeker")) {
            throw new AssistantException(403, "Sign in with a recruiter or job seeker account to use the assistant.");
        }
        if (!assistant.isConfigured()) {
            throw new AssistantException(503, "AI is not connected yet. The site owner needs to configure GEMINI_API_KEY on the server.");
        }
        // Bound provider usage per session, without an unbounded in-memory user map.
        synchronized (session) {
            long now = System.currentTimeMillis();
            Long lastRequest = (Long) session.getAttribute("aiLastRequest");
            if (lastRequest != null && now - lastRequest < 30_000) {
                throw new AssistantException(429, "Please wait 30 seconds between AI requests.");
            }
            session.setAttribute("aiLastRequest", now);
        }
        return Map.of("text", assistant.assist(request.context().trim(), recruiter));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<Map<String, String>> assistantError(AssistantException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validationError() {
        return ResponseEntity.badRequest().body(Map.of("message", "Enter 10–3,000 characters and agree to send this text to Gemini."));
    }

    public record AssistantRequest(@NotBlank @Size(min = 10, max = 3000) String context,
                                   @AssertTrue boolean consent) { }
}
