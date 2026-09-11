package com.somil.jobportal.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
public class VerificationMailService {
    private final String apiKey;
    private final String sender;
    private final RestClient client;
    public VerificationMailService(@Value("${RESEND_API_KEY:}") String apiKey,
                                   @Value("${EMAIL_FROM:}") String sender) {
        this.apiKey = apiKey;
        this.sender = sender;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        client = RestClient.builder().baseUrl("https://api.resend.com").requestFactory(factory).build();
    }
    public void send(String email, String code, String deliveryId) {
        if (apiKey.isBlank() || sender.isBlank())
            throw new IllegalStateException("Email verification is temporarily unavailable. Please try again later.");
        try {
            client.post().uri("/emails").header("Authorization", "Bearer " + apiKey)
                    .header("Idempotency-Key", deliveryId)
                    .body(Map.of("from", sender, "to", List.of(email), "subject", "Verify your HotDevJobs email",
                            "text", "Your HotDevJobs verification code is: " + code
                                    + "\n\nThis code expires in 10 minutes. Do not share it."
                                    + "\nIf you did not request an account, you can ignore this email."))
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.RestClientException failure) {
            // Provider errors can contain recipient details; never expose/log the response body.
            throw new IllegalStateException("We could not send the verification email. Please try again later.");
        }
    }
}
