package com.somil.jobportal.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class VerificationMailService {
    private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);
    private static final String DELIVERY_ERROR = "We could not send your email code right now. Please try again later.";
    private final String resendApiKey;
    private final String resendSender;
    private final String gmailClientId;
    private final String gmailClientSecret;
    private final String gmailRefreshToken;
    private final String gmailSender;
    private final RestClient client;

    @Autowired
    public VerificationMailService(@Value("${RESEND_API_KEY:}") String apiKey,
                                   @Value("${EMAIL_FROM:}") String sender,
                                   @Value("${GMAIL_CLIENT_ID:}") String gmailClientId,
                                   @Value("${GMAIL_CLIENT_SECRET:}") String gmailClientSecret,
                                   @Value("${GMAIL_REFRESH_TOKEN:}") String gmailRefreshToken,
                                   @Value("${GMAIL_FROM:hotdevjobs.co@gmail.com}") String gmailSender) {
        this(apiKey, sender, gmailClientId, gmailClientSecret, gmailRefreshToken, gmailSender, RestClient.builder());
    }

    VerificationMailService(String apiKey, String sender, String gmailClientId, String gmailClientSecret,
                            String gmailRefreshToken, String gmailSender, RestClient.Builder builder) {
        this(apiKey, sender, gmailClientId, gmailClientSecret, gmailRefreshToken, gmailSender, timeoutClient(builder));
    }

    VerificationMailService(String apiKey, String sender, String gmailClientId, String gmailClientSecret,
                            String gmailRefreshToken, String gmailSender, RestClient client) {
        this.resendApiKey = apiKey == null ? "" : apiKey.trim();
        this.resendSender = sender == null ? "" : sender.trim();
        this.gmailClientId = gmailClientId == null ? "" : gmailClientId.trim();
        this.gmailClientSecret = gmailClientSecret == null ? "" : gmailClientSecret.trim();
        this.gmailRefreshToken = gmailRefreshToken == null ? "" : gmailRefreshToken.trim();
        this.gmailSender = gmailSender == null ? "" : gmailSender.trim();
        this.client = client;
    }

    private static RestClient timeoutClient(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return builder.requestFactory(factory).build();
    }

    public void send(String email, String code, String deliveryId) {
        deliver(email, deliveryId,
                "Verify your HotDevJobs email",
                "Your HotDevJobs verification code is: " + code
                        + "\r\n\r\nThis code expires in 10 minutes. Do not share it."
                        + "\r\nIf you did not request an account, you can ignore this email.");
    }

    public void sendPasswordReset(String email, String code, String deliveryId) {
        deliver(email, deliveryId,
                "Reset your HotDevJobs password",
                "Your HotDevJobs password reset code is: " + code
                        + "\r\n\r\nThis code expires in 10 minutes. Do not share it."
                        + "\r\nIf you did not request a password reset, you can ignore this email.");
    }

    private void deliver(String email, String deliveryId, String subject, String body) {
        if (gmailConfigured()) {
            try {
                sendWithGmail(email, subject, body);
                return;
            } catch (MailDeliveryFailure failure) {
                if (!resendConfigured()) throw failure;
                // Reuse the same code if Gmail accepted the message before a timeout.
                log.warn("Email delivery: Gmail failed; trying configured Resend fallback.");
            }
        }
        if (!resendConfigured()) {
            log.warn("Email delivery unavailable: configure Gmail credentials or RESEND_API_KEY and EMAIL_FROM.");
            throw new IllegalStateException("Email verification is temporarily unavailable. Please try again later.");
        }
        try {
            client.post().uri("https://api.resend.com/emails").header("Authorization", "Bearer " + resendApiKey)
                    .header("Idempotency-Key", deliveryId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", resendSender, "to", List.of(email), "subject", subject, "text", body))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException failure) {
            throw deliveryFailure("resend-send", failure);
        }
    }

    private boolean resendConfigured() {
        return !resendApiKey.isBlank() && !resendSender.isBlank();
    }

    private boolean gmailConfigured() {
        return !gmailClientId.isBlank() && !gmailClientSecret.isBlank()
                && !gmailRefreshToken.isBlank() && !gmailSender.isBlank();
    }

    private void sendWithGmail(String email, String subject, String body) {
        String accessToken = refreshAccessToken();
        try {
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime(email, subject, body).getBytes(StandardCharsets.UTF_8));
            client.post().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("raw", raw))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException failure) {
            throw deliveryFailure("gmail-send", failure);
        }
    }

    private String refreshAccessToken() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("client_id=" + encode(gmailClientId)
                            + "&client_secret=" + encode(gmailClientSecret)
                            + "&refresh_token=" + encode(gmailRefreshToken)
                            + "&grant_type=refresh_token")
                    .retrieve().body(Map.class);
            Object token = response == null ? null : response.get("access_token");
            if (!(token instanceof String value) || value.isBlank()) {
                log.warn("Email delivery failed: stage=gmail-token reason=missing-access-token");
                throw new MailDeliveryFailure();
            }
            return value;
        } catch (RestClientException failure) {
            throw deliveryFailure("gmail-token", failure);
        }
    }

    private MailDeliveryFailure deliveryFailure(String stage, RestClientException failure) {
        int status = failure instanceof RestClientResponseException response ? response.getStatusCode().value() : 0;
        String reason = status == 0 ? "transport-error" : "provider-rejected";
        // Only allowlisted OAuth codes are logged, never provider descriptions, tokens or recipients.
        if (stage.equals("gmail-token") && failure instanceof RestClientResponseException response) {
            try {
                String code = new ObjectMapper().readTree(response.getResponseBodyAsString()).path("error").asText();
                if (java.util.Set.of("invalid_grant", "invalid_client", "unauthorized_client", "invalid_scope").contains(code))
                    reason = code;
            } catch (Exception ignored) { /* A non-JSON response still has a safe status code. */ }
        }
        log.warn("Email delivery failed: stage={} status={} reason={}", stage, status, reason);
        return new MailDeliveryFailure();
    }

    private static final class MailDeliveryFailure extends IllegalStateException {
        private MailDeliveryFailure() { super(DELIVERY_ERROR); }
    }

    private String mime(String email, String subject, String body) {
        return "From: HotDevJobs <" + gmailSender + ">\r\n"
                + "To: " + email + "\r\n"
                + "Subject: " + subject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + body;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
