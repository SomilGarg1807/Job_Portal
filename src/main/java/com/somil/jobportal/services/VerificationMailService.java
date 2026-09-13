package com.somil.jobportal.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class VerificationMailService {
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
        if (gmailConfigured()) {
            sendWithGmail(email, code);
            return;
        }
        if (resendApiKey.isBlank() || resendSender.isBlank())
            throw new IllegalStateException("Email verification is temporarily unavailable. Please try again later.");
        try {
            client.post().uri("https://api.resend.com/emails").header("Authorization", "Bearer " + resendApiKey)
                    .header("Idempotency-Key", deliveryId)
                    .body(Map.of("from", resendSender, "to", List.of(email), "subject", "Verify your HotDevJobs email",
                            "text", "Your HotDevJobs verification code is: " + code
                                    + "\n\nThis code expires in 10 minutes. Do not share it."
                                    + "\nIf you did not request an account, you can ignore this email."))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException failure) {
            // Provider errors can contain recipient details; never expose/log the response body.
            throw new IllegalStateException("We could not send the verification email. Please try again later.");
        }
    }

    private boolean gmailConfigured() {
        return !gmailClientId.isBlank() && !gmailClientSecret.isBlank()
                && !gmailRefreshToken.isBlank() && !gmailSender.isBlank();
    }

    private void sendWithGmail(String email, String code) {
        try {
            String accessToken = refreshAccessToken();
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime(email, code).getBytes(StandardCharsets.UTF_8));
            client.post().uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("raw", raw))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException failure) {
            throw new IllegalStateException("We could not send the verification email. Please try again later.");
        }
    }

    private String refreshAccessToken() {
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
            throw new IllegalStateException("Email verification is temporarily unavailable. Please try again later.");
        }
        return value;
    }

    private String mime(String email, String code) {
        return "From: HotDevJobs <" + gmailSender + ">\r\n"
                + "To: " + email + "\r\n"
                + "Subject: Verify your HotDevJobs email\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + "Your HotDevJobs verification code is: " + code
                + "\r\n\r\nThis code expires in 10 minutes. Do not share it."
                + "\r\nIf you did not request an account, you can ignore this email.";
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
